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

import java.util.concurrent.locks.Lock;


/**
 * A double-buffer of {@link DataBuffer} objects.
 * One is the "current" buffer, and new data is added to it.
 * The other is the "previous" buffer, and is used as a sorce
 * of computed statistics.
 *
 * @see DataPublisher
 *
 * @author $Author: netflixoss $
 */
public abstract class DataAccumulator implements DataCollector {

    private DataBuffer current;
    private DataBuffer previous;
    private final Object swapLock = new Object();

    /*
     * Constructor(s)
     */

    /** // 唯一构造器：必须要指定缓冲区的大小
    // 因为缓冲区的大小决定了它最大能承载多大的量。比如缓冲区是10
    // 这期间及时你100个请求打进来也只会给你统计最近的10个
     * Creates a new initially empty DataAccumulator.
     *
     * @param bufferSize the size of the buffers to use
     */
    public DataAccumulator(int bufferSize) {
        this.current = new DataBuffer(bufferSize);
        this.previous = new DataBuffer(bufferSize);
    }

    /*
     * Accumulating new values
     */
 // 数据记录在current里。但是注意：加锁了，swapLock交换锁
 	// 也就是说在swap的时候不让新增数据，新增数据的时候不让swap
    /** {@inheritDoc} */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "MDM_WAIT_WITHOUT_TIMEOUT")
    public void noteValue(double val) {
        synchronized (swapLock) {
            Lock l = current.getLock();
            l.lock();
            try {
                current.noteValue(val);
            } finally {
                l.unlock();
            }
        }
    }

    /**交换数据收集缓冲区，并计算统计数据关于目前收集的数据。
     * Swaps the data collection buffers, and computes statistics
     * about the data collected up til now.
     */
    @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "MDM_WAIT_WITHOUT_TIMEOUT")
    public void publish() {
        /*
         * Some care is required here to correctly swap the DataBuffers,
         * but not hold the synchronization object while compiling stats
         * (a potentially long computation).  This ensures that continued
         * data collection (calls to noteValue()) will not be blocked for any
         * significant period.
         */
        DataBuffer tmp = null;
        Lock l = null;
        synchronized (swapLock) {
            // Swap buffers
            tmp = current;
            current = previous;
            previous = tmp;
            // Start collection in the new "current" buffer
            l = current.getLock();
            l.lock();
            try {// 开启收集
                current.startCollection();
            } finally {
                l.unlock();
            }
            // Grab lock on new "previous" buffer
         // 把previous老的结束收集，并且把数据发布出去参与计算
            l = tmp.getLock();
            l.lock();
        }
        // Release synchronizaton *before* publishing data
        try {
            tmp.endCollection();
            publish(tmp);
        } finally {
            l.unlock();
        }
    }

    /**
     * Called to publish recently collected data.
     * When called, the {@link Lock} associated with the "previous"
     * buffer is held, so the data will not be changed.
     * Other locks have been released, and so new data can be
     * collected in the "current" buffer.
     * The data in the buffer has also been sorted in increasing order.
     *
     * @param buf the {@code DataBuffer} that is now "previous".
     */
    protected abstract void publish(DataBuffer buf);

} // DataAccumulator
