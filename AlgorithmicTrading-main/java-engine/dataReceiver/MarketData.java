package dataReceiver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MARKET DATA (In-Memory Database)
 * This class acts as the central repository for all real-time market states.
 * It uses thread-safe data structures (ConcurrentHashMap) to allow the DataCleaner
 * to update prices in the background while the Trading Strategy reads them simultaneously.
 * 
 * 
 * @author Davide Cortivo
 */
public class MarketData {

    // Thread-safe maps to store the latest prices. Key = tickerId, Value = price.
    private final Map<Integer, Double> currentBids = new ConcurrentHashMap<>();
    private final Map<Integer, Double> currentAsks = new ConcurrentHashMap<>();
    private final Map<Integer, Double> currentLasts = new ConcurrentHashMap<>();

    
    private final Map<Integer, Long> lastBidTimestamps = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastAskTimestamps = new ConcurrentHashMap<>();
    private static final long MAX_STALE_TIMEOUT_MS = 120000;
    
    /**
     * Updates the internal database with a clean, validated price.
     * This method is called by the DataCleaner.
     * * @param tickerId The internal ID of the asset.
     * @param field    The IBKR field type (1=Bid, 2=Ask, 4=Last).
     * @param price    The validated price.
     */
    public void updateCleanPrice(int tickerId, int field, double price) {
long now = System.currentTimeMillis();
        
        switch (field) {
            case 1:  // Live Bid
            case 66: // Delayed Bid
                currentBids.put(tickerId, price);
                lastBidTimestamps.put(tickerId, now); // Update Bid
                break;
            case 2:  // Live Ask
            case 67: // Delayed Ask
                currentAsks.put(tickerId, price);
                lastAskTimestamps.put(tickerId, now); // Update Ask
                break;
            case 4:  // Live Last
            case 68: // Delayed Last
                currentLasts.put(tickerId, price);
                break;
            default:
                break;
        }
        
     
    }

   

     //DATA ACCESS METHODS (Used by the Trading Strategy / Pair Trading)
     
    private boolean isDataStale(int tickerId) {
    	long now = System.currentTimeMillis();
        long lastBid = lastBidTimestamps.getOrDefault(tickerId, 0L);
        long lastAsk = lastAskTimestamps.getOrDefault(tickerId, 0L);
        
        return (now - lastBid > MAX_STALE_TIMEOUT_MS) || (now - lastAsk > MAX_STALE_TIMEOUT_MS);
    }

    public double getBid(int tickerId) {
        // getOrDefault returns the price if it exists, or NaN if we haven't received it yet
        return currentBids.getOrDefault(tickerId, Double.NaN);
    }

    public double getAsk(int tickerId) {
        return currentAsks.getOrDefault(tickerId, Double.NaN);
    }

    public double getLast(int tickerId) {
        return currentLasts.getOrDefault(tickerId, Double.NaN);
    }

    /**
     * Calculates the Mid-Price
     * Formula: (Bid + Ask) / 2
     */
    public double getMidPrice(int tickerId) {
    	if (isDataStale(tickerId)) {
    		System.err.println("[MARKET DATA] Stale Data Warning: No recents updates for Ticker ID " + tickerId + ". Return NaN.");
    		return Double.NaN;
    	}
        double bid = currentBids.getOrDefault(tickerId, Double.NaN);
        double ask = currentAsks.getOrDefault(tickerId, Double.NaN);
        
        if (Double.isNaN(bid) || Double.isNaN(ask)) {
            return Double.NaN;
        }
        
        return (bid + ask) / 2.0;
    }

    /**
     * Calculates the current Bid-Ask spread.
     */
    public double getSpread(int tickerId) {
        double bid = currentBids.getOrDefault(tickerId, Double.NaN);
        double ask = currentAsks.getOrDefault(tickerId, Double.NaN);
        
        if (Double.isNaN(bid) || Double.isNaN(ask)) {
            return Double.NaN;
        }
        
        return ask - bid;
    }
}
