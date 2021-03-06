package cn.edu.tsinghua.iotdb.service;

import cn.edu.tsinghua.iotdb.auth.AuthException;
import cn.edu.tsinghua.iotdb.auth.AuthorityChecker;
import cn.edu.tsinghua.iotdb.auth.authorizer.IAuthorizer;
import cn.edu.tsinghua.iotdb.auth.authorizer.LocalFileAuthorizer;
import cn.edu.tsinghua.iotdb.conf.TsFileDBConstant;
import cn.edu.tsinghua.iotdb.conf.TsfileDBConfig;
import cn.edu.tsinghua.iotdb.conf.TsfileDBDescriptor;
import cn.edu.tsinghua.iotdb.engine.filenode.FileNodeManager;
import cn.edu.tsinghua.iotdb.exception.ArgsErrorException;
import cn.edu.tsinghua.iotdb.exception.FileNodeManagerException;
import cn.edu.tsinghua.iotdb.exception.PathErrorException;
import cn.edu.tsinghua.iotdb.metadata.ColumnSchema;
import cn.edu.tsinghua.iotdb.metadata.MManager;
import cn.edu.tsinghua.iotdb.metadata.MNode;
import cn.edu.tsinghua.iotdb.metadata.Metadata;
import cn.edu.tsinghua.iotdb.qp.QueryProcessor;
import cn.edu.tsinghua.iotdb.qp.exception.IllegalASTFormatException;
import cn.edu.tsinghua.iotdb.qp.exception.QueryProcessorException;
import cn.edu.tsinghua.iotdb.qp.executor.OverflowQPExecutor;
import cn.edu.tsinghua.iotdb.qp.logical.Operator;
import cn.edu.tsinghua.iotdb.qp.physical.PhysicalPlan;
import cn.edu.tsinghua.iotdb.qp.physical.crud.IndexQueryPlan;
import cn.edu.tsinghua.iotdb.qp.physical.crud.MultiQueryPlan;
import cn.edu.tsinghua.iotdb.qp.physical.sys.AuthorPlan;
import cn.edu.tsinghua.iotdb.query.aggregation.AggregateFunction;
import cn.edu.tsinghua.iotdb.query.aggregation.AggregationConstant;
import cn.edu.tsinghua.iotdb.query.management.ReadCacheManager;
import cn.edu.tsinghua.iotdb.queryV2.engine.control.QueryJobManager;
import cn.edu.tsinghua.service.rpc.thrift.*;
import cn.edu.tsinghua.tsfile.common.exception.ProcessorException;
import cn.edu.tsinghua.tsfile.timeseries.read.support.Path;
import cn.edu.tsinghua.tsfile.timeseries.readV2.query.QueryDataSet;
import org.apache.thrift.TException;
import org.apache.thrift.server.ServerContext;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Statement;
import java.util.*;

import static cn.edu.tsinghua.iotdb.qp.logical.Operator.OperatorType.INDEXQUERY;
import static cn.edu.tsinghua.iotdb.qp.logical.Operator.OperatorType.QUERY;

/**
 * Thrift RPC implementation at server side
 */

public class TSServiceImpl implements TSIService.Iface, ServerContext {

	private QueryProcessor processor = new QueryProcessor(new OverflowQPExecutor());
	// Record the username for every rpc connection. Username.get() is null if
	// login is failed.
	private ThreadLocal<String> username = new ThreadLocal<>();
	private ThreadLocal<HashMap<String, PhysicalPlan>> queryStatus = new ThreadLocal<>();
	private ThreadLocal<HashMap<String, QueryDataSet>> queryRet = new ThreadLocal<>();
	private ThreadLocal<DateTimeZone> timeZone = new ThreadLocal<>();
	private TsfileDBConfig config = TsfileDBDescriptor.getInstance().getConfig();

	private static final Logger LOGGER = LoggerFactory.getLogger(TSServiceImpl.class);

	public TSServiceImpl() throws IOException {

	}

