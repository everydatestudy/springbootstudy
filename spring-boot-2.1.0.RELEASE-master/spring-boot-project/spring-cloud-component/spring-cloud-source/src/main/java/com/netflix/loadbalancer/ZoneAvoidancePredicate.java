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

import java.util.Map;
import java.util.Set;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.client.config.IClientConfig;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicDoubleProperty;
import com.netflix.config.DynamicPropertyFactory;

/**负责过滤掉这样的Server们：当某个zone非常糟糕，糟糕到统计达到了threshold阈值，
 * 那么就会过滤掉这个zone里面所有的Server们，所以该断言器是具有很强的zone区域意识的。
     说明：区域意识在跨机房、跨区域部署的时候非常好用，内置的负载均衡支持能够大大减少你自己的开发量。
 * A server predicate that filters out all servers in a worst zone if the aggregated metric for that zone reaches a threshold.
 * The logic to determine the worst zone is described in class {@link ZoneAwareLoadBalancer}.  
 * 
 * @author awang
 *
 */
public class ZoneAvoidancePredicate extends  AbstractServerPredicate {

    private volatile DynamicDoubleProperty triggeringLoad = new DynamicDoubleProperty("ZoneAwareNIWSDiscoveryLoadBalancer.triggeringLoadPerServerThreshold", 0.2d);

    private volatile DynamicDoubleProperty triggeringBlackoutPercentage = new DynamicDoubleProperty("ZoneAwareNIWSDiscoveryLoadBalancer.avoidZoneWithBlackoutPercetage", 0.99999d);
    
    private static final Logger logger = LoggerFactory.getLogger(ZoneAvoidancePredicate.class);
    
    private static final DynamicBooleanProperty ENABLED = DynamicPropertyFactory
            .getInstance().getBooleanProperty(
                    "niws.loadbalancer.zoneAvoidanceRule.enabled", true);


    public ZoneAvoidancePredicate(IRule rule, IClientConfig clientConfig) {
        super(rule, clientConfig);
        initDynamicProperties(clientConfig);
    }

    public ZoneAvoidancePredicate(LoadBalancerStats lbStats,
            IClientConfig clientConfig) {
        super(lbStats, clientConfig);
        initDynamicProperties(clientConfig);
    }

    ZoneAvoidancePredicate(IRule rule) {
        super(rule);
    }
    
    private void initDynamicProperties(IClientConfig clientConfig) {
        if (clientConfig != null) {
            triggeringLoad = DynamicPropertyFactory.getInstance().getDoubleProperty(
                    "ZoneAwareNIWSDiscoveryLoadBalancer." + clientConfig.getClientName() + ".triggeringLoadPerServerThreshold", 0.2d);

            triggeringBlackoutPercentage = DynamicPropertyFactory.getInstance().getDoubleProperty(
                    "ZoneAwareNIWSDiscoveryLoadBalancer." + clientConfig.getClientName() + ".avoidZoneWithBlackoutPercetage", 0.99999d);
        }
        
    }

    @Override
    public boolean apply(PredicateKey input) {
    	// 若开关关闭了，也就是禁用了这个策略。那就永远true呗
        if (!ENABLED.get()) {
            return true;
        }
        // 拿到该Server所在的zone，进而完成判断
        String serverZone = input.getServer().getZone();
        if (serverZone == null) {
            // there is no zone information from the server, we do not want to filter
            // out this server
            return true;
        }
        LoadBalancerStats lbStats = getLBStats();
        if (lbStats == null) {
            // no stats available, do not filter
            return true;
        }
        // 若可用区只剩一个了，那也不要过滤了（有总比没有强）
        if (lbStats.getAvailableZones().size() <= 1) {
            // only one zone is available, do not filter
            return true;
        }
        Map<String, ZoneSnapshot> zoneSnapshot = ZoneAvoidanceRule.createSnapshot(lbStats);
		// 拿到全部可用的zone后，判断该Server坐在的Zone是否属于可用区内
        if (!zoneSnapshot.keySet().contains(serverZone)) {
            // The server zone is unknown to the load balancer, do not filter it out 
            return true;
        }
        logger.debug("Zone snapshots: {}", zoneSnapshot);
        Set<String> availableZones = ZoneAvoidanceRule.getAvailableZones(zoneSnapshot, triggeringLoad.get(), triggeringBlackoutPercentage.get());
        logger.debug("Available zones: {}", availableZones);
        if (availableZones != null) {
            return availableZones.contains(input.getServer().getZone());
        } else {
            return false;
        }
    }    
}
