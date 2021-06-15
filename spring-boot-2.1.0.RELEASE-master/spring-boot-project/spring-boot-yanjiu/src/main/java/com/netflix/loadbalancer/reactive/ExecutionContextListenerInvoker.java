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

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.reactive.ExecutionListener.AbortExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to invoke the list of {@link ExecutionListener} with
 * {@link ExecutionContext}
 *
 * @author Allen Wang
 */
public class ExecutionContextListenerInvoker<I, O> {

	private final static Logger logger = LoggerFactory.getLogger(ExecutionContextListenerInvoker.class);
	// 执行上下文
	private final ExecutionContext<I> context;
	// 执行时的监听器们
	private final List<ExecutionListener<I, O>> listeners;
	private final IClientConfig clientConfig;
	// key表示的是ExecutionListener实现类的全类名
	// value：IClientConfigKey。它的值是"listener." + key+ ".disabled"
	// 因为CommonClientConfigKey.valueOf()比较耗时，所以这里用了缓存
	private final ConcurrentHashMap<String, IClientConfigKey> classConfigKeyMap;

	public ExecutionContextListenerInvoker(ExecutionContext<I> context, List<ExecutionListener<I, O>> listeners) {
		this(context, listeners, null);
	}

	public ExecutionContextListenerInvoker(List<ExecutionListener<I, O>> listeners, IClientConfig config) {
		this(null, listeners, config);
	}

	public ExecutionContextListenerInvoker(ExecutionContext<I> context, List<ExecutionListener<I, O>> listeners,
			IClientConfig config) {
		this.listeners = Collections.unmodifiableList(listeners);
		this.context = context;
		this.clientConfig = config;
		if (clientConfig != null) {
			classConfigKeyMap = new ConcurrentHashMap<String, IClientConfigKey>();
		} else {
			classConfigKeyMap = null;
		}
	}

	public ExecutionContextListenerInvoker(List<ExecutionListener<I, O>> listeners) {
		this(null, listeners);
	}

	public void onExecutionStart() {
		onExecutionStart(this.context);
	}

	public void onExecutionStart(ExecutionContext<I> context) {
		for (ExecutionListener<I, O> listener : listeners) {
			try {
				//// 若这个Listener没有被禁用，那就执行它
				if (!isListenerDisabled(listener)) {
					// 请注意：这里的上下文使用的是子上下文哦
					// 所以保证了每个监听器均有一个自己的上下文，各有一份自己的数据，线程安全
					listener.onExecutionStart(context.getChildContext(listener));
				}
			} catch (Throwable e) {
				if (e instanceof AbortExecutionException) {
					throw (AbortExecutionException) e;
				}
				logger.error("Error invoking listener " + listener, e);
			}
		}
	}

	public void onStartWithServer(ExecutionInfo info) {
		onStartWithServer(this.context, info);
	}

	/**
	 * Called when a server is chosen and the request is going to be executed on the
	 * server.
	 *
	 */
	public void onStartWithServer(ExecutionContext<I> context, ExecutionInfo info) {
		for (ExecutionListener<I, O> listener : listeners) {
			try {
				if (!isListenerDisabled(listener)) {
					listener.onStartWithServer(context.getChildContext(listener), info);
				}
			} catch (Throwable e) {
				if (e instanceof AbortExecutionException) {
					throw (AbortExecutionException) e;
				}
				logger.error("Error invoking listener " + listener, e);
			}
		}
	}

	public void onExceptionWithServer(Throwable exception, ExecutionInfo info) {
		onExceptionWithServer(this.context, exception, info);
	}

	/**
	 * Called when an exception is received from executing the request on a server.
	 *
	 * @param exception Exception received
	 */
	public void onExceptionWithServer(ExecutionContext<I> context, Throwable exception, ExecutionInfo info) {
		for (ExecutionListener<I, O> listener : listeners) {
			try {
				if (!isListenerDisabled(listener)) {
					listener.onExceptionWithServer(context.getChildContext(listener), exception, info);
				}
			} catch (Throwable e) {
				logger.error("Error invoking listener " + listener, e);
			}
		}
	}

	public void onExecutionSuccess(O response, ExecutionInfo info) {
		onExecutionSuccess(this.context, response, info);
	}

	/**
	 * Called when the request is executed successfully on the server
	 *
	 * @param response Object received from the execution
	 */
	public void onExecutionSuccess(ExecutionContext<I> context, O response, ExecutionInfo info) {
		for (ExecutionListener<I, O> listener : listeners) {
			try {
				if (!isListenerDisabled(listener)) {
					listener.onExecutionSuccess(context.getChildContext(listener), response, info);
				}
			} catch (Throwable e) {
				logger.error("Error invoking listener " + listener, e);
			}
		}
	}

	public void onExecutionFailed(Throwable finalException, ExecutionInfo info) {
		onExecutionFailed(this.context, finalException, info);
	}

	/**
	 * Called when the request is considered failed after all retries.
	 *
	 * @param finalException Final exception received.
	 */
	public void onExecutionFailed(ExecutionContext<I> context, Throwable finalException, ExecutionInfo info) {
		for (ExecutionListener<I, O> listener : listeners) {
			try {
				if (!isListenerDisabled(listener)) {
					listener.onExecutionFailed(context.getChildContext(listener), finalException, info);
				}
			} catch (Throwable e) {
				logger.error("Error invoking listener " + listener, e);
			}
		}
	}
	// 执行监听器其它方法的逻辑一毛一样，略

		// 判断一个监听器是否被禁用。可通过动态配置来开/关
		// "listener." + className + ".disabled"：禁用
	private boolean isListenerDisabled(ExecutionListener<?, ?> listener) {
		if (clientConfig == null) {
			return false;
		} else {
			String className = listener.getClass().getName();
			IClientConfigKey key = classConfigKeyMap.get(className);
			if (key == null) {
				key = CommonClientConfigKey.valueOf("listener." + className + ".disabled");
				IClientConfigKey old = classConfigKeyMap.putIfAbsent(className, key);
				if (old != null) {
					key = old;
				}
			}
			return clientConfig.getPropertyAsBoolean(key, false);
		}
	}
}