	@Override
	public TSOpenSessionResp openSession(TSOpenSessionReq req) throws TException {
		LOGGER.info("{}: receive open session request from username {}",TsFileDBConstant.GLOBAL_DB_NAME, req.getUsername());

		boolean status;
		IAuthorizer authorizer = null;
		try {
			authorizer = LocalFileAuthorizer.getInstance();
		} catch (AuthException e) {
			throw new TException(e);
		}
		try {
			status = authorizer.login(req.getUsername(), req.getPassword());
		} catch (AuthException e) {
			status = false;
		}
		TS_Status ts_status;
		if (status) {
			ts_status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
			ts_status.setErrorMessage("login successfully.");
			username.set(req.getUsername());
			timeZone.set(config.timeZone);
			initForOneSession();
		} else {
			ts_status = new TS_Status(TS_StatusCode.ERROR_STATUS);
			ts_status.setErrorMessage("login failed. Username or password is wrong.");
		}
		TSOpenSessionResp resp = new TSOpenSessionResp(ts_status, TSProtocolVersion.TSFILE_SERVICE_PROTOCOL_V1);
		resp.setSessionHandle(new TS_SessionHandle(new TSHandleIdentifier(ByteBuffer.wrap(req.getUsername().getBytes()),
				ByteBuffer.wrap((req.getPassword().getBytes())))));
		LOGGER.info("{}: Login status: {}. User : {}",TsFileDBConstant.GLOBAL_DB_NAME, ts_status.getErrorMessage(), req.getUsername());

		return resp;
	}

	private void initForOneSession() {
		queryStatus.set(new HashMap<>());
		queryRet.set(new HashMap<>());
	}

	@Override
	public TSCloseSessionResp closeSession(TSCloseSessionReq req) throws TException {
		LOGGER.info("{}: receive close session",TsFileDBConstant.GLOBAL_DB_NAME);
		TS_Status ts_status;
		if (username.get() == null) {
			ts_status = new TS_Status(TS_StatusCode.ERROR_STATUS);
			ts_status.setErrorMessage("Has not logged in");
			if(timeZone.get() != null){
				timeZone.remove();
			}
		} else {
			ts_status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
			username.remove();
			if(timeZone.get() != null){
				timeZone.remove();
			}
		}
		return new TSCloseSessionResp(ts_status);
	}

	@Override
	public TSCancelOperationResp cancelOperation(TSCancelOperationReq req) throws TException {
		return new TSCancelOperationResp(new TS_Status(TS_StatusCode.SUCCESS_STATUS));
	}

	@Override
	public TSCloseOperationResp closeOperation(TSCloseOperationReq req) throws TException {
		LOGGER.info("{}: receive close operation",TsFileDBConstant.GLOBAL_DB_NAME);
		try {
			ReadCacheManager.getInstance().unlockForOneRequest();
			QueryJobManager.getInstance().closeAllJobForOneQuery();
			clearAllStatusForCurrentRequest();
		} catch (ProcessorException | IOException e) {
			LOGGER.error("Error in closeOperation : {}", e.getMessage());
		}
		return new TSCloseOperationResp(new TS_Status(TS_StatusCode.SUCCESS_STATUS));
	}

	private void clearAllStatusForCurrentRequest() {
		if(this.queryRet.get() != null){
			this.queryRet.get().clear();
		}
		if(this.queryStatus.get() != null){
			this.queryStatus.get().clear();
		}
	}

