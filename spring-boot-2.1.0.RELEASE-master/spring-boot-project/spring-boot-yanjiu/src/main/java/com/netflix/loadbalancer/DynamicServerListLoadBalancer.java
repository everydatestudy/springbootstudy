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

import com.google.common.annotations.VisibleForTesting;
import com.netflix.client.ClientFactory;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**它是BaseLoadBalancer子类，具有动态源获取服务器列表的功能。即服务器列表在运行时可能会更改，此外，它还包含一些工具，其中包含服务器列表可以通过筛选条件过滤掉不需要的服务器。
 * A LoadBalancer that has the capabilities to obtain the candidate list of
 * servers using a dynamic source. i.e. The list of servers can potentially be
 * changed at Runtime. It also contains facilities wherein the list of servers
 * can be passed through a Filter criteria to filter out servers that do not
 * meet the desired criteria.
 * 可以看到，ILoadBalancer管理的五大核心组件至此全部齐活：

实际生产环境中，但凡稍微大点的应用，跨区域部署几乎是必然的。因此ZoneAwareLoadBalancer重写了setServerListForZones()方法。

该方法在其父类DynamicServerListLoadBalancer的中仅仅是根据zone进入了分组，
赋值了Map<String, List<? extends Server>> upServerListZoneMap和Map<String, ZoneStats> zoneStatsMap这两个属性

    可是，你是否有疑问，为毛这段初始化逻辑不放在父类上呢？？？
	 解答：父类BaseLoadBalancer的服务列表是静态的，一旦设置上去将不会再根据负载情况、
	 熔断情况等Stats动态的去做移除等操作，所以放在父类上并无意义。
	父类BaseLoadBalancer管理2个：IPing、IRule。负责了Server isAlive的探活，负责了负载均衡算法选择Server
        子类DynamicServerListLoadBalancer管理3个：ServerList、ServerListFilter、ServerListUpdater负责动态管理、更新服务列表
 * @author stonse
 * 
 */
