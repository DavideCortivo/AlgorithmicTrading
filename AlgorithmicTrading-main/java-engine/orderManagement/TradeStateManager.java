package orderManagement;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TRADE STATE MANAGER
 * Manages the local persistence of statistical regimes for open positions.
 * It freezes the directional parameters (Beta and Intercept) to avoid rebalancing costs,
 * and maintains a backup of the latest volatility metrics (Mean and StdDev) 
 * to recover "orphan" trades that might disappear from the daily Python screener.
 * 
 * @author Davide Cortivo
 */
public class TradeStateManager {
	private final String stateFilePath;
    private final Map<String, FrozenStructure> activeStates = new ConcurrentHashMap<>();

    /**
     * Immutable data structure representing the persisted trade state.
     */
    public static class FrozenStructure {
        public final double beta;
        public final double intercept;
        public final double lastMean;
        public final double lastStdDev;

        public FrozenStructure(double beta, double intercept, double lastMean, double lastStdDev) {
            this.beta = beta;
            this.intercept = intercept;
            this.lastMean = lastMean;
            this.lastStdDev = lastStdDev;
        }
    }

    /**
     * Constructor.
     * @param stateFilePath The absolute path where the state CSV should be saved/loaded.
     */
    public TradeStateManager(String stateFilePath) {
        this.stateFilePath = stateFilePath;
        loadStateFromFile();
    }

    /**
     * Captures and persists the statistical parameters when entering a new trade.
     * 
     * @param pairName  The symbol of the pair 
     * @param beta      The fixed hedge ratio (Scanner Beta).
     * @param intercept The fixed OLS intercept (Alpha).
     * @param mean      The starting spread mean 
     * @param stdDev    The starting spread standard deviation.
     */
    public void freezeTradeStructure(String pairName, double beta, double intercept, double mean, double stdDev) {
        activeStates.put(pairName, new FrozenStructure(beta, intercept, mean, stdDev));
        saveStateToFile();
        System.out.println("[STATE MANAGER] Parameters frozen for new position: " + pairName);
    }

    /**
     * Updates the backup volatility metrics for an already open trade.
     * Called every morning when fresh data arrives from the Python CSV.
     * 
     * @param pairName   The symbol of the pair.
     * @param newMean    The updated spread mean from Python.
     * @param newStdDev  The updated spread standard deviation from Python.
     */
    public void updateVolatilityBackup(String pairName, double newMean, double newStdDev) {
        FrozenStructure current = activeStates.get(pairName);
        if (current != null) {
            activeStates.put(pairName, new FrozenStructure(current.beta, current.intercept, newMean, newStdDev));
            saveStateToFile();
        }
    }

    /**
     * Deletes the local state from the disk once the trade is successfully closed.
     * 
     * @param pairName The symbol of the flattened pair.
     */
    public void releaseTradeStructure(String pairName) {
        if (activeStates.remove(pairName) != null) {
            saveStateToFile();
            System.out.println("[STATE MANAGER] Trade closed. Snapshot released for " + pairName);
        }
    }

    /**
     * Retrieves the active state for a specific pair. Returns null if not at market.
     */
    public FrozenStructure getFrozenStructure(String pairName) {
        return activeStates.get(pairName);
    }

    /**
     * Returns a copy of all active states. Crucial for recovering orphan trades.
     */
    public Map<String, FrozenStructure> getAllActiveStates() {
        return new HashMap<>(activeStates);
    }

    // INTERNAL DISK I/O METHODS

    private synchronized void saveStateToFile() {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(stateFilePath))) {
            bw.write("Pair,Beta,Intercept,LastMean,LastStdDev\n");
            for (Map.Entry<String, FrozenStructure> entry : activeStates.entrySet()) {
                FrozenStructure s = entry.getValue();
                bw.write(entry.getKey() + "," + s.beta + "," + s.intercept + "," + s.lastMean + "," + s.lastStdDev + "\n");
            }
        } catch (IOException e) {
            System.err.println("[STATE MANAGER CRITICAL] Cannot save local state: " + e.getMessage());
        }
    }

    private void loadStateFromFile() {
        Path path = Paths.get(stateFilePath);
        if (!Files.exists(path)) return;

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }
                String[] parts = line.split(",");
                if (parts.length == 5) {
                    activeStates.put(parts[0], new FrozenStructure(
                        Double.parseDouble(parts[1]), 
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Double.parseDouble(parts[4])
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[STATE MANAGER CRITICAL] Cannot load local state: " + e.getMessage());
        }
    }
}
