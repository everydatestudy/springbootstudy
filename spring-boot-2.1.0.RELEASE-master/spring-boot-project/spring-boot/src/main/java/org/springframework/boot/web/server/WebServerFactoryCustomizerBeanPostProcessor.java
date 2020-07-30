/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.util.LambdaSafe;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;

/**
 * {@link BeanPostProcessor} that applies all {@link WebServerFactoryCustomizer} beans
 * from the bean factory to {@link WebServerFactory} beans.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 2.0.0
 */
public class WebServerFactoryCustomizerBeanPostProcessor
		implements BeanPostProcessor, BeanFactoryAware {

	private ListableBeanFactory beanFactory;

	private List<WebServerFactoryCustomizer<?>> customizers;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		Assert.isInstanceOf(ListableBeanFactory.class, beanFactory,
				"WebServerCustomizerBeanPostProcessor can only be used "
						+ "with a ListableBeanFactory");
		this.beanFactory = (ListableBeanFactory) beanFactory;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		if (bean instanceof WebServerFactory) {
			postProcessBeforeInitialization((WebServerFactory) bean);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@SuppressWarnings("unchecked")
	private void postProcessBeforeInitialization(WebServerFactory webServerFactory) {
	    // 在Spring容器中寻找ConfigurableEmbeddedServletContainer类型的bean，
		//SpringBoot内部的3种内置Servlet容器工厂都实现了这个接口，该接口的作用就是进行Servlet容器的配置
	    // 比如添加Servlet初始化器addInitializers、添加错误页addErrorPages、设置session超时时间setSessionTimeout、设置端口setPort等等
//		通过beanFactory 获得EmbeddedServletContainerCustomizer类型的bean,对于当前场景,有5个.如下:
//
//			[org.springframework.boot.autoconfigure.websocket.servlet.TomcatWebSocketServletWebServerCustomizer@40147317, 
//			 org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryCustomizer@210f0cc1, 
//			 org.springframework.boot.autoconfigure.web.servlet.TomcatServletWebServerFactoryCustomizer@19542407, 
//			 org.springframework.boot.autoconfigure.web.embedded.TomcatWebServerFactoryCustomizer@6f95cd51, 
//			 org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration$LocaleCharsetMappingsCustomizer@c7a977f]
 
		LambdaSafe
				.callbacks(WebServerFactoryCustomizer.class, getCustomizers(),
						webServerFactory)
				.withLogger(WebServerFactoryCustomizerBeanPostProcessor.class)
				.invoke((customizer) -> customizer.customize(webServerFactory));
	}

	private Collection<WebServerFactoryCustomizer<?>> getCustomizers() {
		if (this.customizers == null) {
			// Look up does not include the parent context
			this.customizers = new ArrayList<>(getWebServerFactoryCustomizerBeans());
			this.customizers.sort(AnnotationAwareOrderComparator.INSTANCE);
			this.customizers = Collections.unmodifiableList(this.customizers);
		}
		return this.customizers;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Collection<WebServerFactoryCustomizer<?>> getWebServerFactoryCustomizerBeans() {
		return (Collection) this.beanFactory
				.getBeansOfType(WebServerFactoryCustomizer.class, false, false).values();
	}

}
