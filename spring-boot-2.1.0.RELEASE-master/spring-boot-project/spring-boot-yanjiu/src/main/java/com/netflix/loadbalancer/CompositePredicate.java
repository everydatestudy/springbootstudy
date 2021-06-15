
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

import java.util.Iterator;
import java.util.List;



import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;

/**
 * A predicate that is composed from one or more predicates in "AND" relationship.
 * It also has the functionality of "fallback" to one of more different predicates.
 * If the primary predicate yield too few filtered servers from the {@link #getEligibleServers(List, Object)}
 * API, it will try the fallback predicates one by one, until the number of filtered servers
 * exceeds certain number threshold or percentage threshold. 
 * 
 * @author awang
 *
 */
public class CompositePredicate extends AbstractServerPredicate {

    private AbstractServerPredicate delegate;
    
    private List<AbstractServerPredicate> fallbacks = Lists.newArrayList();
        
    private int minimalFilteredServers = 1;
    
    private float minimalFilteredPercentage = 0;    
    
    @Override
    public boolean apply(PredicateKey input) {
        return delegate.apply(input);
    }

    
    public static class Builder {
        
        private CompositePredicate toBuild;
        
        Builder(AbstractServerPredicate primaryPredicate) {
            toBuild = new CompositePredicate();    
            toBuild.delegate = primaryPredicate;                    
        }

        Builder(AbstractServerPredicate ...primaryPredicates) {
            toBuild = new CompositePredicate();
            Predicate<PredicateKey> chain = Predicates.<PredicateKey>and(primaryPredicates);
            toBuild.delegate =  AbstractServerPredicate.ofKeyPredicate(chain);                
        }

        public Builder addFallbackPredicate(AbstractServerPredicate fallback) {
            toBuild.fallbacks.add(fallback);
            return this;
        }
                
        public Builder setFallbackThresholdAsMinimalFilteredNumberOfServers(int number) {
            toBuild.minimalFilteredServers = number;
            return this;
        }
        
        public Builder setFallbackThresholdAsMinimalFilteredPercentage(float percent) {
            toBuild.minimalFilteredPercentage = percent;
            return this;
        }
        
        public CompositePredicate build() {
            return toBuild;
        }
    }
    
    public static Builder withPredicates(AbstractServerPredicate ...primaryPredicates) {
        return new Builder(primaryPredicates);
    }

    public static Builder withPredicate(AbstractServerPredicate primaryPredicate) {
        return new Builder(primaryPredicate);
    }

    /**从主Predicate获取**过滤后**的服务器，如果过滤后的服务器的数量还不够
	 （应该说还太多），继续尝试使用fallback的Predicate继续过滤
     * Get the filtered servers from primary predicate, and if the number of the filtered servers
     * are not enough, trying the fallback predicates  
     */
//    对此部分“加强版”过滤逻辑做如下文字总结：
//
//    使用主Predicate（当然它可能是个链）执行过滤，若剩余的server数量不够数（比如我最小希望有1台），那么就触发fallback让它去尝试完成使命
//    若fallback有值（不为空），就顺序的一个一个的尝试，让若经过谁处理完后数量大于最小值了，那就立马停止返回结果；但是若执行完所有的fallback后数量还是小于阈值不合格咋办呢？那就last win 
//    重点：每次执行Predicate都是基于原来的ServerList的，所以每次执行都是独立的，这点特别重要
    @Override
    public List<Server> getEligibleServers(List<Server> servers, Object loadBalancerKey) {
    	// 1、使用主Predicate完成过滤，留下合格的Server们
        List<Server> result = super.getEligibleServers(servers, loadBalancerKey);
    	// 2、继续执行fallback的断言器
        Iterator<AbstractServerPredicate> i = fallbacks.iterator();
        while (!(result.size() >= minimalFilteredServers && result.size() > (int) (servers.size() * minimalFilteredPercentage))
                && i.hasNext()) {
            AbstractServerPredicate predicate = i.next();
         // 特别注意：这里传入的是Server，而非在result基础上过滤
			// 所以每次执行过滤和上一次的结果没有半毛钱关系
            result = predicate.getEligibleServers(servers, loadBalancerKey);
        }
        return result;
    }
}
