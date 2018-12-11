package cn.edu.tsinghua.iotdb.queryV2.factory;

import cn.edu.tsinghua.iotdb.engine.querycontext.OverflowInsertFile;
import cn.edu.tsinghua.tsfile.file.metadata.ChunkMetaData;
import cn.edu.tsinghua.tsfile.file.metadata.TimeSeriesChunkMetaData;
import cn.edu.tsinghua.tsfile.timeseries.readV2.common.EncodedSeriesChunkDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is used to generate SeriesChunkDescriptors according to given SeriesChunkMetadata.
 */
public class SeriesDescriptorGenerator {

  public static List<ChunkMetaData> genSeriesChunkDescriptorList(List<OverflowInsertFile> overflowInsertFileList) {

    List<EncodedSeriesChunkDescriptor> seriesChunkDescriptors = new ArrayList<>();

    for (OverflowInsertFile overflowInsertFile : overflowInsertFileList) {
      seriesChunkDescriptors.addAll(genSeriesChunkDescriptorsForOneFile(overflowInsertFile));
    }

    return seriesChunkDescriptors;
  }

  private static List<ChunkMetaData> genSeriesChunkDescriptorsForOneFile(OverflowInsertFile overflowInsertFile) {

    List<EncodedSeriesChunkDescriptor> seriesChunkDescriptors = new ArrayList<>();

    for (TimeSeriesChunkMetaData timeSeriesChunkMetaData : overflowInsertFile.getTimeSeriesChunkMetaDatas()) {
      ChunkMetaData encodedSeriesChunkDescriptor = new ChunkMetaData(
              overflowInsertFile.getPath(),
              timeSeriesChunkMetaData.getProperties().getFileOffset(),
              timeSeriesChunkMetaData.getTotalByteSize(),
              timeSeriesChunkMetaData.getProperties().getCompression(),
              timeSeriesChunkMetaData.getVInTimeSeriesChunkMetaData().getDataType(),
              timeSeriesChunkMetaData.getVInTimeSeriesChunkMetaData().getDigest(),
              timeSeriesChunkMetaData.getTInTimeSeriesChunkMetaData().getStartTime(),
              timeSeriesChunkMetaData.getTInTimeSeriesChunkMetaData().getEndTime(),
              timeSeriesChunkMetaData.getNumRows(),
              timeSeriesChunkMetaData.getVInTimeSeriesChunkMetaData().getEnumValues());
      seriesChunkDescriptors.add(encodedSeriesChunkDescriptor);
    }
    return seriesChunkDescriptors;
  }
}
