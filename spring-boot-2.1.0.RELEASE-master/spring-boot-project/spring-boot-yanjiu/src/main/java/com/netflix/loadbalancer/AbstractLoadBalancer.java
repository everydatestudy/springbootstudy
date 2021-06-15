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

import java.util.List;

/**
 * 抽象实现：对Server们使用ServerGroup进行了分组，
 * 并且新增了2个抽象方法，使得LB实现必须和LoadBalancerStats绑定了。
 * 其它所有实现均为它的子类，也就是说所有的LB实现均是有LoadBalancerStats的喽~
    有了LoadBalancerStats就有知晓运行状态的能力，进而可以动态的感知到Server、Zone区域的负载等状况，最终做到更高效的负载均衡~
 * AbstractLoadBalancer contains features required for most loadbalancing
 * implementations.
 * 
 * An anatomy of a typical LoadBalancer consists of 1. A List of Servers (nodes)
 * that are potentially bucketed based on a specific criteria. 2. A Class that
 * defines and implements a LoadBalacing Strategy via <code>IRule</code> 3. A
 * Class that defines and implements a mechanism to determine the
 * suitability/availability of the nodes/servers in the List.
 * 
 * 
 * @author stonse
 * 
 */
public abstract class AbstractLoadBalancer implements ILoadBalancer {
    
    public enum ServerGroup{
        ALL,
        STATUS_UP,
        STATUS_NOT_UP        
    }
        
    /** 选择具体的服务实例，key为null，忽略key的条件判断
     * delegate to {@link #chooseServer(Object)} with parameter null.
     */
    public Server chooseServer() {
    	return chooseServer(null);
    }

    
    /** 定义了根据分组类型来获取不同的服务实例的列表。
     * List of servers that this Loadbalancer knows about
     * 
     * @param serverGroup Servers grouped by status, e.g., {@link ServerGroup#STATUS_UP}
     */
    public abstract List<Server> getServerList(ServerGroup serverGroup);
    
    /**	 获得所属的LoadBalancerStats，它是LB的状态仓储，对负载均衡规则有很大作用
     * Obtain LoadBalancer related Statistics
     */
    public abstract LoadBalancerStats getLoadBalancerStats();    
}
