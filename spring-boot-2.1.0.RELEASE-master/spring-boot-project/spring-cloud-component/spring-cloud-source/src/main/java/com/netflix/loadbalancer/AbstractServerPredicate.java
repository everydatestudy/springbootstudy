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
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

 

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.PredicateKey;

/**它是服务器过滤逻辑的基础组件，可用于rules and server list filters。它传入的是一个PredicateKey，含有一个Server和loadBalancerKey，由此可以通过服务器和负载均衡器来开发过滤服务器的逻辑。

它是基于谷歌的Predicate实现的断言逻辑，该接口和JDK8的java.util.function.Predicate一毛一样，可以概念互替。
————————————————
版权声明：本文为CSDN博主「王伟王胖胖」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/wangwei19871103/article/details/105651261

 * A basic building block for server filtering logic which can be used in rules and server list filters.
 * The input object of the predicate is {@link PredicateKey}, which has Server and load balancer key
 * information. Therefore, it is possible to develop logic to filter servers by both Server and load balancer
 * key or either one of them. 
 * 
 * @author awang
 *
 */
public abstract class AbstractServerPredicate implements Predicate<PredicateKey> {
    //rule：负载均衡器LoadBalancer规则：能从其获取到ILoadBalancer，从而得到一个对应的LoadBalancerStats实例
    protected IRule rule;
    // LoadBalancer状态信息。可以通过构造器指定/set方法指定，若没有指定的话将会从IRule里拿
    private volatile LoadBalancerStats lbStats;
     // ：随机数。当过滤后还剩多台Server将从中随机获取
    private final Random random = new Random();
    //下一个角标。用于轮询算法的指示
    private final AtomicInteger nextIndex = new AtomicInteger();
    //一个特殊的Predicate：只有Server参数并无loadBalancerKey参数的PredicateKey，最终也是使用AbstractServerPredicate完成断言
    private final Predicate<Server> serverOnlyPredicate =  new Predicate<Server>() {
        @Override
        public boolean apply( Server input) {                    
            return AbstractServerPredicate.this.apply(new PredicateKey(input));
        }
    };

    public static AbstractServerPredicate alwaysTrue() { 
        return new AbstractServerPredicate() {        
            @Override
            public boolean apply( PredicateKey input) {
                return true;
            }
        };
    }

    public AbstractServerPredicate() {
        
    }
    
    public AbstractServerPredicate(IRule rule) {
        this.rule = rule;
    }
    
    public AbstractServerPredicate(IRule rule, IClientConfig clientConfig) {
        this.rule = rule;
    }
    
    public AbstractServerPredicate(LoadBalancerStats lbStats, IClientConfig clientConfig) {
        this.lbStats = lbStats;
    }
    // 得到负载均衡器对应的LoadBalancerStats实例
 	// 该方法为protected，在子类中会被调用用于判断
    protected LoadBalancerStats getLBStats() {
        if (lbStats != null) {
            return lbStats;
			//从rule里面拿到ILoadBalancer，进而拿到LoadBalancerStats
        } else if (rule != null) {
            ILoadBalancer lb = rule.getLoadBalancer();
            if (lb instanceof AbstractLoadBalancer) {
                LoadBalancerStats stats =  ((AbstractLoadBalancer) lb).getLoadBalancerStats();
                setLoadBalancerStats(stats);
                return stats;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
    
    public void setLoadBalancerStats(LoadBalancerStats stats) {
        this.lbStats = stats;
    }
    
    /**
     * Get the predicate to filter list of servers. The load balancer key is treated as null
     * as the input of this predicate.
     */
    public Predicate<Server> getServerOnlyPredicate() {
        return serverOnlyPredicate;
    }
    
    /**Eligible：适合的
	 * 把servers通过Predicate#apply(PredicateKey)删选一把后，返回符合条件的Server们
	 * loadBalancerKey非必须的。
     * Get servers filtered by this predicate from list of servers. Load balancer key
     * is presumed to be null. 
     * 
     * @see #getEligibleServers(List, Object)
     * 
     */
    public List<Server> getEligibleServers(List<Server> servers) {
        return getEligibleServers(servers, null);
    }
 
    /**
     * Get servers filtered by this predicate from list of servers. 
     */
    public List<Server> getEligibleServers(List<Server> servers, Object loadBalancerKey) {
        if (loadBalancerKey == null) {
            return ImmutableList.copyOf(Iterables.filter(servers, this.getServerOnlyPredicate()));            
        } else {
            List<Server> results = Lists.newArrayList();
            for (Server server: servers) {
            	//com.netflix.loadbalancer.CompositePredicate@57d5e0c0
                if (this.apply(new PredicateKey(loadBalancerKey, server))) {
                    results.add(server);
                }
            }
            return results;            
        }
    }

    /** 它是轮询算法的实现。轮询算法，下面会有应用
     * Referenced from RoundRobinRule
     * Inspired by the implementation of {@link AtomicInteger#incrementAndGet()}.
     *
     * @param modulo The modulo to bound the value of the counter.
     * @return The next value.
     */
    private int incrementAndGetModulo(int modulo) {
        for (;;) {
            int current = nextIndex.get();
            int next = (current + 1) % modulo;
            if (nextIndex.compareAndSet(current, next) && current < modulo)
                return current;
        }
    }
    
    /* 得到Eligible合适的机器们后，采用随机策略随便选一台
     * Choose a random server after the predicate filters a list of servers. Load balancer key 
     * is presumed to be null.
     *  
     */
    public Optional<Server> chooseRandomlyAfterFiltering(List<Server> servers) {
        List<Server> eligible = getEligibleServers(servers);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
    
    /**
     * Choose a server in a round robin fashion after the predicate filters a list of servers. Load balancer key 
     * is presumed to be null.
     */
    public Optional<Server> chooseRoundRobinAfterFiltering(List<Server> servers) {
        List<Server> eligible = getEligibleServers(servers);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(incrementAndGetModulo(eligible.size())));
    }
    
    /**
     * Choose a random server after the predicate filters list of servers given list of servers and
     * load balancer key. 
     *  
     */
    public Optional<Server> chooseRandomlyAfterFiltering(List<Server> servers, Object loadBalancerKey) {
        List<Server> eligible = getEligibleServers(servers, loadBalancerKey);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(random.nextInt(eligible.size())));
    }
    
    /**
     * Choose a server in a round robin fashion after the predicate filters a given list of servers and load balancer key. 
     */
    public Optional<Server> chooseRoundRobinAfterFiltering(List<Server> servers, Object loadBalancerKey) {
        List<Server> eligible = getEligibleServers(servers, loadBalancerKey);
        if (eligible.size() == 0) {
            return Optional.absent();
        }
        return Optional.of(eligible.get(incrementAndGetModulo(eligible.size())));
    }
        
    /**
     * Create an instance from a predicate.
     */
    public static AbstractServerPredicate ofKeyPredicate(final Predicate<PredicateKey> p) {
        return new AbstractServerPredicate() {
            @Override
            @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP")
            public boolean apply(PredicateKey input) {
                return p.apply(input);
            }            
        };        
    }
    
    /**
     * Create an instance from a predicate.
     */
    public static AbstractServerPredicate ofServerPredicate(final Predicate<Server> p) {
        return new AbstractServerPredicate() {
            @Override
            @edu.umd.cs.findbugs.annotations.SuppressWarnings(value = "NP")
            public boolean apply(PredicateKey input) {
                return p.apply(input.getServer());
            }            
        };        
    }
}