	@Override
	public TSFetchMetadataResp fetchMetadata(TSFetchMetadataReq req) throws TException {
		TS_Status status;
		if (!checkLogin()) {
			LOGGER.info("{}: Not login.",TsFileDBConstant.GLOBAL_DB_NAME);
			status = new TS_Status(TS_StatusCode.ERROR_STATUS);
			status.setErrorMessage("Not login");
			return new TSFetchMetadataResp(status);
		}
		TSFetchMetadataResp resp = new TSFetchMetadataResp();
		switch (req.getType()) {
			case "SHOW_TIMESERIES":
				String path = req.getColumnPath();
				try {
					List<List<String>> showTimeseriesList = MManager.getInstance().getShowTimeseriesPath(path);
					resp.setShowTimeseriesList(showTimeseriesList);
				} catch (PathErrorException e) {
				    status = new TS_Status(TS_StatusCode.ERROR_STATUS);
				    status.setErrorMessage(String.format("Failed to fetch timeseries %s's metadata because: %s", req.getColumnPath(), e));
				    resp.setStatus(status);
				    return resp;
				} catch (OutOfMemoryError outOfMemoryError) { // TODO OOME
					LOGGER.error(String.format("Failed to fetch timeseries %s's metadata", req.getColumnPath()), outOfMemoryError);
					status = new TS_Status(TS_StatusCode.ERROR_STATUS);
					status.setErrorMessage(String.format("Failed to fetch timeseries %s's metadata because: %s", req.getColumnPath(), outOfMemoryError));
					resp.setStatus(status);
					return resp;
				}
				status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
				break;
			case "SHOW_STORAGE_GROUP":
				try {
					HashSet<String> storageGroups = MManager.getInstance().getAllStorageGroup();
					resp.setShowStorageGroups(storageGroups);
				} catch (PathErrorException e) {
					status = new TS_Status(TS_StatusCode.ERROR_STATUS);
					status.setErrorMessage(String.format("Failed to fetch storage groups' metadata because: %s", e));
					resp.setStatus(status);
					return resp;
				} catch (OutOfMemoryError outOfMemoryError) { // TODO OOME
					LOGGER.error("Failed to fetch storage groups' metadata", outOfMemoryError);
					status = new TS_Status(TS_StatusCode.ERROR_STATUS);
					status.setErrorMessage(String.format("Failed to fetch storage groups' metadata because: %s", outOfMemoryError));
					resp.setStatus(status);
					return resp;
				}
				status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
				break;
			case "METADATA_IN_JSON":
				String metadataInJson = null;
                		try {
                    			metadataInJson = MManager.getInstance().getMetadataInString();
                		} catch (OutOfMemoryError outOfMemoryError) { // TODO OOME
                    			LOGGER.error("Failed to fetch all metadata in json", outOfMemoryError);
                    			status = new TS_Status(TS_StatusCode.ERROR_STATUS);
                    			status.setErrorMessage(String.format("Failed to fetch all metadata in json because: %s", outOfMemoryError));
                    			resp.setStatus(status);
                    			return resp;
                		}
                		resp.setMetadataInJson(metadataInJson);
				status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
				break;
			case "DELTA_OBEJECT":
				Metadata metadata;
				try {
                    			String column = req.getColumnPath();
                    			metadata = MManager.getInstance().getMetadata();
                    			Map<String, List<String>> deltaObjectMap = metadata.getDeltaObjectMap();
                    			if (deltaObjectMap == null || !deltaObjectMap.containsKey(column)) {
                        			resp.setColumnsList(new ArrayList<>());
                    			} else {
						resp.setColumnsList(deltaObjectMap.get(column));
                    			}
                		} catch (PathErrorException e) {
                    			LOGGER.error("cannot get delta object map", e);
                    			status = new TS_Status(TS_StatusCode.ERROR_STATUS);
                    			status.setErrorMessage(String.format("Failed to fetch delta object map because: %s", e));
                    			resp.setStatus(status);
                    			return resp;
                		} catch (OutOfMemoryError outOfMemoryError) { // TODO OOME
                    			LOGGER.error("Failed to get delta object map", outOfMemoryError);
                    			status = new TS_Status(TS_StatusCode.ERROR_STATUS);
                    			status.setErrorMessage(String.format("Failed to get delta object map because: %s", outOfMemoryError));
                    			break;
                		}
				status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
				break;
			case "COLUMN":
				try {
                    			resp.setDataType(MManager.getInstance().getSeriesType(req.getColumnPath()).toString());
                		} catch (PathErrorException e) { //TODO aggregate path e.g. last(root.ln.wf01.wt01.status)
			//                    status = new TS_Status(TS_StatusCode.ERROR_STATUS);
			//                    status.setErrorMessage(String.format("Failed to fetch %s's data type because: %s", req.getColumnPath(), e));
			//                    resp.setStatus(status);
			//                    return resp;
                		}
				status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
				break;
			case "ALL_COLUMNS":
				try {
                    			resp.setColumnsList(MManager.getInstance().getPaths(req.getColumnPath()));
                		} catch (PathErrorException e) {
                    			status = new TS_Status(TS_StatusCode.ERROR_STATUS);
                    			status.setErrorMessage(String.format("Failed to fetch %s's all columns because: %s", req.getColumnPath(), e));
                    			resp.setStatus(status);
                    			return resp;
                		} catch (OutOfMemoryError outOfMemoryError) { // TODO OOME
                    			LOGGER.error("Failed to fetch path {}'s all columns", req.getColumnPath(), outOfMemoryError);
                    			status = new TS_Status(TS_StatusCode.ERROR_STATUS);
                    			status.setErrorMessage(String.format("Failed to fetch %s's all columns because: %s", req.getColumnPath(), outOfMemoryError));
                    			break;
                		}
				status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
				break;
			default:
				status = new TS_Status(TS_StatusCode.ERROR_STATUS);
				status.setErrorMessage(String.format("Unsuport fetch metadata operation %s", req.getType()));
				break;
		}
		resp.setStatus(status);
		return resp;
	}

