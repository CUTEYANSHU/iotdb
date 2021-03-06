package cn.edu.tsinghua.iotdb.engine.querycontext;

import cn.edu.tsinghua.iotdb.engine.memtable.TimeValuePairSorter;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.timeseries.readV2.datatype.TimeValuePair;
import cn.edu.tsinghua.tsfile.timeseries.readV2.datatype.TsPrimitiveType;

import java.util.Iterator;
import java.util.List;

/**
 * Created by zhangjinrui on 2018/1/26.
 */
//TODO: merge RawSeriesChunkLazyLoadImpl and PrimitiveMemSeries, RawSeriesChunk and IMemSeries
public class RawSeriesChunkLazyLoadImpl implements RawSeriesChunk {

    private boolean initialized;

    private TSDataType dataType;
    private TimeValuePairSorter memSeries;
    private List<TimeValuePair> sortedTimeValuePairList;

    public RawSeriesChunkLazyLoadImpl(TSDataType dataType, TimeValuePairSorter memSeries) {
        this.dataType = dataType;
        this.memSeries = memSeries;
        this.initialized = false;
    }

    private void checkInitialized() {
        if (!initialized) {
            init();
        }
    }

    private void init() {
        sortedTimeValuePairList = memSeries.getSortedTimeValuePairList();
        initialized = true;
    }

    @Override
    public TSDataType getDataType() {
        return dataType;
    }

    @Override
    public long getMaxTimestamp() {
        checkInitialized();
        if (!isEmpty()) {
            return sortedTimeValuePairList.get(sortedTimeValuePairList.size() - 1).getTimestamp();
        } else {
            return -1;
        }
    }

    @Override
    public long getMinTimestamp() {
        checkInitialized();
        if (!isEmpty()) {
            return sortedTimeValuePairList.get(0).getTimestamp();
        } else {
            return -1;
        }
    }

    @Override
    public TsPrimitiveType getValueAtMaxTime() {
        checkInitialized();
        if (!isEmpty()) {
            return sortedTimeValuePairList.get(sortedTimeValuePairList.size() - 1).getValue();
        } else {
            return null;
        }
    }

    @Override
    public TsPrimitiveType getValueAtMinTime() {
        checkInitialized();
        if (!isEmpty()) {
            return sortedTimeValuePairList.get(0).getValue();
        } else {
            return null;
        }
    }

    @Override
    public Iterator<TimeValuePair> getIterator() {
        checkInitialized();
        return sortedTimeValuePairList.iterator();
    }

    @Override
    public boolean isEmpty() {
        checkInitialized();
        return sortedTimeValuePairList.size() == 0;
    }
}
