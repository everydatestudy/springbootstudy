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

/**它选择策略的实现很简单，内部定义了RoundRobinRule，choose方法还是采用了RoundRobinRule的choose方法，所以它的选择策略和RoundRobinRule的选择策略一致。
 * This class essentially contains the RoundRobinRule class defined in the
 * loadbalancer package
 * 该策略较为特殊，我们一般不直接使用它。
 * 因为它本身并没有实现什么特殊的处理逻辑，效果同RoundRobinRule。
 * 通过继承该策略，默认的choose就实现了线性轮询机制，在子类中做一些高级策略时通常可能存在一些无法实施的情况，就可以用父类的实现作为备选，所以它作为父类用于兜底。

 	设计上，其实把该类Abstract化或许更为合理
 * @author stonse
 * 
 */
public class ClientConfigEnabledRoundRobinRule extends AbstractLoadBalancerRule {

    RoundRobinRule roundRobinRule = new RoundRobinRule();

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        roundRobinRule = new RoundRobinRule();
    }

    @Override
    public void setLoadBalancer(ILoadBalancer lb) {
    	super.setLoadBalancer(lb);
    	roundRobinRule.setLoadBalancer(lb);
    }
    
    @Override
    public Server choose(Object key) {
        if (roundRobinRule != null) {
            return roundRobinRule.choose(key);
        } else {
            throw new IllegalArgumentException(
                    "This class has not been initialized with the RoundRobinRule class");
        }
    }

}
