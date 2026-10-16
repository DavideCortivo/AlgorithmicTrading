package riskManagement;

/**
 * RISK MANAGEMENT INTERFACE
 * Acts as a pure mathematical engine. It does not make execution decisions; 
 * it only provides exact risk and sizing metrics to the Order Manager.
 * 
 * 
 * @author Davide Cortivo
 */

public interface RiskManagement {

	
/**
 * Receives the continuous stream of account updates from the broker
 * @param key   The account metric identifier
 * @param value   The value of the metric
 * @param currency  The currency of the value
 */
void currentAccountUpdates(String key, String value, String currency);


/**
 * Getter to read the current capital available 
 */
double getNetCash();

/**
 * Calculates the required amount of shares for both legs to ensure 
 * the portfolio remains strictly Beta-Neutral.
 * @param priceLeg1  The current market price of the first leg
 * @param priceLeg2  The current market price of the second leg
 * @param hedgeRatio The Beta coefficient derived from the cointegration analysis
 * @return A PairPositionSize object containing the exact share quantities
 */

PairPositionSize computePositionSizes(double priceLeg1, double priceLeg2, double hedgeRatio);


/**
 * compute the historical Expected Shortfall for the portfolio
 */

double computeExpectedShortfall(double histES, double allocatedCapital);



}
