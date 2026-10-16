package orderManagement;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.ComboLeg;
import com.ib.client.Order;
import com.ib.client.TagValue;

import configurator.DataConfigurator;
import riskManagement.PairPositionSize;
import riskManagement.RiskManager;
import tradingModel.PairTrading;

/**
 * ORDER MANAGER
 * The brain of the Execution Management System. Evaluates signals, 
 * queries the Risk Manager, checks real profitability, and delegates 
 * actual IBKR routing to the Execution class.
 * 
 * @author Davide Cortivo
 */

public class OrderManager {

	private final RiskManager riskManager; 
	private final Executions execution; 
	private final TradeStateManager stateManager;
	// Thread-safe map to store pendingTrades while waiting for IBKR's What-If response
	private final ConcurrentHashMap<Integer, PendingTrade> pendingTrades = new ConcurrentHashMap<>();
	// Thread-safe set (implemented via Map) to prevent signal spamming and duplicate orders
	private final ConcurrentHashMap<String, Boolean> activePairsLock = new ConcurrentHashMap<>();
	// Tracks which order ID belongs to which pair to handle remote rejections/cancellations
	private final ConcurrentHashMap<Integer, String> orderToPairMap = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Long> cooldownMap = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, SignalType> orderSignalTracker = new ConcurrentHashMap<>();
	// Tracks pairs that are currently being closed to prevent EXIT spamming
	private final ConcurrentHashMap<String, Boolean> exitInProgress = new ConcurrentHashMap<>();
	public enum SignalType{ LONG, SHORT, EXIT}


	/**
	 * Inner class that acts as a temporary data holder for trades 
	 * waiting for the broker's What-If commission calculation.
	 */

	private static class PendingTrade {
		final PairTrading strategy;
		final SignalType signal;
		final PairPositionSize sizes;
		final double priceLeg1;
		final double priceLeg2;
		final double expectedGrossProfit;

		PendingTrade(PairTrading strategy, SignalType signal, PairPositionSize sizes, double priceLeg1, double priceLeg2, double expectedGrossProfit) {
			this.strategy = strategy;
			this.signal = signal;
			this.sizes = sizes;
			this.priceLeg1 = priceLeg1;
			this.priceLeg2 = priceLeg2;
			this.expectedGrossProfit = expectedGrossProfit;
		}
	}

	/**
	 * Constructor
	 * @param riskManager The risk evaluation engine
	 * @param execution   The module responsible for communicating with the EClientSocket
	 */

	public OrderManager(RiskManager riskManager, Executions execution, TradeStateManager stateManager) {
		this.riskManager = riskManager;
		this.execution = execution;
		this.stateManager = stateManager;
	}

	/**
	 * SYNCHRONIZATION
	 * Manually locks a pair if the system detects an open position upon startup.
	 * This prevents the bot from doubling positions if restarted during market hours.
	 */
	public void lockPairFromPortfolio(String pairName) {
		activePairsLock.put(pairName, true);
		System.out.println("[ORDER MANAGER] Synced with Portfolio: Locked " + pairName);
	}
	/**
	 * Calculates the Greatest Common Divisor (GCD) using the Euclidean algorithm.
	 * Required to simplify share ratios for IBKR's native BAG (Combo) orders.
	 */
	private int gcd(int a, int b) {
		if (b == 0) return a;
		return gcd(b, a % b);
	}

