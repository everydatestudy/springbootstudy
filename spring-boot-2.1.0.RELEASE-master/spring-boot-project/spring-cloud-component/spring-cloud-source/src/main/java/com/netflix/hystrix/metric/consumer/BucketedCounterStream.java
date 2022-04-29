/**
 * Copyright 2015 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.metric.consumer;

import com.netflix.hystrix.metric.HystrixEvent;
import com.netflix.hystrix.metric.HystrixEventStream;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.BehaviorSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract class that imposes a bucketing structure and provides streams of buckets
 *
 * @param <Event> type of raw data that needs to get summarized into a bucket
 * @param <Bucket> type of data contained in each bucket
 * @param <Output> type of data emitted to stream subscribers (often is the same as A but does not have to be)
 */
//Event：需要汇聚到桶里面的原始事件类型（HystrixEvent是原始的，HystrixRollingNumberEvent是直接的）
	// Hystrix 中的调用事件，如命令开始执行、命令执行完成等
//Bucket：每个桶中包含的数据类型
//Output：最终输出类型：发送给流订阅者的数据类型(通常与Bucket相同，但不必相同)
public abstract class BucketedCounterStream<Event extends HystrixEvent, Bucket, Output> {
    protected final int numBuckets;
    protected final Observable<Bucket> bucketedStream;
    // 订阅信息：允许订阅or取消订阅
    protected final AtomicReference<Subscription> subscription = new AtomicReference<Subscription>(null);
	// 它是一个函数。用于把Observable<Event>转为Observable<Bucket>
    private final Func1<Observable<Event>, Observable<Bucket>> reduceBucketToSummary;

    private final BehaviorSubject<Output> counterSubject = BehaviorSubject.create(getEmptyOutputValue());
   // inputEventStream：事件流，input输入。比如command执行开始、结束时都会有输入
 	// numBuckets：用户不配置的话，默认它是10
 	// bucketSizeInMs：窗口毫秒值。若不配置回事1秒
 	// appendRawEventToBucket：它是一个函数 R call(T1 t1, T2 t2) 输入Bucket, Event返回Bucket类型
   
    protected BucketedCounterStream(final HystrixEventStream<Event> inputEventStream, final int numBuckets, final int bucketSizeInMs,
                                    final Func2<Bucket, Event, Bucket> appendRawEventToBucket) {
        this.numBuckets = numBuckets;
       // getEmptyBucketSummary是否抽象方法：获取空桶
        this.reduceBucketToSummary = new Func1<Observable<Event>, Observable<Bucket>>() {
            @Override
            public Observable<Bucket> call(Observable<Event> eventBucket) {
            	//这个reduce函数的初始值为：getEmptyBucketSummary()也就是空桶，
            	//它是抽象方法由子类实现。appendRawEventToBucket负责具体的reduce聚合逻辑，
            	//这是由构造函数传进来的函数：Bucket + Event -> Bucket，表示：对于每个 Event，都将其聚合到 Bucket 中，并返回聚合后的 Bucket。
            	// 说明：不同的实现对归约appendRawEventToBucket函数的实现是不同的，
            	//比如熔断器依赖的HealthCountsStream它就是以long[]作为每个桶的。
                return eventBucket.reduce(getEmptyBucketSummary(), appendRawEventToBucket);
            }
        };

        final List<Bucket> emptyEventCountsToStart = new ArrayList<Bucket>();
        for (int i = 0; i < numBuckets; i++) {
            emptyEventCountsToStart.add(getEmptyBucketSummary());
        }

        this.bucketedStream = Observable.defer(new Func0<Observable<Bucket>>() {
            @Override
            public Observable<Bucket> call() {
                return inputEventStream
                        .observe()
                         // 按单元窗口长度来将某个时间段内的调用事件聚集起来
                       // 它可以按单元窗口长度来将某个时间段内的调用事件聚集起来。
                        .window(bucketSizeInMs, TimeUnit.MILLISECONDS) //bucket it by the counter window so we can emit to the next operator in time chunks, not on every OnNext
                        // 将每个单元窗口内聚集起来的事件集合聚合成桶
                        .flatMap(reduceBucketToSummary)                //for a given bucket, turn it into a long array containing counts of event types
                        // 为了保证窗口的完整性，开始的时候先产生一串空的桶
                        .startWith(emptyEventCountsToStart);           //start it with empty arrays to make consumer logic as generic as possible (windows are always full)
            }
        });
    }
    // 抽象方法：访问权限是Default哦~~~
    abstract Bucket getEmptyBucketSummary();
    // 空的输出值。作为BehaviorSubject的默认值
    abstract Output getEmptyOutputValue();

    /**
     * Return the stream of buckets
     * @return stream of buckets
     */
	// 注意：这个泛型是output，并不是输入哦。返回的是处理后的输出流，所以一般是桶
	// 它是public的
    public abstract Observable<Output> observe();

    public void startCachingStreamValuesIfUnstarted() {
        if (subscription.get() == null) {
            //the stream is not yet started
            Subscription candidateSubscription = observe().subscribe(counterSubject);
            if (subscription.compareAndSet(null, candidateSubscription)) {
                //won the race to set the subscription
            } else {
                //lost the race to set the subscription, so we need to cancel this one
                candidateSubscription.unsubscribe();
            }
        }
    }

    /**这是一个同步调用。以检索最后一个计算的桶，而不需要等待任何发射
	// 该方法会在很多地方被调用
     * Synchronous call to retrieve the last calculated bucket without waiting for any emissions
     * @return last calculated bucket
     */
    public Output getLatest() {
        startCachingStreamValuesIfUnstarted();
        if (counterSubject.hasValue()) {
            return counterSubject.getValue();
        } else {
            return getEmptyOutputValue();
        }
    }
    // 取消subscription的订阅（它的设值方法见下）
    public void unsubscribe() {
        Subscription s = subscription.get();
        if (s != null) {
            s.unsubscribe();
            subscription.compareAndSet(s, null);
        }
    }
}
