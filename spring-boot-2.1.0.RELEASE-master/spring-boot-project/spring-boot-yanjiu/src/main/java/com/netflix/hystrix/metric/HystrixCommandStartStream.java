/**
 * Copyright 2015 Netflix, Inc.
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
 */
package com.netflix.hystrix.metric;

import com.netflix.hystrix.HystrixCommandKey;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-Command stream of {@link HystrixCommandExecutionStarted}s.  This gets written to by {@link HystrixThreadEventStream}s.
 * Events are emitted synchronously in the same thread that performs the command execution.
 */
public class HystrixCommandStartStream implements HystrixEventStream<HystrixCommandExecutionStarted> {
    private final HystrixCommandKey commandKey;
 // Subject它既能发送，又能监听
 	// 发送和接受的数据类型是一样，均是HystrixCommandExecutionStarted类型
    private final Subject<HystrixCommandExecutionStarted, HystrixCommandExecutionStarted> writeOnlySubject;
    private final Observable<HystrixCommandExecutionStarted> readOnlyStream;
 // 缓存：每个commandKey对应同一个HystrixCommandStartStream实例
 	// 这样发送数据流也方便统计
    private static final ConcurrentMap<String, HystrixCommandStartStream> streams = new ConcurrentHashMap<String, HystrixCommandStartStream>();

    public static HystrixCommandStartStream getInstance(HystrixCommandKey commandKey) {
        HystrixCommandStartStream initialStream = streams.get(commandKey.name());
        if (initialStream != null) {
            return initialStream;
        } else {
            synchronized (HystrixCommandStartStream.class) {
                HystrixCommandStartStream existingStream = streams.get(commandKey.name());
                if (existingStream == null) {
                    HystrixCommandStartStream newStream = new HystrixCommandStartStream(commandKey);
                    streams.putIfAbsent(commandKey.name(), newStream);
                    return newStream;
                } else {
                    return existingStream;
                }
            }
        }
    }

    HystrixCommandStartStream(final HystrixCommandKey commandKey) {
        this.commandKey = commandKey;

        this.writeOnlySubject = new SerializedSubject<HystrixCommandExecutionStarted, HystrixCommandExecutionStarted>(PublishSubject.<HystrixCommandExecutionStarted>create());
        this.readOnlyStream = writeOnlySubject.share();
    }

    public static void reset() {
        streams.clear();
    }
 // 重要。提供写方法：把该event写到发射器里面去，这样订阅者就能读啦
 	// 该方法的唯一调用处是：HystrixThreadEventStream
    public void write(HystrixCommandExecutionStarted event) {
        writeOnlySubject.onNext(event);
    }

    @Override
    public Observable<HystrixCommandExecutionStarted> observe() {
        return readOnlyStream;
    }

    @Override
    public String toString() {
        return "HystrixCommandStartStream(" + commandKey.name() + ")";
    }
}
