package dataReceiver;

import java.util.List;
import orderManagement.OrderManager;
import orderManagement.Executions;
import riskManagement.RiskManager;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ib.client.Bar;
import com.ib.client.CommissionAndFeesReport;
import com.ib.client.Contract;
import com.ib.client.ContractDescription;
import com.ib.client.ContractDetails;
import com.ib.client.Decimal;
import com.ib.client.DeltaNeutralContract;
import com.ib.client.DepthMktDataDescription;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.FamilyCode;
import com.ib.client.HistogramEntry;
import com.ib.client.HistoricalSession;
import com.ib.client.HistoricalTick;
import com.ib.client.HistoricalTickBidAsk;
import com.ib.client.HistoricalTickLast;
import com.ib.client.NewsProvider;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.PriceIncrement;
import com.ib.client.SoftDollarTier;
import com.ib.client.TickAttrib;
import com.ib.client.TickAttribBidAsk;
import com.ib.client.TickAttribLast;
import com.ib.client.protobuf.AccountDataEndProto.AccountDataEnd;
import com.ib.client.protobuf.AccountSummaryEndProto.AccountSummaryEnd;
import com.ib.client.protobuf.AccountSummaryProto.AccountSummary;
import com.ib.client.protobuf.AccountUpdateMultiEndProto.AccountUpdateMultiEnd;
import com.ib.client.protobuf.AccountUpdateMultiProto.AccountUpdateMulti;
import com.ib.client.protobuf.AccountUpdateTimeProto.AccountUpdateTime;
import com.ib.client.protobuf.AccountValueProto.AccountValue;
import com.ib.client.protobuf.CompletedOrderProto.CompletedOrder;
import com.ib.client.protobuf.CompletedOrdersEndProto.CompletedOrdersEnd;
import com.ib.client.protobuf.ConfigResponseProto.ConfigResponse;
import com.ib.client.protobuf.ContractDataEndProto.ContractDataEnd;
import com.ib.client.protobuf.ContractDataProto.ContractData;
import com.ib.client.protobuf.CurrentTimeInMillisProto.CurrentTimeInMillis;
import com.ib.client.protobuf.CurrentTimeProto.CurrentTime;
import com.ib.client.protobuf.DisplayGroupListProto.DisplayGroupList;
import com.ib.client.protobuf.DisplayGroupUpdatedProto.DisplayGroupUpdated;
import com.ib.client.protobuf.ErrorMessageProto.ErrorMessage;
import com.ib.client.protobuf.ExecutionDetailsEndProto.ExecutionDetailsEnd;
import com.ib.client.protobuf.ExecutionDetailsProto.ExecutionDetails;
import com.ib.client.protobuf.FamilyCodesProto.FamilyCodes;
import com.ib.client.protobuf.FundamentalsDataProto.FundamentalsData;
import com.ib.client.protobuf.HeadTimestampProto.HeadTimestamp;
import com.ib.client.protobuf.HistogramDataProto.HistogramData;
import com.ib.client.protobuf.HistoricalDataEndProto.HistoricalDataEnd;
import com.ib.client.protobuf.HistoricalDataProto.HistoricalData;
import com.ib.client.protobuf.HistoricalDataUpdateProto.HistoricalDataUpdate;
import com.ib.client.protobuf.HistoricalNewsEndProto.HistoricalNewsEnd;
import com.ib.client.protobuf.HistoricalNewsProto.HistoricalNews;
import com.ib.client.protobuf.HistoricalScheduleProto.HistoricalSchedule;
import com.ib.client.protobuf.HistoricalTicksBidAskProto.HistoricalTicksBidAsk;
import com.ib.client.protobuf.HistoricalTicksLastProto.HistoricalTicksLast;
import com.ib.client.protobuf.HistoricalTicksProto.HistoricalTicks;
import com.ib.client.protobuf.ManagedAccountsProto.ManagedAccounts;
import com.ib.client.protobuf.MarketDataTypeProto.MarketDataType;
import com.ib.client.protobuf.MarketDepthExchangesProto.MarketDepthExchanges;
import com.ib.client.protobuf.MarketDepthL2Proto.MarketDepthL2;
import com.ib.client.protobuf.MarketDepthProto.MarketDepth;
import com.ib.client.protobuf.MarketRuleProto.MarketRule;
import com.ib.client.protobuf.NewsArticleProto.NewsArticle;
import com.ib.client.protobuf.NewsBulletinProto.NewsBulletin;
import com.ib.client.protobuf.NewsProvidersProto.NewsProviders;
import com.ib.client.protobuf.NextValidIdProto.NextValidId;
import com.ib.client.protobuf.OpenOrderProto.OpenOrder;
import com.ib.client.protobuf.OpenOrdersEndProto.OpenOrdersEnd;
import com.ib.client.protobuf.OrderBoundProto.OrderBound;
import com.ib.client.protobuf.OrderStatusProto.OrderStatus;
import com.ib.client.protobuf.PnLProto.PnL;
import com.ib.client.protobuf.PnLSingleProto.PnLSingle;
import com.ib.client.protobuf.PortfolioValueProto.PortfolioValue;
import com.ib.client.protobuf.PositionEndProto.PositionEnd;
import com.ib.client.protobuf.PositionMultiEndProto.PositionMultiEnd;
import com.ib.client.protobuf.PositionMultiProto.PositionMulti;
import com.ib.client.protobuf.PositionProto.Position;
import com.ib.client.protobuf.RealTimeBarTickProto.RealTimeBarTick;
import com.ib.client.protobuf.ReceiveFAProto.ReceiveFA;
import com.ib.client.protobuf.ReplaceFAEndProto.ReplaceFAEnd;
import com.ib.client.protobuf.RerouteMarketDataRequestProto.RerouteMarketDataRequest;
import com.ib.client.protobuf.RerouteMarketDepthRequestProto.RerouteMarketDepthRequest;
import com.ib.client.protobuf.ScannerDataProto.ScannerData;
import com.ib.client.protobuf.ScannerParametersProto.ScannerParameters;
import com.ib.client.protobuf.SecDefOptParameterEndProto.SecDefOptParameterEnd;
import com.ib.client.protobuf.SecDefOptParameterProto.SecDefOptParameter;
import com.ib.client.protobuf.SmartComponentsProto.SmartComponents;
import com.ib.client.protobuf.SoftDollarTiersProto.SoftDollarTiers;
import com.ib.client.protobuf.SymbolSamplesProto.SymbolSamples;
import com.ib.client.protobuf.TickByTickDataProto.TickByTickData;
import com.ib.client.protobuf.TickGenericProto.TickGeneric;
import com.ib.client.protobuf.TickNewsProto.TickNews;
import com.ib.client.protobuf.TickOptionComputationProto.TickOptionComputation;
import com.ib.client.protobuf.TickPriceProto.TickPrice;
import com.ib.client.protobuf.TickReqParamsProto.TickReqParams;
import com.ib.client.protobuf.TickSizeProto.TickSize;
import com.ib.client.protobuf.TickSnapshotEndProto.TickSnapshotEnd;
import com.ib.client.protobuf.TickStringProto.TickString;
import com.ib.client.protobuf.UpdateConfigResponseProto.UpdateConfigResponse;
import com.ib.client.protobuf.UserInfoProto.UserInfo;
import com.ib.client.protobuf.VerifyCompletedProto.VerifyCompleted;
import com.ib.client.protobuf.VerifyMessageApiProto.VerifyMessageApi;
import com.ib.client.protobuf.WshEventDataProto.WshEventData;
import com.ib.client.protobuf.WshMetaDataProto.WshMetaData;


