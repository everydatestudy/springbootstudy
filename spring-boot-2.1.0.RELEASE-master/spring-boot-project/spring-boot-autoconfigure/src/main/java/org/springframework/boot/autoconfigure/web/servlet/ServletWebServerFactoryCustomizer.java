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

package org.springframework.boot.autoconfigure.web.servlet;

import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.core.Ordered;

/**
 * 设置属性
 * 添加SessionConfiguringInitializer这个Servlet初始化器,
 * SessionConfiguringInitializer初始化器的作用是基于ServerProperties的内部静态类Session设置Servlet中session和cookie的配置。
 * 添加InitParameterConfiguringServletContextInitializer初始化器,
 * InitParameterConfiguringServletContextInitializer初始化器的作用是基于ServerProperties的contextParameters配置设置到ServletContext的init
 * param中
 * 
 * 那么有个问题,这些属性是何时注入的呢?
 * 在调用org.springframework.boot.context.embedded.EmbeddedWebApplicationContext#getEmbeddedServletContainerFactory,
 * 获得EmbeddedServletContainerFactory时,
 * 会调用org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#applyBeanPostProcessorsBeforeInitialization,
 * 进行扩展点的执行,其中EmbeddedServletContainerCustomizerBeanPostProcessor#postProcessBeforeInitialization执行时,会触发
 * 加载 EmbeddedServletContainerCustomizer 类型的bean,
 * ServerProperties实现了EmbeddedServletContainerCustomizer接口,因此会在此时被加载.
 * 同样在加载过程中,会调用AbstractAutowireCapableBeanFactory#applyBeanPostProcessorsBeforeInitialization.
 * 因此会触发ConfigurationPropertiesBindingPostProcessor#postProcessBeforeInitialization的调用,由于该类有@ConfigurationProperties
 * 注解,因此会最终调用org.springframework.boot.bind.PropertiesConfigurationFactory#bindPropertiesToTarget,其后就开始真正的属性绑定,调用链如下:
 * 
 * {@link WebServerFactoryCustomizer} to apply {@link ServerProperties} to
 * servlet web servers.
 *
 * @author Brian Clozel
 * @author Stephane Nicoll
 * @author Olivier Lamy
 * @author Yunkun Huang
 * @since 2.0.0
 */
public class ServletWebServerFactoryCustomizer
		implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>, Ordered {

	private final ServerProperties serverProperties;

	public ServletWebServerFactoryCustomizer(ServerProperties serverProperties) {
		this.serverProperties = serverProperties;
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void customize(ConfigurableServletWebServerFactory factory) {
		PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
		map.from(this.serverProperties::getPort).to(factory::setPort);
		map.from(this.serverProperties::getAddress).to(factory::setAddress);
		map.from(this.serverProperties.getServlet()::getContextPath).to(factory::setContextPath);
		map.from(this.serverProperties.getServlet()::getApplicationDisplayName).to(factory::setDisplayName);
		map.from(this.serverProperties.getServlet()::getSession).to(factory::setSession);
		map.from(this.serverProperties::getSsl).to(factory::setSsl);
		map.from(this.serverProperties.getServlet()::getJsp).to(factory::setJsp);
		map.from(this.serverProperties::getCompression).to(factory::setCompression);
		map.from(this.serverProperties::getHttp2).to(factory::setHttp2);
		map.from(this.serverProperties::getServerHeader).to(factory::setServerHeader);
		// 添加SessionConfiguringInitializer这个Servlet初始化器
		// SessionConfiguringInitializer初始化器的作用是基于ServerProperties的内部静态类Session设置Servlet中session和cookie的配置
		map.from(this.serverProperties.getServlet()::getContextParameters).to(factory::setInitParameters);
	}

}
