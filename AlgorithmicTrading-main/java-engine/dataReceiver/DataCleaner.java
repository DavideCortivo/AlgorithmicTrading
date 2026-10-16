package dataReceiver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * DATA CLEANER
 * This class filters anomalous market data and implements a thread-safe Queue 
 * to act as a shock-absorber (buffer) against market data bursts (tick storms).
 * It uses a Producer-Consumer pattern to decouple data reception from data processing.
 * 
 * @author Davide Cortivo
 */
public class DataCleaner {

    // The next module as done before with dependency injection
    private final MarketData marketData;
    
    // The buffer queue. LinkedBlockingQueue is thread-safe and highly efficient.
    // We set a capacity of 100,000 to prevent OutOfMemory errors in extreme scenarios.
    private final BlockingQueue<RawTick> tickQueue = new LinkedBlockingQueue<>(100000);
    
    // Flag to control the life-cycle of our background processing thread
    private volatile boolean isRunning = true;
    private Thread consumerThread;
 
    public void stopEngine() {
        this.isRunning = false;
        if (consumerThread != null) {
            consumerThread.interrupt(); 
        }
        System.out.println("Engine stopped. Thread closing...");
    }

    /**
     * Constructor
     * @param marketData The in-memory database to forward cleaned data to.
     */
    public DataCleaner(MarketData marketData) {
        this.marketData = marketData;
        
        // As soon as the DataCleaner is created, we start the background engine
        startConsumerThread();
    }

 
     // PRODUCER SIDE (Called by MarketDataReceiver)
    
    
    /**
     * Receives the raw tick from the network and instantly pushes it into the queue.
     * This method executes incredibly fast, freeing up the network thread immediately.
     */
    public void processRawTick(int tickerId, int field, double price) {
        // Create a new envelope (RawTick) and try to put it in the queue
        RawTick tick = new RawTick(tickerId, field, price);
        
        // offer() inserts the element into the queue if it's not full.
        // If the queue is full (over 100k unprocessed ticks), it drops the tick to prevent a crash.
        boolean added = tickQueue.offer(tick);
        if (!added) {
            System.err.println("CRITICAL WARNING: Queue is full! Dropping tick for ID " + tickerId);
        }
    }


     // CONSUMER SIDE (Background processing engine)
    
    /**
     * Starts a dedicated background thread that constantly polls the queue 
     * and processes the ticks one by one at its own pace.
     */
    private void startConsumerThread() {
        this.consumerThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // take() pulls the first element from the queue. 
                    // If the queue is empty, the thread goes to sleep automatically (0 CPU usage).
                    RawTick tick = tickQueue.take();
                    
                    // Now we actually clean the data
                    cleanAndForward(tick);
                    
                } catch (InterruptedException e) {
                    System.err.println("Consumer thread interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                } catch (Exception e) {
  
                	System.err.println("[DATA CLEANER - ERROR] Unexpected exception processing tick. "
                            + "Worker will continue: " + e.getMessage());
                	e.printStackTrace();
                }
            }
        });
        
        // A daemon thread will automatically shut down when the main program stops
        consumerThread.setDaemon(true); 
        consumerThread.setName("DataCleaner-Worker");
        consumerThread.start();
    }
    
    /**
     * Applies quantitative filters to the tick. If valid, forwards it to MarketData.
     */
    private void cleanAndForward(RawTick tick) {
        // 1. Basic Filter: prices cannot be zero or negative
        if (tick.price <= 0) {
            // Bad print detected, discard it
            return; 
        }

       

        // 2. If the data survives the filters, forward it to the official database
        if (marketData != null) {
            marketData.updateCleanPrice(tick.tickerId, tick.field, tick.price);
        }
    }

  
    
    
     //INTERNAL DATA STRUCTURE (The "Envelope")
     
    /**
     * A simple internal class (immutable) to hold the raw data while it sits in the queue.
     */
    private static class RawTick {
        final int tickerId;
        final int field;
        final double price;

        RawTick(int tickerId, int field, double price) {
            this.tickerId = tickerId;
            this.field = field;
            this.price = price;
        }
    }
}
