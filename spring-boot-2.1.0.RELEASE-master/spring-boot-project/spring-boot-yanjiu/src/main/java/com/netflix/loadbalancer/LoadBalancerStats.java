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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.config.IClientConfig;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;

/**
  * 用作操作特性和统计信息的存储库LaodBalancer中的每个节点/服务器，这些信息可以用来观察和理解运行时行为的LoadBalancer，
  * 用来决定负载平衡策略。简单的说，它就是作为ServerStats实例列表的容器，统一维护（当然还有zone区域的概念）。
  * 内部有三个缓存类型的成员变量，一是upServerListZoneMap，二是serverStatsCache，他俩的关系如下图所示：
 * Class that acts as a repository of operational charateristics and statistics
 * of every Node/Server in the LaodBalancer.
 * 
 * This information can be used to just observe and understand the runtime
 * behavior of the loadbalancer or more importantly for the basis that
 * determines the loadbalacing strategy
 * 
 * @author stonse
 * // 它实现了IClientConfigAware接口，所以很方便的得到IClientConfig配置
 */
public class LoadBalancerStats implements IClientConfigAware {
    
    private static final String PREFIX = "LBStats_";
    
    String name;
    // 该变量最初使用的ConcurrentHashMap缓存
    // Map<Server,ServerStats> serverStatsMap = new ConcurrentHashMap<Server,ServerStats>();
    volatile Map<String, ZoneStats> zoneStatsMap = new ConcurrentHashMap<String, ZoneStats>();
    volatile Map<String, List<? extends Server>> upServerListZoneMap = new ConcurrentHashMap<String, List<? extends Server>>();
    
    private volatile CachedDynamicIntProperty connectionFailureThreshold;
        
    private volatile CachedDynamicIntProperty circuitTrippedTimeoutFactor;

    private volatile CachedDynamicIntProperty maxCircuitTrippedTimeout;

    private static final DynamicIntProperty SERVERSTATS_EXPIRE_MINUTES = 
        DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.serverStats.expire.minutes", 30);
//    存储了Server和ServerStats的对应关系。老版本使用的Map缓存的，新版本使用了guaua的cache（增加了过期时间，对内存更友好） 
//    Server默认的缓存时长是30s，请尽量保持此值>=熔断最大时长的值（它默认也是30s）
    private final LoadingCache<Server, ServerStats> serverStatsCache = 
        CacheBuilder.newBuilder()
    	// 在一定时间内没有读写，会移除该key
		// 在30s内没有读写该Server的时候会移除对应的没有被访问的key
            .expireAfterAccess(SERVERSTATS_EXPIRE_MINUTES.get(), TimeUnit.MINUTES)
			// 移除的时候把其pulish的功能也关了（不然定时任务一直在运行）
            .removalListener(new RemovalListener<Server, ServerStats>() {
                @Override
                public void onRemoval(RemovalNotification<Server, ServerStats> notification) {
                    notification.getValue().close();
                }
            })
            // 首次get木有的话，就会调用此方法给你新建一个新的Server实例
            .build(
                new CacheLoader<Server, ServerStats>() {
                    public ServerStats load(Server server) {
                        return createServerStats(server);
                    }
                });
    // 为何这里默认值为何是1000和1000？？？和ServerStats里的常量值并不一样哦
    protected ServerStats createServerStats(Server server) {
        ServerStats ss = new ServerStats(this);
        //configure custom settings
        ss.setBufferSize(1000);
        ss.setPublishInterval(1000);           
     // 请务必调用此方法完成初始化
        ss.initialize(server);
        return ss;        
    }
    
    public LoadBalancerStats(){
    	//每个zone对应一个ZoneStats，代表着该可用区的健康状态
        zoneStatsMap = new ConcurrentHashMap<String, ZoneStats>();  
        //存储了zone和server状态ZoneStats的对应关系（一个zone内可以有多台Server）
        upServerListZoneMap = new ConcurrentHashMap<String, List<? extends Server>>();        
    }
    
