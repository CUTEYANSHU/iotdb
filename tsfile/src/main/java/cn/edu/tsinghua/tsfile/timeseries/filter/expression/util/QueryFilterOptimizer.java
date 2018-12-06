package cn.edu.tsinghua.tsfile.timeseries.filter.expression.util;

import cn.edu.tsinghua.tsfile.timeseries.filter.basic.Filter;
import cn.edu.tsinghua.tsfile.timeseries.filter.exception.QueryFilterOptimizationException;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.BinaryQueryFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.QueryFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.QueryFilterType;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.UnaryQueryFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.impl.GlobalTimeFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.impl.QueryFilterFactory;
import cn.edu.tsinghua.tsfile.timeseries.filter.expression.impl.SeriesFilter;
import cn.edu.tsinghua.tsfile.timeseries.filter.factory.FilterFactory;
import cn.edu.tsinghua.tsfile.timeseries.read.common.Path;

import java.util.List;

public class QueryFilterOptimizer {

    private static class QueryFilterOptimizerHelper {
        private static final QueryFilterOptimizer INSTANCE = new QueryFilterOptimizer();
    }

    private QueryFilterOptimizer() {

    }

    /**
     * try to remove GlobalTimeFilter
     *
     * @param queryFilter queryFilter to be transferred
     * @param selectedSeries selected series
     * @return an executable query filter, whether a GlobalTimeFilter or All leaf nodes are SeriesFilter
     */
    public QueryFilter optimize(QueryFilter queryFilter, List<Path> selectedSeries) throws QueryFilterOptimizationException {
        if (queryFilter instanceof UnaryQueryFilter) {
            return queryFilter;
        } else if (queryFilter instanceof BinaryQueryFilter) {
            QueryFilterType relation = queryFilter.getType();
            QueryFilter left = ((BinaryQueryFilter) queryFilter).getLeft();
            QueryFilter right = ((BinaryQueryFilter) queryFilter).getRight();
            if (left.getType() == QueryFilterType.GLOBAL_TIME && right.getType() == QueryFilterType.GLOBAL_TIME) {
                return combineTwoGlobalTimeFilter((GlobalTimeFilter) left, (GlobalTimeFilter) right, queryFilter.getType());
            } else if (left.getType() == QueryFilterType.GLOBAL_TIME && right.getType() != QueryFilterType.GLOBAL_TIME) {
                return handleOneGlobalTimeFilter((GlobalTimeFilter) left, right, selectedSeries, relation);
            } else if (left.getType() != QueryFilterType.GLOBAL_TIME && right.getType() == QueryFilterType.GLOBAL_TIME) {
                return handleOneGlobalTimeFilter((GlobalTimeFilter) right, left, selectedSeries, relation);
            } else if (left.getType() != QueryFilterType.GLOBAL_TIME && right.getType() != QueryFilterType.GLOBAL_TIME) {
                QueryFilter regularLeft = optimize(left, selectedSeries);
                QueryFilter regularRight = optimize(right, selectedSeries);
                BinaryQueryFilter midRet = null;
                if (relation == QueryFilterType.AND) {
                    midRet = QueryFilterFactory.and(regularLeft, regularRight);
                } else if (relation == QueryFilterType.OR) {
                    midRet = QueryFilterFactory.or(regularLeft, regularRight);
                } else {
                    throw new UnsupportedOperationException("unsupported queryFilter type: " + relation);
                }
                if (midRet.getLeft().getType() == QueryFilterType.GLOBAL_TIME || midRet.getRight().getType() == QueryFilterType.GLOBAL_TIME) {
                    return optimize(midRet, selectedSeries);
                } else {
                    return midRet;
                }

            } else if (left.getType() == QueryFilterType.SERIES && right.getType() == QueryFilterType.SERIES) {
                return queryFilter;
            }
        }
        throw new UnsupportedOperationException("unknown queryFilter type: " + queryFilter.getClass().getName());
    }

