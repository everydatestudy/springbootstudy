/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.netflix.ribbon;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import com.netflix.client.IClient;
import com.netflix.client.http.HttpRequest;
import com.netflix.ribbon.Ribbon;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.actuator.HasFeatures;
import org.springframework.cloud.client.loadbalancer.AsyncLoadBalancerAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancedRetryFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

/**这个配置类最重要的是完成了Ribbon相关组件的自动配置，有了LoadBalancerClient才能做负载均衡（这里使用的是它的唯一实现类RibbonLoadBalancerClient）
 * Auto configuration for Ribbon (client side load balancing).
 *
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Biju Kunjummen
 */
@Configuration
//类路径存在com.netflix.client.IClient、RestTemplate等时生效
@Conditional(RibbonAutoConfiguration.RibbonClassesConditions.class)
@RibbonClients
//若有Eureka，那就在Eureka配置好后再配置它~~~（如果是别的注册中心呢，ribbon还能玩吗？）
@AutoConfigureAfter(name = "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration")
@AutoConfigureBefore({ LoadBalancerAutoConfiguration.class,
		AsyncLoadBalancerAutoConfiguration.class })
@EnableConfigurationProperties({ RibbonEagerLoadProperties.class,
		ServerIntrospectorProperties.class })
public class RibbonAutoConfiguration {
	// Ribbon的配置文件们~~~~~~~（复杂且重要）
	@Autowired(required = false)
	private List<RibbonClientSpecification> configurations = new ArrayList<>();

	@Autowired
	private RibbonEagerLoadProperties ribbonEagerLoadProperties;
	// 特征，FeaturesEndpoint这个端点(`/actuator/features`)会使用它org.springframework.cloud.client.actuator.HasFeatures
	@Bean
	public HasFeatures ribbonFeature() {
		return HasFeatures.namedFeature("Ribbon", Ribbon.class);
	}
	//这里有两个比较重要的一个是SpringClientFactory，可以创建各种和负载均衡相关的对象，还有一个是RibbonLoadBalancerClient，做具体的负载均衡算法选择和调用。
	// 它是最为重要的，是一个org.springframework.cloud.context.named.NamedContextFactory  此工厂用于创建命名的Spring容器
		// 这里传入配置文件，每个不同命名空间就会创建一个新的容器（和Feign特别像） 设置当前容器为父容器

	@Bean
	public SpringClientFactory springClientFactory() {
		SpringClientFactory factory = new SpringClientFactory();
		factory.setConfigurations(this.configurations);
		return factory;
	}

	@Bean
	@ConditionalOnMissingBean(LoadBalancerClient.class)
	public LoadBalancerClient loadBalancerClient() {
		return new RibbonLoadBalancerClient(springClientFactory());
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.retry.support.RetryTemplate")
	@ConditionalOnMissingBean
	public LoadBalancedRetryFactory loadBalancedRetryPolicyFactory(
			final SpringClientFactory clientFactory) {
		return new RibbonLoadBalancedRetryFactory(clientFactory);
	}

	@Bean
	@ConditionalOnMissingBean
	public PropertiesFactory propertiesFactory() {
		return new PropertiesFactory();
	}

	@Bean
	@ConditionalOnProperty("ribbon.eager-load.enabled")
	public RibbonApplicationContextInitializer ribbonApplicationContextInitializer() {
		return new RibbonApplicationContextInitializer(springClientFactory(),
				ribbonEagerLoadProperties.getClients());
	}

	@Configuration
	@ConditionalOnClass(HttpRequest.class)
	@ConditionalOnRibbonRestClient
	protected static class RibbonClientHttpRequestFactoryConfiguration {

		@Autowired
		private SpringClientFactory springClientFactory;

		@Bean
		public RestTemplateCustomizer restTemplateCustomizer(
				final RibbonClientHttpRequestFactory ribbonClientHttpRequestFactory) {
			return restTemplate -> restTemplate
					.setRequestFactory(ribbonClientHttpRequestFactory);
		}

		@Bean
		public RibbonClientHttpRequestFactory ribbonClientHttpRequestFactory() {
			return new RibbonClientHttpRequestFactory(this.springClientFactory);
		}

	}

	// TODO: support for autoconfiguring restemplate to use apache http client or okhttp

	@Target({ ElementType.TYPE, ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Conditional(OnRibbonRestClientCondition.class)
	@interface ConditionalOnRibbonRestClient {

	}

	private static class OnRibbonRestClientCondition extends AnyNestedCondition {

		OnRibbonRestClientCondition() {
			super(ConfigurationPhase.REGISTER_BEAN);
		}

		@Deprecated // remove in Edgware"
		@ConditionalOnProperty("ribbon.http.client.enabled")
		static class ZuulProperty {

		}

		@ConditionalOnProperty("ribbon.restclient.enabled")
		static class RibbonProperty {

		}

	}

	/**
	 * {@link AllNestedConditions} that checks that either multiple classes are present.
	 */
	static class RibbonClassesConditions extends AllNestedConditions {

		RibbonClassesConditions() {
			super(ConfigurationPhase.PARSE_CONFIGURATION);
		}

		@ConditionalOnClass(IClient.class)
		static class IClientPresent {

		}

		@ConditionalOnClass(RestTemplate.class)
		static class RestTemplatePresent {

		}

		@ConditionalOnClass(AsyncRestTemplate.class)
		static class AsyncRestTemplatePresent {

		}

		@ConditionalOnClass(Ribbon.class)
		static class RibbonPresent {

		}

	}

}
