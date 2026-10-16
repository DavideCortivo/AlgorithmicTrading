package configurator;


import com.ib.client.Contract;

/**
 * This interface contains the main methods to extract informations from the Python backtest
 * 
 * @author Davide Cortivo
 */

public interface DataConfigurator {
		
	/**
	 * This method returns the name of the pair 
	 */
	 String getPairName();
	 
	 /**
	  * this method, which is contract type (Contract IBK), returns the first leg of the pair
	  */
	 Contract getLeg1();
	 
	 /**
	  * this method, which is contract type (Contract IBK), returns the second leg of the pair
	  */
	 Contract getLeg2();
	
	
	 /**
	  * this method returns the beta of the pair, how many shares of Leg2 to hedge a share of Leg1
	  * @return
	  */
	 double getHedgeRatio();
	 
	 
	 /**
	  * this method returns true if the pair is cointegrated, false if it's not
	  * @return
	  */
	 boolean isAcceptable();
	 
	 
	 /**
	  * this method returns the half-life of the pair
	  */
	 double getHalfLife();
	 
	 
	 /**
	  * Get the historical expected shortfall 
	  */
	 double getHistoricalES();
	 
	 /**
	  * Get the daily spread mean
	  */
	 
	 double getMean();
	 
	 /**
	  * Get the daily spread std Dev
	  */
	 
	 double getStdDev();
	 
	 /**
	  * get the intercept of the regression
	  */
	 double getIntercept();
}