/**
 * Core asynchronous callback wrapper for the Interactive Brokers TWS API.
 * 
 * This class implements the EWrapper interface and acts as an event router.
 * It intercepts raw asynchronous messages from the TWS/Gateway and dispatches them 
 * to the appropriate domain-specific managers (Market Data, Order Management, Risk Management).
 * Unused API callbacks are grouped and ignored at the bottom of the class.
 * 
 * @author Davide Cortivo
 */
public class IBWrapperImpl implements EWrapper{
	
	// field of the class for the dependency injection
	private MarketDataReceiver dataReceiver;
	private OrderManager orderManager;
	private RiskManager riskManager;
	private Executions execution; 
	private java.util.concurrent.CountDownLatch initLatch;
	private java.util.Map<Integer, Contract> contractRequests;
	private java.util.concurrent.CountDownLatch positionLatch;
	private volatile int nextOrderId = -1;
	// constructor
	
	public IBWrapperImpl () {
		
	}
	
	/**
     * Injects the core algorithmic modules required to process IBKR data streams.
     * 
     * @param dataReceiver Handles tick-by-tick market data pricing.
     * @param orderManager Handles order execution state and routing.
     * @param riskManager  Monitors real-time margin and account constraints.
     * @param execution    Tracks active portfolio positions.
     */
	public void setDependencies(MarketDataReceiver dataReceiver, OrderManager orderManager, 
			RiskManager riskManager, Executions execution) {
		this.dataReceiver = dataReceiver;
		this.orderManager = orderManager;
		this.riskManager = riskManager;
		this.execution = execution;
	}
	
