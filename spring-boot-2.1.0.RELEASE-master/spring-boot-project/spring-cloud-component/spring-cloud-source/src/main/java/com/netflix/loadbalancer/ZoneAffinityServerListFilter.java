package com.netflix.loadbalancer;

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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DeploymentContext.ContextKey;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.loadbalancer.AbstractServerListFilter;
import com.netflix.loadbalancer.LoadBalancerStats;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ZoneAffinityPredicate;
import com.netflix.loadbalancer.ZoneSnapshot;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;

/**
 * This server list filter deals with filtering out servers based on the Zone
 * affinity. This filtering will be turned on if either
 * {@link CommonClientConfigKey#EnableZoneAffinity} or
 * {@link CommonClientConfigKey#EnableZoneExclusivity} is set to true in
 * {@link IClientConfig} object passed into this class during initialization.
 * When turned on, servers outside the same zone (as indicated by
 * {@link Server#getZone()}) will be filtered out. By default, zone affinity and
 * exclusivity are turned off and nothing is filtered out.
 * 
 * @author stonse
 *
 */
public class ZoneAffinityServerListFilter<T extends Server> extends AbstractServerListFilter<T>
		implements IClientConfigAware {
	//	控制是否要开启ZoneAffinity的开关，默认是false 
	//	可以通过EnableZoneAffinity来配置。也就是xxx.ribbon.EnableZoneAffinity或者全局默认ribbon.EnableZoneAffinity
	private volatile boolean zoneAffinity = DefaultClientConfigImpl.DEFAULT_ENABLE_ZONE_AFFINITY;
	//	同样是可以控制是否要开启ZoneAffinity的开关。同时它在Filter过滤Server的时候还起到开关的通，默认是false 
	// 可以通过EnableZoneExclusivity这个key进行配置（全局or定制）
	private volatile boolean zoneExclusive = DefaultClientConfigImpl.DEFAULT_ENABLE_ZONE_EXCLUSIVITY;
//	最大负载阈值，默认值是0.6d 
//	可通过<clientName>.ribbon.zoneAffinity.maxLoadPerServer = xxx来配置
//	对比ZoneAvoidancePredicate里面的ZoneAwareNIWSDiscoveryLoadBalancer.triggeringLoadPerServerThreshold属性你会发现默认值是及其不合理的，后面会用例子解释
	private DynamicDoubleProperty activeReqeustsPerServerThreshold;
//	blackOutServerPercentageThreshold：默认值0.8d 
	private DynamicDoubleProperty blackOutServerPercentageThreshold;
	private DynamicIntProperty availableServersThreshold;
	private Counter overrideCounter;
	private ZoneAffinityPredicate zoneAffinityPredicate = new ZoneAffinityPredicate();

	private static Logger logger = LoggerFactory.getLogger(ZoneAffinityServerListFilter.class);

	String zone;

	// 构造器通过initWithNiwsConfig为成员属性赋值~~~~
	public ZoneAffinityServerListFilter() {
	}

	public ZoneAffinityServerListFilter(IClientConfig niwsClientConfig) {
		initWithNiwsConfig(niwsClientConfig);
	}

	@Override
	public void initWithNiwsConfig(IClientConfig niwsClientConfig) {
		String sZoneAffinity = "" + niwsClientConfig.getProperty(CommonClientConfigKey.EnableZoneAffinity, false);
		if (sZoneAffinity != null) {
			zoneAffinity = Boolean.parseBoolean(sZoneAffinity);
			logger.debug("ZoneAffinity is set to {}", zoneAffinity);
		}
		String sZoneExclusive = "" + niwsClientConfig.getProperty(CommonClientConfigKey.EnableZoneExclusivity, false);
		if (sZoneExclusive != null) {
			zoneExclusive = Boolean.parseBoolean(sZoneExclusive);
		}
		if (ConfigurationManager.getDeploymentContext() != null) {
			zone = ConfigurationManager.getDeploymentContext().getValue(ContextKey.zone);
		}
		activeReqeustsPerServerThreshold = DynamicPropertyFactory.getInstance()
				.getDoubleProperty(niwsClientConfig.getClientName() + "." + niwsClientConfig.getNameSpace()
						+ ".zoneAffinity.maxLoadPerServer", 0.6d);
		logger.debug("activeReqeustsPerServerThreshold: {}", activeReqeustsPerServerThreshold.get());
		blackOutServerPercentageThreshold = DynamicPropertyFactory.getInstance()
				.getDoubleProperty(niwsClientConfig.getClientName() + "." + niwsClientConfig.getNameSpace()
						+ ".zoneAffinity.maxBlackOutServesrPercentage", 0.8d);
		logger.debug("blackOutServerPercentageThreshold: {}", blackOutServerPercentageThreshold.get());
		availableServersThreshold = DynamicPropertyFactory.getInstance().getIntProperty(niwsClientConfig.getClientName()
				+ "." + niwsClientConfig.getNameSpace() + ".zoneAffinity.minAvailableServers", 2);
		logger.debug("availableServersThreshold: {}", availableServersThreshold.get());
		overrideCounter = Monitors.newCounter("ZoneAffinity_OverrideCounter");

		Monitors.registerObject("NIWSServerListFilter_" + niwsClientConfig.getClientName());
	}
	// 是否开启根据zone进行过滤
		// 说明：filtered是已经经过zone过滤后，肯定是同一个zone里面的server们了
	private boolean shouldEnableZoneAffinity(List<T> filtered) {
		// 如果zoneAffinity=false 并且 zoneExclusive = false才表示不开启zone过滤
		// 默认两个都是false哦
		if (!zoneAffinity && !zoneExclusive) {
			return false;
		}
		  // 若显示开启zone排除，那就直接返回true
        // 否则会计算，根据负载情况动态判断
		if (zoneExclusive) {
			return true;
		}
		LoadBalancerStats stats = getLoadBalancerStats();
		if (stats == null) {
			return zoneAffinity;
		} else {
			logger.debug("Determining if zone affinity should be enabled with given server list: {}", filtered);
			// 拿到zone的快照，从而拿到zone的实例总数、负载、熔断总数等
			ZoneSnapshot snapshot = stats.getZoneSnapshot(filtered);
			double loadPerServer = snapshot.getLoadPerServer();
			int instanceCount = snapshot.getInstanceCount();
			int circuitBreakerTrippedCount = snapshot.getCircuitTrippedCount();
			if (((double) circuitBreakerTrippedCount) / instanceCount >= blackOutServerPercentageThreshold.get()
					|| loadPerServer >= activeReqeustsPerServerThreshold.get()
					|| (instanceCount - circuitBreakerTrippedCount) < availableServersThreshold.get()) {
				logger.debug(
						"zoneAffinity is overriden. blackOutServerPercentage: {}, activeReqeustsPerServer: {}, availableServers: {}",
						new Object[] { (double) circuitBreakerTrippedCount / instanceCount, loadPerServer,
								instanceCount - circuitBreakerTrippedCount });
				return false;
			} else {
				return true;
			}

		}
	}
//	若你配置了zoneAffinity或者zoneExclusive任何一个为true，则将开启此筛选逻辑 
//	若你是zoneExclusive=true，说明你同意这种排除逻辑，那就直接生效开启返回true喽
//	否则，进入根据动态指标的计算逻辑
//	下面复杂的逻辑计算，有如下情况均会返回false(不执行过滤，而是返回全部Server)： 
//	circuitBreakerTrippedCount/instanceCount >= blackOutServerPercentageThreshold，也就是说呗熔断的占比率超过0.8，也就是80%的机器都被熔断了，那就返回false（毕竟此zone已基本不可用了，那还是返回所有Server保险点）
//	loadPerServer >= activeReqeustsPerServerThreshold，若平均负载超过0.6，那就返回fasle（因为没必要把负载过高的zone返回出去，还是返回所有Server较好）
//	(instanceCount - circuitBreakerTrippedCount) < availableServersThreshold，如果“活着的（没熔断的）”实例总数不足2个（仅有1个了），那就返回false
//	若以上三种情况均没发生，那就返回true
//	该方法返回值释义：
//
//	true：最终只留下本zone的Server们
//	false，返回所有Server，相当于忽略此Filter的操作
//	这么做的目的是：担心你配置的zone里面的Server情况不乐观，如果这个时候只返回该zone的Server的话，反倒不好，还不如把所有Server都返回更为合适。
	@Override
	public List<T> getFilteredListOfServers(List<T> servers) {
		if (zone != null && (zoneAffinity || zoneExclusive) && servers != null && servers.size() > 0) {
			List<T> filteredServers = Lists
					.newArrayList(Iterables.filter(servers, this.zoneAffinityPredicate.getServerOnlyPredicate()));
			if (shouldEnableZoneAffinity(filteredServers)) {
				return filteredServers;
			} else if (zoneAffinity) {
				overrideCounter.increment();
			}
		}
		return servers;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("ZoneAffinityServerListFilter:");
		sb.append(", zone: ").append(zone).append(", zoneAffinity:").append(zoneAffinity);
		sb.append(", zoneExclusivity:").append(zoneExclusive);
		return sb.toString();
	}
}
