package riskManagement;



/**
 * RISK MANAGER
 * Concrete implementation of the RiskManagement interface.
 * This class acts as a quantitative engine to evaluate position sizing 
 * and historical tail risk without taking operational decisions.
 * 
 * @author Davide Cortivo
 */

public class RiskManager implements RiskManagement {
	
	// Internal state updated by Interactive Brokers via socket
	private volatile double netLiquidationValue = 0.0;
	private volatile double availableFunds = 0.0;

	private final int numberOfPairs;
	private final double investmentPart = 0.7;
	private final double maxCapitalForEachPair = 250000;

	
	/**
	 * Constructor for the Risk Manager.
	 * * @param numberOfPairs The total number of actively traded pairs.
	 */
	public RiskManager (int numberOfPairs) {
		this.numberOfPairs = numberOfPairs;
	}

	/**
	 * Dynamicaly calculates the capital allocated to a single pair.
	 * It uses an Equal Weighting Allocation strategy, applying the investment 
	 * fraction to the real-time net liquidation value.
	 * * @return The precise capital amount allocated to the pair in the base currency.
	 */
	private double getCapitalForEachPair() {
		if (numberOfPairs == 0) {
			return 0.0;
		} else {
			double theoreticalCapital = (this.netLiquidationValue*this.investmentPart)/this.numberOfPairs;
			return Math.min(theoreticalCapital, maxCapitalForEachPair);
		}	
	}

	/**
	 * Callback method triggered by the Interactive Brokers socket.
	 * It filters the incoming stream of account updates to intercept the real-time liquidity.
	 * @param key      The identifier of the account metric (e.g., "NetLiquidation").
	 * @param val      The value of the metric provided as a String.
	 * @param currency The currency associated with the value.
	 */
	@Override
	public void currentAccountUpdates(String key, String val, String currency) {
		// We only intercept the specific key representing the total account equity
				
					try {
						if (key.equals("NetLiquidation")) {
						// The API sends the value as a String, so we must parse it into a double
						this.netLiquidationValue = Double.parseDouble(val);
						}
						
						// cash available in this moment
					else if (key.equals("AvailableFunds")) {
							this.availableFunds = Double.parseDouble(val);
						}
						
					} catch (NumberFormatException e) {
						System.err.println("[RISK MANAGER] Warning: Unable to parse NetLiquidation value: " + val);
					}
				}
    
    

	/**
	 * Retrieves the current total liquidity of the account.
	 * * @return The latest net liquidation value captured from the broker.
	 */
	@Override
	public double getNetCash() {
		return this.netLiquidationValue;
	}

	/**
	 * Computes the required amount of shares for both legs to ensure 
	 * the strategy is strictly Beta-Neutral.
	 * * @param priceLeg1  The current market price of the first leg.
	 * @param priceLeg2  The current market price of the second leg.
	 * @param hedgeRatio The Beta coefficient derived from the cointegration analysis.
	 * @return A PairPositionSize object containing the exact share quantities.
	 */
	
	
	@Override
	public PairPositionSize computePositionSizes(double priceLeg1, double priceLeg2, double hedgeRatio) {
		// Failsafe: Avoid division by zero or errors if prices are missing
				if (priceLeg1 <= 0 || priceLeg2 <= 0 || hedgeRatio <= 0) {
					System.out.println("[RISK MANAGER] Insufficient capital allocated. Returned (0,0) size.");
					return new PairPositionSize(0, 0);
				}
		double allocatedCapital = this.getCapitalForEachPair();
		if (allocatedCapital > this.availableFunds) {
			System.out.printf("[RISK MANAGER] REJECTED: Requested capital (%.2f EUR) bigger than liquid cash available (%.2f EUR).%n", 
			                  allocatedCapital, this.availableFunds);
			return new PairPositionSize(0, 0); 
		}
		double dollarLeg1 = allocatedCapital / ((1.0 + hedgeRatio));
	    double quantity1 = dollarLeg1 / priceLeg1;
		
		//Convert to integers and pack them into our return object.
		
		int finalSizeLeg1 = (int) Math.floor(quantity1);
		int finalSizeLeg2 = (int) Math.round(finalSizeLeg1* hedgeRatio * (priceLeg1 / priceLeg2));
		return new PairPositionSize(finalSizeLeg1, finalSizeLeg2);
	}
	
	
	/**
	 * Calculates the Historical Expected Shortfall  
	 */

	@Override
	public double computeExpectedShortfall(double histES, double allocatedCapital) {
	return allocatedCapital* Math.abs(histES);
	}
}