	/**
     * Injects synchronization latches and reference maps for the startup initialization phase.
     */
	public void setInitializationData(java.util.concurrent.CountDownLatch latch, java.util.Map<Integer, Contract> requests) {
		this.initLatch = latch;
		this.contractRequests = requests;
	}
	
	/**
     * Injects the synchronization latch for the portfolio position download phase.
     */
	public void setPositionLatch(java.util.concurrent.CountDownLatch latch) {
		this.positionLatch = latch;
	}

	
	//methods
	
	/**
     * Market Data Stream: Receives real-time price updates for subscribed assets.
     * 
     * @param tickerId The unique request identifier assigned to the asset.
     * @param field    The tick type identifier (e.g., 1=BID, 2=ASK, 4=LAST).
     * @param price    The updated market price.
     * @param attribs  Tick attributes (e.g., pre-open conditions).
     */
	@Override
	public void tickPrice (int tickerId, int field, double price, TickAttrib attribs) { 
		if (dataReceiver != null) {                                                     
            dataReceiver.onPriceUpdate(tickerId, field, price);
        }	
	}
	
	/**
     * Returns the next valid order identifier provided by the IBKR server.
     * @return The next valid order ID.
     */
	public int getNextOrderId() {
		return nextOrderId;
	}
	
	/**
	 * Order flow and feedback on orders executions:
	 * This method is called asynchronously by IBKR whenever the status of 
	 * an active order changes (e.g., Submitted, Filled, Cancelled).
	 * 
	 * orderId: The unique ID we assigned to the order when we sent it.
	 * status: The current state of the order (e.g., "PreSubmitted", "Filled", "Cancelled").
	 * filled: The number of shares/contracts that have been executed so far.
	 * avgFillPrice: The average price at which the shares were executed.
	 */
	@Override
	public void orderStatus(int orderId, String status, Decimal filled,
	                        Decimal remaining, double avgFillPrice, long permId,
	                        int parentId, double lastFillPrice, int clientId,
	                        String whyHeld, double mktCapPrice) {
		//check if the OrderManager is instantiated before routing the event.
	    if (orderManager != null) {
	        orderManager.onOrderStatusChanged(orderId, status, filled, avgFillPrice);
	    }
	}
	
	
	/**
	 * risk flow informations, margins and cash:
	 * 
	 * This method receives continuous updates regarding the account's balances, 
	 * margins, and available funds.
	 * 
	 * key: The specific metric being updated (e.g., "NetLiquidation", "AvailableFunds", "InitMarginReq").
	 * val: The value of the metric (provided as a String, needs parsing in the RiskManager).
	 * currency: The currency of the specific balance (e.g., "USD", "EUR", "BASE").
	 * 
	 */
	@Override
    public void updateAccountValue(String key, String val, String currency, String accountName) {
		// Route the financial data to the RiskManager for real-time margin checks.
        if (riskManager != null) {
            riskManager.currentAccountUpdates(key, val, currency);
        }
    }
	
	
	/**
	 * Error management:
	 * 
	 * This callback receives all errors and warning messages from the TWS/Gateway.
	 * NOTE: In IBKR API, not all "errors" are actual failures; some are just 
	 * informational messages (e.g., connection status updates).
	 * 
	 * id: The tickerId or orderId that caused the error. (-1 if it's a system-wide message).
	 * errorCode: IBKR specific numeric code (e.g., 202 = Order Cancelled, 1102 = Connection Restored).
	 * errorMsg: The human-readable description of the event.
	 */
	@Override
	public void error(int id, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
	    System.err.println("IBKR Error [" + errorCode + "] per l'id " + id + " (Time: " + errorTime + "): " + errorMsg);
	    
	    // Route the error to the OrderManager. 
	    // This is crucial to let the algorithm know if an order was rejected (e.g., margin violation)
	    // so it can stop retrying or shut down safely.
	    if (orderManager != null) {
	        orderManager.onError(id, errorCode, errorMsg);
	    }
	}


// these methods are not implemented because in this moment we don't need them
	@Override
	public void tickSize(int tickerId, int field, Decimal size) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickOptionComputation(int tickerId, int field, int tickAttrib, double impliedVol, double delta,
			double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickGeneric(int tickerId, int tickType, double value) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickString(int tickerId, int tickType, String value) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
			double impliedFuture, int holdDays, String futureLastTradeDate, double dividendImpact,
			double dividendsToLastTradeDate) {
		// TODO Auto-generated method stub
		
	}



	


