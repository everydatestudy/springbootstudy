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
package com.netflix.client;

import com.google.common.collect.Lists;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.List;

/**
 * A default {@link RetryHandler}. The implementation is limited to
 * known exceptions in java.net. Specific client implementation should provide its own
 * {@link RetryHandler}
 * 
 * @author awang
 */
public class DefaultLoadBalancerRetryHandler implements RetryHandler {
	
	// 这两个异常会进行重试。代表连接不上嘛，重试是很合理的
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> retriable = 
            Lists.<Class<? extends Throwable>>newArrayList(ConnectException.class, SocketTimeoutException.class);
    // 和电路circuit相关的异常类型
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> circuitRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class, SocketTimeoutException.class);
    // 不解释。它哥三个都可以通过IClientConfig配置
 	// `MaxAutoRetries`，默认值是0。也就是说在同一机器上不重试（只会执行一次，失败就失败了）
    protected final int retrySameServer;
    
    // `MaxAutoRetriesNextServer`，默认值是1，也就是只会再试下面一台机器 不行就不行了
    protected final int retryNextServer;
 // 重试开关。true：开启重试  false：不开启重试
    // `OkToRetryOnAllOperations`属性控制其值，默认也是false 也就是说默认并不重试
    protected final boolean retryEnabled;

    public DefaultLoadBalancerRetryHandler() {
        this.retrySameServer = 0;
        this.retryNextServer = 0;
        this.retryEnabled = false;
    }
    
    public DefaultLoadBalancerRetryHandler(int retrySameServer, int retryNextServer, boolean retryEnabled) {
        this.retrySameServer = retrySameServer;
        this.retryNextServer = retryNextServer;
        this.retryEnabled = retryEnabled;
    }
    //构造器赋值：值可以从IClientConfig里来（常用）
	// 当然你也可以通过其他构造器传过来
    public DefaultLoadBalancerRetryHandler(IClientConfig clientConfig) {
        this.retrySameServer = clientConfig.get(CommonClientConfigKey.MaxAutoRetries, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES);
        this.retryNextServer = clientConfig.get(CommonClientConfigKey.MaxAutoRetriesNextServer, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER);
        this.retryEnabled = clientConfig.get(CommonClientConfigKey.OkToRetryOnAllOperations, false);
    }
    // 是否是可进行重试的异常类型？
    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
    	// 1、若retryEnabled=false全局关闭了禁止重试，那就掉头就走，不用看了
    	// 2、若retryEnabled=true，就继续看看吧
        if (retryEnabled) {
        	// 3、若是在同一台Server上（注意此Server上首次请求已经失败），所以需要看这次的异常类型是啥
            if (sameServer) {
                return Utils.isPresentAsCause(e, getRetriableExceptions());
            } else {
            	// 若是不同Server，那就直接告诉说可以重试呗
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if {@link SocketException} or {@link SocketTimeoutException} is a cause in the Throwable.
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return Utils.isPresentAsCause(e, getCircuitRelatedExceptions());        
    }

    @Override
    public int getMaxRetriesOnSameServer() {
        return retrySameServer;
    }

    @Override
    public int getMaxRetriesOnNextServer() {
        return retryNextServer;
    }
    
    protected List<Class<? extends Throwable>> getRetriableExceptions() {
        return retriable;
    }
    
    protected List<Class<? extends Throwable>>  getCircuitRelatedExceptions() {
        return circuitRelated;
    }
}