	/**
	 * Judge whether the statement is ADMIN COMMAND and if true, execute it.
	 *
	 * @param statement
	 *            command
	 * @return true if the statement is ADMIN COMMAND
	 * @throws IOException
	 *             exception
	 */
	private boolean execAdminCommand(String statement) throws IOException {
		if (!username.get().equals("root")) {
			return false;
		}
		if (statement == null) {
			return false;
		}
		statement = statement.toLowerCase();
		switch (statement) {
		case "flush":
			try {
				FileNodeManager.getInstance().closeAll();
			} catch (FileNodeManagerException e) {
				e.printStackTrace();
				throw new IOException(e);
			}
			// writeLogManager.overflowFlush();
			// writeLogManager.bufferFlush();
			// MManager.getInstance().flushObjectToFile();
			return true;
		case "merge":
			try {
				FileNodeManager.getInstance().mergeAll();
			} catch (FileNodeManagerException e) {
				e.printStackTrace();
				throw new IOException(e);
			}
			return true;
		}
		return false;
	}

	@Override
	public TSExecuteBatchStatementResp executeBatchStatement(TSExecuteBatchStatementReq req) throws TException {
		try {
			if (!checkLogin()) {
				LOGGER.info("{}: Not login.",TsFileDBConstant.GLOBAL_DB_NAME);
				return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Not login", null);
			}
			List<String> statements = req.getStatements();
			List<Integer> result = new ArrayList<>();
			boolean isAllSuccessful = true;
			String batchErrorMessage = "";
			
			for (String statement : statements) {
				try {
					PhysicalPlan physicalPlan = processor.parseSQLToPhysicalPlan(statement, timeZone.get());
					physicalPlan.setProposer(username.get());
					if (physicalPlan.isQuery()) {
						return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "statement is query :" + statement,
								result);
					}
					TSExecuteStatementResp resp = ExecuteUpdateStatement(physicalPlan);
					if(resp.getStatus().getStatusCode().equals(TS_StatusCode.SUCCESS_STATUS)){
						result.add(Statement.SUCCESS_NO_INFO);
					} 
					else{
						result.add(Statement.EXECUTE_FAILED);
						isAllSuccessful = false;
						batchErrorMessage = resp.getStatus().getErrorMessage();
					}
				} catch (Exception e) {
					String errMessage = String.format("Fail to generate physcial plan and execute for statement %s beacuse %s", statement, e.getMessage());
					//LOGGER.error(errMessage);
					result.add(Statement.EXECUTE_FAILED);
					isAllSuccessful = false;
					batchErrorMessage = errMessage;
				}
			}
			if(isAllSuccessful) {
				return getTSBathExecuteStatementResp(TS_StatusCode.SUCCESS_STATUS, "Execute batch statements successfully", result);
			} else {
				return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS, batchErrorMessage, result);
			}
		} catch (Exception e) {
			LOGGER.error("{}: error occurs when executing statements",TsFileDBConstant.GLOBAL_DB_NAME, e);
			return getTSBathExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage(), null);
		}
	}

	@Override
	public TSExecuteStatementResp executeStatement(TSExecuteStatementReq req) throws TException {
		try {
			if (!checkLogin()) {
				LOGGER.info("{}: Not login.",TsFileDBConstant.GLOBAL_DB_NAME);
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Not login");
			}
			String statement = req.getStatement();

			try {
				if (execAdminCommand(statement)) {
					return getTSExecuteStatementResp(TS_StatusCode.SUCCESS_STATUS, "ADMIN_COMMAND_SUCCESS");
				}
			} catch (Exception e) {
				e.printStackTrace();
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
			}

			PhysicalPlan physicalPlan;
			try {
				physicalPlan = processor.parseSQLToPhysicalPlan(statement, timeZone.get());
				physicalPlan.setProposer(username.get());
			} catch (IllegalASTFormatException e) {
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS,
						"Statement format is not right:" + e.getMessage());
			} catch (NullPointerException e) {
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Statement is not allowed");
			}
			if (physicalPlan.isQuery()) {
				return executeQueryStatement(req);
			} else {
				return ExecuteUpdateStatement(physicalPlan);
			}
		} catch (Exception e) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}
	}

	@Override
	public TSExecuteStatementResp executeQueryStatement(TSExecuteStatementReq req) throws TException {

		try {
			if (!checkLogin()) {
				LOGGER.info("{}: Not login.",TsFileDBConstant.GLOBAL_DB_NAME);
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Not login");
			}

			String statement = req.getStatement();
			PhysicalPlan plan = processor.parseSQLToPhysicalPlan(statement, timeZone.get());
			plan.setProposer(username.get());
			String targetUser = null;
			if(plan instanceof AuthorPlan)
				targetUser = ((AuthorPlan) plan).getUserName();

			List<Path> paths;
			paths = plan.getPaths();

			// check path exists
			if (paths.size() == 0) {
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Timeseries does not exist.");
			}

			// check file level set
			try {
				MManager.getInstance().checkFileLevel(paths);
			} catch (PathErrorException e) {
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
			}

			// check permissions
			if (!checkAuthorization(paths, plan)) {
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "No permissions for this query.");
			}

			TSExecuteStatementResp resp = getTSExecuteStatementResp(TS_StatusCode.SUCCESS_STATUS, "");
			List<String> columns = new ArrayList<>();
			// Restore column header of aggregate to func(column_name), only
			// support single aggregate function for now
			if (plan.getOperatorType() == INDEXQUERY){
				columns = ((IndexQueryPlan)plan).getColumnHeader();
			} else if(plan instanceof MultiQueryPlan) {
				switch (((MultiQueryPlan) plan).getType()) {
					case FILL:
						for (Path p : paths) {
							columns.add(p.getFullPath());
						}
						break;
					case GROUPBY:
					case AGGREGATION:
						List<String> aggregations = plan.getAggregations();
						if (aggregations.size() != paths.size()) {
							for (int i = 1; i < paths.size(); i++) {
								aggregations.add(aggregations.get(0));
							}
						}
						for (int i = 0; i < paths.size(); i++) {
							columns.add(aggregations.get(i) + "(" + paths.get(i).getFullPath() + ")");
						}
						break;
					default:
						throw new TException("unsupported query type: " + ((MultiQueryPlan) plan).getType());
				}
			} else {
				Operator.OperatorType type = plan.getOperatorType();
				switch (type) {
					case QUERY:
					case FILL:
						for (Path p : paths) {
							columns.add(p.getFullPath());
						}
						break;
					case AGGREGATION:
					case GROUPBY:
						List<String> aggregations = plan.getAggregations();
						if (aggregations.size() != paths.size()) {
							for (int i = 1; i < paths.size(); i++) {
								aggregations.add(aggregations.get(0));
							}
						}
						for (int i = 0; i < paths.size(); i++) {
							columns.add(aggregations.get(i) + "(" + paths.get(i).getFullPath() + ")");
						}
						break;
					default:
						throw new RuntimeException("not support " + type + " in new read process");
				}
			}
				
			if (plan.getOperatorType() == INDEXQUERY) {
				resp.setOperationType(INDEXQUERY.toString());
			} else if(plan instanceof MultiQueryPlan){
				resp.setOperationType(((MultiQueryPlan) plan).getType().toString());
			} else {
				resp.setOperationType(plan.getOperatorType().toString());
			}
			TSHandleIdentifier operationId = new TSHandleIdentifier(ByteBuffer.wrap(username.get().getBytes()),
					ByteBuffer.wrap(("PASS".getBytes())));
			TSOperationHandle operationHandle;
			resp.setColumns(columns);
			operationHandle = new TSOperationHandle(operationId, true);
			resp.setOperationHandle(operationHandle);
			recordANewQuery(statement, plan);
			return resp;
		} catch (Exception e) {
			LOGGER.error("{}: Internal server error: {}",TsFileDBConstant.GLOBAL_DB_NAME, e.getMessage());
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}
	}

	@Override
	public TSFetchResultsResp fetchResults(TSFetchResultsReq req) throws TException {
		try {
			if (!checkLogin()) {
				return getTSFetchResultsResp(TS_StatusCode.ERROR_STATUS, "Not login.");
			}
			String statement = req.getStatement();

			if (!queryStatus.get().containsKey(statement)) {
				return getTSFetchResultsResp(TS_StatusCode.ERROR_STATUS, "Has not executed statement");
			}

			int fetchSize = req.getFetch_size();
			QueryDataSet queryDataSet;
			if (!queryRet.get().containsKey(statement)) {
				PhysicalPlan physicalPlan = queryStatus.get().get(statement);
				processor.getExecutor().setFetchSize(fetchSize);
				queryDataSet = processor.getExecutor().processQuery(physicalPlan);
				queryRet.get().put(statement, queryDataSet);
			} else {
				queryDataSet = queryRet.get().get(statement);
			}
			TSQueryDataSet result = Utils.convertQueryDataSetByFetchSize(queryDataSet, fetchSize);
			boolean hasResultSet = result.getRecords().size() > 0;
			if(!hasResultSet && queryRet.get() != null) {
				queryRet.get().remove(statement);
			}
			TSFetchResultsResp resp = getTSFetchResultsResp(TS_StatusCode.SUCCESS_STATUS, "FetchResult successfully. Has more result: " + hasResultSet);
			resp.setHasResultSet(hasResultSet);
			resp.setQueryDataSet(result);
			return resp;
		} catch (Exception e) {
			LOGGER.error("{}: Internal server error: {}",TsFileDBConstant.GLOBAL_DB_NAME, e.getMessage());
			return getTSFetchResultsResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}
	}

	@Override
	public TSExecuteStatementResp executeUpdateStatement(TSExecuteStatementReq req) throws TException {
		try {
			if (!checkLogin()) {
				return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Not login");
			}
			String statement = req.getStatement();
			return ExecuteUpdateStatement(statement);
		} catch (ProcessorException e) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		} catch (Exception e) {
			LOGGER.error("{}: server Internal Error: {}",TsFileDBConstant.GLOBAL_DB_NAME, e.getMessage());
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}
	}

	private TSExecuteStatementResp ExecuteUpdateStatement(PhysicalPlan plan) throws TException {
		List<Path> paths = plan.getPaths();

		try {
			if (!checkAuthorization(paths, plan)) {
                return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "No permissions for this operation " + plan.getOperatorType());
            }
		} catch (AuthException e) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Uninitialized authorizer " + e.getMessage());
		}
		// TODO
		// In current version, we only return OK/ERROR
		// Do we need to add extra information of executive condition
		boolean execRet;
		try {
			execRet = processor.getExecutor().processNonQuery(plan);
		} catch (ProcessorException e) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}
