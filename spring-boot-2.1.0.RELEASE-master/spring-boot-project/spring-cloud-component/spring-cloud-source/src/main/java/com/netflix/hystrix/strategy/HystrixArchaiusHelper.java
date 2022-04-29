/**
 * Copyright 2016 Netflix, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix.strategy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.netflix.hystrix.strategy.properties.HystrixDynamicProperties;

/**
 * @ExcludeFromJavadoc
 * @author agentgt
 */
class HystrixArchaiusHelper {

	/**
	 * // 懒加载 // 对于那些在类路径中有Archaius **但选择不使用它的类** // 要保持类装入最少。所以按需加载 //
	 * ConfigurationManager是Archaius的核心API To keep class loading minimal for those
	 * that have archaius in the classpath but choose not to use it.
	 * 
	 * @ExcludeFromJavadoc
	 * @author agent
	 */
	private static class LazyHolder {
		// Method方法：此方法用于加载xxx.proerties资源，下面附带解释一把
		private final static Method loadCascadedPropertiesFromResources;
		private final static String CONFIG_MANAGER_CLASS = "com.netflix.config.ConfigurationManager";

		static {
			Method load = null;
			try {
				Class<?> configManager = Class.forName(CONFIG_MANAGER_CLASS);

				load = configManager.getMethod("loadCascadedPropertiesFromResources", String.class);
			} catch (Exception e) {
			}

			loadCascadedPropertiesFromResources = load;
		}
	}

	/**
	 * @ExcludeFromJavadoc
	 */
	static boolean isArchaiusV1Available() {
		return LazyHolder.loadCascadedPropertiesFromResources != null;
	}

	// 使用这个Method，加载内容到Configuration里来
	static void loadCascadedPropertiesFromResources(String name) {
		if (isArchaiusV1Available()) {
			try {
				LazyHolder.loadCascadedPropertiesFromResources.invoke(null, name);
			} catch (IllegalAccessException e) {
			} catch (IllegalArgumentException e) {
			} catch (InvocationTargetException e) {
			}
		}
	}

//    表面上看，仅仅是文件名不同，效果却大不一样。而实际上背后是它的加载原理，这在前面Archaius正解里有详细描述，这里简单复习一下：
//
//    在Archaius中，仅仅是PolledConfigurationSource的配置元，才会有动态性
//    URLConfigurationSource是该接口的一个实现类，所以关联到的属性文件均具有动态性。而它默认关联的文件名是：config.properties 
//    在Spring Boot环境其实你可以把它改为application.properties也是可行的~
//    ConfigurationManager管理的是一个ConcurrentCompositeConfiguration组合配置，而这个组合配置就含有DynamicURLConfiguration（ + 系统属性）
//    综上可知，这就是为何默认config.properties文件的属性是具有动态性的原因。而hystrix-plugins.properties它是被ConfigurationManager#loadCascadedPropertiesFromResources()加载的，所以仅仅只是属性生效而已，并不具有动态性。
//
//    实际生产中，名hystrix-plugins.properties的属性文件并不是给你配置其它属性的，从命名中你就知道：它给你配置插件用，也就是SPI使用的，后面会再次提到它。
//
//     再次强调：生产环境，请勿在名为hystrix-plugins.properties的文件里配置业务属性，避免不必要的干扰

	/**
	 * 这就是这个Method的作用：用于加载属性文件内容到Configuration里。
	 * 同时会提供一个方法，用于创建一个HystrixDynamicProperties实例（当然是基于archaius的）：
	 * 
	 * @ExcludeFromJavadoc
	 */
	static HystrixDynamicProperties createArchaiusDynamicProperties() {
		if (isArchaiusV1Available()) {
			loadCascadedPropertiesFromResources("hystrix-plugins");
			try {
				// HystrixDynamicPropertiesArchaius的全类名
//            	该方法会创建一个HystrixDynamicPropertiesArchaius实例，并且加载名为hystrix-plugins.properties、hystrix-plugins-环境名.properties的属性文件到全局配置里。
//
//            	综上，确切的说：Hystrix是通过HystrixDynamicPropertiesArchaius完成和Archaius的整合的。
				Class<?> defaultProperties = Class.forName(
						"com.netflix.hystrix.strategy.properties.archaius" + ".HystrixDynamicPropertiesArchaius");
				return (HystrixDynamicProperties) defaultProperties.newInstance();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			} catch (InstantiationException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		// Fallback to System properties.
		return null;
	}

}