    private QueryFilter handleOneGlobalTimeFilter(GlobalTimeFilter globalTimeFilter, QueryFilter queryFilter
            , List<Path> selectedSeries, QueryFilterType relation) throws QueryFilterOptimizationException {
        QueryFilter regularRightQueryFilter = optimize(queryFilter, selectedSeries);
        if (regularRightQueryFilter instanceof GlobalTimeFilter) {
            return combineTwoGlobalTimeFilter(globalTimeFilter, (GlobalTimeFilter) regularRightQueryFilter, relation);
        }
        if (relation == QueryFilterType.AND) {
            addTimeFilterToQueryFilter((globalTimeFilter).getFilter(), regularRightQueryFilter);
            return regularRightQueryFilter;
        } else if (relation == QueryFilterType.OR) {
            return QueryFilterFactory.or(pushGlobalTimeFilterToAllSeries(globalTimeFilter, selectedSeries), queryFilter);
        }
        throw new QueryFilterOptimizationException("unknown relation in queryFilter:" + relation);
    }


    /**
     * Combine GlobalTimeFilter with all selected series
     *
     * example:
     *
     * input:
     *
     * GlobalTimeFilter(timeFilter)
     * Selected Series: path1, path2, path3
     *
     * output:
     *
     * QueryFilterOR(
     *      QueryFilterOR(
     *              SeriesFilter(path1, timeFilter),
     *              SeriesFilter(path2, timeFilter)
     *              ),
     *      SeriesFilter(path3, timeFilter)
     * )
     *
     * @return a DNF query filter without GlobalTimeFilter
     */
    private QueryFilter pushGlobalTimeFilterToAllSeries(
            GlobalTimeFilter timeFilter, List<Path> selectedSeries) throws QueryFilterOptimizationException {
        if (selectedSeries.size() == 0) {
            throw new QueryFilterOptimizationException("size of selectSeries could not be 0");
        }
        QueryFilter queryFilter = new SeriesFilter(selectedSeries.get(0), timeFilter.getFilter());
        for (int i = 1; i < selectedSeries.size(); i++) {
            queryFilter = QueryFilterFactory.or(queryFilter, new SeriesFilter(selectedSeries.get(i), timeFilter.getFilter()));
        }
        return queryFilter;
    }


    /**
     * Combine TimeFilter with all SeriesFilters in the QueryFilter
     */
    private void addTimeFilterToQueryFilter(Filter timeFilter, QueryFilter queryFilter) {
        if (queryFilter instanceof SeriesFilter) {
            addTimeFilterToSeriesFilter(timeFilter, (SeriesFilter) queryFilter);
        } else if (queryFilter instanceof QueryFilterFactory) {
            addTimeFilterToQueryFilter(timeFilter, ((QueryFilterFactory) queryFilter).getLeft());
            addTimeFilterToQueryFilter(timeFilter, ((QueryFilterFactory) queryFilter).getRight());
        } else {
            throw new UnsupportedOperationException("queryFilter should contains only SeriesFilter but other type is found:"
                    + queryFilter.getClass().getName());
        }
    }


    /**
     * Merge the timeFilter with the filter in SeriesFilter with And
     *
     * example:
     *
     * input:
     *
     * timeFilter
     * SeriesFilter(path, filter)
     *
     * output:
     *
     * SeriesFilter(
     *      path,
     *      And(filter, timeFilter)
     *      )
     *
     */
    private void addTimeFilterToSeriesFilter(Filter timeFilter, SeriesFilter seriesFilter) {
        seriesFilter.setFilter(FilterFactory.and(seriesFilter.getFilter(), timeFilter));
    }


    /**
     * combine two GlobalTimeFilter by merge the TimeFilter in each GlobalTimeFilter
     *
     * example:
     *
     * input:
     * QueryFilterAnd/OR(
     *      GlobalTimeFilter(timeFilter1),
     *      GlobalTimeFilter(timeFilter2)
     *      )
     *
     * output:
     *
     * GlobalTimeFilter(
     *      And/OR(timeFilter1, timeFilter2)
     *      )
     *
     */
    private GlobalTimeFilter combineTwoGlobalTimeFilter(GlobalTimeFilter left, GlobalTimeFilter right, QueryFilterType type) {
        if (type == QueryFilterType.AND) {
            return new GlobalTimeFilter(FilterFactory.and(left.getFilter(), right.getFilter()));
        } else if (type == QueryFilterType.OR) {
            return new GlobalTimeFilter(FilterFactory.or(left.getFilter(), right.getFilter()));
        }
        throw new UnsupportedOperationException("unrecognized QueryFilterOperatorType :" + type);
    }

    public static QueryFilterOptimizer getInstance() {
        return QueryFilterOptimizerHelper.INSTANCE;
    }
}