	/**
     * Receives order commission and margin requirement estimates when an order is flagged with whatIf=true.
     */
	@Override
	public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
if (order.whatIf() && orderManager != null) {
			
			double exactCommission = orderState.maxCommissionAndFees(); 
			
			if (exactCommission != Double.MAX_VALUE) {
				System.out.println("[IBWrapper] Ricevuti costi What-If per ID " + orderId + ": " + exactCommission + " " + orderState.marginCurrency());
				orderManager.evaluateWhatIfAndExecute(orderId, exactCommission);
				
			}
		}
		
	}



	@Override
	public void openOrderEnd() {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updatePortfolio(Contract contract, Decimal position, double marketPrice, double marketValue,
			double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateAccountTime(String timeStamp) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountDownloadEnd(String accountName) {
		// TODO Auto-generated method stub
		
	}

	/**
     * Updates the local sequence identifier for the next order submission.
     */
	@Override
	public void nextValidId(int orderId) {
		this.nextOrderId = orderId;
		System.out.println("[IBWrapper] Received Next Valid Order ID from TWS: " + orderId);
	
		
	}


	/**
     * Confirms the precise contract parameters (e.g., conId, multiplier) resolved by the IBKR servers.
     */
	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails) {
		if (contractRequests != null && contractRequests.containsKey(reqId)) {
			Contract originalContract = contractRequests.get(reqId);
			originalContract.conid(contractDetails.contract().conid());
			
			System.out.println("[IBWrapper] Identificated " + originalContract.symbol() + " -> conId: " + originalContract.conid());
		}
		
	}



	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub
		
	}


	/**
     * Signals the completion of a specific contract resolution request.
     */
	@Override
	public void contractDetailsEnd(int reqId) {
		if (initLatch != null) {
			initLatch.countDown();
		}
		
	}



	public void execDetails(int reqId, Contract contract, Execution execution) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void execDetailsEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateMktDepth(int tickerId, int position, int operation, int side, double price, Decimal size) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price,
			Decimal size, boolean isSmartDepth) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void managedAccounts(String accountsList) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void receiveFA(int faDataType, String xml) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalData(int reqId, Bar bar) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void scannerParameters(String xml) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark,
			String projection, String legsStr) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void scannerDataEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close, Decimal volume,
			Decimal wap, int count) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void currentTime(long time) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void fundamentalData(int reqId, String data) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickSnapshotEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void marketDataType(int reqId, int marketDataType) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void commissionAndFeesReport(CommissionAndFeesReport commissionAndFeesReport) {
		// TODO Auto-generated method stub
		
	}


	/**
     * Downloads existing portfolio inventory upon connection.
     */
	@Override
	public void position(String account, Contract contract, Decimal pos, double avgCost) {
		if (execution != null) {
			execution.updatePositionCache(contract.symbol(), pos);
		}
		
	}


	/**
     * Signals the completion of the portfolio position download phase.
     */
	@Override
	public void positionEnd() {
		System.out.println("[IBWrapper] Download of starting positions completed.");
		
		if (positionLatch != null) {
			positionLatch.countDown();
		}
		
	}



	@Override
	public void accountSummary(int reqId, String account, String tag, String value, String currency) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountSummaryEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void verifyMessageAPI(String apiData) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void verifyCompleted(boolean isSuccessful, String errorText) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void verifyAndAuthMessageAPI(String apiData, String xyzChallenge) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void displayGroupList(int reqId, String groups) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void displayGroupUpdated(int reqId, String contractInfo) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void error(Exception e) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void error(String str) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void connectionClosed() {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void connectAck() {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void positionMulti(int reqId, String account, String modelCode, Contract contract, Decimal pos,
			double avgCost) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void positionMultiEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value,
			String currency) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountUpdateMultiEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId,
			String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void securityDefinitionOptionalParameterEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void familyCodes(FamilyCode[] familyCodes) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline,
			String extraData) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void smartComponents(int reqId, Map<Integer, Entry<String, Character>> theMap) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void newsProviders(NewsProvider[] newsProviders) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void newsArticle(int requestId, int articleType, String articleText) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalNewsEnd(int requestId, boolean hasMore) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void headTimestamp(int reqId, String headTimestamp) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void histogramData(int reqId, List<HistogramEntry> items) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalDataUpdate(int reqId, Bar bar) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void rerouteMktDataReq(int reqId, int conId, String exchange) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void pnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL,
			double value) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickByTickAllLast(int reqId, int tickType, long time, double price, Decimal size,
			TickAttribLast tickAttribLast, String exchange, String specialConditions) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, Decimal bidSize,
			Decimal askSize, TickAttribBidAsk tickAttribBidAsk) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickByTickMidPoint(int reqId, long time, double midPoint) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void orderBound(long permId, int clientId, int orderId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void completedOrder(Contract contract, Order order, OrderState orderState) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void completedOrdersEnd() {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void replaceFAEnd(int reqId, String text) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void wshMetaData(int reqId, String dataJson) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void wshEventData(int reqId, String dataJson) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalSchedule(int reqId, String startDateTime, String endDateTime, String timeZone,
			List<HistoricalSession> sessions) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void userInfo(int reqId, String whiteBrandingId) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void currentTimeInMillis(long timeInMillis) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void orderStatusProtoBuf(OrderStatus orderStatusProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void openOrderProtoBuf(OpenOrder openOrderProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void openOrdersEndProtoBuf(OpenOrdersEnd openOrdersEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void errorProtoBuf(ErrorMessage errorMessageProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void execDetailsProtoBuf(ExecutionDetails executionDetailsProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void execDetailsEndProtoBuf(ExecutionDetailsEnd executionDetailsEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void completedOrderProtoBuf(CompletedOrder completedOrderProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void completedOrdersEndProtoBuf(CompletedOrdersEnd completedOrdersEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void orderBoundProtoBuf(OrderBound orderBoundProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void contractDataProtoBuf(ContractData contractDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void bondContractDataProtoBuf(ContractData contractDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void contractDataEndProtoBuf(ContractDataEnd contractDataEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickPriceProtoBuf(TickPrice tickPriceProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickSizeProtoBuf(TickSize tickSizeProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickOptionComputationProtoBuf(TickOptionComputation tickOptionComputationProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickGenericProtoBuf(TickGeneric tickGenericProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickStringProtoBuf(TickString tickStringProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickSnapshotEndProtoBuf(TickSnapshotEnd tickSnapshotEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateMarketDepthProtoBuf(MarketDepth marketDepthProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateMarketDepthL2ProtoBuf(MarketDepthL2 marketDepthL2Proto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void marketDataTypeProtoBuf(MarketDataType marketDataTypeProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickReqParamsProtoBuf(TickReqParams tickReqParamsProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateAccountValueProtoBuf(AccountValue accounValueProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updatePortfolioProtoBuf(PortfolioValue portfolioValueProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateAccountTimeProtoBuf(AccountUpdateTime accountUpdateTimeProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountDataEndProtoBuf(AccountDataEnd accountDataEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void managedAccountsProtoBuf(ManagedAccounts managedAccountsProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void positionProtoBuf(Position positionProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void positionEndProtoBuf(PositionEnd positionEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountSummaryProtoBuf(AccountSummary accountSummaryProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountSummaryEndProtoBuf(AccountSummaryEnd accountSummaryEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void positionMultiProtoBuf(PositionMulti positionMultiProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void positionMultiEndProtoBuf(PositionMultiEnd positionMultiEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountUpdateMultiProtoBuf(AccountUpdateMulti accountUpdateMultiProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void accountUpdateMultiEndProtoBuf(AccountUpdateMultiEnd accountUpdateMultiEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalDataProtoBuf(HistoricalData historicalDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalDataUpdateProtoBuf(HistoricalDataUpdate historicalDataUpdateProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalDataEndProtoBuf(HistoricalDataEnd historicalDataEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void realTimeBarTickProtoBuf(RealTimeBarTick realTimeBarTickProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void headTimestampProtoBuf(HeadTimestamp headTimestampProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void histogramDataProtoBuf(HistogramData histogramDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalTicksProtoBuf(HistoricalTicks historicalTicksProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalTicksBidAskProtoBuf(HistoricalTicksBidAsk historicalTicksBidAskProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalTicksLastProtoBuf(HistoricalTicksLast historicalTicksLastProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickByTickDataProtoBuf(TickByTickData tickByTickDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateNewsBulletinProtoBuf(NewsBulletin newsBulletinProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void newsArticleProtoBuf(NewsArticle newsArticleProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void newsProvidersProtoBuf(NewsProviders newsProvidersProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalNewsProtoBuf(HistoricalNews historicalNewsProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalNewsEndProtoBuf(HistoricalNewsEnd historicalNewsEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void wshMetaDataProtoBuf(WshMetaData wshMetaDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void wshEventDataProtoBuf(WshEventData wshEventDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void tickNewsProtoBuf(TickNews tickNewsProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void scannerParametersProtoBuf(ScannerParameters scannerParametersProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void scannerDataProtoBuf(ScannerData scannerDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void fundamentalsDataProtoBuf(FundamentalsData fundamentalsDataProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void pnlProtoBuf(PnL pnlProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void pnlSingleProtoBuf(PnLSingle pnlSingleProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void receiveFAProtoBuf(ReceiveFA receiveFAProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void replaceFAEndProtoBuf(ReplaceFAEnd replaceFAEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void commissionAndFeesReportProtoBuf(
			com.ib.client.protobuf.CommissionAndFeesReportProto.CommissionAndFeesReport commissionAndFeesReportProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void historicalScheduleProtoBuf(HistoricalSchedule historicalScheduleProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void rerouteMarketDataRequestProtoBuf(RerouteMarketDataRequest rerouteMarketDataRequestProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void rerouteMarketDepthRequestProtoBuf(RerouteMarketDepthRequest rerouteMarketDepthRequestProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void secDefOptParameterProtoBuf(SecDefOptParameter secDefOptParameterProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void secDefOptParameterEndProtoBuf(SecDefOptParameterEnd secDefOptParameterEndProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void softDollarTiersProtoBuf(SoftDollarTiers softDollarTiersProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void familyCodesProtoBuf(FamilyCodes familyCodesProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void symbolSamplesProtoBuf(SymbolSamples symbolSamplesProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void smartComponentsProtoBuf(SmartComponents smartComponentsProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void marketRuleProtoBuf(MarketRule marketRuleProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void userInfoProtoBuf(UserInfo userInfoProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void nextValidIdProtoBuf(NextValidId nextValidIdProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void currentTimeProtoBuf(CurrentTime currentTimeProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void currentTimeInMillisProtoBuf(CurrentTimeInMillis currentTimeInMillisProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void verifyMessageApiProtoBuf(VerifyMessageApi verifyMessageApiProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void verifyCompletedProtoBuf(VerifyCompleted verifyCompletedProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void displayGroupListProtoBuf(DisplayGroupList displayGroupListProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void displayGroupUpdatedProtoBuf(DisplayGroupUpdated displayGroupUpdatedProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void marketDepthExchangesProtoBuf(MarketDepthExchanges marketDepthExchangesProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void configResponseProtoBuf(ConfigResponse configResponseProto) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public void updateConfigResponseProtoBuf(UpdateConfigResponse updateConfigResponseProto) {
		// TODO Auto-generated method stub
		
	}

}
	
	
	
	
	
	
	
	
	
	