//			if (TsfileDBDescriptor.getInstance().getConfig().enableWal
//					&& !WriteLogManager.isRecovering && execRet && needToBeWrittenToLog(plan)) {
//				writeLogManager.write(plan);
//			}
		TS_StatusCode statusCode = execRet ? TS_StatusCode.SUCCESS_STATUS : TS_StatusCode.ERROR_STATUS;
		String msg = execRet ? "Execute successfully" : "Execute statement error.";
		TSExecuteStatementResp resp = getTSExecuteStatementResp(statusCode, msg);
		TSHandleIdentifier operationId = new TSHandleIdentifier(ByteBuffer.wrap(username.get().getBytes()),
				ByteBuffer.wrap(("PASS".getBytes())));
		TSOperationHandle operationHandle;
		operationHandle = new TSOperationHandle(operationId, false);
		resp.setOperationHandle(operationHandle);
		return resp;
	}

	private TSExecuteStatementResp ExecuteUpdateStatement(String statement)
			throws TException, QueryProcessorException, IOException, ProcessorException {

		PhysicalPlan physicalPlan;
		try {
			physicalPlan = processor.parseSQLToPhysicalPlan(statement, timeZone.get());
			physicalPlan.setProposer(username.get());
		} catch (QueryProcessorException | ArgsErrorException e) {
			e.printStackTrace();
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}

		if (physicalPlan.isQuery()) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Statement is a query statement.");
		}

		// if operation belongs to add/delete/update
		List<Path> paths = physicalPlan.getPaths();

		try {
			if (!checkAuthorization(paths, physicalPlan)) {
                return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "No permissions for this operation " + physicalPlan.getOperatorType());
            }
		} catch (AuthException e) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, "Uninitialized authorizer : " + e.getMessage());
	}

		// TODO
		// In current version, we only return OK/ERROR
		// Do we need to add extra information of executive condition
		boolean execRet;
		try {
			execRet = processor.getExecutor().processNonQuery(physicalPlan);
		} catch (ProcessorException e) {
			return getTSExecuteStatementResp(TS_StatusCode.ERROR_STATUS, e.getMessage());
		}