	/**
	 * Main entry point for trading signals. Evaluates risk limits, calculates expected profit,
	 * formats the atomic BAG order, and sends a simulated (What-If) request to the broker.
	 */
	public void processSignal (PairTrading strategy, SignalType signal, double priceLeg1, double priceLeg2, double currentSpread, double meanSpread) {
	
		String pairName = strategy.getPairName();
		DataConfigurator config = strategy.getConfig();

		// EXIT Signals
		if (signal == SignalType.EXIT) {
			
			if (activePairsLock.containsKey(pairName)|| execution.hasOpenPosition(config.getLeg1(), config.getLeg2())) {
				if (exitInProgress.putIfAbsent(pairName, true) != null) {
					return; 
				}
				// Only process the exit if the pair is currently locked (active in the market)
				System.out.println("[ORDER MANAGER] Routing EXIT orders for " + pairName);
				int exitOrderId = execution.routeExitOrder(strategy.getConfig()); 
				if (exitOrderId != -1) {
					// Order ID mapping, waiting for confirmation 
					orderToPairMap.put(exitOrderId, pairName); 
					orderSignalTracker.put(exitOrderId, SignalType.EXIT);
					
					String exitAlertMsg = String.format(
							"🛑 <b>CLOSING POSITION SENT (MKT)</b>%n" +
							"➖➖➖➖➖➖➖➖➖➖%n" +
							"🔹 <b>Pair:</b> %s%n" +
							"🔹 <b>Action:</b> EXIT Spread%n" +
							"➖➖➖➖➖➖➖➖➖➖%n" +
							"<i>Routing exit orders to IBKR...</i>", 
							pairName);
					
					TelegramNotifier.send(exitAlertMsg);
				} else {
					exitInProgress.remove(pairName);
				}
				
			}
			return;
		}
		
		// Avoid spamming if the trade was recently rejected for costs (60 seconds cooldown)
				long lastAttempt = cooldownMap.getOrDefault(pairName, 0L);
				if (System.currentTimeMillis() - lastAttempt < 60000) { 
					return;
				}

	   // GHOST PAIR GUARD: Block entry signals if the pair is not acceptable but still in the config. list (added with VIP Pass)
				if (!config.isAcceptable()) {
					return;
				}
		// ANTI-SPAM GUARD 
		// If the pair is already being evaluated or is at market, ignore new signals
		if (activePairsLock.putIfAbsent(pairName, true) != null) {
					return;
				}
		
		System.out.println("\n[ORDER MANAGER] Evaluating " + signal + " signal for " + pairName);
		
		double hedgeRatio = config.getHedgeRatio();
		PairPositionSize sizes = riskManager.computePositionSizes(priceLeg1, priceLeg2, hedgeRatio);
		

		// RISK MANAGEMENT: POSITION SIZING ---
		if (sizes.getSizeLeg1() == 0 || sizes.getSizeLeg2() == 0) {
			System.out.println("[REJECTED] Insufficient capital to build a Beta-Neutral position.");
			activePairsLock.remove(pairName); // Unlock the pair
			return;
		}

		// RISK MANAGEMENT: TAIL RISK (EXPECTED SHORTFALL)
		double size1 = sizes.getSizeLeg1();
		double size2 = sizes.getSizeLeg2();
		double tradedCapital = size1*priceLeg1 + size2*priceLeg2;
		
		double histES = riskManager.computeExpectedShortfall(config.getHistoricalES(), tradedCapital);
		double maxAllowedLoss = riskManager.getNetCash()*0.05;

		if(histES>maxAllowedLoss) {
			System.out.printf("[REJECTED] Tail Risk too high! ES: %.2f EUR (Max Allowed: %.2f EUR)%n", histES, maxAllowedLoss);
			activePairsLock.remove(pairName); // Unlock the pair
			return;
		}


		// GROSS PROFIT CALCULATION and WHAT-IF
		
		// Log-spread is a percentage deviation. Multiply it by the monetary exposure of Leg1! leg1Exposure is in euro
				double leg1Exposure = sizes.getSizeLeg1() * priceLeg1;
				double expectedGrossProfit = Math.abs(currentSpread - meanSpread) * leg1Exposure;
		
		int whatIfOrderId = execution.generateOrderId(); 
		
		// Track the order ID to its pair and store the trade data in memory
		orderToPairMap.put(whatIfOrderId, pairName);
		pendingTrades.put(whatIfOrderId, new PendingTrade(strategy, signal, sizes, priceLeg1, priceLeg2, expectedGrossProfit));
		
		// Calculate simplified ratios for the BAG order
				int gcdValue = gcd(sizes.getSizeLeg1(), sizes.getSizeLeg2());
				if (gcdValue == 0) gcdValue = 1; 

				int ratio1 = sizes.getSizeLeg1() / gcdValue;
				int ratio2 = sizes.getSizeLeg2() / gcdValue;
		
		Contract bagContract = createBagContract(config, signal, ratio1, ratio2);
		
		Order whatIfOrder = new Order();
		whatIfOrder.action("BUY"); 
		whatIfOrder.totalQuantity(Decimal.get(gcdValue));
		whatIfOrder.orderType("MKT");
		whatIfOrder.whatIf(true); 
		
		ArrayList<TagValue> smartComboRoutingParams = new ArrayList<>();
		smartComboRoutingParams.add(new TagValue("NonGuaranteed", "0"));
		whatIfOrder.smartComboRoutingParams(smartComboRoutingParams);

		// Delegate routing to the execution class
		execution.requestWhatIfCosts(whatIfOrderId, bagContract, whatIfOrder);	
	}

