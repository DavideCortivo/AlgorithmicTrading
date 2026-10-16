package orderManagement;

import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.TagValue;
import com.ib.client.EClientSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import configurator.DataConfigurator;


/**
 * EXECUTIONS
 * A generic, "dumb" execution engine. It routes pre-packaged Orders and Contracts 
 * to IBKR and manages the live synchronization of the portfolio positions to ensure safe exits.
 * 
 * @author Davide Cortivo
 */

public class Executions {

	private final EClientSocket clientSocket;
	private final AtomicInteger orderIdCounter;

	// Live-synchronized cache of open positions directly driven by IBKR EWrapper callbacks
	// Key: Stock Symbol | Value: Exact Decimal position size
	private final ConcurrentHashMap<String, Decimal> activePositionsCache = new ConcurrentHashMap<>();



	/**
	 * Constructor
	 * @param clientSocket The active connection socket to Interactive Brokers
	 * @param nextValidId  The initial valid order ID provided by the broker on startup
	 */

	public Executions(EClientSocket clientSocket, int nextValidId) {
		this.clientSocket = clientSocket;
		this.orderIdCounter = new AtomicInteger(nextValidId);
	}

	/**
	 * Extracts the exact share count currently held at the broker from the live-synchronized cache 
	 * and executes market orders in the opposite direction to perfectly flatten the position.
	 * @return 
	 */

	public int routeExitOrder(DataConfigurator config) {
		Contract leg1Contract = config.getLeg1();
		Contract leg2Contract = config.getLeg2();


		// Extract current real positions from our synchronized cache
		Decimal posLeg1 = activePositionsCache.getOrDefault(leg1Contract.symbol(), Decimal.ZERO);
		Decimal posLeg2 = activePositionsCache.getOrDefault(leg2Contract.symbol(), Decimal.ZERO);


		if (posLeg1.isZero() && posLeg2.isZero()) {
		    System.out.println("[EXECUTION] Aborting exit. No positions found.");
		    return -1;
		}
		
		// Defining converse actions
		String action1 = posLeg1.value().doubleValue() > 0 ? "SELL" : "BUY";
		String action2 = posLeg2.value().doubleValue() > 0 ? "SELL" : "BUY";

		// Creating exit combo contract
		Contract comboContract = new Contract();
		String bagSymbol = config.getLeg1().symbol().compareTo(config.getLeg2().symbol()) < 0 ? 
		    config.getLeg1().symbol() + "," + config.getLeg2().symbol() : 
		    config.getLeg2().symbol() + "," + config.getLeg1().symbol();

		comboContract.symbol(bagSymbol);
		comboContract.secType("BAG");
		comboContract.currency("EUR");
		comboContract.exchange("SMART");
		comboContract.comboLegs(new java.util.ArrayList<>());

		// Leg 1
		ComboLeg leg1 = new ComboLeg();
		leg1.conid(config.getLeg1().conid());
		leg1.ratio(Math.abs(posLeg1.value().intValue())); 
		leg1.action(action1);
		leg1.exchange(config.getLeg1().exchange());
		comboContract.comboLegs().add(leg1);

		// Leg 2
		ComboLeg leg2 = new ComboLeg();
		leg2.conid(config.getLeg2().conid());
		leg2.ratio(Math.abs(posLeg2.value().intValue()));
		leg2.action(action2);
		leg2.exchange(config.getLeg2().exchange());
		comboContract.comboLegs().add(leg2);

		
		int exitOrderId = generateOrderId();
		Order exitOrder = new Order();
		exitOrder.orderType("MKT");
		exitOrder.action("BUY"); 
		exitOrder.totalQuantity(Decimal.ONE); // Execute 1 unit of the BAG

		java.util.ArrayList<TagValue> smartComboRoutingParams = new java.util.ArrayList<>();
		smartComboRoutingParams.add(new TagValue("NonGuaranteed", "0"));
		exitOrder.smartComboRoutingParams(smartComboRoutingParams);
		
		System.out.println("            Routing EXIT Combo Order ID: " + exitOrderId);
		clientSocket.placeOrder(exitOrderId, comboContract, exitOrder);
		return exitOrderId;
	}

	/**
	 * Thread-safe generation of unique sequential order IDs required by IBKR.
	 * @return A unique integer order ID
	 */
	public int generateOrderId() {
		return orderIdCounter.getAndIncrement();
	}

	/**
	 * Updates the internal position cache. This method MUST be called by the 
	 * EWrapper implementation whenever a 'position' callback is triggered.
	 */
	public void updatePositionCache(String symbol, Decimal pos) {
		if (pos == null || pos.isZero()) {
			activePositionsCache.remove(symbol);
			System.out.println("[EXECUTION CACHE] Position cleared for symbol: " + symbol);
		} else {
			activePositionsCache.put(symbol, pos);
		}

	}


	/**
	 * Sends a simulated Market Order (What-If) to IBKR to calculate exact execution fees.
	 * Highly generic: works with STK, OPT, or BAG contracts.
	 */
	public void requestWhatIfCosts(int whatIfOrderId, Contract contract, Order whatIfOrder) {
		System.out.println("[EXECUTION] Requesting What-If costs for ID " + whatIfOrderId + " on " + contract.symbol());
		clientSocket.placeOrder(whatIfOrderId, contract, whatIfOrder);
	}		



	/**
	 * Routes live Market Orders to the exchange.
	 * Highly generic: acts purely as a transport layer.
	 */
	public void routeRealOrder(int orderId, Contract contract, Order realOrder) {
		System.out.println("[EXECUTION] Routing REAL order ID: " + orderId + " on " + contract.symbol());
		clientSocket.placeOrder(orderId, contract, realOrder);

	}
	
	
	public boolean hasOpenPosition(Contract leg1, Contract leg2) {
		Decimal pos1 = activePositionsCache.getOrDefault(leg1.symbol(), Decimal.ZERO);
		Decimal pos2 = activePositionsCache.getOrDefault(leg2.symbol(), Decimal.ZERO);
		return !pos1.isZero() || !pos2.isZero();
	}


}