    public LoadBalancerStats(String name){
        this();
        this.name = name;
        Monitors.registerObject(name, this); 
    }
    // 给name赋值为ClientName
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig)
    {
        this.name = clientConfig.getClientName();
        Monitors.registerObject(name, this);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    CachedDynamicIntProperty getConnectionFailureCountThreshold() {
        if (connectionFailureThreshold == null) {
            connectionFailureThreshold = new CachedDynamicIntProperty(
                    "niws.loadbalancer." + name + ".connectionFailureCountThreshold", 3);
        }
        return connectionFailureThreshold;

    }

    CachedDynamicIntProperty getCircuitTrippedTimeoutFactor() {
        if (circuitTrippedTimeoutFactor == null) {
            circuitTrippedTimeoutFactor = new CachedDynamicIntProperty(
                    "niws.loadbalancer." + name + ".circuitTripTimeoutFactorSeconds", 10);
        }
        return circuitTrippedTimeoutFactor;        
    }

    CachedDynamicIntProperty getCircuitTripMaxTimeoutSeconds() {
        if (maxCircuitTrippedTimeout == null) {
            maxCircuitTrippedTimeout = new CachedDynamicIntProperty(
                    "niws.loadbalancer." + name + ".circuitTripMaxTimeoutSeconds", 30);
        }
        return maxCircuitTrippedTimeout;        
    }
    
    /**这个update的作用有两个：
	// 1、touch一下，保证缓存里的不过期
	// 2、touch的时候发现缓存木有了，就给新建一个
     * The caller o this class is tasked to call this method every so often if
     * the servers participating in the LoadBalancer changes
     * @param servers
     */
    public void updateServerList(List<Server> servers){
        for (Server s: servers){
            addServer(s);
        }
    }
    
	// 增加一台Server到缓存里。该方法仅被下面调用
    public void addServer(Server server) {
        try {
            serverStatsCache.get(server);
        } catch (ExecutionException e) {
            ServerStats stats = createServerStats(server);
            serverStatsCache.asMap().putIfAbsent(server, stats);
        }
    } 
    
    /**记录响应时间数据：dataDist和responseTimeDist会记录
     * Method that updates the internal stats of Response times maintained on a per Server
     * basis
     * @param server
     * @param msecs
     */
    public void noteResponseTime(Server server, double msecs){
        ServerStats ss = getServerStats(server);  
        ss.noteResponseTime(msecs);
    }
    // 获取Server对应的ServerStats实例：从缓存里获取
    protected ServerStats getServerStats(Server server) {
        try {
            return serverStatsCache.get(server);
        } catch (ExecutionException e) {
            ServerStats stats = createServerStats(server);
            serverStatsCache.asMap().putIfAbsent(server, stats);
            return serverStatsCache.asMap().get(server);
        }
    }
    
    public void incrementActiveRequestsCount(Server server) {
        ServerStats ss = getServerStats(server); 
        ss.incrementActiveRequestsCount();
    }

    public void decrementActiveRequestsCount(Server server) {
        ServerStats ss = getServerStats(server); 
        ss.decrementActiveRequestsCount();
    }

    private ZoneStats getZoneStats(String zone) {
        zone = zone.toLowerCase();
        ZoneStats zs = zoneStatsMap.get(zone);
        if (zs == null){
            zoneStatsMap.put(zone, new ZoneStats(this.getName(), zone, this));
            zs = zoneStatsMap.get(zone);
        }
        return zs;
    }
 // 判断当前Server是否已经处于熔断状态
    public boolean isCircuitBreakerTripped(Server server) {
        ServerStats ss = getServerStats(server);
        return ss.isCircuitBreakerTripped();
    }
        
    public void incrementSuccessiveConnectionFailureCount(Server server) {
        ServerStats ss = getServerStats(server);
        ss.incrementSuccessiveConnectionFailureCount();
    }
    
    public void clearSuccessiveConnectionFailureCount(Server server) {
        ServerStats ss = getServerStats(server);
        ss.clearSuccessiveConnectionFailureCount();        
    }

    public void incrementNumRequests(Server server){
        ServerStats ss = getServerStats(server);  
        ss.incrementNumRequests();
    }
    // 打理ZoneStats的方法
    public void incrementZoneCounter(Server server) {
        String zone = server.getZone();
        if (zone != null) {
            getZoneStats(zone).incrementCounter();
        }
    }
    // 用心的Map代替掉缓存内容。每次都调用一次getZoneStats()是为了确保每个zone都能有一个ZoneStats实例
    // updateZoneServerMapping是唯一给upServerListZoneMap赋值的方法哦~~~
    // 改方法会在DynamicServerListLoadBalancer#setServerListForZones调用
    public void updateZoneServerMapping(Map<String, List<Server>> map) {
        upServerListZoneMap = new ConcurrentHashMap<String, List<? extends Server>>(map);
        // make sure ZoneStats object exist for available zones for monitoring purpose
        for (String zone: map.keySet()) {
            getZoneStats(zone);
        }
    }

    public int getInstanceCount(String zone) {
        if (zone == null) {
            return 0;
        }
        zone = zone.toLowerCase();
        List<? extends Server> currentList = upServerListZoneMap.get(zone);
        if (currentList == null) {
            return 0;
        }
        return currentList.size();
    }
    
    public int getActiveRequestsCount(String zone) {
        return getZoneSnapshot(zone).getActiveRequestsCount();
    }
        
    public double getActiveRequestsPerServer(String zone) {
        return getZoneSnapshot(zone).getLoadPerServer();
    }
    // 根据zone获取到server列表，根据server获取到统计信息，从而计算出整个zone的快照状态，包含的信息就是一个ZoneSnapshot实例。
    // zone不区分大小写。拿到该zone的一个快照，它的一句是该zone下的ServerList
    public ZoneSnapshot getZoneSnapshot(String zone) {
        if (zone == null) {
            return new ZoneSnapshot();
        }
        zone = zone.toLowerCase();
        List<? extends Server> currentList = upServerListZoneMap.get(zone);
        return getZoneSnapshot(currentList);        
    }
    
    /**根据这些servers，计算出快照值（4个属性）
     * This is the core function to get zone stats. All stats are reported to avoid
     * going over the list again for a different stat.
     * 
     * @param servers
     */
    public ZoneSnapshot getZoneSnapshot(List<? extends Server> servers) {
        if (servers == null || servers.size() == 0) {
            return new ZoneSnapshot();
        }
        int instanceCount = servers.size();
        int activeConnectionsCount = 0;
        int activeConnectionsCountOnAvailableServer = 0;
        int circuitBreakerTrippedCount = 0;
        double loadPerServer = 0;
        long currentTime = System.currentTimeMillis();
        // 从每个Server身上统计数据
        for (Server server: servers) {
        	// 先拿到每个Server自己所属的stat
            ServerStats stat = getSingleServerStat(server);   
            if (stat.isCircuitBreakerTripped(currentTime)) {
                circuitBreakerTrippedCount++;
            } else {
                activeConnectionsCountOnAvailableServer += stat.getActiveRequestsCount(currentTime);
            }
            activeConnectionsCount += stat.getActiveRequestsCount(currentTime);
        }
        if (circuitBreakerTrippedCount == instanceCount) {
            if (instanceCount > 0) {
                // should be NaN, but may not be displayable on Epic
                loadPerServer = -1;
            }
        } else {
            loadPerServer = ((double) activeConnectionsCountOnAvailableServer) / (instanceCount - circuitBreakerTrippedCount);
        }
        return new ZoneSnapshot(instanceCount, circuitBreakerTrippedCount, activeConnectionsCount, loadPerServer);
    }
    
    public int getCircuitBreakerTrippedCount(String zone) {
        return getZoneSnapshot(zone).getCircuitTrippedCount();
    }

    @Monitor(name=PREFIX + "CircuitBreakerTrippedCount", type = DataSourceType.GAUGE)   
    public int getCircuitBreakerTrippedCount() {
        int count = 0;
        for (String zone: upServerListZoneMap.keySet()) {
            count += getCircuitBreakerTrippedCount(zone);
        }
        return count;
    }
    
    public long getMeasuredZoneHits(String zone) {
        if (zone == null) {
            return 0;
        }
        zone = zone.toLowerCase();
        long count = 0;
        List<? extends Server> currentList = upServerListZoneMap.get(zone);
        if (currentList == null) {
            return 0;
        }
        for (Server server: currentList) {
            ServerStats stat = getSingleServerStat(server);
            count += stat.getMeasuredRequestsCount();
        }
        return count;
    }
 // 该方法的效果和ZoneSnapshot.loadPerServer效果基本一致
 	// 该方法并没有任何调用，可忽略
    public int getCongestionRatePercentage(String zone) {
        if (zone == null) {
            return 0;
        }
        zone = zone.toLowerCase();
        List<? extends Server> currentList = upServerListZoneMap.get(zone);
        if (currentList == null || currentList.size() == 0) {
            return 0;            
        }
        int serverCount = currentList.size(); 
        int activeConnectionsCount = 0;
        int circuitBreakerTrippedCount = 0;
        for (Server server: currentList) {
            ServerStats stat = getSingleServerStat(server);   
            activeConnectionsCount += stat.getActiveRequestsCount();
            if (stat.isCircuitBreakerTripped()) {
                circuitBreakerTrippedCount++;
            }
        }
        return (int) ((activeConnectionsCount + circuitBreakerTrippedCount) * 100L / serverCount); 
    }
	// 拿到所有的可用的zone区域（有对应的up的Server的就叫有用的zone，叫可用区）
   // 若有下面逻辑的存在，其实我觉得该方法的命令是颇具歧义的，或许叫getAllAvailableZones()会更合适一些。
   //因为它仅是一个普通的获取方法，并不考虑对应zone内Server的负载情况、可用情况，这些都交给下面这个工具方法进行完成。
    @Monitor(name=PREFIX + "AvailableZones", type = DataSourceType.INFORMATIONAL)   
    public Set<String> getAvailableZones() {
        return upServerListZoneMap.keySet();
    }
    
    public ServerStats getSingleServerStat(Server server) {
        return getServerStats(server);
    }

    /**
     * returns map of Stats for all servers
     */
    public Map<Server,ServerStats> getServerStats(){
        return serverStatsCache.asMap();
    }
    
    public Map<String, ZoneStats> getZoneStats() {
        return zoneStatsMap;
    }
    
    @Override
    public String toString() {
        return "Zone stats: " + zoneStatsMap.toString() 
                + "," + "Server stats: " + getSortedServerStats(getServerStats().values()).toString();
    }
    
    private static Comparator<ServerStats> serverStatsComparator = new Comparator<ServerStats>() {
        @Override
        public int compare(ServerStats o1, ServerStats o2) {
            String zone1 = "";
            String zone2 = "";
            if (o1.server != null && o1.server.getZone() != null) {
                zone1 = o1.server.getZone();
            }
            if (o2.server != null && o2.server.getZone() != null) {
                zone2 = o2.server.getZone();
            }
            return zone1.compareTo(zone2);            
        }
    };
    
    private static Collection<ServerStats> getSortedServerStats(Collection<ServerStats> stats) {
        List<ServerStats> list = new ArrayList<ServerStats>(stats);
        Collections.sort(list, serverStatsComparator);
        return list;
    }

}
