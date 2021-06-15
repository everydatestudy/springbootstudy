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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.netflix.client.config.IClientConfig;

/**
 * A rule that uses the a {@link CompositePredicate} to filter servers based on zone and availability. The primary predicate is composed of
 * a {@link ZoneAvoidancePredicate} and {@link AvailabilityPredicate}, with the fallbacks to {@link AvailabilityPredicate}
 * and an "always true" predicate returned from {@link AbstractServerPredicate#alwaysTrue()} 
 * 
 * @author awang
 *
 */
public class ZoneAvoidanceRule extends PredicateBasedRule {

    private static final Random random = new Random();
    
    private CompositePredicate compositePredicate;
    
    public ZoneAvoidanceRule() {
        super();
        //默认值均为0.2d 
        ZoneAvoidancePredicate zonePredicate = new ZoneAvoidancePredicate(this);
        AvailabilityPredicate availabilityPredicate = new AvailabilityPredicate(this);
        compositePredicate = createCompositePredicate(zonePredicate, availabilityPredicate);
    }
    
    private CompositePredicate createCompositePredicate(ZoneAvoidancePredicate p1, AvailabilityPredicate p2) {
        return CompositePredicate.withPredicates(p1, p2)
                             .addFallbackPredicate(p2)
                             .addFallbackPredicate(AbstractServerPredicate.alwaysTrue())
                             .build();
        
    }
    
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        ZoneAvoidancePredicate zonePredicate = new ZoneAvoidancePredicate(this, clientConfig);
        AvailabilityPredicate availabilityPredicate = new AvailabilityPredicate(this, clientConfig);
        compositePredicate = createCompositePredicate(zonePredicate, availabilityPredicate);
    }

    static Map<String, ZoneSnapshot> createSnapshot(LoadBalancerStats lbStats) {
        Map<String, ZoneSnapshot> map = new HashMap<String, ZoneSnapshot>();
        for (String zone : lbStats.getAvailableZones()) {
            ZoneSnapshot snapshot = lbStats.getZoneSnapshot(zone);
            map.put(zone, snapshot);
        }
        return map;
    }
    //这个随机算法最核心的就是最后面的index和sum算法，看完后你应该有如下疑问：为何不来个从chooseFrom这个集合里随机弹出一个zone就成，而非弄的这么麻烦呢？
    //其实这么做的是很有意义的，这么做能保证：zone里面机器数越多的话，被选中的概率是越大的，这样随机才是最合理的。
    static String randomChooseZone(Map<String, ZoneSnapshot> snapshot,
            Set<String> chooseFrom) {
        if (chooseFrom == null || chooseFrom.size() == 0) {
            return null;
        }
        // 注意：默认选择的是第一个zone区域
     	// 若总共就1个区域，那就是它了。若有多个，那就需要随机去选
        String selectedZone = chooseFrom.iterator().next();
        if (chooseFrom.size() == 1) {
            return selectedZone;
        }
       // 所有的区域中总的Server实例数
        int totalServerCount = 0;
        for (String zone : chooseFrom) {
            totalServerCount += snapshot.get(zone).getInstanceCount();
        }
        // 从所有的实例总数中随机选个数字。比如总数是10台机器
        // 那就是从[1-10]之间随机选个数字，比如选中为6
        int index = random.nextInt(totalServerCount) + 1;
    	// sum代表当前实例统计的总数
		// 它的逻辑是：当sum超过这个index时，就以这个区域为准
        int sum = 0;
        for (String zone : chooseFrom) {
            sum += snapshot.get(zone).getInstanceCount();
            if (index <= sum) {
                selectedZone = zone;
                break;
            }
        }
        return selectedZone;
    }
    //该方法是一个静态工具方法，顾名思义它用于获取真实的可用区，
    //它在LoadBalancerStats#getAvailableZones方法的基础上，
    //结合每个zone对应的ZoneSnapshot的情况再结合阈值设置，筛选真正可用的zone区域。
