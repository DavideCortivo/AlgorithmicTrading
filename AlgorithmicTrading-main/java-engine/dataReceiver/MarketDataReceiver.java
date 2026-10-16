package dataReceiver;
/**
 * This class acts as the initial entry point for all real-time market data 
 * arriving from the Interactive Brokers API (via IBWrapperImpl).
 * Its responsibility is to receive the raw tick and route it to the 
 * DataCleaner, ensuring a strict separation of concerns in the data pipeline.
 * 
 * @author Davide Cortivo
 */
public class MarketDataReceiver {
	
	// we use the dependency injection here to pass real-time market data to the following class: DataCleaner
	private final DataCleaner dataCleaner;
	// dependency injection in the constructor
	
	/**
	 * Constructor
	 * * @param dataCleaner The component responsible for filtering and validating the raw data.
	 */
	public MarketDataReceiver(DataCleaner dataCleaner) {
		this.dataCleaner = dataCleaner;
	}

	
	/**
	 * Receives a raw price update from the broker and forwards it for cleaning.
	 * * @param tickerId The unique internal identifier for the asset (e.g., 1 for Asset A, 2 for Asset B).
	 * @param field    The type of price update (e.g., 1 for Bid, 2 for Ask, 4 for Last).
	 * @param price    The raw decimal price received from the exchange.
	 */
	
	public void onPriceUpdate(int tickerId, int field, double price) {
		if (dataCleaner != null) {
            dataCleaner.processRawTick(tickerId, field, price);
        }
    
    }
	
	
	
}