public class DynamicServerListLoadBalancer<T extends Server> extends BaseLoadBalancer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicServerListLoadBalancer.class);
	// 这两个属性木有任何用处，也不知道是不是开发人员忘记删了  哈哈
    boolean isSecure = false;
    boolean useTunnel = false;

    // to keep track of modification of server lists
    //跟踪服务器列表的修改，当正在修改列表时，赋值为true，放置多个线程重复去操作，有点上锁的意思
    protected AtomicBoolean serverListUpdateInProgress = new AtomicBoolean(false);
    //提供服务列表。默认实现是ConfigurationBasedServerList，也就是Server列表来自于配置文件，如：account.ribbon.listOfServers = xxx,xxx 
    //具体实现类可以通过key：NIWSServerListClassName来配置，比如你的自定义实现。当然你也可以通过set方法/构造器初始化时指定
    //ribbon下默认使用的ConfigurationBasedServerList，但是eureka环境下默认给你配置的是DomainExtractingServerList（详见EurekaRibbonClientConfiguration）
    volatile ServerList<T> serverListImpl;
    //对ServerList执行过滤。默认使用的ZoneAffinityServerListFilter，可以通过key：NIWSServerListFilterClassName来配置指定
    volatile ServerListFilter<T> filter;
    //执行更新动作的action：updateListOfServers()更新所有
    protected final ServerListUpdater.UpdateAction updateAction = new ServerListUpdater.UpdateAction() {
        @Override
        public void doUpdate() {
            updateListOfServers();
        }
    };
    //更新器。默认使用PollingServerListUpdater 30轮询一次的更新方式，当然你可以通过ServerListUpdaterClassName这个key自己去指定。当然set/构造器传入进来也是可以的
    protected volatile ServerListUpdater serverListUpdater;

    public DynamicServerListLoadBalancer() {
        super();
    }

    @Deprecated
    public DynamicServerListLoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping, 
            ServerList<T> serverList, ServerListFilter<T> filter) {
        this(
                clientConfig,
                rule,
                ping,
                serverList,
                filter,
                new PollingServerListUpdater()
        );
    }

    public DynamicServerListLoadBalancer(IClientConfig clientConfig, IRule rule, IPing ping,
                                         ServerList<T> serverList, ServerListFilter<T> filter,
                                         ServerListUpdater serverListUpdater) {
        super(clientConfig, rule, ping);
        this.serverListImpl = serverList;
        this.filter = filter;
        this.serverListUpdater = serverListUpdater;
        if (filter instanceof AbstractServerListFilter) {
            ((AbstractServerListFilter) filter).setLoadBalancerStats(getLoadBalancerStats());
        }
        restOfInit(clientConfig);
    }

    public DynamicServerListLoadBalancer(IClientConfig clientConfig) {
        initWithNiwsConfig(clientConfig);
    }
    
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        try {
            super.initWithNiwsConfig(clientConfig);
            String niwsServerListClassName = clientConfig.getPropertyAsString(
                    CommonClientConfigKey.NIWSServerListClassName,
                    DefaultClientConfigImpl.DEFAULT_SEVER_LIST_CLASS);

            ServerList<T> niwsServerListImpl = (ServerList<T>) ClientFactory
                    .instantiateInstanceWithClientConfig(niwsServerListClassName, clientConfig);
            this.serverListImpl = niwsServerListImpl;

            if (niwsServerListImpl instanceof AbstractServerList) {
                AbstractServerListFilter<T> niwsFilter = ((AbstractServerList) niwsServerListImpl)
                        .getFilterImpl(clientConfig);
                niwsFilter.setLoadBalancerStats(getLoadBalancerStats());
                this.filter = niwsFilter;
            }

            String serverListUpdaterClassName = clientConfig.getPropertyAsString(
                    CommonClientConfigKey.ServerListUpdaterClassName,
                    DefaultClientConfigImpl.DEFAULT_SERVER_LIST_UPDATER_CLASS
            );

            this.serverListUpdater = (ServerListUpdater) ClientFactory
                    .instantiateInstanceWithClientConfig(serverListUpdaterClassName, clientConfig);

            restOfInit(clientConfig);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Exception while initializing NIWSDiscoveryLoadBalancer:"
                            + clientConfig.getClientName()
                            + ", niwsClientConfig:" + clientConfig, e);
        }
    }
    // // ~~~~~update之后马上进行首次连接~~~~~
    //该初始化方法会在其它初始化事项完成后（如给各属性赋值）执行：对当前的Server列表进行初始化、更新。
    void restOfInit(IClientConfig clientConfig) {
        boolean primeConnection = this.isEnablePrimingConnections();
        // turn this off to avoid duplicated asynchronous priming done in BaseLoadBalancer.setServerList()
        this.setEnablePrimingConnections(false);
        enableAndInitLearnNewServersFeature();

        updateListOfServers();
        if (primeConnection && this.getPrimeConnections() != null) {
            this.getPrimeConnections()
                    .primeConnections(getReachableServers());
        }
        this.setEnablePrimingConnections(primeConnection);
        LOGGER.info("DynamicServerListLoadBalancer for client {} initialized: {}", clientConfig.getClientName(), this.toString());
    }
    
