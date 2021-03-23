/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.context.annotation;

/**
 * A {@link Condition} that offers more fine-grained control when used with
 * {@code @Configuration}. Allows certain {@link Condition}s to adapt when they
 * match based on the configuration phase. For example, a condition that checks
 * if a bean has already been registered might choose to only be evaluated
 * during the {@link ConfigurationPhase#REGISTER_BEAN REGISTER_BEAN}
 * {@link ConfigurationPhase}.
 *
 *
 * ConfigurationCondition：判断带有@Configuration注解的配置Class是否满足condition条件
 * 
 * 所以这2个阶段的枚举的含义也就很明确了：
 * 
 * PARSE_CONFIGURATION：判断是否在解析配置类的时候就进行 condition 条件判断，若失败，则该配置类不注册
 * 
 * REGISTER_BEAN：所有配置类均注册为Bean
 * 
 * 默认情况下，带有@Configuration注解的类 为full conguration ， phase 为
 * PARSE_CONFIGURATION，其余方法带有@Bean，或者注解元数据中
 * 带有 @Component，@ComponentScan，@Import，@ImportResource的 为 lite 模式，phase
 * 为REGISTER_BEAN
 *
 * 我的理解：
 * ConfigurationPhase的作用就是根据条件来判断是否加载这个配置类，OnBeanCondition（此注解的功能就是判断是否存在某个bean，如果存在，则不注入标注的bean或者类）之所以返回REGISTER_BEAN，是因为需要无论如何都要加载这个配置类（如果是PARSE_CONFIGURATION，则有可能不加载），配置类中的bean的注入需要再根据bean的注入条件来判断。
 * 
 * 再者，@onBeanCondition的设计是想如果matches方法返回true，则注入bean，如果返回false则不注入bean。如果枚举值选择了PARSE_CONFIGURATION，matches返回false整个配置将不被加载了，和设计有冲突。
 * 
 * 
 * 真·总结：
 * 实验证明，ConfigurationPhase的作用并不是根据条件来判断是否加载这个配置类，实际ConfigurationPhase控制的是过滤的时机，是在创建Configuration类的时候过滤还是在创建bean的时候过滤（也可用条件注解的生效阶段来描述）。
 *
 *
 * @author Phillip Webb
 * @since 4.0
 * @see Configuration
 */
public interface ConfigurationCondition extends Condition {

	/**
	 * Return the {@link ConfigurationPhase} in which the condition should be
	 * evaluated.
	 */
	ConfigurationPhase getConfigurationPhase();

	/**
	 * The various configuration phases where the condition could be evaluated.
	 */
	enum ConfigurationPhase {

		/**
		 * The {@link Condition} should be evaluated as a {@code @Configuration} class
		 * is being parsed.
		 * <p>
		 * If the condition does not match at this point, the {@code @Configuration}
		 * class will not be added.
		 */
	    // 当前的Condition在配置类解析时执行.如果该condition返回false,则该配置类不会被解析
		PARSE_CONFIGURATION,

		/**
		 * The {@link Condition} should be evaluated when adding a regular (non
		 * {@code @Configuration}) bean. The condition will not prevent
		 * {@code @Configuration} classes from being added.
		 * <p>
		 * At the time that the condition is evaluated, all {@code @Configuration}s will
		 * have been parsed.
		 */
		REGISTER_BEAN
	}

}