//    这个选择可用区的步骤还是比较重要的，毕竟现在多区域部署、多云部署都比价常见，现在对它的处理过程做如下文字总结：
//
//    若zone为null，返回null。若只有一个zone，就返回当前zone，不用再继续判断。否则默认返回所有zone：availableZones。接下来会一步步做remove()移除动作
//    使用变量Set<String> worstZones记录所有zone中比较糟糕的zone们；用maxLoadPerServer表示所有zone中负载最高的区域；用limitedZoneAvailability表示是否是部分zone可用（true：部分可用，false：全部可用）
//    遍历所有的zone，根据其对应的快照ZoneSnapshot来判断负载情况
//    若当前zone的instanceCount也就是实例总数是0，那就remove(当前zone)，并且标记limitedZoneAvailability=true（因为移除了一个，就不是全部了嘛）。若当前zone的实例数>0，那就继续
//    拿到当前总的平均负载loadPerServer，如果zone内的熔断实例数 / 总实例数 >= triggeringBlackoutPercentage阈值 或者 loadPerServer < 0的话，那就执行remove(当前zone)，并且limitedZoneAvailability=true 
//    熔断实例数 / 总实例数 >= 阈值标记为当前zone就不可用了（移除掉），这个很好理解。这个阈值为0.99999d也就说所有的Server实例被熔断了，该zone才算不可用了
//    loadPerServer < 0是什么鬼？那么什么时候loadPerServer会是负数呢？它在LoadBalancerStats#getZoneSnapshot()方法里：if (circuitBreakerTrippedCount == instanceCount)的时候，loadPerServer = -1，也就说当所有实例都熔断了，那么loadPerServer也无意义了嘛，所以赋值为-1。
//    总的来说1和2触达条件差不多，只是1的阈值是可以配置的，比如你配置为0.9那就是只有当90%机器都熔断了就认为该zone不可用了，而不用100%（请原谅我把0.99999d当1来看待）
//    经过以上步骤，说明所有的zone是基本可用的，但可能有些负载高有些负载低，因此接下来需要判断区域负载情况，就是如下这段代码。这段代码的总体意思是：从所有zone中找出负载最高的区域们（若负载差在0.000001d只能被认为是相同负载，都认为是负载最高的们）。 
//    说明：worstZones里面装载着负载最高的zone们，也就是top1（当然可能多个并列第一的情况）
    public static Set<String> getAvailableZones(
            Map<String, ZoneSnapshot> snapshot, double triggeringLoad,
            double triggeringBlackoutPercentage) {
        if (snapshot.isEmpty()) {
            return null;
        }
    	//最终需要return的可用区，中途会进行排除的逻辑
        Set<String> availableZones = new HashSet<String>(snapshot.keySet());
        // 如果有且仅有一个zone可用，再糟糕也得用，不用进行其他逻辑了
        if (availableZones.size() == 1) {
            return availableZones;
        }
        // 记录很糟糕的，如请求超时啊，
        Set<String> worstZones = new HashSet<String>();
        // 所有zone中，平均负载最高值
        double maxLoadPerServer = 0;
        // true：zone有限可用
     	// false：zone全部可用
        boolean limitedZoneAvailability = false;
        // 对每个zone的情况逐一分析
        for (Map.Entry<String, ZoneSnapshot> zoneEntry : snapshot.entrySet()) {
            String zone = zoneEntry.getKey();
            ZoneSnapshot zoneSnapshot = zoneEntry.getValue();
            int instanceCount = zoneSnapshot.getInstanceCount();
            // 若该zone内一个实例都木有了，那就是完全不可用，那就移除该zone
            // 然后标记zone是有限可用的（并非全部可用喽）
            if (instanceCount == 0) {
                availableZones.remove(zone);
                limitedZoneAvailability = true;
            } else {
            	// 该zone的平均负载
                double loadPerServer = zoneSnapshot.getLoadPerServer();
            	// 机器的熔断总数 / 总实例数已经超过了阈值（默认为1，也就是全部熔断才会认为该zone完全不可用）
				// 或者 loadPerServer < 0 （啥时候小于0？？？下面说）
                if (((double) zoneSnapshot.getCircuitTrippedCount())/instanceCount >= triggeringBlackoutPercentage
                        || loadPerServer < 0) {
                	// 证明这个zone完全不可用，就移除掉
                	availableZones.remove(zone);
                    limitedZoneAvailability = true;
                } else {
                	// 并不是完全不可用，就看看状态是不是很糟糕
                	// 若当前负载和最大负载相当，那认为已经很糟糕了
                    if (Math.abs(loadPerServer - maxLoadPerServer) < 0.000001d) {
                        // they are the same considering double calculation
                        // round error
                        worstZones.add(zone);
                    	// 或者若当前负载大于最大负载了
                    } else if (loadPerServer > maxLoadPerServer) {
//                    	分析好数据后，最后准备返回结果。若统计完所有的区域后，最高负载maxLoadPerServer仍旧小于提供的triggeringLoad阈值，并且并且limitedZoneAvailability=false（就是说所有zone都可用的情况下），那就返回所有的zone吧：availableZones。 
//                    			这个很好理解：所有的兄弟们负载都很低，并且一个哥们都没“死”，那就都返回出去呗
//                    			triggeringLoad阈值的默认值是0.2，负载的计算方式是：loadPerServer = 整个zone的活跃请求总数 / 整个zone内可用实例总数。 
//                    			注意：一定是活跃连接数。也就是说正在处理中的链接数才算做服务压力嘛
//                    			若最大负载超过阈值（或者死了一个/N个兄弟），那么就不能返回全部拉。那就从负载最高的兄弟们中（因为可能多个，可能1个，大概率是只有1个值的）随机选择一个出来：randomChooseZone(snapshot, worstZones)，然后执行移除remove(zoneToAvoid)掉，这么处理的目的是把负载最高的那个哥们T除掉，再返回结果。 
//                    			说明：这里使用的随机算法就是上面所讲述的（谁的zone里面实例数最多，就越可能被选中）
//                    			总而言之：选择可用区的原则是T除掉不可用的、T掉负载最高的区域，其它区域返回结果，这样处理后返回的结果才是健康程度综合最好的。
                        maxLoadPerServer = loadPerServer;
                        worstZones.clear();
                        worstZones.add(zone);
                    }
                }
            }
        }
        // 若最大负载小于设定的负载阈值 并且limitedZoneAvailability=false
     	// 就是说全部zone都可用，并且最大负载都还没有达到阈值，那就把全部zone返回
        if (maxLoadPerServer < triggeringLoad && !limitedZoneAvailability) {
            // zone override is not needed here
            return availableZones;
        }
        String zoneToAvoid = randomChooseZone(snapshot, worstZones);
        if (zoneToAvoid != null) {
            availableZones.remove(zoneToAvoid);
        }
        return availableZones;

    }
 // snapshot：zone对应的ZoneSnapshot的一个map
 	// triggeringLoad：
 	// triggeringBlackoutPercentage：
    public static Set<String> getAvailableZones(LoadBalancerStats lbStats,
            double triggeringLoad, double triggeringBlackoutPercentage) {
        if (lbStats == null) {
            return null;
        }
        Map<String, ZoneSnapshot> snapshot = createSnapshot(lbStats);
        return getAvailableZones(snapshot, triggeringLoad,
                triggeringBlackoutPercentage);
    }

    @Override
    public AbstractServerPredicate getPredicate() {
        return compositePredicate;
    }    
}
