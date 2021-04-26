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
package com.netflix.loadbalancer;

/** 正文
IRule一共提供7种规则算法：

RoundRobinRule轮询规则：线性轮询
WeightedResponseTimeRule加权轮询规则：Server的rt响应时间作为权重参考，响应时间越短，权重越高，从而越容易被选中
RandomRule随机规则：使用ThreadLocalRandom.current().nextInt(serverCount)随机选择一个Server
AvailabilityFilteringRule可用性过滤规则：过滤掉已熔断/负载过高的Server们，然后剩下的Server们使用线性轮询
ZoneAvoidanceRule区域可用性规则：评估zone可用区的性能，然后从多个可用区中按权重选择一个zone，在从zone里面线性轮询出一台Server
RetryRule重试规则：它可以包装一个IRule subRule（默认是RoundRobinRule），当一个周期内没找到Server时，进行重试
BestAvailableRule最小并发请求规则：选择一个最小并发数（也就是ServerStats.activeRequestsCount最小）的Server
 * Interface that defines a "Rule" for a LoadBalancer. A Rule can be thought of
 * as a Strategy for loadbalacing. Well known loadbalancing strategies include
 * Round Robin, Response Time based etc.
 * 
 * @author stonse
 * 
 */
public interface IRule{
    /*
     * choose one alive server from lb.allServers or
     * lb.upServers according to key
     * 
     * @return choosen Server object. NULL is returned if none
     *  server is available 
     */

    public Server choose(Object key);
    
    public void setLoadBalancer(ILoadBalancer lb);
    
    public ILoadBalancer getLoadBalancer();    
}
