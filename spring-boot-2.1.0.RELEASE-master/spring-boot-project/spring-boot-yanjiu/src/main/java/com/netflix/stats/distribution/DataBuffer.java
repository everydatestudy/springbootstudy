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

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**在父类Distribution的基础上，
 * 增加了固定大小的数据收集缓冲区double[] buf，
 * 用于保存滑动窗口最近增加的值。因为可以缓冲数据了，所以有startCollection和endCollection动作，代表着一个收集周期。
 * A fixed-size data collection buffer that holds a sliding window
 * of the most recent values added.
 * The {@code DataBuffer} is also a {@link Distribution} and so collects
 * basic statistics about the data added to the buffer.
 * This statistical data is managed on-the-fly, and reflects all the data
 * added, if those values that may have been dropped due to buffer overflow.
 * <p>
 * This class is <em>not</em> synchronized, but can instead managed by a
 * {@link Lock} attached to the {@code DataBuffer} (see {@link #getLock}).
 * @author netflixoss
 */
public class DataBuffer extends Distribution {

    private final Lock lock;
 // 这就是这个缓冲区，它里面的值代表着一个窗口期内的数据记录
    private final double[] buf;
    private long startMillis;
    private long endMillis;
    private int size;
    private int insertPos;

    /*
     * Constructors
     */

    /**
     * Creates a new {@code DataBuffer} with a given capacity.
     */
    public DataBuffer(int capacity) {
        lock = new ReentrantLock();
        buf = new double[capacity];
        startMillis = 0;
        size = 0;
        insertPos = 0;
    }

    /*
     * Accessors
     */

    /**
     * Gets the {@link Lock} to use to manage access to the
     * contents of the {@code DataBuffer}.
     */
    public Lock getLock() {
        return lock;
    }

    /**
     * Gets the capacity of the {@code DataBuffer}; that is,
     * the maximum number of values that the {@code DataBuffer} can hold.
     */
    public int getCapacity() {
        return buf.length;
    }

    /**一个样本时间窗口。比如5000ms
     * Gets the length of time over which the data was collected,
     * in milliseconds.
     * The value is only valid after {@link #endCollection}
     * has been called (and before a subsequent call to {@link #startCollection}).
     */
    public long getSampleIntervalMillis() {
        return (endMillis - startMillis);
    }

    /**样本大小（说明：并不一定是buf的长度哦）
     * Gets the number of values currently held in the buffer.
     * This value may be smaller than the value of {@link #getNumValues}
     * depending on how the percentile values were computed.
     */
    public int getSampleSize() {
        return size;
    }

    /*
     * Managing the data
     */

    /** {@inheritDoc} */
    @Override
    public void clear() {
        super.clear();
        startMillis = 0;
        size = 0;
        insertPos = 0;
    }

    /** **一切归零**，开始收集
     * Notifies the buffer that data is collection is now enabled.
     */
    public void startCollection() {
        clear();
        startMillis = System.currentTimeMillis();
    }

    /**
     * Notifies the buffer that data has just ended.
     * <p>
     * <b>Performance Note:</b>
     * <br>This method sorts the underlying data buffer,
     * and so may be slow.  It is best to call this at most once
     * and fetch all percentile values desired, instead of making
     * a number of repeated calls.
     */
    public void endCollection() {
        endMillis = System.currentTimeMillis();
        Arrays.sort(buf, 0, size);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The buffer wraps-around if it is full, overwriting the oldest
     * entry with the new value.
     */
    @Override
    public void noteValue(double val) {
        super.noteValue(val);
        buf[insertPos++] = val;
     // 如果缓冲区满了，就会覆盖最老的缓冲区输入新值。
     		// 注意这个算法：覆盖最老的缓冲区的值，并不是简单粗暴的从头开始覆盖
        if (insertPos >= buf.length) {
            insertPos = 0;
            size = buf.length;
        } else if (insertPos > size) {
            size = insertPos;
        }
    }

    /**计算，并获取请求百分比的统计信息
     * Gets the requested percentile statistics.
     *
     * @param percents array of percentile values to compute,
     *    which must be in the range {@code [0 .. 100]}
     * @param percentiles array to fill in with the percentile values;
     *    must be the same length as {@code percents}
     * @return the {@code percentiles} array
     * @see <a href="http://en.wikipedia.org/wiki/Percentile">Percentile (Wikipedia)</a>
     * @see <a href="http://cnx.org/content/m10805/latest/">Percentile</a>
     */
    public double[] getPercentiles(double[] percents, double[] percentiles) {
        for (int i = 0; i < percents.length; i++) {
        	// 计算百分比统计信息。比如若percents[i] = 50的话
        	// 就是计算buf缓冲区里中位数的值
        	// 90的话：计算90分位数的值（也就是该值比90%的数值都大）
        	// computePercentile是私有方法：根据当前窗口内收集到的数据进行计算分位数
            percentiles[i] = computePercentile(percents[i]);
        }
        return percentiles;
    }

    private double computePercentile(double percent) {
        // Some just-in-case edge cases
        if (size <= 0) {
            return 0.0;
        } else if (percent <= 0.0) {
            return buf[0];
        } else if (percent >= 100.0) {        // SUPPRESS CHECKSTYLE MagicNumber
            return buf[size - 1];
        }
        /*
         * Note:
         * Documents like http://cnx.org/content/m10805/latest
         * use a one-based ranking, while this code uses a zero-based index,
         * so the code may not look exactly like the formulas.
         */
        double index = (percent / 100.0) * size; // SUPPRESS CHECKSTYLE MagicNumber
        int iLow = (int) Math.floor(index);
        int iHigh = (int) Math.ceil(index);
        assert 0 <= iLow && iLow <= index && index <= iHigh && iHigh <= size;
        assert (iHigh - iLow) <= 1;
        if (iHigh >= size) {
            // Another edge case
            return buf[size - 1];
        } else if (iLow == iHigh) {
            return buf[iLow];
        } else {
            // Interpolate between the two bounding values
            return buf[iLow] + (index - iLow) * (buf[iHigh] - buf[iLow]);
        }
    }

} // DataBuffer
