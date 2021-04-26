/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.client.loadbalancer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/** ServerList：可以响应客户端的特定服务的服务器列表
	ServerListFilter：可以动态获得的具有所需特征的候选服务器列表的过滤器
	ServerListUpdater：用于执行动态服务器列表更新
	IRule：负载均衡策略，用于确定从服务器列表返回哪个服务器
	IPing：客户端用于快速检查服务器当时是否处于活动状态（心跳检测）默认使用的DummyPing并没有现实意义 nacos有自己的心跳检查机制，没有用springcloud额
	ILoadBalancer：负载均衡器，负责负载均衡调度的管理
	说明，以上核心组件所在的Jar其实是ribbon-loadbalancer，它包含ribbon-core，更面向于应用层面，所以一般都会使用它。
 * Annotation to mark a RestTemplate bean to be configured to use a LoadBalancerClient.
 * @author Spencer Gibb
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Qualifier
public @interface LoadBalanced {

}
