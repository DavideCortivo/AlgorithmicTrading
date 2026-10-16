import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.EReaderSignal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

import configurator.Configurator;
import configurator.DataConfigurator;
import dataReceiver.DataCleaner;
import dataReceiver.IBWrapperImpl;
import dataReceiver.MarketData;
import dataReceiver.MarketDataReceiver;
import orderManagement.OrderManager;
import orderManagement.TradeStateManager;
import riskManagement.RiskManager;
import tradingModel.PairTrading; 
import orderManagement.Executions;

/**
 * Main application entry point and runtime orchestrator.
 * 
 * Coordinates the 5-phase startup sequence:
 * 1. Configuration & model ingestion from Walk-Forward Analysis (WFA) results.
 * 2. Socket establishment & EReader background event dispatching.
 * 3. Asynchronous barrier synchronization (conId resolution & active inventory recovery).
 * 4. Market data streaming subscription & pair model instantiation.
 * 5. Periodic strategic evaluation loop with warm-up protection.
 * 
 * @author Davide Cortivo
 */
public class Engine {

	private static final String IB_HOST = "127.0.0.1";
	private static final int IB_PORT = 4002; // 7497 for Paper Trading TWS, 7496 for Live Trading, 4002 on IB Gateway
	private static final int CLIENT_ID = 1;

