package cn.edu.tsinghua.tsfile.read.query.executor;

import cn.edu.tsinghua.tsfile.file.metadata.ChunkMetaData;
import cn.edu.tsinghua.tsfile.file.metadata.enums.TSDataType;
import cn.edu.tsinghua.tsfile.read.controller.ChunkLoader;
import cn.edu.tsinghua.tsfile.read.reader.series.SeriesReaderWithFilter;
import cn.edu.tsinghua.tsfile.read.reader.series.SeriesReaderWithoutFilter;
import cn.edu.tsinghua.tsfile.read.filter.exception.QueryFilterOptimizationException;
import cn.edu.tsinghua.tsfile.read.expression.QueryFilter;
import cn.edu.tsinghua.tsfile.read.expression.impl.GlobalTimeFilter;
import cn.edu.tsinghua.tsfile.read.expression.util.QueryFilterOptimizer;
import cn.edu.tsinghua.tsfile.read.common.Path;
import cn.edu.tsinghua.tsfile.read.controller.MetadataQuerier;
import cn.edu.tsinghua.tsfile.read.query.QueryExpression;
import cn.edu.tsinghua.tsfile.read.query.dataset.DataSetWithoutTimeGenerator;
import cn.edu.tsinghua.tsfile.read.query.dataset.QueryDataSet;
import cn.edu.tsinghua.tsfile.read.reader.series.SeriesReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class TsFileExecutor implements QueryExecutor {

    private MetadataQuerier metadataQuerier;
    private ChunkLoader chunkLoader;

    public TsFileExecutor(MetadataQuerier metadataQuerier, ChunkLoader chunkLoader) {
        this.metadataQuerier = metadataQuerier;
        this.chunkLoader = chunkLoader;
    }

    @Override
    public QueryDataSet execute(QueryExpression queryExpression) throws IOException {

        metadataQuerier.loadChunkMetaDatas(queryExpression.getSelectedSeries());

        if (queryExpression.hasQueryFilter()) {
            try {
                QueryFilter queryFilter = queryExpression.getQueryFilter();
                QueryFilter regularQueryFilter = QueryFilterOptimizer.getInstance().optimize(queryFilter, queryExpression.getSelectedSeries());
                queryExpression.setQueryFilter(regularQueryFilter);

                if (regularQueryFilter instanceof GlobalTimeFilter) {
                    return execute(queryExpression.getSelectedSeries(), (GlobalTimeFilter) regularQueryFilter);
                } else {
                    return new ExecutorWithTimeGenerator(metadataQuerier, chunkLoader).execute(queryExpression);
                }
            } catch (QueryFilterOptimizationException e) {
                throw new IOException(e);
            }
        } else {
            return execute(queryExpression.getSelectedSeries());
        }
    }


    /**
     * no filter, can use multi-way merge
     *
     * @param selectedPathList all selected paths
     * @return DataSet without TimeGenerator
     */
    private QueryDataSet execute(List<Path> selectedPathList) throws IOException {
        List<SeriesReader> readersOfSelectedSeries = new ArrayList<>();
        List<TSDataType> dataTypes = new ArrayList<>();

        for (Path path : selectedPathList) {
            List<ChunkMetaData> chunkMetaDataList = metadataQuerier.getChunkMetaDataList(path);
            SeriesReader seriesReader = new SeriesReaderWithoutFilter(chunkLoader, chunkMetaDataList);
            readersOfSelectedSeries.add(seriesReader);
            dataTypes.add(chunkMetaDataList.get(0).getTsDataType());
        }
        return new DataSetWithoutTimeGenerator(selectedPathList, dataTypes, readersOfSelectedSeries);
    }


    /**
     * has a GlobalTimeFilter, can use multi-way merge
     *
     * @param selectedPathList all selected paths
     * @param timeFilter       GlobalTimeFilter that takes effect to all selected paths
     * @return DataSet without TimeGenerator
     */
    private QueryDataSet execute(List<Path> selectedPathList, GlobalTimeFilter timeFilter) throws IOException {
        List<SeriesReader> readersOfSelectedSeries = new ArrayList<>();
        List<TSDataType> dataTypes = new ArrayList<>();

        for (Path path : selectedPathList) {
            List<ChunkMetaData> chunkMetaDataList = metadataQuerier.getChunkMetaDataList(path);
            SeriesReader seriesReader = new SeriesReaderWithFilter(chunkLoader, chunkMetaDataList, timeFilter.getFilter());
            readersOfSelectedSeries.add(seriesReader);
            dataTypes.add(chunkMetaDataList.get(0).getTsDataType());
        }

        return new DataSetWithoutTimeGenerator(selectedPathList, dataTypes, readersOfSelectedSeries);
    }


}