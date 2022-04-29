package com.netflix.client;

import java.net.SocketException;
import java.util.List;


import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;

/**
 * Implementation of RetryHandler created for each request which allows for request
 * specific override
 * @author elandau
 *
 */
public class RequestSpecificRetryHandler implements RetryHandler {
	// fallback默认使用的是RetryHandler.DEFAULT
		// 有点代理的意思
    private final RetryHandler fallback;
    private int retrySameServer = -1;
    private int retryNextServer = -1;
    // 只有是连接异常，也就是SocketException或者其子类异常才执行重试
    private final boolean okToRetryOnConnectErrors;
    // 若是true：只要异常了，任何错都执行重试
    private final boolean okToRetryOnAllErrors;
    
    protected List<Class<? extends Throwable>> connectionRelated = 
            Lists.<Class<? extends Throwable>>newArrayList(SocketException.class);

    public RequestSpecificRetryHandler(boolean okToRetryOnConnectErrors, boolean okToRetryOnAllErrors) {
        this(okToRetryOnConnectErrors, okToRetryOnAllErrors, RetryHandler.DEFAULT, null);    
    }
	// 构造器为属性赋值。requestConfig可以是单独的，若没指定就使用默认全局的
    public RequestSpecificRetryHandler(boolean okToRetryOnConnectErrors, boolean okToRetryOnAllErrors, RetryHandler baseRetryHandler,  IClientConfig requestConfig) {
        Preconditions.checkNotNull(baseRetryHandler);
        this.okToRetryOnConnectErrors = okToRetryOnConnectErrors;
        this.okToRetryOnAllErrors = okToRetryOnAllErrors;
        this.fallback = baseRetryHandler;
        if (requestConfig != null) {
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetries)) {
                retrySameServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetries); 
            }
            if (requestConfig.containsProperty(CommonClientConfigKey.MaxAutoRetriesNextServer)) {
                retryNextServer = requestConfig.get(CommonClientConfigKey.MaxAutoRetriesNextServer); 
            } 
        }
    }
    
    public boolean isConnectionException(Throwable e) {
        return Utils.isPresentAsCause(e, connectionRelated);
    }
 // 若强制开启所有错误都重试，那就没啥好说的
	// 此参数默认是false，只能通过构造器来指定其值
    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        if (okToRetryOnAllErrors) {
            return true;
        } 
		// ClientException属于执行过程中会抛出的异常类型，所以需要加以判断
        else if (e instanceof ClientException) {
            ClientException ce = (ClientException) e;
            // 若是服务端异常，那就同一台Server上不用重试了，没要求是同一台Server才允许其重试
            if (ce.getErrorType() == ClientException.ErrorType.SERVER_THROTTLED) {
                return !sameServer;
             // 若不是服务端异常类型，那就换台Server都不用重试了
            } else {
                return false;
            }
        } 
        else  {
            return okToRetryOnConnectErrors && isConnectionException(e);
        }
    }

    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return fallback.isCircuitTrippingException(e);
    }

    @Override
    public int getMaxRetriesOnSameServer() {
        if (retrySameServer >= 0) {
            return retrySameServer;
        }
        return fallback.getMaxRetriesOnSameServer();
    }

    @Override
    public int getMaxRetriesOnNextServer() {
        if (retryNextServer >= 0) {
            return retryNextServer;
        }
        return fallback.getMaxRetriesOnNextServer();
    }    
}
