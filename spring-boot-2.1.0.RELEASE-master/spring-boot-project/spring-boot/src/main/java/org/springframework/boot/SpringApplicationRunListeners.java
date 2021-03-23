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

package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ReflectionUtils;

/**
 * A collection of {@link SpringApplicationRunListener}.
 *
 * @author Phillip Webb
 */
class SpringApplicationRunListeners {

	private final Log log;

	private final List<SpringApplicationRunListener> listeners;

	SpringApplicationRunListeners(Log log,
			Collection<? extends SpringApplicationRunListener> listeners) {
		this.log = log;
		this.listeners = new ArrayList<>(listeners);
	}

	public void starting() {
		// 遍历listeners集合，这里实质取出的就是刚才从spring.factories中取出的SPI实现类EventPublishingRunListener
		// 而 {@link EventPublishingRunListener}对象承担了SpringBoot启动过程中负责广播不同的生命周期事件
		for (SpringApplicationRunListener listener : this.listeners) {
			// 调用EventPublishingRunListener的starting方法来广播ApplicationStartingEvent事件
			listener.starting();
		}
	}
//	org.springframework.boot.context.config.ConfigFileApplicationListener, 
//	org.springframework.boot.context.config.AnsiOutputApplicationListener, 
//	org.springframework.boot.logging.LoggingApplicationListener, 
//	org.springframework.boot.logging.ClasspathLoggingApplicationListener, 
//	org.springframework.boot.autoconfigure.BackgroundPreinitializer, 
//	org.springframework.boot.context.config.DelegatingApplicationListener, 
//	org.springframework.boot.context.FileEncodingApplicationListener
//	————————————————
//	版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/qq_26000415/article/details/78914944
	public void environmentPrepared(ConfigurableEnvironment environment) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.environmentPrepared(environment);
		}
	}

	public void contextPrepared(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextPrepared(context);
		}
	}
	//调用EventPublishingRunListener#contextLoaded,
//	
//	2件事
//
//    遍历application 中的ApplicationListener,如果listener 实现了ApplicationContextAware的话,就调用其setApplicationContext进行赋值.当前的如下:
//
//    org.springframework.boot.context.config.ConfigFileApplicationListener,
//    org.springframework.boot.context.config.AnsiOutputApplicationListener, 
//    org.springframework.boot.logging.LoggingApplicationListener, 
//    org.springframework.boot.logging.ClasspathLoggingApplicationListener, 
//    org.springframework.boot.autoconfigure.BackgroundPreinitializer, 
//    org.springframework.boot.context.config.DelegatingApplicationListener, 
//    org.springframework.boot.builder.ParentContextCloserApplicationListener,  
//    org.springframework.boot.ClearCachesApplicationListener, 
//    org.springframework.boot.context.FileEncodingApplicationListener,
//    org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener
//  
//
//    只有ParentContextCloserApplicationListener实现了ApplicationContextAware接口.
//    发送ApplicationPreparedEvent 事件.由前可知,会依次调用listener的onApplicationEvent事件,
 
	
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.contextLoaded(context);
		}
	}

	public void started(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.started(context);
		}
	}

	public void running(ConfigurableApplicationContext context) {
		for (SpringApplicationRunListener listener : this.listeners) {
			listener.running(context);
		}
	}

	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		for (SpringApplicationRunListener listener : this.listeners) {
			callFailedListener(listener, context, exception);
		}
	}

	private void callFailedListener(SpringApplicationRunListener listener,
			ConfigurableApplicationContext context, Throwable exception) {
		try {
			listener.failed(context, exception);
		}
		catch (Throwable ex) {
			if (exception == null) {
				ReflectionUtils.rethrowRuntimeException(ex);
			}
			if (this.log.isDebugEnabled()) {
				this.log.error("Error handling failed", ex);
			}
			else {
				String message = ex.getMessage();
				message = (message != null) ? message : "no error message";
				this.log.warn("Error handling failed (" + message + ")");
			}
		}
	}

}
