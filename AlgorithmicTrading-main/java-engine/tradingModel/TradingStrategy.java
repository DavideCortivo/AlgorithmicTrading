package tradingModel;

/**
 * TRADING STRATEGY
 * Extends the TradingModel marker interface.
 * Defines the standard behavioral contract that every live strategy must implement.
 * 
 * @author Davide Cortivo
 */

public interface TradingStrategy extends TradingModel{

	/**
     * The core engine of the strategy. Called at every price update.
     * It reads the latest data and updates internal mathematical indicators.
     */
    void evaluateMarketState();
	
    /**
     * Returns true if the mathematical model triggers a Long entry signal.
     */
    boolean hasLongSignal();

    /**
     * Returns true if the mathematical model triggers a Short entry signal.
     */
    boolean hasShortSignal();

    /**
     * Returns true if the strategy conditions dictate closing an open position.
     */
    boolean hasExitSignal();
	
	
	
}
