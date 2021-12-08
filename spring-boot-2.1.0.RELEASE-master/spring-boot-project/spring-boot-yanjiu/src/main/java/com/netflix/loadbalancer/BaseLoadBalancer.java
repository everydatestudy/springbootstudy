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

import static java.util.Collections.singleton;

import com.google.common.collect.ImmutableList;
import com.netflix.client.ClientFactory;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.PrimeConnections;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.util.concurrent.ShutdownEnabledTimer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** 它也是个PrimeConnectionListener，在测试请求完成后会标记readyToServe=true
 * A basic implementation of the load balancer where an arbitrary list of
 * servers can be set as the server pool. A ping can be set to determine the
 * liveness of a server. Internally, this class maintains an "all" server list
 * and an "up" server list and use them depending on what the caller asks for.
 * 能改变它两值的地方仅有两处： 
   Pinger#runPinger：基于allServerList对没台Server完成ping操作，
      所以它只会改变upServerList的值（isAlive=true才属于up）
   setServersList()：它会用新set进来的对allServerList全覆盖，并且完成对没台Server的初始化，包括识别出upServerList（这种识别其实依赖也是上面一样的ping操作）
      综上可知，upServerList的值有且仅是把allServerList经过IPing处理后，
      若isAlive=true就属于这个行列了。因此若你没指定IPing策略或者是默认的DummyPing，那么它它哥俩就永远相等（认为所有的Server均永远可用~）。
 * 动态服务器列表（因为可用Server可能会重启、停机、新增等，希望被动态发现）
Server过滤（因为某台Server可能负载偏高、已被熔断，此时希望此些Server被过滤掉）
zone区域意识（服务之间的调用希望尽量是同区域进行的，减少延迟）
 * @author stonse
 * 
 */
