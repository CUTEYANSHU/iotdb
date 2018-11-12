package cn.edu.tsinghua.tsfile.timeseries.write.desc;

import cn.edu.tsinghua.tsfile.common.conf.TSFileConfig;
import cn.edu.tsinghua.tsfile.common.conf.TSFileDescriptor;
import cn.edu.tsinghua.tsfile.common.exception.UnSupportedDataTypeException;
import cn.edu.tsinghua.tsfile.compress.Compressor;
import cn.edu.tsinghua.tsfile.encoding.encoder.Encoder;
import cn.edu.tsinghua.tsfile.encoding.encoder.TSEncodingBuilder;
import cn.edu.tsinghua.tsfile.file.metadata.enums.CompressionType;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSEncoding;
import cn.edu.tsinghua.tsfile.timeseries.utils.StringContainer;
import cn.edu.tsinghua.tsfile.timeseries.write.schema.FileSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;



/**
 * This class describes a measurement's information registered in {@linkplain FileSchema FilSchema},
 * including measurement id, data type, encoding and compressor type. For each TSEncoding,
 * MeasurementDescriptor maintains respective TSEncodingBuilder; For TSDataType, only ENUM has
 * TSDataTypeConverter up to now.
 *
 * @author kangrong
 * @since version 0.1.0
 */
public class MeasurementDescriptor implements Comparable<MeasurementDescriptor> {
  private static final Logger LOG = LoggerFactory.getLogger(MeasurementDescriptor.class);
  private final TSDataType type;
  private final TSEncoding encoding;
  private String measurementId;
  private TSEncodingBuilder encodingConverter;
  private Compressor compressor;
  private TSFileConfig conf;
  private Map<String, String> props;

  /**
   * set properties as an empty Map
   */
  public MeasurementDescriptor(String measurementId, TSDataType type, TSEncoding encoding) {
    this(measurementId, type, encoding, CompressionType.valueOf(TSFileDescriptor.getInstance().getConfig().compressor), Collections.emptyMap());
  }
  public MeasurementDescriptor(String measurementId, TSDataType type, TSEncoding encoding, CompressionType compressionType) {
    this(measurementId, type, encoding, compressionType, Collections.emptyMap());
  }

  /**
   *
   * @param measurementId
   * @param type
   * @param encoding
   * @param props         information in encoding method.
   *                      For RLE, Encoder.MAX_POINT_NUMBER
   *                      For PLAIN, Encoder.MAX_STRING_LENGTH
   */
  public MeasurementDescriptor(String measurementId, TSDataType type, TSEncoding encoding,  CompressionType compressionType,
      Map<String, String> props) {
    this.type = type;
    this.measurementId = measurementId;
    this.encoding = encoding;
    this.props = props == null? Collections.emptyMap(): props;
    // get config from TSFileDescriptor
    this.conf = TSFileDescriptor.getInstance().getConfig();
    // initialize TSEncoding. e.g. set max error for PLA and SDT
    encodingConverter = TSEncodingBuilder.getConverter(encoding);
    encodingConverter.initFromProps(props);
    this.compressor = Compressor.getCompressor(compressionType);
  }

  public String getMeasurementId() {
    return measurementId;
  }

  public Map<String, String> getProps(){
    return props;
  }

  public void setMeasurementId(String measurementId) {
    this.measurementId = measurementId;
  }

  public TSEncoding getEncodingType() {
    return encoding;
  }

  public TSDataType getType() {
    return type;
  }

  /**
   * return the max possible length of given type.
   *
   * @return length in unit of byte
   */
  public int getTypeLength() {
    switch (type) {
      case BOOLEAN:
        return 1;
      case INT32:
        return 4;
      case INT64:
        return 8;
      case FLOAT:
        return 4;
      case DOUBLE:
        return 8;
      case TEXT:
        // 4 is the length of string in type of Integer.
        // Note that one char corresponding to 3 byte is valid only in 16-bit BMP
        return conf.maxStringLength * TSFileConfig.BYTE_SIZE_PER_CHAR + 4;
      default:
        throw new UnSupportedDataTypeException(type.toString());
    }
  }

  public Encoder getTimeEncoder() {
    TSFileConfig conf = TSFileDescriptor.getInstance().getConfig();
    TSEncoding timeSeriesEncoder = TSEncoding.valueOf(conf.timeSeriesEncoder);
    TSDataType timeType = TSDataType.valueOf(conf.timeSeriesDataType);
    return TSEncodingBuilder.getConverter(timeSeriesEncoder).getEncoder(timeType);
  }

  /**
   * get Encoder of value from encodingConverter by measurementID and data type
   * @return Encoder for value
   */
  public Encoder getValueEncoder() {
    return encodingConverter.getEncoder(type);
  }

  public Compressor getCompressor() {
    return compressor;
  }

  @Override
  public int hashCode() {
    return measurementId.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof MeasurementDescriptor))
      return false;
    MeasurementDescriptor ot = (MeasurementDescriptor) other;
    return this.measurementId.equals(ot.measurementId);
  }

  /**
   * compare by measurementID
   */
  @Override
  public int compareTo(MeasurementDescriptor o) {
    if (equals(o))
      return 0;
    else
      return this.measurementId.compareTo(o.measurementId);
  }

  @Override
  public String toString() {
    StringContainer sc = new StringContainer(",");
    sc.addTail("[", measurementId, type.toString(), encoding.toString(),
        encodingConverter.toString(), compressor.getCodecName().toString());
    sc.addTail("]");
    return sc.toString();
  }
}
