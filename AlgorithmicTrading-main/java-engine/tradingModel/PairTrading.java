package tradingModel;




import configurator.DataConfigurator;
import dataReceiver.MarketData;


/**
 * This concrete class implements the interface TradingStrategy and provides the
 * mathematical engine for the strategy
 * 
 * @author Davide Cortivo
 */
public class PairTrading implements TradingStrategy{
	
	// DEPENDENCIES 
    private final MarketData marketData;
    private final DataConfigurator config;
    
    // To fetch the correct prices from MarketData, the strategy needs the numeric IDs
    private final int leg1Id;
    private final int leg2Id;
	
    // MATHEMATICAL STATE
    private double currentSpread;
    private double currentZScore;
    
    
    // Thresholds for the signals (typically derived from research, e.g., 2.0 and -2.0)
    private final double entryThreshold = 2.0;
    private final double exitThreshold = 0.5;
   
	
    /**
     * CONSTRUCTOR (Dependency Injection)
     * @param marketData The live in-memory database.
     * @param config The specific parameters for this pair (Leg1, Leg2, Beta).
     * @param leg1Id id associated with the first leg
     * @paramleg2Id id associated with the second leg
     */
    
    public PairTrading(MarketData marketData, DataConfigurator config, int leg1Id, int leg2Id) {
    	this.marketData = marketData;
    	this.config = config;
    	this.leg1Id = leg1Id;
    	this.leg2Id = leg2Id;
    	
    	this.currentSpread = Double.NaN;
    	this.currentZScore = Double.NaN;	
    	
    }
	
    /**
     * Ingests latest mid-market prices, evaluates the synthetic log-spread, and standardizes the Z-score.
     */
    @Override
    public void evaluateMarketState() {

    	// Fetch live prices from the in-memory database using the assigned IDs
    	double priceLeg1 = marketData.getMidPrice(leg1Id);
    	double priceLeg2 = marketData.getMidPrice(leg2Id);

    	// If the market is closed or illiquid, wait. Do not calculate ghost spreads.
    	if (priceLeg1 <=0 || priceLeg2 <=0 || Double.isNaN(priceLeg1) || Double.isNaN(priceLeg2)) {
    		currentZScore = Double.NaN;
    		return; 
    	}
    	
    	double logP1 = Math.log(priceLeg1);
    	double logP2 = Math.log(priceLeg2);
    	
    	// Compute the current synthetic spread
    	currentSpread = logP1 - (config.getHedgeRatio()*logP2 + config.getIntercept());

    	

    	// Compute real time Z-score
    	// First compute mean and standard deviation. We will use them to standardize currentSpread
    	
    		double mean = config.getMean();
    		double stdDev = config.getStdDev();

    		// Protect against division by zero in case of perfectly flat spreads
    		if (stdDev > 0.000001) {
    			currentZScore = (currentSpread-mean)/stdDev;
    		} else {
    			currentZScore = Double.NaN;
    		}
    	}
    

	@Override
	public boolean hasLongSignal() {
		// LONG SIGNAL: Spread is too low, we expect it to revert up.
        // Action: Buy Leg1, Short Leg2
		return !Double.isNaN(currentZScore) && currentZScore < -entryThreshold;
	}

	@Override
	public boolean hasShortSignal() {
		// SHORT SIGNAL: Spread is too high, we expect it to revert down.
        // Action: Short Leg1, Buy Leg2
	return !Double.isNaN(currentZScore) && currentZScore > entryThreshold;
	}

	@Override
	public boolean hasExitSignal() {
		// EXIT SIGNAL: Spread has reverted to the mean (Z-Score crosses 0.5)
		return (!Double.isNaN(currentZScore) && Math.abs(currentZScore) <= exitThreshold) || (!Double.isNaN(currentZScore) && Math.abs(currentZScore) >= 10);
	}
	
	
	// Getters methods
	public double getCurrentSpread() {
        return currentSpread;
    }

    public double getCurrentZScore() {
        return currentZScore;
    }
    
    public String getPairName() {
        return config.getPairName();
    }
    
    public DataConfigurator getConfig() {
        return this.config;
    }
	
    public int getLeg1Id() {
    	return leg1Id;
    }
    
    public int getLeg2Id() {
    	return leg2Id;
    }
	
    public double getMeanSpread() {
        
            return config.getMean();
    }
	

}