public class BaseLoadBalancer extends AbstractLoadBalancer implements
        PrimeConnections.PrimeConnectionListener, IClientConfigAware {

    private static Logger logger = LoggerFactory
            .getLogger(BaseLoadBalancer.class);
    private final static IRule DEFAULT_RULE = new RoundRobinRule();
    private final static SerialPingStrategy DEFAULT_PING_STRATEGY = new SerialPingStrategy();
    private static final String DEFAULT_NAME = "default";
    private static final String PREFIX = "LoadBalancer_";
    //默认是RoundRobinRule，可以通过set方法指定
    protected IRule rule = DEFAULT_RULE;
    //ping的策略，默认是SerialPingStrategy -> 串行全ping 
    //若你的实力非常之多，自己实现一个并行的策略是个很好的方案
    protected IPingStrategy pingStrategy = DEFAULT_PING_STRATEGY;

    protected IPing ping = null;
    //所有实例
    @Monitor(name = PREFIX + "AllServerList", type = DataSourceType.INFORMATIONAL)
    protected volatile List<Server> allServerList = Collections
            .synchronizedList(new ArrayList<Server>());
    //up实例（STATUS_NOT_UP实例列表通过两者差集取出）
    @Monitor(name = PREFIX + "UpServerList", type = DataSourceType.INFORMATIONAL)
    protected volatile List<Server> upServerList = Collections
            .synchronizedList(new ArrayList<Server>());

    protected ReadWriteLock allServerLock = new ReentrantReadWriteLock();
    protected ReadWriteLock upServerLock = new ReentrantReadWriteLock();
    // 负载均衡器的名称，一般同ClientName，若没指定为default
    protected String name = DEFAULT_NAME;
    //启动PingTask，定时使用IPing去检查Server的isAlive状态的定时器
    protected Timer lbTimer = null;
    //默认30ping一次。可通过key：NFLoadBalancerPingInterval配置
    protected int pingIntervalSeconds = 10;
    //    每次ping的最长时间 
    //    默认值是2s，可通过NFLoadBalancerMaxTotalPingTime这个key配置
    protected int maxTotalPingTimeSeconds = 5;
    //：按照Server的id排序，方便轮询时候输出，并无实际作用
    protected Comparator<Server> serverComparator = new ServerComparator();
   // 标志是否正在ping中，如果是就避免重复进入去ping，做无用功
    protected AtomicBoolean pingInProgress = new AtomicBoolean(false);
    // 默认是new LoadBalancerStats(name)，你也可以set进来一个现存的，当然你可以通过配置 
    //注意注意注意：它的实现类你可以通过key NFLoadBalancerStatsClassName来配置，
    //只不过默认值是com.netflix.loadbalancer.LoadBalancerStats。在实际应用中，你很有可能去继承然后复写它，然后配置进来就使用你自己的啦 
    //因为前面我们说过：LoadBalancerStats#createServerStats它获取一个
    //ServerStats写死的默认参数是很不合理的，若你想精确控制Server的状态收集，建议你复写此类，然后配置好使用你自己优化后的实现吧~
    protected LoadBalancerStats lbStats;
    //servo的计数器。略
    private volatile Counter counter = Monitors.newCounter("LoadBalancer_ChooseServer");
    //启动连接器。用于初始检测Server的readyToServe是否能够提供服务（默认是关闭的，见下面的开关）
    private PrimeConnections primeConnections;
    //默认值是false，可通过EnablePrimeConnections这个key来开启
    private volatile boolean enablePrimingConnections = false;
    
    private IClientConfig config;
    //当allServerList里面的内容（总数or每个Server的状态属性等）发生变化时，会触发此监听器
    private List<ServerListChangeListener> changeListeners = new CopyOnWriteArrayList<ServerListChangeListener>();
    //但凡只要Server的isAlive状态发生了变化，就会触发此监听器。有如下2种情况可能会触发： 
//    会触发此监听器。有如下2种情况可能会触发： 
//    IPing的时候
//    显示调用markServerDown(Server)/markServerDown(String id)的时候（该方法暂无任何显示调用处）
    private List<ServerStatusChangeListener> serverStatusListeners = new CopyOnWriteArrayList<ServerStatusChangeListener>();

    /**
     * Default constructor which sets name as "default", sets null ping, and
     * {@link RoundRobinRule} as the rule.
     * <p>
     * This constructor is mainly used by {@link ClientFactory}. Calling this
     * constructor must be followed by calling {@link #init()} or
     * {@link #initWithNiwsConfig(IClientConfig)} to complete initialization.
     * This constructor is provided for reflection. When constructing
     * programatically, it is recommended to use other constructors.
     */
    public BaseLoadBalancer() {
        this.name = DEFAULT_NAME;
        this.ping = null;
        setRule(DEFAULT_RULE);
        setupPingTask();
        lbStats = new LoadBalancerStats(DEFAULT_NAME);
    }

    public BaseLoadBalancer(String lbName, IRule rule, LoadBalancerStats lbStats) {
        this(lbName, rule, lbStats, null);
    }

    public BaseLoadBalancer(IPing ping, IRule rule) {
        this(DEFAULT_NAME, rule, new LoadBalancerStats(DEFAULT_NAME), ping);
    }

    public BaseLoadBalancer(IPing ping, IRule rule, IPingStrategy pingStrategy) {
        this(DEFAULT_NAME, rule, new LoadBalancerStats(DEFAULT_NAME), ping, pingStrategy);
    }

    public BaseLoadBalancer(String name, IRule rule, LoadBalancerStats stats,
            IPing ping) {
        this(name, rule, stats, ping, DEFAULT_PING_STRATEGY);
    }
    
    public BaseLoadBalancer(String name, IRule rule, LoadBalancerStats stats,
            IPing ping, IPingStrategy pingStrategy) {
	
        logger.debug("LoadBalancer [{}]:  initialized", name);
        
        this.name = name;
        this.ping = ping;
        this.pingStrategy = pingStrategy;
        setRule(rule);
        setupPingTask();
        lbStats = stats;
        init();
    }

    public BaseLoadBalancer(IClientConfig config) {
        initWithNiwsConfig(config);
    }

    public BaseLoadBalancer(IClientConfig config, IRule rule, IPing ping) {
        initWithConfig(config, rule, ping, createLoadBalancerStatsFromConfig(config));
    }

    void initWithConfig(IClientConfig clientConfig, IRule rule, IPing ping) {
        initWithConfig(clientConfig, rule, ping, createLoadBalancerStatsFromConfig(config));
    }
    
    void initWithConfig(IClientConfig clientConfig, IRule rule, IPing ping, LoadBalancerStats stats) {
        this.config = clientConfig;
        String clientName = clientConfig.getClientName();
        this.name = clientName;
        int pingIntervalTime = Integer.parseInt(""
                + clientConfig.getProperty(
                        CommonClientConfigKey.NFLoadBalancerPingInterval,
                        Integer.parseInt("30")));
        int maxTotalPingTime = Integer.parseInt(""
                + clientConfig.getProperty(
                        CommonClientConfigKey.NFLoadBalancerMaxTotalPingTime,
                        Integer.parseInt("2")));

        setPingInterval(pingIntervalTime);
        setMaxTotalPingTime(maxTotalPingTime);

        // cross associate with each other
        // i.e. Rule,Ping meet your container LB
        // LB, these are your Ping and Rule guys ...
        setRule(rule);
        setPing(ping);

        setLoadBalancerStats(stats);
        rule.setLoadBalancer(this);
        if (ping instanceof AbstractLoadBalancerPing) {
            ((AbstractLoadBalancerPing) ping).setLoadBalancer(this);
        }
        logger.info("Client: {} instantiated a LoadBalancer: {}", name, this);
        boolean enablePrimeConnections = clientConfig.get(
                CommonClientConfigKey.EnablePrimeConnections, DefaultClientConfigImpl.DEFAULT_ENABLE_PRIME_CONNECTIONS);

        if (enablePrimeConnections) {
            this.setEnablePrimingConnections(true);
            PrimeConnections primeConnections = new PrimeConnections(
                    this.getName(), clientConfig);
            this.setPrimeConnections(primeConnections);
        }
        init();

    }
    //所有成员属性的初始化均是构造器+initWithNiwsConfig()配置的方式完成。
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        String ruleClassName = (String) clientConfig
                .getProperty(CommonClientConfigKey.NFLoadBalancerRuleClassName);
        String pingClassName = (String) clientConfig
                .getProperty(CommonClientConfigKey.NFLoadBalancerPingClassName);
        IRule rule;
        IPing ping;
        LoadBalancerStats stats;
        try {
            rule = (IRule) ClientFactory.instantiateInstanceWithClientConfig(
                    ruleClassName, clientConfig);
            ping = (IPing) ClientFactory.instantiateInstanceWithClientConfig(
                    pingClassName, clientConfig);
            stats = createLoadBalancerStatsFromConfig(clientConfig);
        } catch (Exception e) {
            throw new RuntimeException("Error initializing load balancer", e);
        }
        initWithConfig(clientConfig, rule, ping, stats);
    }

    private LoadBalancerStats createLoadBalancerStatsFromConfig(IClientConfig clientConfig) {
        String loadBalancerStatsClassName = clientConfig
                .get(CommonClientConfigKey.NFLoadBalancerStatsClassName, LoadBalancerStats.class.getName());
        try {
            return (LoadBalancerStats) ClientFactory.instantiateInstanceWithClientConfig(
                    loadBalancerStatsClassName, clientConfig);
        } catch (Exception e) {
            logger.warn("Error initializing configured LoadBalancerStats class - " + String.valueOf(loadBalancerStatsClassName)
                    + ". Falling-back to a new LoadBalancerStats instance instead.", e);
            return new LoadBalancerStats(clientConfig.getClientName());
        }
    }

    public void addServerListChangeListener(ServerListChangeListener listener) {
        changeListeners.add(listener);
    }
    
    public void removeServerListChangeListener(ServerListChangeListener listener) {
        changeListeners.remove(listener);
    }

    public void addServerStatusChangeListener(ServerStatusChangeListener listener) {
        serverStatusListeners.add(listener);
    }

    public void removeServerStatusChangeListener(ServerStatusChangeListener listener) {
        serverStatusListeners.remove(listener);
    }

    public IClientConfig getClientConfig() {
    	return config;
    }
    
    private boolean canSkipPing() {
        if (ping == null
                || ping.getClass().getName().equals(DummyPing.class.getName())) {
            // default ping, no need to set up timer
            return true;
        } else {
            return false;
        }
    }
    // 开启对所有Server IPing的定时任务
 	// 注意：PingTask它是去ping allServers所有的服务器，毕竟有些Server它还会活过来的
    void setupPingTask() {
        if (canSkipPing()) {
            return;
        }
        if (lbTimer != null) {
            lbTimer.cancel();
        }
        lbTimer = new ShutdownEnabledTimer("NFLoadBalancer-PingTimer-" + name,
                true);
        lbTimer.schedule(new PingTask(), 0, pingIntervalSeconds * 1000);
       // 在任务还没启动前，先快速强制执行一把
        forceQuickPing();
    }

    /**
     * Set the name for the load balancer. This should not be called since name
     * should be immutable after initialization. Calling this method does not
     * guarantee that all other data structures that depend on this name will be
     * changed accordingly.
     */
    void setName(String name) {
        // and register
        this.name = name;
        if (lbStats == null) {
            lbStats = new LoadBalancerStats(name);
        } else {
            lbStats.setName(name);
        }
    }

    public String getName() {
        return name;
    }

    @Override
    public LoadBalancerStats getLoadBalancerStats() {
        return lbStats;
    }

    public void setLoadBalancerStats(LoadBalancerStats lbStats) {
        this.lbStats = lbStats;
    }

    public Lock lockAllServerList(boolean write) {
        Lock aproposLock = write ? allServerLock.writeLock() : allServerLock
                .readLock();
        aproposLock.lock();
        return aproposLock;
    }

    public Lock lockUpServerList(boolean write) {
        Lock aproposLock = write ? upServerLock.writeLock() : upServerLock
                .readLock();
        aproposLock.lock();
        return aproposLock;
    }

    public void setPingInterval(int pingIntervalSeconds) {
        if (pingIntervalSeconds < 1) {
            return;
        }

        this.pingIntervalSeconds = pingIntervalSeconds;
        if (logger.isDebugEnabled()) {
            logger.debug("LoadBalancer [{}]:  pingIntervalSeconds set to {}",
        	    name, this.pingIntervalSeconds);
        }
        setupPingTask(); // since ping data changed
    }

    public int getPingInterval() {
        return pingIntervalSeconds;
    }

    /*
     * Maximum time allowed for the ping cycle
     */
    public void setMaxTotalPingTime(int maxTotalPingTimeSeconds) {
        if (maxTotalPingTimeSeconds < 1) {
            return;
        }
        this.maxTotalPingTimeSeconds = maxTotalPingTimeSeconds;
        logger.debug("LoadBalancer [{}]: maxTotalPingTime set to {}", name, this.maxTotalPingTimeSeconds);

    }

    public int getMaxTotalPingTime() {
        return maxTotalPingTimeSeconds;
    }

    public IPing getPing() {
        return ping;
    }

    public IRule getRule() {
        return rule;
    }

    public boolean isPingInProgress() {
        return pingInProgress.get();
    }

    /* Specify the object which is used to send pings. */

    public void setPing(IPing ping) {
        if (ping != null) {
            if (!ping.equals(this.ping)) {
                this.ping = ping;
                setupPingTask(); // since ping data changed
            }
        } else {
            this.ping = null;
            // cancel the timer task
            lbTimer.cancel();
        }
    }

    /* Ignore null rules */

    public void setRule(IRule rule) {
        if (rule != null) {
            this.rule = rule;
        } else {
            /* default rule */
            this.rule = new RoundRobinRule();
        }
        if (this.rule.getLoadBalancer() != this) {
            this.rule.setLoadBalancer(this);
        }
    }

    /**
     * get the count of servers.
     * 
     * @param onlyAvailable
     *            if true, return only up servers.
     */
    public int getServerCount(boolean onlyAvailable) {
        if (onlyAvailable) {
            return upServerList.size();
        } else {
            return allServerList.size();
        }
    }

    /**该方法是核心接口方法：LB所管理的服务列表均通过此方法添加进来。
     * 将服务器列表添加到“allServer”列表;不验证唯一性
 	 // 所以您可以通过添加更多的服务器来提供更大的共享一次
 	  通过该方法添加进来的Server都会进入到allServerList全部服务列表里面，对于这些“新”Server的初始化工作依赖于setServersList()来完成：
     * Add a server to the 'allServer' list; does not verify uniqueness, so you
     * could give a server a greater share by adding it more than once.
     */
    public void addServer(Server newServer) {
        if (newServer != null) {
            try {
                ArrayList<Server> newList = new ArrayList<Server>();

                newList.addAll(allServerList);
                newList.add(newServer);
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Error adding newServer {}", name, newServer.getHost(), e);
            }
        }
    }

    /**
     * Add a list of servers to the 'allServer' list; does not verify
     * uniqueness, so you could give a server a greater share by adding it more
     * than once
     */
    @Override
    public void addServers(List<Server> newServers) {
        if (newServers != null && newServers.size() > 0) {
            try {
                ArrayList<Server> newList = new ArrayList<Server>();
                newList.addAll(allServerList);
                newList.addAll(newServers);
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Exception while adding Servers", name, e);
            }
        }
    }

    /*
     * Add a list of servers to the 'allServer' list; does not verify
     * uniqueness, so you could give a server a greater share by adding it more
     * than once USED by Test Cases only for legacy reason. DO NOT USE!!
     */
    void addServers(Object[] newServers) {
        if ((newServers != null) && (newServers.length > 0)) {

            try {
                ArrayList<Server> newList = new ArrayList<Server>();
                newList.addAll(allServerList);

                for (Object server : newServers) {
                    if (server != null) {
                        if (server instanceof String) {
                            server = new Server((String) server);
                        }
                        if (server instanceof Server) {
                            newList.add((Server) server);
                        }
                    }
                }
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Exception while adding Servers", name, e);
            }
        }
    }

    /** 小细节：此处的List不带泛型，是因为它要接受List<Server>和List<String>这两种集合
     * Set the list of servers used as the server pool. This overrides existing
     * server list.
     * 该方法完成Server列表的“初始化”逻辑：
			将入参的List完全替换掉allServerList
			对所有的新Server完成primeConnections初始链接检测，若开启了的话
			对所有的Server完成ping测试，从而给upServerList赋值（若开了ping的话） 
			若你木有指定ping或者使用就是DummyPing这个ping，那么upServerList和allServerList永远是一样的
			因为所有的Server交给ILoadBalancer来管理都是通过addServers()添加进来的，所以必经setServersList()该方法完成初始化~
     */
    public void setServersList(List lsrv) {
        Lock writeLock = allServerLock.writeLock();
        logger.debug("LoadBalancer [{}]: clearing server list (SET op)", name);
        
        ArrayList<Server> newServers = new ArrayList<Server>();
        writeLock.lock();
        try {
            ArrayList<Server> allServers = new ArrayList<Server>();
            for (Object server : lsrv) {
                if (server == null) {
                    continue;
                }

                if (server instanceof String) {
                    server = new Server((String) server);
                }

                if (server instanceof Server) {
                    logger.debug("LoadBalancer [{}]:  addServer [{}]", name, ((Server) server).getId());
                    allServers.add((Server) server);
                } else {
                    throw new IllegalArgumentException(
                            "Type String or Server expected, instead found:"
                                    + server.getClass());
                }

            }
            //// 编辑列表的内容是否有变更  只要内容不一样（包括数量、属性等）就算变更了
            boolean listChanged = false;
            if (!allServerList.equals(allServers)) {
                listChanged = true;
                if (changeListeners != null && changeListeners.size() > 0) {
                   List<Server> oldList = ImmutableList.copyOf(allServerList);
                   List<Server> newList = ImmutableList.copyOf(allServers);                   
                   for (ServerListChangeListener l: changeListeners) {
                       try {
                    	   // 若注册了监听器，就触发
                           l.serverListChanged(oldList, newList);
                       } catch (Exception e) {
                           logger.error("LoadBalancer [{}]: Error invoking server list change listener", name, e);
                       }
                   }
                }
            }
            if (isEnablePrimingConnections()) {
            	// 它记录的是newServers，也就是“新添加进来的”
    			// 因为只有新添加进来的时候才需要执行首次链接测试：primeConnections.primeConnectionsAsync(newServers, this);
                for (Server server : allServers) {
                    if (!allServerList.contains(server)) {
                        server.setReadyToServe(false);
                        newServers.add((Server) server);
                    }
                }
                //它传入的监听器是this，因为BaseLoadBalancer自己便是该监听器的实现：
                //这么做的目的很明显：对刚放进来的Server逐一做链接检查，然后赋值到Server.readyToServe属性身上。
                //注意：enablePrimingConnections通过key EnablePrimeConnections可配置，不过它的默认值是false。
                if (primeConnections != null) {
                    primeConnections.primeConnectionsAsync(newServers, this);
                }
            }
            // This will reset readyToServe flag to true on all servers
            // regardless whether
            // previous priming connections are success or not
            // 一切处理好后：全面覆盖旧值
            allServerList = allServers;
            if (canSkipPing()) {// 如果不需要ping，那么每台Server都是活的，永远死不了
                for (Server s : allServerList) {
                    s.setAlive(true);
                }
                upServerList = allServerList;
                // 若Server发生了变化，才需要立马触发ping呗，否则也没有必要
            } else if (listChanged) {


            }
        } finally {
            writeLock.unlock();
        }
    }

    /* List in string form. SETS, does not add. */
    void setServers(String srvString) {
        if (srvString != null) {

            try {
                String[] serverArr = srvString.split(",");
                ArrayList<Server> newList = new ArrayList<Server>();

                for (String serverString : serverArr) {
                    if (serverString != null) {
                        serverString = serverString.trim();
                        if (serverString.length() > 0) {
                            Server svr = new Server(serverString);
                            newList.add(svr);
                        }
                    }
                }
                setServersList(newList);
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Exception while adding Servers", name, e);
            }
        }
    }

    /**
     * return the server
     * 
     * @param index
     * @param availableOnly
     */
    public Server getServerByIndex(int index, boolean availableOnly) {
        try {
            return (availableOnly ? upServerList.get(index) : allServerList
                    .get(index));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Server> getServerList(boolean availableOnly) {
        return (availableOnly ? getReachableServers() : getAllServers());
    }

    @Override
    public List<Server> getReachableServers() {
        return Collections.unmodifiableList(upServerList);
    }

    @Override
    public List<Server> getAllServers() {
        return Collections.unmodifiableList(allServerList);
    }

    @Override
    public List<Server> getServerList(ServerGroup serverGroup) {
        switch (serverGroup) {
        case ALL:
            return allServerList;
        case STATUS_UP:
            return upServerList;
        case STATUS_NOT_UP:
            ArrayList<Server> notAvailableServers = new ArrayList<Server>(
                    allServerList);
            ArrayList<Server> upServers = new ArrayList<Server>(upServerList);
            notAvailableServers.removeAll(upServers);
            return notAvailableServers;
        }
        return new ArrayList<Server>();
    }

    public void cancelPingTask() {
        if (lbTimer != null) {
            lbTimer.cancel();
        }
    }

    /**
     * TimerTask that keeps runs every X seconds to check the status of each
     * server/node in the Server List
     * 
     * @author stonse
     * 
     */
    class PingTask extends TimerTask {
        public void run() {
            try {
            	new Pinger(pingStrategy).runPinger();
            } catch (Exception e) {
                logger.error("LoadBalancer [{}]: Error pinging", name, e);
            }
        }
    }

    /**
     * Class that contains the mechanism to "ping" all the instances
     * 
     * @author stonse
     *
     */
    class Pinger {

        private final IPingStrategy pingerStrategy;

        public Pinger(IPingStrategy pingerStrategy) {
            this.pingerStrategy = pingerStrategy;
        }

        public void runPinger() throws Exception {
            if (!pingInProgress.compareAndSet(false, true)) { 
                return; // Ping in progress - nothing to do
            }
            
            // we are "in" - we get to Ping

            Server[] allServers = null;
            boolean[] results = null;

            Lock allLock = null;
            Lock upLock = null;

            try {
                /*
                 * The readLock should be free unless an addServer operation is
                 * going on...
                 */
                allLock = allServerLock.readLock();
                allLock.lock();
                allServers = allServerList.toArray(new Server[allServerList.size()]);
                allLock.unlock();

                int numCandidates = allServers.length;
                results = pingerStrategy.pingServers(ping, allServers);

                final List<Server> newUpList = new ArrayList<Server>();
                final List<Server> changedServers = new ArrayList<Server>();

                for (int i = 0; i < numCandidates; i++) {
                    boolean isAlive = results[i];
                    Server svr = allServers[i];
                    boolean oldIsAlive = svr.isAlive();

                    svr.setAlive(isAlive);

                    if (oldIsAlive != isAlive) {
                        changedServers.add(svr);
                        logger.debug("LoadBalancer [{}]:  Server [{}] status changed to {}", 
                    		name, svr.getId(), (isAlive ? "ALIVE" : "DEAD"));
                    }

                    if (isAlive) {
                        newUpList.add(svr);
                    }
                }
                upLock = upServerLock.writeLock();
                upLock.lock();
                upServerList = newUpList;
                upLock.unlock();

                notifyServerStatusChangeListener(changedServers);
            } finally {
                pingInProgress.set(false);
            }
        }
    }

    private void notifyServerStatusChangeListener(final Collection<Server> changedServers) {
        if (changedServers != null && !changedServers.isEmpty() && !serverStatusListeners.isEmpty()) {
            for (ServerStatusChangeListener listener : serverStatusListeners) {
                try {
                    listener.serverStatusChanged(changedServers);
                } catch (Exception e) {
                    logger.error("LoadBalancer [{}]: Error invoking server status change listener", name, e);
                }
            }
        }
    }

    private final Counter createCounter() {
        return Monitors.newCounter("LoadBalancer_ChooseServer");
    }

    /*
     * Get the alive server dedicated to key
     * 
     * @return the dedicated server
     */
    public Server chooseServer(Object key) {
        if (counter == null) {
            counter = createCounter();
        }
        counter.increment();
        if (rule == null) {
            return null;
        } else {
            try {
                return rule.choose(key);
            } catch (Exception e) {
                logger.warn("LoadBalancer [{}]:  Error choosing server for key {}", name, key, e);
                return null;
            }
        }
    }

    /* Returns either null, or "server:port/servlet" */
    public String choose(Object key) {
        if (rule == null) {
            return null;
        } else {
            try {
                Server svr = rule.choose(key);
                return ((svr == null) ? null : svr.getId());
            } catch (Exception e) {
                logger.warn("LoadBalancer [{}]:  Error choosing server", name, e);
                return null;
            }
        }
    }

    public void markServerDown(Server server) {
        if (server == null || !server.isAlive()) {
            return;
        }

        logger.error("LoadBalancer [{}]:  markServerDown called on [{}]", name, server.getId());
        server.setAlive(false);
        // forceQuickPing();

        notifyServerStatusChangeListener(singleton(server));
    }

    public void markServerDown(String id) {
        boolean triggered = false;

        id = Server.normalizeId(id);

        if (id == null) {
            return;
        }

        Lock writeLock = upServerLock.writeLock();
    	writeLock.lock();
        try {
            final List<Server> changedServers = new ArrayList<Server>();

            for (Server svr : upServerList) {
                if (svr.isAlive() && (svr.getId().equals(id))) {
                    triggered = true;
                    svr.setAlive(false);
                    changedServers.add(svr);
                }
            }

            if (triggered) {
                logger.error("LoadBalancer [{}]:  markServerDown called for server [{}]", name, id);
                notifyServerStatusChangeListener(changedServers);
            }

        } finally {
            writeLock.unlock();
        }
    }

    /*
     * Force an immediate ping, if we're not currently pinging and don't have a
     * quick-ping already scheduled.
     */
    public void forceQuickPing() {
        if (canSkipPing()) {
            return;
        }
        logger.debug("LoadBalancer [{}]:  forceQuickPing invoking", name);
        
        try {
        	new Pinger(pingStrategy).runPinger();
        } catch (Exception e) {
            logger.error("LoadBalancer [{}]: Error running forceQuickPing()", name, e);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{NFLoadBalancer:name=").append(this.getName())
                .append(",current list of Servers=").append(this.allServerList)
                .append(",Load balancer stats=")
                .append(this.lbStats.toString()).append("}");
        return sb.toString();
    }

    /**
     * Register with monitors and start priming connections if it is set.
     */
    protected void init() {
        Monitors.registerObject("LoadBalancer_" + name, this);
        // register the rule as it contains metric for available servers count
        Monitors.registerObject("Rule_" + name, this.getRule());
        if (enablePrimingConnections && primeConnections != null) {
            primeConnections.primeConnections(getReachableServers());
        }
    }

    public final PrimeConnections getPrimeConnections() {
        return primeConnections;
    }

    public final void setPrimeConnections(PrimeConnections primeConnections) {
        this.primeConnections = primeConnections;
    }

    @Override
    public void primeCompleted(Server s, Throwable lastException) {
        s.setReadyToServe(true);
    }

    public boolean isEnablePrimingConnections() {
        return enablePrimingConnections;
    }

    public final void setEnablePrimingConnections(
            boolean enablePrimingConnections) {
        this.enablePrimingConnections = enablePrimingConnections;
    }
    
    public void shutdown() {
        cancelPingTask();
        if (primeConnections != null) {
            primeConnections.shutdown();
        }
        Monitors.unregisterObject("LoadBalancer_" + name, this);
        Monitors.unregisterObject("Rule_" + name, this.getRule());
    }

    /**
     * Default implementation for <c>IPingStrategy</c>, performs ping
     * serially, which may not be desirable, if your <c>IPing</c>
     * implementation is slow, or you have large number of servers.
     */
    private static class SerialPingStrategy implements IPingStrategy {

        @Override
        public boolean[] pingServers(IPing ping, Server[] servers) {
            int numCandidates = servers.length;
            boolean[] results = new boolean[numCandidates];

            logger.debug("LoadBalancer:  PingTask executing [{}] servers configured", numCandidates);

            for (int i = 0; i < numCandidates; i++) {
                results[i] = false; /* Default answer is DEAD. */
                try {
                    // NOTE: IFF we were doing a real ping
                    // assuming we had a large set of servers (say 15)
                    // the logic below will run them serially
                    // hence taking 15 times the amount of time it takes
                    // to ping each server
                    // A better method would be to put this in an executor
                    // pool
                    // But, at the time of this writing, we dont REALLY
                    // use a Real Ping (its mostly in memory eureka call)
                    // hence we can afford to simplify this design and run
                    // this
                    // serially
                    if (ping != null) {
                        results[i] = ping.isAlive(servers[i]);
                    }
                } catch (Exception e) {
                    logger.error("Exception while pinging Server: '{}'", servers[i], e);
                }
            }
            return results;
        }
    }
}
