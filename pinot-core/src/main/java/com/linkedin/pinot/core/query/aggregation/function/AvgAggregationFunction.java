package com.linkedin.pinot.core.query.aggregation.function;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import com.linkedin.pinot.common.data.FieldSpec.DataType;
import com.linkedin.pinot.common.request.AggregationInfo;
import com.linkedin.pinot.core.common.BlockValIterator;
import com.linkedin.pinot.core.query.aggregation.AggregationFunction;
import com.linkedin.pinot.core.query.aggregation.CombineLevel;
import com.linkedin.pinot.core.query.aggregation.function.AvgAggregationFunction.AvgPair;
import com.linkedin.pinot.core.query.utils.Pair;


/**
 * This function will take a column and do sum on that.
 *
 */
public class AvgAggregationFunction implements AggregationFunction<AvgPair, AvgPair> {

  private String _avgByColumn;

  public AvgAggregationFunction() {

  }

  @Override
  public void init(AggregationInfo aggregationInfo) {
    _avgByColumn = aggregationInfo.getAggregationParams().get("column");

  }

  @Override
  public AvgPair aggregate(BlockValIterator[] blockValIterators) {
    double ret = 0;
    long cnt = 0;
    while (blockValIterators[0].hasNext()) {
      ret += blockValIterators[0].nextDoubleVal();
      cnt++;
    }
    return new AvgPair(ret, cnt);
  }

  @Override
  public AvgPair aggregate(AvgPair oldValue, BlockValIterator[] blockValIterators) {
    if (oldValue == null) {
      return new AvgPair(blockValIterators[0].nextDoubleVal(), (long) 1);
    }
    return new AvgPair(oldValue.getFirst() + blockValIterators[0].nextDoubleVal(), oldValue.getSecond() + 1);
  }

  @Override
  public List<AvgPair> combine(List<AvgPair> aggregationResultList, CombineLevel combineLevel) {
    double combinedSumResult = 0;
    long combinedCntResult = 0;
    for (AvgPair aggregationResult : aggregationResultList) {
      combinedSumResult += aggregationResult.getFirst();
      combinedCntResult += aggregationResult.getSecond();
    }
    aggregationResultList.clear();
    aggregationResultList.add(new AvgPair(combinedSumResult, combinedCntResult));
    return aggregationResultList;
  }

  @Override
  public AvgPair combineTwoValues(AvgPair aggregationResult0, AvgPair aggregationResult1) {
    if (aggregationResult0 == null) {
      return aggregationResult1;
    }
    if (aggregationResult1 == null) {
      return aggregationResult0;
    }
    return new AvgPair(aggregationResult0.getFirst() + aggregationResult1.getFirst(), aggregationResult0.getSecond()
        + aggregationResult1.getSecond());
  }

  @Override
  public AvgPair reduce(List<AvgPair> combinedResult) {

    double reducedSumResult = 0;
    long reducedCntResult = 0;
    for (AvgPair combineResult : combinedResult) {
      reducedSumResult += combineResult.getFirst();
      reducedCntResult += combineResult.getSecond();
    }
    return new AvgPair(reducedSumResult, reducedCntResult);
  }

  @Override
  public JSONObject render(AvgPair finalAggregationResult) {
    try {
      double avgResult = Double.NaN;
      if ((finalAggregationResult != null) && (finalAggregationResult.getSecond() != 0)) {
        avgResult = finalAggregationResult.getFirst() / ((double) finalAggregationResult.getSecond());
      }
      return new JSONObject().put("value", String.format("%.5f", avgResult));
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public DataType aggregateResultDataType() {
    return DataType.OBJECT;
  }

  @Override
  public String getFunctionName() {
    return "avg_" + _avgByColumn;
  }

  public class AvgPair extends Pair<Double, Long> implements Comparable<AvgPair>, Serializable {
    public AvgPair(Double first, Long second) {
      super(first, second);
    }

    @Override
    public int compareTo(AvgPair o) {
      if (getSecond() == 0) {
        return -1;
      }
      if (o.getSecond() == 0) {
        return 1;
      }
      if ((getFirst() / getSecond()) > (o.getFirst() / o.getSecond())) {
        return 1;
      }
      if ((getFirst() / getSecond()) < (o.getFirst() / o.getSecond())) {
        return -1;
      }
      return 0;
    }

    @Override
    public String toString() {
      return new DecimalFormat("####################.##########").format((getFirst() / getSecond()));
    }
  }
}