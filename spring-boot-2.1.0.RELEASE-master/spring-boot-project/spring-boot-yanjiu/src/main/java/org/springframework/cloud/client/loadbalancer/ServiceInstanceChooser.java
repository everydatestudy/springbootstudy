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

import org.springframework.cloud.client.ServiceInstance;

/**请求被拦截后，最终都是委托给了LoadBalancerClient处理。
 * Implemented by classes which use a load balancer to choose a server to send a request
 * to.
 * 由使用负载平衡器选择要向其发送请求的服务器的类实现
 * @author Ryan Baxter
 */
public interface ServiceInstanceChooser {

	/** 从负载平衡器中为指定的服务选择Service服务实例。
	 也就是根据调用者传入的serviceId，负载均衡的选择出一个具体的实例出来
	 * Chooses a ServiceInstance from the LoadBalancer for the specified service.
	 * @param serviceId The service ID to look up the LoadBalancer.
	 * @return A ServiceInstance that matches the serviceId.
	 */
	ServiceInstance choose(String serviceId);

}
