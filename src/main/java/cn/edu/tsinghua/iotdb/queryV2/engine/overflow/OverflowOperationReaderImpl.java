package cn.edu.tsinghua.iotdb.queryV2.engine.overflow;

import cn.edu.tsinghua.iotdb.engine.overflow.treeV2.IntervalTreeOperation;
import cn.edu.tsinghua.iotdb.engine.querycontext.OverflowUpdateDeleteFile;
import cn.edu.tsinghua.tsfile.file.metadata.TimeSeriesChunkMetaData;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.timeseries.read.query.DynamicOneColumnData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class OverflowOperationReaderImpl implements OverflowOperationReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverflowOperationReaderImpl.class);
    private int index = 0;
    private List<OverflowOperation> updateOperations = new ArrayList<>();
    private TSDataType dataType;

    public OverflowOperationReaderImpl(DynamicOneColumnData memoryUpdate, List<OverflowUpdateDeleteFile> overflowUpdateFileList,
                                       TSDataType dataType) {
        this.dataType = dataType;
        IntervalTreeOperation overflowIndex = new IntervalTreeOperation(dataType);
        DynamicOneColumnData updateDynamic = memoryUpdate == null ? new DynamicOneColumnData(dataType, true) : memoryUpdate;

        InputStream in = null;
        try {
            for (int i = overflowUpdateFileList.size() - 1; i >= 0; i--) {
                OverflowUpdateDeleteFile overflowUpdateDeleteFile = overflowUpdateFileList.get(i);
                List<TimeSeriesChunkMetaData> timeSeriesChunkMetaDataList = overflowUpdateDeleteFile.getTimeSeriesChunkMetaDataList();
                for (int j = timeSeriesChunkMetaDataList.size() - 1; j >= 0; j--) {
                    TimeSeriesChunkMetaData seriesMetaData = timeSeriesChunkMetaDataList.get(i);
                    if (!seriesMetaData.getVInTimeSeriesChunkMetaData().getDataType().equals(dataType)) {
                        continue;
                    }
                    int chunkSize = (int) seriesMetaData.getTotalByteSize();
                    long offset = seriesMetaData.getProperties().getFileOffset();
                    in = getSeriesChunkBytes(overflowUpdateDeleteFile.getFilePath(), chunkSize, offset);
                    updateDynamic = overflowIndex.queryFileBlock(null, null, in, memoryUpdate);
                }
            }

            updateOperations = overflowIndex.getDynamicList(updateDynamic);
        } catch (IOException e) {
            LOGGER.error("Read overflow file block failed, reason {}", e.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    LOGGER.error("Close overflow file stream failed, reason {}", e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    public InputStream getSeriesChunkBytes(String path, int chunkSize, long offset) {
        try {
            RandomAccessFile raf = new RandomAccessFile(path, "r");
            raf.seek(offset);
            byte[] chunk = new byte[chunkSize];
            int off = 0;
            int len = chunkSize;
            while (len > 0) {
                int num = raf.read(chunk, off, len);
                off = off + num;
                len = len - num;
            }
            return new ByteArrayInputStream(chunk);
        } catch (IOException e) {
            LOGGER.error("Read series chunk failed, reason is {}", e.getMessage());
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    @Override
    public boolean hasNext() {
        return index < updateOperations.size();
    }

    @Override
    public OverflowOperation next() {
        return updateOperations.get(index ++);
    }

    @Override
    public OverflowOperation getCurrentOperation() {
        return updateOperations.get(index);
    }

    @Override
    public void close() throws IOException {

    }
}