//    逻辑简单，描述如下：
//
//    从ServerList拿到所有的Server们（如从配置文件中读取、从配置中心里拉取）
//    经过ServerListFilter过滤一把：如过滤掉zone负载过高的 / Server负载过高或者已经熔断了的Server
//    经过1、2后生效的就是有效的Servers了，交给setServersList()方法完成初始化。此处需要注意的是，本子类复写了此初始化方法：
    @Override
    public void setServersList(List lsrv) {
    	//在父类super.setServersList(lsrv)初始化的基础上，完成了对LoadBalancerStats、ServerStats的初始化。
        super.setServersList(lsrv);
        List<T> serverList = (List<T>) lsrv;
        Map<String, List<Server>> serversInZones = new HashMap<String, List<Server>>();
        for (Server server : serverList) {
            // make sure ServerStats is created to avoid creating them on hot
            // path
        	// 调用这句的作用：确保初始化的时候就把ServerStats创建好
            getLoadBalancerStats().getSingleServerStat(server);
            // 把Server们按照zone进行分类，最终放到LoadBalancerStats.upServerListZoneMap属性里面去
            String zone = server.getZone();
            if (zone != null) {
                zone = zone.toLowerCase();
                List<Server> servers = serversInZones.get(zone);
                if (servers == null) {
                    servers = new ArrayList<Server>();
                    serversInZones.put(zone, servers);
                }
                servers.add(server);
            }
        }
        // 该方法父类实现很简单：getLoadBalancerStats().updateZoneServerMapping(zoneServersMap)
        //子类有复写哦
        setServerListForZones(serversInZones);
    }

    protected void setServerListForZones(
            Map<String, List<Server>> zoneServersMap) {
        LOGGER.debug("Setting server list for zones: {}", zoneServersMap);
        getLoadBalancerStats().updateZoneServerMapping(zoneServersMap);
    }

    public ServerList<T> getServerListImpl() {
        return serverListImpl;
    }

    public void setServerListImpl(ServerList<T> niwsServerList) {
        this.serverListImpl = niwsServerList;
    }

    public ServerListFilter<T> getFilter() {
        return filter;
    }

    public void setFilter(ServerListFilter<T> filter) {
        this.filter = filter;
    }

    public ServerListUpdater getServerListUpdater() {
        return serverListUpdater;
    }

    public void setServerListUpdater(ServerListUpdater serverListUpdater) {
        this.serverListUpdater = serverListUpdater;
    }

    @Override
    /**
     * Makes no sense to ping an inmemory disc client
     * 
     */
    public void forceQuickPing() {
        // no-op
    }

    /**
     * Feature that lets us add new instances (from AMIs) to the list of
     * existing servers that the LB will use Call this method if you want this
     * feature enabled
     */
    public void enableAndInitLearnNewServersFeature() {
        LOGGER.info("Using serverListUpdater {}", serverListUpdater.getClass().getSimpleName());
        serverListUpdater.start(updateAction);
    }

    private String getIdentifier() {
        return this.getClientConfig().getClientName();
    }

    public void stopServerListRefreshing() {
        if (serverListUpdater != null) {
            serverListUpdater.stop();
        }
    }
     // 该方法是维护动态列表的核心方法，它在初始化的时候便会调用一次，
    //后续作为一个ServerListUpdater.UpdateAction动作每30s便会执行一次。
    @VisibleForTesting
    public void updateListOfServers() {
        List<T> servers = new ArrayList<T>();
        if (serverListImpl != null) {
            servers = serverListImpl.getUpdatedListOfServers();
            LOGGER.debug("List of Servers for {} obtained from Discovery client: {}",
                    getIdentifier(), servers);

            if (filter != null) {
                servers = filter.getFilteredListOfServers(servers);
                LOGGER.debug("Filtered List of Servers for {} obtained from Discovery client: {}",
                        getIdentifier(), servers);
            }
        }
        updateAllServerList(servers);
    }

    /**	该方法作用：能保证同一时间只有一个线程可以进来做修改动作~~~
	 	      把准备好的Servers更新到列表里。该方法是protected，子类并无复写
	 	      但是，但是，但是setServersList()方法子类是有复写的哦
     * Update the AllServer list in the LoadBalancer if necessary and enabled
     * 
     * @param ls
     */
    protected void updateAllServerList(List<T> ls) {
        // other threads might be doing this - in which case, we pass
        if (serverListUpdateInProgress.compareAndSet(false, true)) {
            try {
            	// 显示标注Server均是活的（至于真活假活交给forceQuickPing()去判断）
                for (T s : ls) {
                    s.setAlive(true); // set so that clients can start using these
                                      // servers right away instead
                                      // of having to wait out the ping cycle.
                }
                setServersList(ls);
                super.forceQuickPing();
            } finally {
                serverListUpdateInProgress.set(false);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DynamicServerListLoadBalancer:");
        sb.append(super.toString());
        sb.append("ServerList:" + String.valueOf(serverListImpl));
        return sb.toString();
    }
    
    @Override 
    public void shutdown() {
        super.shutdown();
        stopServerListRefreshing();
    }


    @Monitor(name="LastUpdated", type=DataSourceType.INFORMATIONAL)
    public String getLastUpdate() {
        return serverListUpdater.getLastUpdate();
    }

    @Monitor(name="DurationSinceLastUpdateMs", type= DataSourceType.GAUGE)
    public long getDurationSinceLastUpdateMs() {
        return serverListUpdater.getDurationSinceLastUpdateMs();
    }

    @Monitor(name="NumUpdateCyclesMissed", type=DataSourceType.GAUGE)
    public int getNumberMissedCycles() {
        return serverListUpdater.getNumberMissedCycles();
    }

    @Monitor(name="NumThreads", type=DataSourceType.GAUGE)
    public int getCoreThreads() {
        return serverListUpdater.getCoreThreads();
    }
}
