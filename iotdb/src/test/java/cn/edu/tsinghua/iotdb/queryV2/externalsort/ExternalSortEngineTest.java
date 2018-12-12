package cn.edu.tsinghua.iotdb.queryV2.externalsort;

import cn.edu.tsinghua.iotdb.queryV2.engine.externalsort.SimpleExternalSortEngine;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.merge.PriorityMergeReader;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.merge.PrioritySeriesReader;
import cn.edu.tsinghua.iotdb.queryV2.engine.reader.merge.PrioritySeriesReader.Priority;
import cn.edu.tsinghua.iotdb.queryV2.reader.SeriesMergeSortReaderTest.FakedSeriesReader;
import cn.edu.tsinghua.tsfile.timeseries.readV2.datatype.TimeValuePair;
import cn.edu.tsinghua.tsfile.timeseries.readV2.reader.TimeValuePairReader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class ExternalSortEngineTest {

    private String baseDir = "externalSortTestTmp";

    @After
    public void after() throws IOException {
        deleteDir();
    }

    @Test
    public void testSimple() throws IOException {
        SimpleExternalSortEngine engine = new SimpleExternalSortEngine(baseDir + "/", 2);
        List<PrioritySeriesReader> readerList1 = genSimple();
        List<PrioritySeriesReader> readerList2 = genSimple();
        readerList1 = engine.execute(readerList1);
        PriorityMergeReader reader1 = new PriorityMergeReader(readerList1);
        PriorityMergeReader reader2 = new PriorityMergeReader(readerList2);
        check(reader1, reader2);
        reader1.close();
        reader2.close();
    }

    @Test
    public void testBig() throws IOException {
        SimpleExternalSortEngine engine = new SimpleExternalSortEngine(baseDir + "/", 50);
        int lineCount = 100;
        int valueCount = 10000;
        List<long[]> data = genData(lineCount, valueCount);

        List<PrioritySeriesReader> readerList1 = genReaders(data);
        List<PrioritySeriesReader> readerList2 = genReaders(data);
        readerList1 = engine.execute(readerList1);
        PriorityMergeReader reader1 = new PriorityMergeReader(readerList1);
        PriorityMergeReader reader2 = new PriorityMergeReader(readerList2);
        check(reader1, reader2);
        reader1.close();
        reader2.close();
    }

    public void efficiencyTest() throws IOException {
        SimpleExternalSortEngine engine = new SimpleExternalSortEngine(baseDir + "/", 50);
        int lineCount = 1000000;
        int valueCount = 100;
        List<long[]> data = genData(lineCount, valueCount);

        List<PrioritySeriesReader> readerList1 = genReaders(data);
        long startTimestamp = System.currentTimeMillis();
        readerList1 = engine.execute(readerList1);
        PriorityMergeReader reader1 = new PriorityMergeReader(readerList1);
        while (reader1.hasNext()) {
            reader1.next();
        }
        System.out.println("Time used WITH external sort:" + (System.currentTimeMillis() - startTimestamp) + "ms");

        List<PrioritySeriesReader> readerList2 = genReaders(data);
        startTimestamp = System.currentTimeMillis();
        PriorityMergeReader reader2 = new PriorityMergeReader(readerList2);
        while (reader2.hasNext()) {
            reader2.next();
        }
        System.out.println("Time used WITHOUT external sort:" + (System.currentTimeMillis() - startTimestamp) + "ms");

        reader1.close();
        reader2.close();
    }

    private List<long[]> genData(int lineCount, int valueCountEachLine) {
        Random rand = new Random();
        List<long[]> data = new ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            long[] tmp = new long[valueCountEachLine];
            long start = rand.nextInt(Integer.MAX_VALUE);
            for (int j = 0; j < valueCountEachLine; j++) {
                tmp[j] = start++;
            }
            data.add(tmp);
        }
        return data;
    }

    private List<PrioritySeriesReader> genReaders(List<long[]> data) {
        List<PrioritySeriesReader> readerList = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            readerList.add(new PrioritySeriesReader(new FakedSeriesReader(data.get(i), i), new Priority(i)));
        }
        return readerList;
    }

    private void check(TimeValuePairReader reader1, TimeValuePairReader reader2) throws IOException {
        while (reader1.hasNext()) {
            TimeValuePair tv1 = reader1.next();
            TimeValuePair tv2 = reader2.next();
            Assert.assertEquals(tv1.getTimestamp(), tv2.getTimestamp());
            Assert.assertEquals(tv1.getValue(), tv2.getValue());
        }
        Assert.assertEquals(false, reader2.hasNext());
    }

    private List<PrioritySeriesReader> genSimple() {
        PrioritySeriesReader reader1 = new PrioritySeriesReader(
                new FakedSeriesReader(new long[]{1, 2, 3, 4, 5}, 1L), new Priority(1));
        PrioritySeriesReader reader2 = new PrioritySeriesReader(
                new FakedSeriesReader(new long[]{1, 5, 6, 7, 8}, 2L), new Priority(2));
        PrioritySeriesReader reader3 = new PrioritySeriesReader(
                new FakedSeriesReader(new long[]{4, 5, 6, 7, 10}, 3L), new Priority(3));

        List<PrioritySeriesReader> readerList = new ArrayList<>();
        readerList.add(reader1);
        readerList.add(reader2);
        readerList.add(reader3);
        return readerList;
    }

    private void deleteDir() throws IOException {
        File file = new File(baseDir);
        if (!file.delete()) {
            throw new IOException("delete tmp file dir error");
        }
    }
}










