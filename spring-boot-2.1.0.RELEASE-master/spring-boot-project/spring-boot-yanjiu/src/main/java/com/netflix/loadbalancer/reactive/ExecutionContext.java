/*
 *
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.loadbalancer.reactive;

import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A context object that is created at start of each load balancer execution
 * and contains certain meta data of the load balancer and mutable state data of 
 * execution per listener per request. Each listener will get its own context
 * to work with. But it can also call {@link ExecutionContext#getGlobalContext()} to
 * get the shared context between all listeners.
 * 
 * @author Allen Wang
 *
 */
public class ExecutionContext<T> {
	// 存储元数据的Map
    private final Map<String, Object> context;
   // ChildContext继承自ExecutionContext
    private final ConcurrentHashMap<Object, ChildContext<T>> subContexts;
	// 请求。requestConfig的优先级比clientConfig的高哦，具有“覆盖”效果
    private final T request;
    private final IClientConfig requestConfig;
    private final RetryHandler retryHandler;
    private final IClientConfig clientConfig;
	// 子上下文：它增加了一个属性ExecutionContext<T> parent;
	// 这样便可以让两个ExecutionContext关联起来
    private static class ChildContext<T> extends ExecutionContext<T> {
        private final ExecutionContext<T> parent;

        ChildContext(ExecutionContext<T> parent) {
            super(parent.request, parent.requestConfig, parent.clientConfig, parent.retryHandler, null);
            this.parent = parent;
        }

        @Override
        public ExecutionContext<T> getGlobalContext() {
            return parent;
        }
    }

    public ExecutionContext(T request, IClientConfig requestConfig, IClientConfig clientConfig, RetryHandler retryHandler) {
        this.request = request;
        this.requestConfig = requestConfig;
        this.clientConfig = clientConfig;
        this.context = new ConcurrentHashMap<String, Object>();
        this.subContexts = new ConcurrentHashMap<Object, ChildContext<T>>();
        this.retryHandler = retryHandler;
    }

    ExecutionContext(T request, IClientConfig requestConfig, IClientConfig clientConfig, RetryHandler retryHandler, ConcurrentHashMap<Object, ChildContext<T>> subContexts) {
        this.request = request;
        this.requestConfig = requestConfig;
        this.clientConfig = clientConfig;
        this.context = new ConcurrentHashMap<String, Object>();
        this.subContexts = subContexts;
        this.retryHandler = retryHandler;
    }

    // default访问权限的方法：获取子执行上下文
 	// 没有就会new一个然后放在ConcurrentHashMap缓存起来。key就是obj
    ExecutionContext<T> getChildContext(Object obj) {
        if (subContexts == null) {
            return null;
        }
        ChildContext<T> subContext = subContexts.get(obj);
        if (subContext == null) {
            subContext = new ChildContext<T>(this);
            ChildContext<T> old = subContexts.putIfAbsent(obj, subContext);
            if (old != null) {
                subContext = old;
            }
        }
        return subContext;
    }

    public T getRequest() {
        return request;
    }

    public Object get(String name) {
        return context.get(name);
    }
    // 先去requestConfig里找，若没找再去clientConfig里找
    public <S> S getClientProperty(IClientConfigKey<S> key) {
        S value;
        if (requestConfig != null) {
            value = requestConfig.get(key);
            if (value != null) {
                return value;
            }
        }
        value = clientConfig.get(key);
        return value;
    }
 // 获得指定name的value。一个简单的k-v而已
    public void put(String name, Object value) {
        context.put(name, value);
    }

    /**
     * @return The IClientConfig object used to override the client's default configuration
     * for this specific execution.
     */
    public IClientConfig getRequestConfig() {
        return requestConfig;
    }

    /**
     *注意：子上下文ChildContext的该方法返回的是parent，所以就打通了
     * @return The shared context for all listeners.
     */
    public ExecutionContext<T> getGlobalContext() {
        return this;
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }
}