	/**
	 * EVALUATION AND FINAL JUDGMENT
	 * Called asynchronously when the Execution module gets the exact costs back from IBKR.
	 */
	public void evaluateWhatIfAndExecute(int orderId, double exactIbkrCommission) {

		// Retrieve the pending trade data linked to this order ID
		PendingTrade pt = pendingTrades.remove(orderId);
		if (pt == null) {
			return; // Order unknown or already processed
		}
	
		DataConfigurator config = pt.strategy.getConfig();
		String pairName = config.getPairName();

		// Calculate external algorithmic costs (State Taxes and Borrowing)
		double tobinTax = calculateTobinTax(pt.signal, config, pt.sizes, pt.priceLeg1, pt.priceLeg2);
		double borrowCost = calculateShortBorrowCost(pt.signal, config, pt.sizes, pt.priceLeg1, pt.priceLeg2);

		// Compute Net profit
		// exactIbkrCommission already contains the fee for BOTH legs thanks to the BAG order
		double totalCommisions = (exactIbkrCommission * 2.0)+ tobinTax + borrowCost;
		double expectedNetProfit = pt.expectedGrossProfit - totalCommisions;

		if (expectedNetProfit > 0) {
			System.out.printf("[APPROVED] Routing REAL Trade! Net Profit: +%.2f EUR%n", expectedNetProfit);
			// Freeze the exact statistical parameters used for the entry decision
            stateManager.freezeTradeStructure(
                pairName, 
                config.getHedgeRatio(), 
                config.getIntercept(),
                config.getMean(),
                config.getStdDev()
            );
			
            int realOrderId = execution.generateOrderId();
			
            //Transfer tracking to the new real ID to intercept potential remote rejections
			orderToPairMap.put(realOrderId, pairName);
			orderSignalTracker.put(realOrderId, pt.signal);
			orderToPairMap.remove(orderId); // Clean up the fake What-If ID
			
			
			// Recalculate GCD and ratios for the real execution
			int gcdValue = gcd(pt.sizes.getSizeLeg1(), pt.sizes.getSizeLeg2());
			if (gcdValue == 0) gcdValue = 1;
			int ratio1 = pt.sizes.getSizeLeg1() / gcdValue;
			int ratio2 = pt.sizes.getSizeLeg2() / gcdValue;
			
			
			Contract bagContract = createBagContract(config, pt.signal, ratio1, ratio2);
			Order realOrder = new Order();
			realOrder.action("BUY"); 
			realOrder.totalQuantity(Decimal.get(gcdValue));
			realOrder.orderType("MKT");
			realOrder.whatIf(false); // REAL execution
			
			ArrayList<TagValue> realSmartComboRoutingParams = new ArrayList<>();
			realSmartComboRoutingParams.add(new TagValue("NonGuaranteed", "0"));
			realOrder.smartComboRoutingParams(realSmartComboRoutingParams);
			
			execution.routeRealOrder(realOrderId, bagContract, realOrder);
			
			String directionMsg = pt.signal.name();
			String alertMsg = String.format(
					"🚀 <b>NEW ORDER SENT (MKT)</b>%n" +
					"➖➖➖➖➖➖➖➖➖➖%n" +
					"🔹 <b>Pair:</b> %s%n" +
					"🔹 <b>Direction:</b> %s Spread%n" +
					"🔹 <b>Exp. Net Profit:</b> +%.2f EUR%n" +
					"➖➖➖➖➖➖➖➖➖➖%n" +
					"<i>Routing order to IBKR..., waiting for response</i>", 
					pairName, directionMsg, expectedNetProfit);
			
			TelegramNotifier.send(alertMsg);
		} else {
			System.out.printf("[REJECTED] Friction eats the Alpha. Gross: %.2f | Costs: %.2f | Net: %.2f%n", 
					pt.expectedGrossProfit, totalCommisions, expectedNetProfit);
			cooldownMap.put(pairName, System.currentTimeMillis());
			activePairsLock.remove(pairName); // Release the lock
		}

	}

