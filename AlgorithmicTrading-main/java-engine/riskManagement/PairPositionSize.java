package riskManagement;

/**
 * An immutable data transfer object carrying the calculated target share quantities 
 * for both legs of a statistical arbitrage pair.
 * 
 * @author Davide Cortivo
 */
public class PairPositionSize {
	private final int sizeLeg1;
    private final int sizeLeg2;

    /**
     * Constructs a new position sizing container.
     * 
     * @param sizeLeg1 The target share quantity for the primary leg (Asset Y).
     * @param sizeLeg2 The target share quantity for the hedging leg (Asset X).
     */
    public PairPositionSize(int sizeLeg1, int sizeLeg2) {
        this.sizeLeg1 = sizeLeg1;
        this.sizeLeg2 = sizeLeg2;
    }
    // getters
    public int getSizeLeg1() { return sizeLeg1; }
    public int getSizeLeg2() { return sizeLeg2; }

}
