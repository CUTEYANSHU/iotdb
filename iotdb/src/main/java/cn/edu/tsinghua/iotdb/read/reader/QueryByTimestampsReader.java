package cn.edu.tsinghua.iotdb.read.reader;

import cn.edu.tsinghua.iotdb.engine.querycontext.QueryDataSource;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PriorityMergeReaderByTimestamp;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PriorityTimeValuePairReader;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PriorityTimeValuePairReaderByTimestamp;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.series.OverflowInsertDataReaderByTimeStamp;
import cn.edu.tsinghua.iotdb.queryV2.factory.SeriesReaderFactory;
import cn.edu.tsinghua.iotdb.utils.TimeValuePair;
import cn.edu.tsinghua.iotdb.utils.TsPrimitiveType;

import java.io.IOException;

/**
 * A reader that can get the corresponding value of the specified time point.
 * It has considered sequence insert data, overflow data.
 *
 * TODO: update and delete operation.
 * */
public class QueryByTimestampsReader {

    private SeriesWithOverflowOpReader seriesWithOverflowOpReader;

    public QueryByTimestampsReader(QueryDataSource queryDataSource) throws IOException {
        int priority = 1;
        //sequence insert data
        SequenceInsertDataByTimeStampReader tsFilesReader = new SequenceInsertDataByTimeStampReader(queryDataSource.getSeriesDataSource());
        PriorityTimeValuePairReaderByTimestamp tsFilesReaderWithPriority = new PriorityTimeValuePairReaderByTimestamp(
                tsFilesReader, new PriorityTimeValuePairReader.Priority(priority++));

        //overflow insert data
        OverflowInsertDataReaderByTimeStamp overflowInsertDataReader = SeriesReaderFactory.getInstance().
                createSeriesReaderForOverflowInsertByTimestamp(queryDataSource.getOverflowSeriesDataSource());
        PriorityTimeValuePairReaderByTimestamp overflowInsertDataReaderWithPriority = new PriorityTimeValuePairReaderByTimestamp(
                overflowInsertDataReader, new PriorityTimeValuePairReader.Priority(priority++));

        //operation of update and delete
        OverflowOperationReader overflowOperationReader = queryDataSource.getOverflowSeriesDataSource().getUpdateDeleteInfoOfOneSeries().getOverflowUpdateOperationReaderNewInstance();

        PriorityMergeReaderByTimestamp insertDataReader = new PriorityMergeReaderByTimestamp(tsFilesReaderWithPriority, overflowInsertDataReaderWithPriority);
        seriesWithOverflowOpReader = new SeriesWithOverflowOpReader(insertDataReader, overflowOperationReader);
    }


    @Override
    public boolean hasNext() throws IOException {
        return seriesWithOverflowOpReader.hasNext();
    }

    @Override
    public TimeValuePair next() throws IOException {
        return seriesWithOverflowOpReader.next();
    }

    @Override
    public void skipCurrentTimeValuePair() throws IOException {
        next();
    }

    @Override
    public void close() throws IOException {
        seriesWithOverflowOpReader.close();
    }


    @Override
    public TsPrimitiveType getValueInTimestamp(long timestamp) throws IOException {
        return seriesWithOverflowOpReader.getValueInTimestamp(timestamp);
    }
}