	/**
	 * Builds an atomic Combo Order (BAG) Contract.
	 * This prevents legging risk by forcing the exchange to execute both legs simultaneously.
	 */
	private Contract createBagContract(DataConfigurator config, SignalType signal, int ratio1, int ratio2) {
		Contract comboContract = new Contract();
		
		
		String sym1 = config.getLeg1().symbol();
		String sym2 = config.getLeg2().symbol();
		
		String bagSymbol;
		if (sym1.compareTo(sym2) < 0) {
			bagSymbol = sym1 + "," + sym2;
		} else {
			bagSymbol = sym2 + "," + sym1;
		}
		comboContract.symbol(bagSymbol);
		
		comboContract.secType("BAG");
		comboContract.currency("EUR");
		comboContract.exchange("SMART");

		ComboLeg leg1 = new ComboLeg();
		leg1.conid(config.getLeg1().conid());
		leg1.ratio(ratio1); 
		leg1.action(signal == SignalType.LONG ? "BUY" : "SELL");
		leg1.exchange(config.getLeg1().exchange());

		ComboLeg leg2 = new ComboLeg();
		leg2.conid(config.getLeg2().conid());
		leg2.ratio(ratio2);
		leg2.action(signal == SignalType.LONG ? "SELL" : "BUY"); 
		leg2.exchange(config.getLeg2().exchange());

		comboContract.comboLegs(new ArrayList<>());
		comboContract.comboLegs().add(leg1);
		comboContract.comboLegs().add(leg2);

		return comboContract;
	}

	/**
	 * Calculates the Financial Transaction Tax (Tobin Tax) based on the asset's jurisdiction.
	 */
	private double calculateTobinTax(SignalType signal, DataConfigurator config, 
			PairPositionSize sizes, double priceLeg1, double priceLeg2) {

		Contract legToTax = null;
		double grossValue = 0.0;

		// FTT applies strictly to the ACQUISITION (BUY leg) of the spread.
		if (signal == SignalType.LONG) {
			legToTax = config.getLeg1();
			grossValue = sizes.getSizeLeg1() * priceLeg1;
		} else if (signal == SignalType.SHORT) {
			legToTax = config.getLeg2();
			grossValue = sizes.getSizeLeg2() * priceLeg2;
		}

		if (legToTax == null || legToTax.primaryExch() == null) {
			return 0.0;
		}

		String exchange = legToTax.primaryExch().toUpperCase();

		// Italy FTT (0.10%)
		if (exchange.contains("MIL") || exchange.contains("BVME")) return grossValue * 0.0010;
		// Spain FTT (0.20%)
		else if (exchange.contains("BME") || exchange.contains("BMEX")) return grossValue * 0.0020;
		// France FTT (0.30%)
		else if (exchange.contains("SBF") || exchange.contains("PAR")) return grossValue * 0.0030;

		return 0.0;
	}


	/**
	 * Estimates the cost of holding the short position for the duration of the Half-Life.
	 */
	private double calculateShortBorrowCost(SignalType signal, DataConfigurator config, 
			PairPositionSize sizes, double priceLeg1, double priceLeg2) {

		double halfLifeDays = config.getHalfLife();
		double shortedValue = (signal == SignalType.LONG) ? (sizes.getSizeLeg2() * priceLeg2) : (sizes.getSizeLeg1() * priceLeg1);
		double borrowRate = 0.02; // This is an estimation, it could be higher or lower
		return shortedValue * (borrowRate * (halfLifeDays / 365.0));
	}

	/**
	 * Callback from the IBWrapper indicating a change in the order status.
	 * Acts as the primary feedback loop to free resources if an order is cancelled.
	 */

