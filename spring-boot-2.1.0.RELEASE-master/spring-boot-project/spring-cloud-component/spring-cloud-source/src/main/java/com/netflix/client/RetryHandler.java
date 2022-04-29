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

import java.net.ConnectException;

/**关于Ribbon的重试处理器RetryHandler就介绍到这了，需要注意的是Ribbon把重试机制放在了ribbon-core包下，而非ribbon-loadbalancer下，是因为重试机制并不是负载均衡的内容，而是execute执行时的概念。
 * A handler that determines if an exception is retriable for load balancer,
 * and if an exception or error response should be treated as circuit related failures
 * so that the load balancer can avoid such server.
 *  
 * @author awang
 */
public interface RetryHandler {

    public static final RetryHandler DEFAULT = new DefaultLoadBalancerRetryHandler();
    
    /**该异常是否可处理（可重试）
	// sameServer：true表示在同一台机器上重试。否则去其它机器重试
     * Test if an exception is retriable for the load balancer
     * 
     * @param e the original exception
     * @param sameServer if true, the method is trying to determine if retry can be 
     *        done on the same server. Otherwise, it is testing whether retry can be
     *        done on a different server
     */
    public boolean isRetriableException(Throwable e, boolean sameServer);

    /**是否是Circuit熔断类型异常。比如java.net.ConnectException就属于这种故障
	// 这种异常类型一般属于比较严重的，发生的次数多了就会把它熔断（下次不会再找它了）
     * Test if an exception should be treated as circuit failure. For example, 
     * a {@link ConnectException} is a circuit failure. This is used to determine
     * whether successive exceptions of such should trip the circuit breaker to a particular
     * host by the load balancer. If false but a server response is absent, 
     * load balancer will also close the circuit upon getting such exception.
     */
    public boolean isCircuitTrippingException(Throwable e);
        
    /** 要在一台服务器上执行的最大重试次数
     * @return Number of maximal retries to be done on one server
     */
    public int getMaxRetriesOnSameServer();

    /** 要重试的最大不同服务器数。2表示最多去2台不同的服务器身上重试
     * @return Number of maximal different servers to retry
     */
    public int getMaxRetriesOnNextServer();
}
