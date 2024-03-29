/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.openfeign;

import java.util.Map;
import java.util.Objects;

import feign.Client;
import feign.Contract;
import feign.ExceptionPropagationPolicy;
import feign.Feign;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.cloud.openfeign.loadbalancer.FeignBlockingLoadBalancerClient;
import org.springframework.cloud.openfeign.ribbon.LoadBalancerFeignClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Venil Noronha
 * @author Eko Kurniawan Khannedy
 * @author Gregor Zurowski
 * @author Matt King
 */
class FeignClientFactoryBean implements FactoryBean<Object>, InitializingBean, ApplicationContextAware {

	/***********************************
	 * WARNING! Nothing in this class should be @Autowired. It causes NPEs because
	 * of some lifecycle race condition.
	 ***********************************/

	private Class<?> type;

	private String name;

	private String url;

	private String contextId;

	private String path;

	private boolean decode404;

	private boolean inheritParentContext = true;

	private ApplicationContext applicationContext;

	private Class<?> fallback = void.class;

	private Class<?> fallbackFactory = void.class;

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.hasText(this.contextId, "Context id must be set");
		Assert.hasText(this.name, "Name must be set");
	}

	protected Feign.Builder feign(FeignContext context) {
		FeignLoggerFactory loggerFactory = get(context, FeignLoggerFactory.class);
		Logger logger = loggerFactory.create(this.type);
		// 通过this.contextId从子容器获取所有必须的实例
		   // 如果如果引入了Hystrix相关jar包，并且feign.hystrix.enabled=true，则调用Feign.Builder=feign.hystrix.HystrixFeign.Builder，否则=feign.Feign.Builder
		// @formatter:off
		Feign.Builder builder = get(context, Feign.Builder.class)
				// required values
				.logger(logger).encoder(get(context, Encoder.class)).decoder(get(context, Decoder.class))
				.contract(get(context, Contract.class));
		// @formatter:on
		 // 继续其余配置，包括超时、Retryer等配置的读取，并放入builder对象
		   // 暂时忽略，等将服务特殊配置的时候再看
		configureFeign(context, builder);

		return builder;
	}

	protected void configureFeign(FeignContext context, Feign.Builder builder) {
		// 读取feign.client开头的properties
		FeignClientProperties properties = this.applicationContext.getBean(FeignClientProperties.class);
		// 设置inheritParentContext属性为true
		FeignClientConfigurer feignClientConfigurer = getOptional(context, FeignClientConfigurer.class);
		setInheritParentContext(feignClientConfigurer.inheritParentConfiguration());

		if (properties != null && inheritParentContext) {
			if (properties.isDefaultToProperties()) {
				// 默认feign.client.defaultToProperties = true 走这个分支
				// 1. 先取子容器的配置类里的配置
				configureUsingConfiguration(context, builder);
				// 2. 用配置文件feign.client.defaultConfig开头的配置文件配置覆盖
				configureUsingProperties(properties.getConfig().get(properties.getDefaultConfig()), builder);
				// 3. 最后用feign.client.config.{contextId}开头的配置文件覆盖
				configureUsingProperties(properties.getConfig().get(this.contextId), builder);
			} else {
				  // 1. 先用配置文件feign.client.defaultConfig开头的配置文件
				configureUsingProperties(properties.getConfig().get(properties.getDefaultConfig()), builder);
				configureUsingProperties(properties.getConfig().get(this.contextId), builder);
				configureUsingConfiguration(context, builder);
			}
		} else {
			configureUsingConfiguration(context, builder);
		}
	}

	protected void configureUsingConfiguration(FeignContext context, Feign.Builder builder) {
		Logger.Level level = getInheritedAwareOptional(context, Logger.Level.class);
		if (level != null) {
			builder.logLevel(level);
		}
		Retryer retryer = getInheritedAwareOptional(context, Retryer.class);
		if (retryer != null) {
			builder.retryer(retryer);
		}
		ErrorDecoder errorDecoder = getInheritedAwareOptional(context, ErrorDecoder.class);
		if (errorDecoder != null) {
			builder.errorDecoder(errorDecoder);
		} else {
			FeignErrorDecoderFactory errorDecoderFactory = getOptional(context, FeignErrorDecoderFactory.class);
			if (errorDecoderFactory != null) {
				ErrorDecoder factoryErrorDecoder = errorDecoderFactory.create(this.type);
				builder.errorDecoder(factoryErrorDecoder);
			}
		}
		Request.Options options = getInheritedAwareOptional(context, Request.Options.class);
		if (options != null) {
			builder.options(options);
		}
		Map<String, RequestInterceptor> requestInterceptors = getInheritedAwareInstances(context,
				RequestInterceptor.class);
		if (requestInterceptors != null) {
			builder.requestInterceptors(requestInterceptors.values());
		}
		QueryMapEncoder queryMapEncoder = getInheritedAwareOptional(context, QueryMapEncoder.class);
		if (queryMapEncoder != null) {
			builder.queryMapEncoder(queryMapEncoder);
		}
		if (this.decode404) {
			builder.decode404();
		}
		ExceptionPropagationPolicy exceptionPropagationPolicy = getInheritedAwareOptional(context,
				ExceptionPropagationPolicy.class);
		if (exceptionPropagationPolicy != null) {
			builder.exceptionPropagationPolicy(exceptionPropagationPolicy);
		}
	}

	protected void configureUsingProperties(FeignClientProperties.FeignClientConfiguration config,
			Feign.Builder builder) {
		if (config == null) {
			return;
		}

		if (config.getLoggerLevel() != null) {
			builder.logLevel(config.getLoggerLevel());
		}

		if (config.getConnectTimeout() != null && config.getReadTimeout() != null) {
			builder.options(new Request.Options(config.getConnectTimeout(), config.getReadTimeout()));
		}

		if (config.getRetryer() != null) {
			Retryer retryer = getOrInstantiate(config.getRetryer());
			builder.retryer(retryer);
		}

		if (config.getErrorDecoder() != null) {
			ErrorDecoder errorDecoder = getOrInstantiate(config.getErrorDecoder());
			builder.errorDecoder(errorDecoder);
		}

		if (config.getRequestInterceptors() != null && !config.getRequestInterceptors().isEmpty()) {
			// this will add request interceptor to builder, not replace existing
			for (Class<RequestInterceptor> bean : config.getRequestInterceptors()) {
				RequestInterceptor interceptor = getOrInstantiate(bean);
				builder.requestInterceptor(interceptor);
			}
		}

		if (config.getDecode404() != null) {
			if (config.getDecode404()) {
				builder.decode404();
			}
		}

		if (Objects.nonNull(config.getEncoder())) {
			builder.encoder(getOrInstantiate(config.getEncoder()));
		}

		if (Objects.nonNull(config.getDecoder())) {
			builder.decoder(getOrInstantiate(config.getDecoder()));
		}

		if (Objects.nonNull(config.getContract())) {
			builder.contract(getOrInstantiate(config.getContract()));
		}

		if (Objects.nonNull(config.getExceptionPropagationPolicy())) {
			builder.exceptionPropagationPolicy(config.getExceptionPropagationPolicy());
		}
	}

	private <T> T getOrInstantiate(Class<T> tClass) {
		try {
			return this.applicationContext.getBean(tClass);
		} catch (NoSuchBeanDefinitionException e) {
			return BeanUtils.instantiateClass(tClass);
		}
	}

	protected <T> T get(FeignContext context, Class<T> type) {
		T instance = context.getInstance(this.contextId, type);
		if (instance == null) {
			throw new IllegalStateException("No bean found of type " + type + " for " + this.contextId);
		}
		return instance;
	}

	protected <T> T getOptional(FeignContext context, Class<T> type) {
		return context.getInstance(this.contextId, type);
	}

	protected <T> T getInheritedAwareOptional(FeignContext context, Class<T> type) {
		if (inheritParentContext) {
			return getOptional(context, type);
		} else {
			return context.getInstanceWithoutAncestors(this.contextId, type);
		}
	}

	protected <T> Map<String, T> getInheritedAwareInstances(FeignContext context, Class<T> type) {
		if (inheritParentContext) {
			return context.getInstances(this.contextId, type);
		} else {
			return context.getInstancesWithoutAncestors(this.contextId, type);
		}
	}

	protected <T> T loadBalance(Feign.Builder builder, FeignContext context, HardCodedTarget<T> target) {
		// 子容器获取feign.Client的实现类，这是之后feign调用流程的主入口，一般实现是LoadBalancerFeignClient
		Client client = getOptional(context, Client.class);
		if (client != null) {
			builder.client(client);
			// 子容器获取Targeter对象，如果引入了Hystrix相关jar包，这里就是HystrixTargeter，否则就是DefaultTargeter
			Targeter targeter = get(context, Targeter.class);
			// 构建代理对象
			return targeter.target(this, builder, context, target);
		}

		throw new IllegalStateException(
				"No Feign Client for loadBalancing defined. Did you forget to include spring-cloud-starter-netflix-ribbon?");
	}

	/**
	 * T 就是@FeignClient修饰的接口的动态代理对象
	 */
	@Override
	public Object getObject() throws Exception {
		return getTarget();
	}

	/**
	 * @param <T> the target type of the Feign client
	 * @return a {@link Feign} client created with the specified data and the
	 *         context information
	 */
	<T> T getTarget() {
		// 获取到FeignContext，相当于有了各种Decoder、Encoder等等
		FeignContext context = this.applicationContext.getBean(FeignContext.class);
		// 利用FeiContext的命名子容器和全局Spring容器（this.applicationContext）里的东西
		// 构建Feign.Builder
		// 链接：https://juejin.cn/post/6878481758539481101
 		Feign.Builder builder = feign(context);

		if (!StringUtils.hasText(this.url)) {
			if (!this.name.startsWith("http")) {
				// 处理url属性，最后是http://服务名/this.path
				this.url = "http://" + this.name;
			} else {
				this.url = this.name;
			}
			this.url += cleanPath();
			// loadBalance方法主要是通过Target.target方法获取最终的代理对象
			return (T) loadBalance(builder, context, new HardCodedTarget<>(this.type, this.name, this.url));
		}
		if (StringUtils.hasText(this.url) && !this.url.startsWith("http")) {
			this.url = "http://" + this.url;
		}
		String url = this.url + cleanPath();
		Client client = getOptional(context, Client.class);
		if (client != null) {
			if (client instanceof LoadBalancerFeignClient) {
				// not load balancing because we have a url,
				// but ribbon is on the classpath, so unwrap
				client = ((LoadBalancerFeignClient) client).getDelegate();
			}
			if (client instanceof FeignBlockingLoadBalancerClient) {
				// not load balancing because we have a url,
				// but Spring Cloud LoadBalancer is on the classpath, so unwrap
				client = ((FeignBlockingLoadBalancerClient) client).getDelegate();
			}
			builder.client(client);
		}
		Targeter targeter = get(context, Targeter.class);
		return (T) targeter.target(this, builder, context, new HardCodedTarget<>(this.type, this.name, url));
	}

	private String cleanPath() {
		String path = this.path.trim();
		if (StringUtils.hasLength(path)) {
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}

	@Override
	public Class<?> getObjectType() {
		return this.type;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	public Class<?> getType() {
		return this.type;
	}

	public void setType(Class<?> type) {
		this.type = type;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getContextId() {
		return this.contextId;
	}

	public void setContextId(String contextId) {
		this.contextId = contextId;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getPath() {
		return this.path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public boolean isDecode404() {
		return this.decode404;
	}

	public void setDecode404(boolean decode404) {
		this.decode404 = decode404;
	}

	public boolean isInheritParentContext() {
		return inheritParentContext;
	}

	public void setInheritParentContext(boolean inheritParentContext) {
		this.inheritParentContext = inheritParentContext;
	}

	public ApplicationContext getApplicationContext() {
		return this.applicationContext;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.applicationContext = context;
	}

	public Class<?> getFallback() {
		return this.fallback;
	}

	public void setFallback(Class<?> fallback) {
		this.fallback = fallback;
	}

	public Class<?> getFallbackFactory() {
		return this.fallbackFactory;
	}

	public void setFallbackFactory(Class<?> fallbackFactory) {
		this.fallbackFactory = fallbackFactory;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FeignClientFactoryBean that = (FeignClientFactoryBean) o;
		return Objects.equals(this.applicationContext, that.applicationContext) && this.decode404 == that.decode404
				&& this.inheritParentContext == that.inheritParentContext
				&& Objects.equals(this.fallback, that.fallback)
				&& Objects.equals(this.fallbackFactory, that.fallbackFactory) && Objects.equals(this.name, that.name)
				&& Objects.equals(this.path, that.path) && Objects.equals(this.type, that.type)
				&& Objects.equals(this.url, that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.applicationContext, this.decode404, this.inheritParentContext, this.fallback,
				this.fallbackFactory, this.name, this.path, this.type, this.url);
	}

	@Override
	public String toString() {
		return new StringBuilder("FeignClientFactoryBean{").append("type=").append(this.type).append(", ")
				.append("name='").append(this.name).append("', ").append("url='").append(this.url).append("', ")
				.append("path='").append(this.path).append("', ").append("decode404=").append(this.decode404)
				.append(", ").append("inheritParentContext=").append(this.inheritParentContext).append(", ")
				.append("applicationContext=").append(this.applicationContext).append(", ").append("fallback=")
				.append(this.fallback).append(", ").append("fallbackFactory=").append(this.fallbackFactory).append("}")
				.toString();
	}

}
