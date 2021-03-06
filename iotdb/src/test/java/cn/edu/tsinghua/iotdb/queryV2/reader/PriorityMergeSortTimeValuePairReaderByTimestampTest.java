package cn.edu.tsinghua.iotdb.queryV2.reader;

import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PriorityMergeSortTimeValuePairReaderByTimestamp;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.PriorityTimeValuePairReaderByTimestamp;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.timeseries.readV2.datatype.TimeValuePair;
import cn.edu.tsinghua.tsfile.timeseries.readV2.datatype.TsPrimitiveType;
import cn.edu.tsinghua.tsfile.timeseries.readV2.reader.SeriesReaderByTimeStamp;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class PriorityMergeSortTimeValuePairReaderByTimestampTest {
    @Test
    public void test() throws IOException {
        FakedPriorityTimeValuePairReaderByTimestamp reader1 = new FakedPriorityTimeValuePairReaderByTimestamp(100, 200, 5, 11, 1);
        FakedPriorityTimeValuePairReaderByTimestamp reader2 = new FakedPriorityTimeValuePairReaderByTimestamp(850, 200, 7, 19, 2);
        FakedPriorityTimeValuePairReaderByTimestamp reader3 = new FakedPriorityTimeValuePairReaderByTimestamp(1080, 200, 13, 31, 3);

        PriorityMergeSortTimeValuePairReaderByTimestamp priorityMergeSortTimeValuePairReader = new PriorityMergeSortTimeValuePairReaderByTimestamp(reader1, reader2, reader3);
        int cnt = 0;

        Random random = new Random();
        for(long time = 4; time < 1080+200*13+600;){
            TsPrimitiveType value = priorityMergeSortTimeValuePairReader.getValueInTimestamp(time);
            System.out.println("time = "+time +" value = "+value);
            if(time < 100){
                //null
                Assert.assertNull(value);
            }
            else if(time < 850){
                //reader 1
                if((time - 100) % 5 == 0){
                    Assert.assertEquals(time % 11, value.getLong());
                }
            }
            else if(time < 1080){
                //reader 2, reader 1
                if(time >= 850 && (time - 850) % 7 == 0){
                    Assert.assertEquals(time % 19, value.getLong());
                }
                else if(time < 1100 && (time - 100) % 5 == 0){
                    Assert.assertEquals(time % 11, value.getLong());
                }
                else {
                    Assert.assertNull(value);
                }

            }
            else if(time < 1080+200*13){
                //reader 3, reader 2, reader 1
                if(time >= 1080 && (time - 1080) % 13==0){
                    Assert.assertEquals(time % 31, value.getLong());
                }
                else if(time < 850 + 200*7 && (time - 850) % 7 == 0){
                    Assert.assertEquals(time % 19, value.getLong());
                }
                else if(time < 1100 && (time - 100) % 5 == 0){
                    Assert.assertEquals(time % 11, value.getLong());
                }
                else {
                    Assert.assertNull(value);
                }
            }
            else{
                //null
                Assert.assertNull(value);
            }
            time += random.nextInt(50)+1;
        }

        while (priorityMergeSortTimeValuePairReader.hasNext()){
            TimeValuePair timeValuePair = priorityMergeSortTimeValuePairReader.next();
            long time = timeValuePair.getTimestamp();
            long value = timeValuePair.getValue().getLong();
            if(time < 850){
                Assert.assertEquals(time % 11, value);
            }
            else if(time < 1080){
                Assert.assertEquals(time % 19, value);
            }
            else {
                Assert.assertEquals(time % 31, value);
            }
            cnt++;
        }

    }


    public static class FakedPriorityTimeValuePairReaderByTimestamp extends PriorityTimeValuePairReaderByTimestamp {

        public FakedPriorityTimeValuePairReaderByTimestamp(FakedTimeValuePairReaderByTimestamp seriesReader, Priority priority) {
            super(seriesReader, priority);
        }

        public FakedPriorityTimeValuePairReaderByTimestamp(long startTime, int size, int interval, int modValue, int priority) {
            this(new FakedTimeValuePairReaderByTimestamp(startTime, size, interval, modValue), new Priority(priority));
        }
    }

    public static class FakedTimeValuePairReaderByTimestamp implements SeriesReaderByTimeStamp {
        private Iterator<TimeValuePair> iterator;
        private long currentTimeStamp = Long.MIN_VALUE;
        private boolean hasCachedTimeValuePair;
        private TimeValuePair cachedTimeValuePair;

        public FakedTimeValuePairReaderByTimestamp(long startTime, int size, int interval, int modValue){
            long time = startTime;
            List<TimeValuePair> list = new ArrayList<>();
            for(int i = 0; i < size; i++){
                list.add(new TimeValuePair(time, TsPrimitiveType.getByType(TSDataType.INT64, time % modValue)));
                time+=interval;
            }
            iterator = list.iterator();
        }

        @Override
        public boolean hasNext() throws IOException {
            if(hasCachedTimeValuePair && cachedTimeValuePair.getTimestamp() >= currentTimeStamp){
                return true;
            }

            while (iterator.hasNext()){
                cachedTimeValuePair = iterator.next();
                if(cachedTimeValuePair.getTimestamp() >= currentTimeStamp){
                    hasCachedTimeValuePair = true;
                    return true;
                }
            }
            return false;
        }

        @Override
        public TimeValuePair next() throws IOException {
            if(hasCachedTimeValuePair){
                hasCachedTimeValuePair = false;
                return cachedTimeValuePair;
            }
            else {
                throw new IOException(" to end! "+ iterator.next());
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
        public TsPrimitiveType getValueInTimestamp(long timestamp) throws IOException {
            this.currentTimeStamp = timestamp;
            if(hasCachedTimeValuePair && cachedTimeValuePair.getTimestamp() == timestamp){
                hasCachedTimeValuePair = false;
                return cachedTimeValuePair.getValue();
            }

            if(hasNext()){
                cachedTimeValuePair = next();
                if(cachedTimeValuePair.getTimestamp() == timestamp){
                    return cachedTimeValuePair.getValue();
                }
                else if(cachedTimeValuePair.getTimestamp() > timestamp){
                    hasCachedTimeValuePair = true;
                }
            }
            return null;
        }
    }
}