//		if (!WriteLogManager.isRecovering && execRet && needToBeWrittenToLog(physicalPlan)) {
//			try {
//				writeLogManager.write(physicalPlan);
//			} catch (PathErrorException e) {
//				throw new ProcessorException(e);
//			}
//		}
		TS_StatusCode statusCode = execRet ? TS_StatusCode.SUCCESS_STATUS : TS_StatusCode.ERROR_STATUS;
		String msg = execRet ? "Execute successfully" : "Execute statement error.";
		TSExecuteStatementResp resp = getTSExecuteStatementResp(statusCode, msg);
		TSHandleIdentifier operationId = new TSHandleIdentifier(ByteBuffer.wrap(username.get().getBytes()),
				ByteBuffer.wrap(("PASS".getBytes())));
		TSOperationHandle operationHandle;
		operationHandle = new TSOperationHandle(operationId, false);
		resp.setOperationHandle(operationHandle);
		return resp;
	}

//	private boolean needToBeWrittenToLog(PhysicalPlan plan) {
//		if (plan.getOperatorType() == Operator.OperatorType.UPDATE) {
//			return true;
//		}
//		if (plan.getOperatorType() == Operator.OperatorType.DELETE) {
//			return true;
//		}
//		return false;
//	}

	private void recordANewQuery(String statement, PhysicalPlan physicalPlan) {
		queryStatus.get().put(statement, physicalPlan);
		// refresh current queryRet for statement
		if (queryRet.get().containsKey(statement)) {
			queryRet.get().remove(statement);
		}
	}

	/**
	 * Check whether current user has logined.
	 *
	 * @return true: If logined; false: If not logined
	 */
	private boolean checkLogin() {
		return username.get() != null;
	}

	private boolean checkAuthorization(List<Path> paths, PhysicalPlan plan) throws AuthException {
		String targetUser = null;
		if(plan instanceof AuthorPlan)
			targetUser = ((AuthorPlan) plan).getUserName();
		return AuthorityChecker.check(username.get(), paths, plan.getOperatorType(), targetUser);
	}

	private TSExecuteStatementResp getTSExecuteStatementResp(TS_StatusCode code, String msg) {
		TSExecuteStatementResp resp = new TSExecuteStatementResp();
		TS_Status ts_status = new TS_Status(code);
		ts_status.setErrorMessage(msg);
		resp.setStatus(ts_status);
		TSHandleIdentifier operationId = new TSHandleIdentifier(ByteBuffer.wrap(username.get().getBytes()),
				ByteBuffer.wrap(("PASS".getBytes())));
		TSOperationHandle operationHandle = new TSOperationHandle(operationId, false);
		resp.setOperationHandle(operationHandle);
		return resp;
	}

	private TSExecuteBatchStatementResp getTSBathExecuteStatementResp(TS_StatusCode code, String msg,
			List<Integer> result) {
		TSExecuteBatchStatementResp resp = new TSExecuteBatchStatementResp();
		TS_Status ts_status = new TS_Status(code);
		ts_status.setErrorMessage(msg);
		resp.setStatus(ts_status);
		resp.setResult(result);
		return resp;
	}

	private TSFetchResultsResp getTSFetchResultsResp(TS_StatusCode code, String msg) {
		TSFetchResultsResp resp = new TSFetchResultsResp();
		TS_Status ts_status = new TS_Status(code);
		ts_status.setErrorMessage(msg);
		resp.setStatus(ts_status);
		return resp;
	}

	public void handleClientExit() throws TException {
		closeOperation(null);
		closeSession(null);
	}

	@Override
	public TSGetTimeZoneResp getTimeZone() throws TException {
		TS_Status ts_status = null;
		TSGetTimeZoneResp resp = null;
		try {
			ts_status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
			resp = new TSGetTimeZoneResp(ts_status, timeZone.get().getID());
		} catch (Exception e) {
			ts_status = new TS_Status(TS_StatusCode.ERROR_STATUS);
			ts_status.setErrorMessage(e.getMessage());
			resp = new TSGetTimeZoneResp(ts_status, "Unknown time zone");
		}
		return resp;
	}

	@Override
	public TSSetTimeZoneResp setTimeZone(TSSetTimeZoneReq req) throws TException {
		TS_Status ts_status = null;
		try {
			String timeZoneID = req.getTimeZone();
			timeZone.set(DateTimeZone.forID(timeZoneID.trim()));
			ts_status = new TS_Status(TS_StatusCode.SUCCESS_STATUS);
		} catch (Exception e) {
			ts_status = new TS_Status(TS_StatusCode.ERROR_STATUS);
			ts_status.setErrorMessage(e.getMessage());
		}
		TSSetTimeZoneResp resp = new TSSetTimeZoneResp(ts_status);
		return resp;
	}

	@Override
	public ServerProperties getProperties() throws TException {
		ServerProperties properties = new ServerProperties();
		properties.setVersion(TsFileDBConstant.VERSION);
		properties.setSupportedTimeAggregationOperations(new ArrayList<>());
		properties.getSupportedTimeAggregationOperations().add(AggregationConstant.MAX_TIME);
		properties.getSupportedTimeAggregationOperations().add(AggregationConstant.MIN_TIME);
		return properties;
	}
}
