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
package com.netflix.loadbalancer;

import java.util.List;

/**BestAvailableRule继承自ClientConfigEnabledRoundRobinRule。
 * 该策略的特性跳过已经被熔断的实例，并且顺表找出最空闲的实例。
 * BestAvailable：最空闲、最可用的（也就是并发请求数最低的）。很明显，统计数据来自云LoadBalancerStats/ServerStats。
 * A rule that skips servers with "tripped" circuit breaker and picks the
 * server with lowest concurrent requests.
 * <p>
 * This rule should typically work with {@link ServerListSubsetFilter} which puts a limit on the 
 * servers that is visible to the rule. This ensure that it only needs to find the minimal 
 * concurrent requests among a small number of servers. Also, each client will get a random list of 
 * servers which avoids the problem that one server with the lowest concurrent requests is 
 * chosen by a large number of clients and immediately gets overwhelmed.
 * 如果loadBalancerStats为null，则BestAvailableRule将回退到采用它的父类即ClientConfigEnabledRoundRobinRule的服务选取策略，即线性轮询。

 说明：没有loadBalancerStats不代表没有ILoadBalancer。ILoadBalancer是必须的，因为Server列表均来自于它~
 * @author awang
 *
 */
public class BestAvailableRule extends ClientConfigEnabledRoundRobinRule {
	// 也就是说：LoadBalancerStats是来自于lb的
    private LoadBalancerStats loadBalancerStats;
    
    @Override
    public Server choose(Object key) {
    	// 若没有统计信息，那就回退到轮询策略呗~~~~
        if (loadBalancerStats == null) {
            return super.choose(key);
        }
        List<Server> serverList = getLoadBalancer().getAllServers();
        // 记录所有Server中最小并发数
        int minimalConcurrentConnections = Integer.MAX_VALUE;
        long currentTime = System.currentTimeMillis();
        Server chosen = null;
        for (Server server: serverList) {
            ServerStats serverStats = loadBalancerStats.getSingleServerStat(server);
            // 只要该Server没有被熔断，就选择上
            if (!serverStats.isCircuitBreakerTripped(currentTime)) {
                int concurrentConnections = serverStats.getActiveRequestsCount(currentTime);
                if (concurrentConnections < minimalConcurrentConnections) {
                    minimalConcurrentConnections = concurrentConnections;
                    chosen = server;
                }
            }
        }
        if (chosen == null) {
            return super.choose(key);
        } else {
            return chosen;
        }
    }

    @Override
    public void setLoadBalancer(ILoadBalancer lb) {
        super.setLoadBalancer(lb);
        if (lb instanceof AbstractLoadBalancer) {
            loadBalancerStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();            
        }
    }
    
    

}