	public static void main(String[] args) {

		try {
			System.out.println("[PHASE 1] Loading configurations and initializing core modules...");
			String stateFilePath = "backtest_results/active_trades_state.csv";
			TradeStateManager tradeStateManager = new TradeStateManager(stateFilePath);
			
			// Load portfolio configurations from the Walk-Forward Analysis CSV
			String csvPath = "backtest_results/wfa_results_java_production.csv";
			List<DataConfigurator> portfolio = Configurator.loadAllPairs(csvPath, tradeStateManager);
			System.out.println("Loaded " + portfolio.size() + " acceptable pairs from CSV.");

			// Initialize the in-memory database and the data cleaning pipeline
			MarketData marketData = new MarketData();
			DataCleaner dataCleaner = new DataCleaner(marketData);
			MarketDataReceiver mktDataReceiver = new MarketDataReceiver(dataCleaner);
			

			// Initialize the Risk Management engine
			RiskManager riskManager = new RiskManager(portfolio.size());


			System.out.println("\n[PHASE 2] Connecting to Interactive Brokers...");
			
			// Instantiate the Wrapper without dependencies 
			IBWrapperImpl wrapper = new IBWrapperImpl(); 
			EReaderSignal readerSignal = new EJavaSignal();
			EClientSocket clientSocket = new EClientSocket(wrapper, readerSignal);

			// Establish physical connection to TWS / IB Gateway
			clientSocket.eConnect(IB_HOST, IB_PORT, CLIENT_ID);

			// Start the background network reading threads required by IBKR API
			final EReader reader = new EReader(clientSocket, readerSignal);
			reader.start();
			new Thread(() -> {
				while (clientSocket.isConnected()) {
					readerSignal.waitForSignal();
					try {
						reader.processMsgs();
					} catch (Exception e) {
						System.err.println("Network Thread Exception: " + e.getMessage());
					}
				}
			}).start();

			// Brief pause to allow the socket connection to stabilize
			Thread.sleep(2000);

			int validId = wrapper.getNextOrderId();
			if (validId <= 0) {
				validId = 1000; 
			}
			System.out.println("Starting Executions from Order ID: " + validId);
			
			// Instantiate execution modules now that the socket is active
			Executions execution = new Executions(clientSocket, validId);
			OrderManager orderManager = new OrderManager(riskManager, execution, tradeStateManager);

			// Inject the fully formed dependencies back into the Wrapper
			wrapper.setDependencies(mktDataReceiver, orderManager, riskManager, execution);


			System.out.println("\n[PHASE 3] Asynchronous Synchronization...");

			// Asynchronous download of Contract IDs (ConId) for BAG orders 
			CountDownLatch initLatch = new CountDownLatch(portfolio.size() * 2);
			Map<Integer, Contract> contractRequests = new ConcurrentHashMap<>();
			wrapper.setInitializationData(initLatch, contractRequests);

			int reqId = 1000;
			for (DataConfigurator config : portfolio) {
				contractRequests.put(reqId, config.getLeg1());
				clientSocket.reqContractDetails(reqId++, config.getLeg1());

				contractRequests.put(reqId, config.getLeg2());
				clientSocket.reqContractDetails(reqId++, config.getLeg2());
			}

			System.out.println("Awaiting contract details (ConIds) from exchange...");
			initLatch.await(); 
			System.out.println("All ConIds successfully resolved.");


			// Synchronize Open Positions 
			CountDownLatch positionLatch = new CountDownLatch(1);
			wrapper.setPositionLatch(positionLatch);

			clientSocket.reqPositions();
			System.out.println("Awaiting current portfolio positions...");
			positionLatch.await(); // BOT PAUSES HERE until positionEnd() callback is received


			// Subscribe to Account Updates (Cash and Margins) 
			clientSocket.reqAccountUpdates(true, "");


			System.out.println("\n[PHASE 4] Subscribing to Market Data streams and building Strategies...");

			// here you have to specify the type of data you want, for paper trading (delayed data) 
			// 1 = Live, 2 = Frozen, 3 = Delayed, 4 = Delayed Frozen
			clientSocket.reqMarketDataType(3); 
			System.out.println("Requested Delayed Market Data (Type 3) to avoid subscription errors.");
			
			
			// List to keep track of all active pair trading strategies
			List<PairTrading> activeStrategies = new ArrayList<>();
			int tickerId = 1;

			for (DataConfigurator config : portfolio) {
				// Assign a unique internal ID to each leg of the spread
				int idLeg1 = tickerId++;
				int idLeg2 = tickerId++;

				// Request real-time market data from the broker for these IDs
				clientSocket.reqMktData(idLeg1, config.getLeg1(), "", false, false, null);
				clientSocket.reqMktData(idLeg2, config.getLeg2(), "", false, false, null);

				// Instantiate the mathematical engine for this specific pair
				PairTrading strategy = new PairTrading(marketData, config, idLeg1, idLeg2);
				activeStrategies.add(strategy);

				System.out.println("Subscribed to market data for " + config.getPairName() + " (ID1: " + idLeg1 + ", ID2: " + idLeg2 + ")");
			}


			System.out.println("\n[PHASE 5] Starting the Strategic Heartbeat Engine...");

			System.out.println("Synchronizing OrderManager with open portfolio positions...");
			for (PairTrading strategy : activeStrategies) {
				DataConfigurator config = strategy.getConfig();
				if (execution.hasOpenPosition(config.getLeg1(), config.getLeg2())) {
					orderManager.lockPairFromPortfolio(config.getPairName());
				}
			}
			
			// Create a dedicated background thread to execute our evaluation loop
			ScheduledExecutorService strategyEngine = Executors.newSingleThreadScheduledExecutor();

			final long startTime = System.currentTimeMillis();
			final long WARMUP_PERIOD_MS = 60000;
			
			strategyEngine.scheduleAtFixedRate(() -> {
				try {
					for (PairTrading strategy : activeStrategies) {
						
						// Read updated prices and compute the current Z-Score
						strategy.evaluateMarketState();

						// If prices haven't arrived yet or market is closed (Z-Score is NaN), skip iteration
						if (Double.isNaN(strategy.getCurrentZScore())) {
							continue;
						}
						if (System.currentTimeMillis() - startTime < WARMUP_PERIOD_MS) {
			                System.out.printf("[WARM-UP] %s | Z-Score: %.2f%n", strategy.getPairName(), strategy.getCurrentZScore());
			                continue; 
			            }
						// 3. Extract the clean prices and spread metrics
						double priceLeg1 = marketData.getMidPrice(strategy.getLeg1Id());
						double priceLeg2 = marketData.getMidPrice(strategy.getLeg2Id());
						double currentSpread = strategy.getCurrentSpread();
						double meanSpread = strategy.getMeanSpread();

					System.out.printf("[RADAR LIVE] %s | P1: %.3f | P2: %.3f | Spread: %.4f | Z-Score: %.2f%n", 
						        strategy.getPairName(), priceLeg1, priceLeg2, currentSpread, strategy.getCurrentZScore());

						// DECISION TREE (Signal Evaluation)
						// The method signature is strictly market data. 
						// Risk parameters (like ES) are encapsulated within the 'strategy' object.
						if (strategy.hasExitSignal()) {
							orderManager.processSignal(strategy, OrderManager.SignalType.EXIT, priceLeg1, priceLeg2, currentSpread, meanSpread);
						} 
						else if (strategy.hasLongSignal()) {
							orderManager.processSignal(strategy, OrderManager.SignalType.LONG, priceLeg1, priceLeg2, currentSpread, meanSpread);
						} 
						else if (strategy.hasShortSignal()) {
							orderManager.processSignal(strategy, OrderManager.SignalType.SHORT, priceLeg1, priceLeg2, currentSpread, meanSpread);
						}
					}
				} catch (Exception e) {
					System.err.println("[CRITICAL] Error inside the strategic loop: " + e.getMessage());
					e.printStackTrace();
				}
			}, 0, 1, TimeUnit.SECONDS); // Delay 0, repeat every 1 second


			System.out.println("\n*** SYSTEM ARMED AND OPERATIONAL ***");
			System.out.println("Press CTRL+C in the console to initiate safe shutdown.");

			// Shutdown Hook to gracefully close connections and threads upon termination
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("\n[SYSTEM] Initiating shutdown procedure...");
				strategyEngine.shutdown();
				dataCleaner.stopEngine();

				// Unsubscribe from market data to avoid clogging IBKR servers
				int cancelId = 1;
				for (int i = 0; i < portfolio.size() * 2; i++) {
					clientSocket.cancelMktData(cancelId++);
				}

				clientSocket.eDisconnect();
				System.out.println("[SYSTEM] Disconnected from IBKR. Goodbye.");
			}));

		} catch (Exception e) {
			System.err.println("FATAL ERROR DURING INITIALIZATION: " + e.getMessage());
			e.printStackTrace();
		}
	}
}                  		
                                		
