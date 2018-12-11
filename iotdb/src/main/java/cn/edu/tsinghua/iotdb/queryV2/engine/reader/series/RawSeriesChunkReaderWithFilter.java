package cn.edu.tsinghua.iotdb.queryV2.engine.reader.series;

import cn.edu.tsinghua.iotdb.engine.querycontext.RawSeriesChunk;
import cn.edu.tsinghua.iotdb.read.ISeriesReader;
import cn.edu.tsinghua.iotdb.utils.TimeValuePair;
import cn.edu.tsinghua.tsfile.read.common.BatchData;
import cn.edu.tsinghua.tsfile.read.expression.impl.SingleSeriesExpression;
import cn.edu.tsinghua.tsfile.read.filter.basic.Filter;

import java.io.IOException;
import java.util.Iterator;


public class RawSeriesChunkReaderWithFilter implements ISeriesReader {

    private Iterator<TimeValuePair> timeValuePairIterator;
    private Filter filter;
    private boolean hasCachedTimeValuePair;
    private TimeValuePair cachedTimeValuePair;

    public RawSeriesChunkReaderWithFilter(RawSeriesChunk rawSeriesChunk, SingleSeriesExpression singleSeriesExpression) {
        timeValuePairIterator = rawSeriesChunk.getIterator();
        this.filter = singleSeriesExpression.getFilter();
    }

    @Override
    public boolean hasNext() throws IOException {
        if (hasCachedTimeValuePair) {
            return true;
        }
        while (timeValuePairIterator.hasNext()) {
            TimeValuePair timeValuePair = timeValuePairIterator.next();
            if (filter.satisfy(timeValuePair.getTimestamp(), timeValuePair.getValue().getValue())) {
                hasCachedTimeValuePair = true;
                cachedTimeValuePair = timeValuePair;
                break;
            }
        }
        return hasCachedTimeValuePair;
    }

    @Override
    public TimeValuePair next() throws IOException {
        if(hasCachedTimeValuePair){
            hasCachedTimeValuePair = false;
            return cachedTimeValuePair;
        }
        else {
            return timeValuePairIterator.next();
        }
    }

    @Override
    public void skipCurrentTimeValuePair() throws IOException {
        next();
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public boolean hasNextBatch() {
        return false;
    }

    @Override
    public BatchData nextBatch() {
        return null;
    }

    @Override
    public BatchData currentBatch() {
        return null;
    }
}
