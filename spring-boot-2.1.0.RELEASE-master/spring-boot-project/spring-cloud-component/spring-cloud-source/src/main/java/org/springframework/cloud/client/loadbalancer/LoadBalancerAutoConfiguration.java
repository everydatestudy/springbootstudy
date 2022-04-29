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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestTemplate;

/**它的负载均衡技术依赖于的是Ribbon组件~
 * 这段配置代码稍微有点长，我把流程总结为如下几步：

LoadBalancerAutoConfiguration要想生效类路径必须有RestTemplate，以及Spring容器内必须有LoadBalancerClient的实现Bean
1. LoadBalancerClient的唯一实现类是：org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient
LoadBalancerInterceptor是个ClientHttpRequestInterceptor客户端请求拦截器。它的作用是在客户端发起请求之前拦截，进而实现客户端的负载均衡
restTemplateCustomizer()返回的匿名定制器RestTemplateCustomizer它用来给所有的RestTemplate加上负载均衡拦截器（需要注意它的@ConditionalOnMissingBean注解~）
不难发现，负载均衡实现的核心就是一个拦截器，就是这个拦截器让一个普通的RestTemplate逆袭成为了一个具有负载均衡功能的请求器

 * Auto-configuration for Ribbon (client-side load balancing).
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Will Tran
 * @author Gang Li
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnBean(LoadBalancerClient.class)
@EnableConfigurationProperties(LoadBalancerRetryProperties.class)
public class LoadBalancerAutoConfiguration {
	// 拿到容器内所有的标注有@LoadBalanced注解的Bean们
		// 注意：必须标注有@LoadBalanced注解的才行
	@LoadBalanced
	@Autowired(required = false)
	private List<RestTemplate> restTemplates = Collections.emptyList();
	// LoadBalancerRequestTransformer接口：允许使用者把request + ServiceInstance --> 改造一下
	// Spring内部默认是没有提供任何实现类的（匿名的都木有）
	@Autowired(required = false)
	private List<LoadBalancerRequestTransformer> transformers = Collections.emptyList();
	// 配置一个匿名的SmartInitializingSingleton 此接口我们应该是熟悉的
		// 它的afterSingletonsInstantiated()方法会在所有的单例Bean初始化完成之后，再调用一个一个的处理BeanName~
		// 本处：使用配置好的所有的RestTemplateCustomizer定制器们，对所有的`RestTemplate`定制处理
		// RestTemplateCustomizer下面有个lambda的实现。若调用者有需要可以书写然后扔进容器里既生效
		// 这种定制器：若你项目中有多个RestTempalte，需要统一处理的话。写一个定制器是个不错的选择
		// （比如统一要放置一个请求拦截器：输出日志之类的）

	@Bean
	public SmartInitializingSingleton loadBalancedRestTemplateInitializerDeprecated(
			final ObjectProvider<List<RestTemplateCustomizer>> restTemplateCustomizers) {
		return () -> restTemplateCustomizers.ifAvailable(customizers -> {
			for (RestTemplate restTemplate : LoadBalancerAutoConfiguration.this.restTemplates) {
				for (RestTemplateCustomizer customizer : customizers) {
					customizer.customize(restTemplate);
				}
			}
		});
	}
	//LoadBalancerRequestFactory，用来拦截后重新创建请求。
	// 这个工厂用于createRequest()创建出一个LoadBalancerRequest
	// 这个请求里面是包含LoadBalancerClient以及HttpRequest request的
	@Bean
	@ConditionalOnMissingBean
	public LoadBalancerRequestFactory loadBalancerRequestFactory(
			LoadBalancerClient loadBalancerClient) {
		return new LoadBalancerRequestFactory(loadBalancerClient, this.transformers);
	}
	// =========到目前为止还和负载均衡没啥关系==========
	// =========接下来的配置才和负载均衡有关（当然上面是基础项）==========

	// 若有Retry的包，就是另外一份配置，和这差不多~~
	@Configuration
	@ConditionalOnMissingClass("org.springframework.retry.support.RetryTemplate")
	static class LoadBalancerInterceptorConfig {
		//实现负载均衡，用的是拦截器，在请求之前把服务名根据负载均衡算法替换成相应的服务实例地址，然后就去请求。
		// 这个Bean的名称叫`loadBalancerClient`，我个人觉得叫`loadBalancerInterceptor`更合适吧（虽然ribbon是唯一实现）
		// 这里直接使用的是requestFactory和Client构建一个拦截器对象
		// LoadBalancerInterceptor可是`ClientHttpRequestInterceptor`，它会介入到http.client里面去
		// LoadBalancerInterceptor也是实现负载均衡的入口，下面详解
		// Tips:这里可没有@ConditionalOnMissingBean哦~~~~
		@Bean
		public LoadBalancerInterceptor ribbonInterceptor(
				LoadBalancerClient loadBalancerClient,
				LoadBalancerRequestFactory requestFactory) {
			return new LoadBalancerInterceptor(loadBalancerClient, requestFactory);
		}
		// 向容器内放入一个RestTemplateCustomizer 定制器
				// 这个定制器的作用上面已经说了：在RestTemplate初始化完成后，应用此定制化器在**所有的实例上**
				// 这个匿名实现的逻辑超级简单：向所有的RestTemplate都塞入一个loadBalancerInterceptor 让其具备有负载均衡的能力
				// Tips：此处有注解@ConditionalOnMissingBean。也就是说如果调用者自己定义过RestTemplateCustomizer类型的Bean，此处是不会执行的
				// 请务必注意这点：容易让你的负载均衡不生效哦~~~~
		@Bean
		@ConditionalOnMissingBean
		public RestTemplateCustomizer restTemplateCustomizer(
				final LoadBalancerInterceptor loadBalancerInterceptor) {
			return restTemplate -> {
				List<ClientHttpRequestInterceptor> list = new ArrayList<>(restTemplate.getInterceptors());
				list.add(loadBalancerInterceptor);
				restTemplate.setInterceptors(list);
			};
		}

	}

	/**
	 * Auto configuration for retry mechanism.
	 */
	@Configuration
	@ConditionalOnClass(RetryTemplate.class)
	public static class RetryAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public LoadBalancedRetryFactory loadBalancedRetryFactory() {
			return new LoadBalancedRetryFactory() {
			};
		}

	}

	/**
	 * Auto configuration for retry intercepting mechanism.
	 */
	@Configuration
	@ConditionalOnClass(RetryTemplate.class)
	public static class RetryInterceptorAutoConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public RetryLoadBalancerInterceptor ribbonInterceptor(
				LoadBalancerClient loadBalancerClient,
				LoadBalancerRetryProperties properties,
				LoadBalancerRequestFactory requestFactory,
				LoadBalancedRetryFactory loadBalancedRetryFactory) {
			return new RetryLoadBalancerInterceptor(loadBalancerClient, properties,
					requestFactory, loadBalancedRetryFactory);
		}

		@Bean
		@ConditionalOnMissingBean
		public RestTemplateCustomizer restTemplateCustomizer(
				final RetryLoadBalancerInterceptor loadBalancerInterceptor) {
			return restTemplate -> {
				List<ClientHttpRequestInterceptor> list = new ArrayList<>(
						restTemplate.getInterceptors());
				list.add(loadBalancerInterceptor);
				restTemplate.setInterceptors(list);
			};
		}

	}

}
