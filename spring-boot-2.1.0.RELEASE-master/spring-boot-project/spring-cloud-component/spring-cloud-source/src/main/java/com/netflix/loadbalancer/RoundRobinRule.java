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

import com.netflix.client.config.IClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The most well known and basic load balancing strategy, i.e. Round Robin Rule.
 *
 * @author stonse
 * @author Nikos Michalakis <nikos@netflix.com>
 *
 */
public class RoundRobinRule extends AbstractLoadBalancerRule {

	private AtomicInteger nextServerCyclicCounter;
	private static final boolean AVAILABLE_ONLY_SERVERS = true;
	private static final boolean ALL_SERVERS = false;

	private static Logger log = LoggerFactory.getLogger(RoundRobinRule.class);

	public RoundRobinRule() {
		nextServerCyclicCounter = new AtomicInteger(0);
	}

	public RoundRobinRule(ILoadBalancer lb) {
		this();
		setLoadBalancer(lb);
	}
//	轮询过程用一句话总结：把所有的Server使用线性轮询算法选出一台Server供以使用。有如下几点有必要提出：
//
//	轮询的是allServers，而非reachableServers
//	轮询最多只会尝试10次，如果10次还没找到可以提供服务的Server，也不继续往下走
//	其中你应该有个疑问：这里是拿出来再判断，那为毛不直接轮询reachableServers呢？这个在后文会给出答案
	// 注意：它仅是个public方法，并非接口方法
	// 另外，它轮询的是lb里的allServers，而并非只是up的哦~~~~
	public Server choose(ILoadBalancer lb, Object key) {
		if (lb == null) {
			log.warn("no load balancer");
			return null;
		}
		// 一次choose中最多轮询10次，如果还没有找到可用的，那就放弃 避免你有100台机器，一直轮询下去
		Server server = null;
		int count = 0;
		while (server == null && count++ < 10) {
			// 说明：up的机器仅仅只是做一个数量的判断，并不参与整整的轮询
						// 真正轮询的是allServers，这点特别注意
			List<Server> reachableServers = lb.getReachableServers();
			List<Server> allServers = lb.getAllServers();
			int upCount = reachableServers.size();
			int serverCount = allServers.size();

			if ((upCount == 0) || (serverCount == 0)) {
				log.warn("No up servers available from load balancer: " + lb);
				return null;
			}
			// 采用线性轮询算法，取一台机器出来
			int nextServerIndex = incrementAndGetModulo(serverCount);
			server = allServers.get(nextServerIndex);

			if (server == null) {
				/* Transient. */
				Thread.yield();
				continue;
			}
			// 若选出的这个server是活的并且可以服务了，那就return
						// 否则，继续下一轮的循环获取
						// 所以，，，为毛不直接轮询upServers呢？？？这个后文会给出解释
			if (server.isAlive() && (server.isReadyToServe())) {
				return (server);
			}

			// Next.
			server = null;
		}

		if (count >= 10) {
			log.warn("No available alive servers after 10 tries from load balancer: " + lb);
		}
		return server;
	}

	/**轮询调度算法的原理是每一次把来自用户的请求轮流分配给内部中的服务器，
	 * 从1开始，直到N(内部服务器个数)，然后重新开始循环。算法的优点是其简洁性，它无需记录当前所有连接的状态，所以它是一种无状态调度。
	 * Inspired by the implementation of {@link AtomicInteger#incrementAndGet()}.
	 *
	 * @param modulo The modulo to bound the value of the counter.
	 * @return The next value.
	 */
	private int incrementAndGetModulo(int modulo) {
		for (;;) {
			int current = nextServerCyclicCounter.get();
			int next = (current + 1) % modulo;
			if (nextServerCyclicCounter.compareAndSet(current, next))
				return next;
		}
	}

	@Override
	public Server choose(Object key) {
		return choose(getLoadBalancer(), key);
	}

	@Override
	public void initWithNiwsConfig(IClientConfig clientConfig) {
	}
}
