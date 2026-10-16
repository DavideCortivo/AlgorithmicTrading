# AlgorithmicTrading

## ⚙️ Java Execution Engine Architecture

The live trading engine is built in Java and interfaces directly with the Interactive Brokers TWS API. It is designed with a strict modular architecture, separating network I/O, quantitative evaluation, risk management, and order routing. 

The engine ingests pre-calibrated econometric parameters via a standardized CSV format, making the execution framework completely decoupled from the external research pipeline.

### Core Orchestration
- **`Engine`**: The bootstrap orchestrator. It manages the 5-phase startup sequence: CSV ingestion, socket connection, asynchronous barrier synchronization (resolving ConIds and active portfolio inventory), market data subscription, and running the strategic heartbeat loop.

### Market Data Ingestion Pipeline (`dataReceiver`)
- **`IBWrapperImpl`**: The asynchronous event router. Implements the `EWrapper` interface to intercept raw network callbacks from IBKR and dispatches them to the appropriate domain managers.
- **`MarketDataReceiver`**: The entry boundary for real-time price ticks, instantly delegating them to the sanitization layer to unblock the network thread.
- **`DataCleaner`**: A Producer-Consumer engine backed by a thread-safe `BlockingQueue`. It acts as a shock-absorber against market tick storms, filtering anomalous prints before updating the cache.
- **`MarketData`**: A high-concurrency, in-memory pricing cache. Uses `ConcurrentHashMap` to allow non-blocking reads by the strategy engine while enforcing timestamp-based staleness validation.

### Configuration & State Ingestion (`configurator`)
- **`DataConfigurator` (Interface)**: Defines the contract for retrieving pre-calibrated econometric parameters (Beta, Alpha, Half-Life, Expected Shortfall).
- **`Configurator`**: The ingestion engine that parses an offline-generated configuration CSV (`wfa_results_java_production.csv`). It implements **Hybrid State Override** (locking beta/alpha for open trades) and **Orphan Trade Recovery** (reconstructing dropped pairs from local backups).

### Quantitative Engine (`tradingModel`)
- **`TradingStrategy` (Interface)**: Defines the standard operations for evaluating market states and triggering ternary execution signals.
- **`PairTrading`**: The mathematical core. Computes the synthetic log-spread in real-time, standardizes it into a Z-Score using rolling volatility, and generates Long/Short/Exit signals based on standard deviation boundaries.

### Risk & Sizing (`riskManagement`)
- **`RiskManagement` (Interface)**: Defines the contract for capital allocation and position sizing calculations.
- **`RiskManager`**: Monitors the account liquidity and computes initial position sizes at trade entry to ensure **Beta-Neutral** exposure. 
- **`PairPositionSize`**: An immutable Data Transfer Object (DTO) safely carrying the calculated integer share quantities for both legs.

### Order Execution & State Handling (`orderManagement`)
- **`OrderManager`**: The execution brain. Intercepts signals, simulates commissions (What-If), calculates external friction (Tobin Tax, Borrowing costs), and builds atomic `BAG` (Combo) orders. It uses concurrent locks to prevent signal spamming and duplicate entries.
- **`Executions`**: The decoupled transport layer. It translates internal strategy intents into raw IBKR `Order` and `Contract` objects, routes them to the exchange, and maintains a live-synchronized cache of the actual portfolio inventory.
- **`TradeStateManager`**: Handles local persistence (`active_trades_state.csv`). Freezes the directional entry parameters of active trades to prevent rebalancing whipsaws and maintains volatility backups to safely unwind positions if they drop off the screener.
- **`TelegramNotifier`**: An asynchronous notification engine built with Java's native `HttpClient`. It broadcasts HTML-formatted real-time trade alerts, order fills, and system warnings to a Telegram channel without blocking the main execution thread.

## ☁️ Cloud Deployment & Production Automation

The live trading engine is deployed on an **Oracle Cloud Infrastructure (OCI) Linux VPS**, operating as a fully autonomous daemon aligned with European equity market hours (CET/CEST).

### Automation Lifecycle
- **Headless Infrastructure:** Managed via `systemd` services and automated cron jobs. Interactive Brokers Gateway runs in headless mode using IBC with automated two-factor keep-alive authentication.
- **Pre-Market Parameter Re-calibration:** Before the market open (08:45 CET), an external Python research pipeline for Pair Trading automatically executes:
  * Pulls fresh end-of-day market closes.
  * Re-estimates rolling data for the engine.
  * Evaluates out-of-sample Walk-Forward robustness and exports production configurations (`wfa_results_java_production.csv`) for the Java engine.
- **Market Session Arming (09:00 CET):** The Java execution engine boots up, resolves exchange contract metadata, syncs current account margins, and engages the evaluation loop across market hours.
- **End-of-Day Shutdown (17:35 CET):** Triggered automatically post-market close via scheduler:
  * Gracefully terminates data feeds and tears down the evaluation thread pool.
  * Flushes and serializes active trade states to disk (`active_trades_state.csv`).
  * Disconnects the IBKR socket cleanly to avoid orphaned Gateway sessions.
