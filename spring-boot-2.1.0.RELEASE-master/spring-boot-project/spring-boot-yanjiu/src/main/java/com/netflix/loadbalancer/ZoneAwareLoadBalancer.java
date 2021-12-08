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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;

/**
 * 它是最强王者：具有zone区域意识的负载均衡器。它是Spring Cloud默认的负载均衡器，是对DynamicServerListLoadBalancer的扩展。
     ZoneAwareLoadBalancer的出现主要是为了弥补DynamicServerListLoadBalancer的不足：
     DynamicServerListLoadBalancer木有重写chooseServer()方法，
         所以它的负载均衡算法依旧是BaseLoadBalancer中默认的线性轮询（所有Server没区分概念，一视同仁，所以有可能这次请求打到区域A，下次去了区域B了~）
         这样如果出现跨区域调用时，就会产生高延迟。比如你华北区域的服务A调用华南区域的服务B，就会延迟较大，很容易造成超时
 * 
 * Load balancer that can avoid a zone as a whole when choosing server. 
 *<p>
 * The key metric used to measure the zone condition is Average Active Requests,
which is aggregated per rest client per zone. It is the
total outstanding requests in a zone divided by number of available targeted instances (excluding circuit breaker tripped instances).
This metric is very effective when timeout occurs slowly on a bad zone.
<p>
The  LoadBalancer will calculate and examine zone stats of all available zones.
 If the Average Active Requests for any zone has reached a configured threshold, 
 this zone will be dropped from the active server list. 
 In case more than one zone has reached the threshold, the zone with the most active requests per server will be dropped.
Once the the worst zone is dropped, a zone will be chosen among the rest with the probability proportional to its number of instances.
A server will be returned from the chosen zone with a given Rule (A Rule is a load balancing strategy, for example {@link AvailabilityFilteringRule})
For each request, the steps above will be repeated. That is to say, each zone related load balancing decisions are made at real time with the up-to-date statistics aiding the choice.

 * @author awang
 *
 * @param <T>
 */
public class ZoneAwareLoadBalancer<T extends Server> extends DynamicServerListLoadBalancer<T> {
	//缓存zone对应的负载均衡器。每个zone都可以有自己的负载均衡器，从而可以有自己的IRule负载均衡策略~ 
	//这个很重要：它能保证zone之间的负载策略隔离，从而具有更好的负载均衡效果
    private ConcurrentHashMap<String, BaseLoadBalancer> balancers = new ConcurrentHashMap<String, BaseLoadBalancer>();
    
    private static final Logger logger = LoggerFactory.getLogger(ZoneAwareLoadBalancer.class);
            
    private volatile DynamicDoubleProperty triggeringLoad;
    //正两个参数讲解的次数太多遍了，请参考ZoneAvoidancePredicate。
    private volatile DynamicDoubleProperty triggeringBlackoutPercentage; 
    //是否启用区域意识的choose选择Server。默认是true，你可以通过配置
    //ZoneAwareNIWSDiscoveryLoadBalancer.enabled=false来禁用它，如果你只有一个zone区域的话 
    //注意这是配置，并不是IClientConfigKey哦~
    private static final DynamicBooleanProperty ENABLED = DynamicPropertyFactory.getInstance().getBooleanProperty("ZoneAwareNIWSDiscoveryLoadBalancer.enabled", true);
            
    void setUpServerList(List<Server> upServerList) {
        this.upServerList = upServerList;
    }
    
    public ZoneAwareLoadBalancer() {
        super();
    }

    @Deprecated
    public ZoneAwareLoadBalancer(IClientConfig clientConfig, IRule rule,
            IPing ping, ServerList<T> serverList, ServerListFilter<T> filter) {
        super(clientConfig, rule, ping, serverList, filter);
    }

    public ZoneAwareLoadBalancer(IClientConfig clientConfig, IRule rule,
                                 IPing ping, ServerList<T> serverList, ServerListFilter<T> filter,
                                 ServerListUpdater serverListUpdater) {
        super(clientConfig, rule, ping, serverList, filter, serverListUpdater);
    }