	public void onOrderStatusChanged(int orderId, String status, Decimal filled, double avgFillPrice) {
System.out.println("ORDER " + orderId + " UPDATED: " + status + " | Filled: " + filled + " @ " + avgFillPrice);
		
        //If the order is cancelled by the user or the system, release the pair lock
		if (status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Inactive")) {
			String pairName = orderToPairMap.remove(orderId);
			SignalType sig = orderSignalTracker.remove(orderId);
			
			if (pairName != null) {
				System.out.println("[CRITICAL] Order ID " + orderId + " (" + pairName + ") cancelled by the system. release the pair lock.");
				if (sig == SignalType.EXIT) {
					// Failed exit. Release just exitInProgress to retry the exit.
					// I do not remove activePairsLock, because the position is still open 
					exitInProgress.remove(pairName);
				} else {
					// The entry (LONG/SHORT) failed. release the pair lock.
					activePairsLock.remove(pairName);
					stateManager.releaseTradeStructure(pairName);
					cooldownMap.put(pairName, System.currentTimeMillis());
				}
				
				String actionStr = (sig != null) ? sig.name() : "UNKNOWN";
				String cancelMsg = String.format(
						"⚠️ <b>ORDER CANCELLED / INACTIVE</b>%n" +
						"➖➖➖➖➖➖➖➖➖➖%n" +
						"🔹 <b>Pair:</b> %s%n" +
						"🔹 <b>Action:</b> %s%n" +
						"🔹 <b>Status:</b> %s%n" +
						"➖➖➖➖➖➖➖➖➖➖%n" +
						"<i>Lock released. Trade aborted.</i>", 
						pairName, actionStr, status);
				TelegramNotifier.send(cancelMsg);
			}
		}
		
		else if (status.equalsIgnoreCase("Filled")) {
			String pairName = orderToPairMap.remove(orderId);
			SignalType sig = orderSignalTracker.remove(orderId);
			
			if (pairName != null) {
				System.out.println("[SUCCESS] Order ID " + orderId + " (" + pairName + ") filled to market.");
				
				if (sig == SignalType.EXIT) {
					// exit completed, released all the locks
					activePairsLock.remove(pairName);
					exitInProgress.remove(pairName);
					stateManager.releaseTradeStructure(pairName); 
				} else {
					// Entry completed (LONG/SHORT). 
					// IMPORTANT: I do not remove activePairsLock! Stay blocked to prevent other orders.
				}
				String actionStr = (sig != null) ? sig.name() : "UNKNOWN";
				String fillMsg = String.format(
						"✅ <b>ORDER FILLED!</b>%n" +
						"➖➖➖➖➖➖➖➖➖➖%n" +
						"🔹 <b>Pair:</b> %s%n" +
						"🔹 <b>Action:</b> %s%n" +
						"🔹 <b>Quantity:</b> %s%n" +
						"🔹 <b>Avg Price:</b> %.4f%n" +
						"➖➖➖➖➖➖➖➖➖➖", 
						pairName, actionStr, filled, avgFillPrice);
				TelegramNotifier.send(fillMsg);
			}
		}
	}

	
	/**
	 * Callback from the IBWrapper indicating a system or order error.
	 * Essential for intercepting margin rejections and releasing locks to prevent deadlocks.
	 */
	public void onError(int id, int errorCode, String errorMsg) {
System.out.println("SYSTEM ERROR[" + errorCode + "] for ID " + id + ": " + errorMsg);
		
		//IBKR Error codes related to rejected orders or insufficient margins
		// 201: Order rejected, 202: Cancelled, 104: TWS connection lost
		if (errorCode == 201 || errorCode == 202 || errorCode == 104) {
			String pairName = orderToPairMap.remove(id);
			SignalType sig = orderSignalTracker.remove(id);
			
			if (pairName != null) {
				System.out.println("[CRITICAL] Order ID " + id + " (" + pairName + ") failed.");
				
				if (sig == SignalType.EXIT) {
					// Exit failed: unlock just the exitInProgress
					exitInProgress.remove(pairName);
				} else {
					// Entry failed: unlock everything
					activePairsLock.remove(pairName);
					stateManager.releaseTradeStructure(pairName);
					pendingTrades.remove(id); 
					cooldownMap.put(pairName, System.currentTimeMillis());
				}
			}
		}
	}
}
