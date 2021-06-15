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

/**该接口主要做了以下的一些事情：

维护了存储服务实例Server对象的二个列表：一个用于存储所有服务实例的清单，一个用于存储正常服务（up服务）的实例清单
初始化得到可用的服务列表，启动定时任务去实时的检测服务列表中的服务的可用性，并且间断性的去更新服务列表
选择可用的服务进行调用（交给IRule去实现，不同的轮询策略）
 * Interface that defines the operations for a software loadbalancer. A typical
 * loadbalancer minimally need a set of servers to loadbalance for, a method to
 * mark a particular server to be out of rotation and a call that will choose a
 * server from the existing list of server.
 * 
 * @author stonse
 * 
 */
public interface ILoadBalancer {

	/** 初始化Server列表。当然后期你可以可以再添加
	// 在某些情况下，你可能想给出更多的“权重”时 该方法有用
	 * Initial list of servers.
	 * This API also serves to add additional ones at a later time
	 * The same logical server (host:port) could essentially be added multiple times
	 * (helpful in cases where you want to give more "weightage" perhaps ..)
	 * 
	 * @param newServers new servers to add
	 */
	public void addServers(List<Server> newServers);
	
	/**根据key从load balancer里面找到一个Server
	// 大多时候太是委托给`IRule`去做
	 * Choose a server from load balancer.
	 * 
	 * @param key An object that the load balancer may use to determine which server to return. null if 
	 *         the load balancer does not use this parameter.
	 * @return server chosen
	 */
	public Server chooseServer(Object key);
	
	/** 由负载均衡器的客户端调用，以通知服务器停机否则
	// LB会认为它还活着，直到下一个Ping周期
	// 也就说该方法可以手动调用，让Server停机
	 * To be called by the clients of the load balancer to notify that a Server is down
	 * else, the LB will think its still Alive until the next Ping cycle - potentially
	 * (assuming that the LB Impl does a ping)
	 * 
	 * @param server Server to mark as down
	 */
	public void markServerDown(Server server);
	
	/**
	 * @deprecated 2016-01-20 This method is deprecated in favor of the
	 * cleaner {@link #getReachableServers} (equivalent to availableOnly=true)
	 * and {@link #getAllServers} API (equivalent to availableOnly=false).
	 *
	 * Get the current list of servers.
	 *
	 * @param availableOnly if true, only live and available servers should be returned
	 */
	@Deprecated
	public List<Server> getServerList(boolean availableOnly);

	/** 只有服务器是可访问的就返回
	 * @return Only the servers that are up and reachable.
     */
    public List<Server> getReachableServers();

    /**所有已知的服务器，包括可访问的和不可访问的。
     * @return All known servers, both reachable and unreachable.
     */
	public List<Server> getAllServers();
}