    public ZoneAwareLoadBalancer(IClientConfig niwsClientConfig) {
        super(niwsClientConfig);
    }
//    调用getLoadBalancer方法来创建负载均衡器为每个zone创建一个LB实例（这是本子类最大增强），并且拥有自己独立的IRule负载均衡策略
//    如果对应的Zone下已经没有实例了，则将Zone区域的实例列表清空，防止zone节点选择时出现异常 
//    该操作的作用是为了后续选择节点时，防止过多的Zone区域统计信息干扰具体实例的选择算法
//    那么，它是如何给每个zone创建一个LB实例的？？？
    @Override
    protected void setServerListForZones(Map<String, List<Server>> zoneServersMap) {
    	// 完成父类的赋值~~~~~
        super.setServerListForZones(zoneServersMap);
        if (balancers == null) {
            balancers = new ConcurrentHashMap<String, BaseLoadBalancer>();
        }
        // getLoadBalancer(zone)的意思是获得指定zone所属的负载均衡器
        // 这个意思是给每个LB设置它所管理的服务列表
        for (Map.Entry<String, List<Server>> entry: zoneServersMap.entrySet()) {
        	String zone = entry.getKey().toLowerCase();
            getLoadBalancer(zone).setServersList(entry.getValue());
        }
        // check if there is any zone that no longer has a server
        // and set the list to empty so that the zone related metrics does not
        // contain stale data
		// 这一步属一个小优化：若指定zone不存在了（木有机器了），就把balancers对应zone的机器置空
        for (Map.Entry<String, BaseLoadBalancer> existingLBEntry: balancers.entrySet()) {
            if (!zoneServersMap.keySet().contains(existingLBEntry.getKey())) {
                existingLBEntry.getValue().setServersList(Collections.emptyList());
            }
        }
    }    
    //在已经掌握了ZoneAvoidanceRule#getAvailableZones、randomChooseZone以及ZoneAvoidancePredicate的工作原理后，对这部分代码的解读就非常的简单了：
    //若开启了区域意识，且zone的个数 > 1，就继续区域选择逻辑
    //根据ZoneAvoidanceRule.getAvailableZones()方法拿到可用区们（会T除掉完全不可用的区域们，以及可用但是负载最高的一个区域）
    //从可用区zone们中，通过ZoneAvoidanceRule.randomChooseZone随机选一个zone出来 
    //该随机遵从权重规则：谁的zone里面Server数量最多，被选中的概率越大
    //在选中的zone里面的所有Server中，采用该zone对对应的Rule，进行choose    
    @Override
    public Server chooseServer(Object key) {
    	// 如果禁用了区域意识。或者只有一个zone，那就遵照父类逻辑
        if (!ENABLED.get() || getLoadBalancerStats().getAvailableZones().size() <= 1) {
            logger.debug("Zone aware logic disabled or there is only one zone");
            return super.chooseServer(key);
        }
        Server server = null;
        try {
            LoadBalancerStats lbStats = getLoadBalancerStats();
            // 核心方法：根据triggeringLoad等阈值计算出可用区~~~~
            Map<String, ZoneSnapshot> zoneSnapshot = ZoneAvoidanceRule.createSnapshot(lbStats);
            logger.debug("Zone snapshots: {}", zoneSnapshot);
            if (triggeringLoad == null) {
                triggeringLoad = DynamicPropertyFactory.getInstance().getDoubleProperty(
                        "ZoneAwareNIWSDiscoveryLoadBalancer." + this.getName() + ".triggeringLoadPerServerThreshold", 0.2d);
            }

            if (triggeringBlackoutPercentage == null) {
                triggeringBlackoutPercentage = DynamicPropertyFactory.getInstance().getDoubleProperty(
                        "ZoneAwareNIWSDiscoveryLoadBalancer." + this.getName() + ".avoidZoneWithBlackoutPercetage", 0.99999d);
            }
            Set<String> availableZones = ZoneAvoidanceRule.getAvailableZones(zoneSnapshot, triggeringLoad.get(), triggeringBlackoutPercentage.get());
            logger.debug("Available zones: {}", availableZones);
            if (availableZones != null &&  availableZones.size() < zoneSnapshot.keySet().size()) {
            	// 从可用区里随机选择一个区域（zone里面机器越多，被选中概率越大）
                String zone = ZoneAvoidanceRule.randomChooseZone(zoneSnapshot, availableZones);
                logger.debug("Zone chosen: {}", zone);
                if (zone != null) {
                    BaseLoadBalancer zoneLoadBalancer = getLoadBalancer(zone);
                    // 按照IRule从该zone内选择一台Server出来
                    server = zoneLoadBalancer.chooseServer(key);
                }
            }
        } catch (Exception e) {
            logger.error("Error choosing server using zone aware logic for load balancer={}", name, e);
        }
        if (server != null) {
            return server;
        } else {
            logger.debug("Zone avoidance logic is not invoked.");
            return super.chooseServer(key);
        }
    }
    //这是一个default访问权限的方法：每个zone对应的LB实例是BaseLoadBalancer类型，
    //使用的IRule是克隆当前的单独实例（因为规则要完全隔离开来，所以必须用单独实例~），
    //这么一来每个zone内部的负载均衡算法就可以达到隔离，负载均衡效果更佳。
    @VisibleForTesting
    BaseLoadBalancer getLoadBalancer(String zone) {
        zone = zone.toLowerCase();
        BaseLoadBalancer loadBalancer = balancers.get(zone);
        if (loadBalancer == null) {
        	// We need to create rule object for load balancer for each zone
        	IRule rule = cloneRule(this.getRule());
            loadBalancer = new BaseLoadBalancer(this.getName() + "_" + zone, rule, this.getLoadBalancerStats());
            BaseLoadBalancer prev = balancers.putIfAbsent(zone, loadBalancer);
            if (prev != null) {
            	loadBalancer = prev;
            }
        } 
        return loadBalancer;        
    }

    private IRule cloneRule(IRule toClone) {
    	IRule rule;
    	if (toClone == null) {
    		rule = new AvailabilityFilteringRule();
    	} else {
    		String ruleClass = toClone.getClass().getName();        		
    		try {
				rule = (IRule) ClientFactory.instantiateInstanceWithClientConfig(ruleClass, this.getClientConfig());
			} catch (Exception e) {
				throw new RuntimeException("Unexpected exception creating rule for ZoneAwareLoadBalancer", e);
			}
    	}
    	return rule;
    }
    
       
    @Override
    public void setRule(IRule rule) {
        super.setRule(rule);
        if (balancers != null) {
            for (String zone: balancers.keySet()) {
                balancers.get(zone).setRule(cloneRule(rule));
            }
        }
    }
}
