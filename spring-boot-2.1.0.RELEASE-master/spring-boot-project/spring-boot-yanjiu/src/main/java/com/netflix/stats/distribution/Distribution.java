/*
*
* Copyright 2013 Netflix, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
*/
package com.netflix.stats.distribution;


/** 实现了接口DistributionMBean，提供一些指标方法方法，如：
// getNumValues()：获得收集到的值的个数
// getMean()：获得平均值
// getVariance()：得到方差
// getStdDev()：标准差
// getMinimum()：最小值
// getMaximum()：最大值
 * Accumulator of statistics about a distribution of
 * observed values that are produced incrementally.
 * <p>
 * Note that the implementation is <em>not</em> synchronized,
 * and simultaneous updates may produce incorrect results.
 * In most cases these incorrect results will be unimportant,
 * but applications that care should synchronize carefully
 * to ensure consistent results.
 * <p>
 * Note that this implements {@link DistributionMBean} and so can be
 * registered as an MBean and accessed via JMX if desired.
 *
 * @author netflixoss $
 * @version $Revision: $
 */
//使用多个成员变量数值维护收集到的数据，
//需要注意的是：本类是线程不安全的，若有多个线程请求同一个实例，那么值有可能不准确（但打点统计数据可以不要求那么精确）。
//
//该类能够记录最终值：比如总次数、总和、平均值、最大最小值等等，但是它没法记录过程值，
//比如这段时间内的最大最小值、平均值等等，在监控体系中这都是有意义的数据，子类DataBuffer将提供此种能力。
public class Distribution implements DistributionMBean, DataCollector {
	// 每noteValue()新增记录一个数据，它就+1
    private long numValues;
    // 所有值的总和
    private double sumValues;
    // sumValues的平方
    private double sumSquareValues;
    // 最大值，最小值
    private double minValue;
    private double maxValue;

    /*
     * Constructors
     */

    /**
     * Creates a new initially empty Distribution.
     */
    public Distribution() {
        numValues = 0L;
        sumValues = 0.0;
        sumSquareValues = 0.0;
        minValue = 0.0;
        maxValue = 0.0;
    }

    /*
     * Accumulating new values
     */

    /** {@inheritDoc} */
    public void noteValue(double val) {
        numValues++;
        sumValues += val;
        sumSquareValues += val * val;
        if (numValues == 1) {
            minValue = val;
            maxValue = val;
        } else if (val < minValue) {
            minValue = val;
        } else if (val > maxValue) {
            maxValue = val;
        }
    }
 // 它是DistributionMBean的接口方法
    /** {@inheritDoc} */
    public void clear() {
        numValues = 0L;
        sumValues = 0.0;
        sumSquareValues = 0.0;
        minValue = 0.0;
        maxValue = 0.0;
    }

    /*
     * Accessors
     */

    /** {@inheritDoc} */
    public long getNumValues() {
        return numValues;
    }

    /** {@inheritDoc} */
    public double getMean() {
        if (numValues < 1) {
            return 0.0;
        } else {
            return sumValues / numValues;
        }
    }

    /** {@inheritDoc} */
    public double getVariance() {
        if (numValues < 2) {
            return 0.0;
        } else if (sumValues == 0.0) {
            return 0.0;
        } else {
            double mean = getMean();
            return (sumSquareValues / numValues) - mean * mean;
        }
    }

    /** {@inheritDoc} */
    public double getStdDev() {
        return Math.sqrt(getVariance());
    }

    /** {@inheritDoc} */
    public double getMinimum() {
        return minValue;
    }

    /** {@inheritDoc} */
    public double getMaximum() {
        return maxValue;
    }

    /**
     * Add another {@link Distribution}'s values to this one.
     *
     * @param anotherDistribution
     *            the other {@link Distribution} instance
     */
    public void add(Distribution anotherDistribution) {
        if (anotherDistribution != null) {
            numValues += anotherDistribution.numValues;
            sumValues += anotherDistribution.sumValues;
            sumSquareValues += anotherDistribution.sumSquareValues;
            minValue = (minValue < anotherDistribution.minValue) ? minValue
                    : anotherDistribution.minValue;
            maxValue = (maxValue > anotherDistribution.maxValue) ? maxValue
                    : anotherDistribution.maxValue;
        }
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append("{Distribution:")
            .append("N=").append(getNumValues())
            .append(": ").append(getMinimum())
            .append("..").append(getMean())
            .append("..").append(getMaximum())
            .append("}")
            .toString();
    }

} // Distribution
