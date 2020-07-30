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

package org.springframework.boot.autoconfigure.condition;

import java.util.Map;

import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.reactive.context.ConfigurableReactiveWebEnvironment;
import org.springframework.boot.web.reactive.context.ReactiveWebApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@link Condition} that checks for the presence or absence of
 * {@link WebApplicationContext}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @see ConditionalOnWebApplication
 * @see ConditionalOnNotWebApplication
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class OnWebApplicationCondition extends FilteringSpringBootCondition {

	private static final String SERVLET_WEB_APPLICATION_CLASS = "org.springframework.web.context.support.GenericWebApplicationContext";

	private static final String REACTIVE_WEB_APPLICATION_CLASS = "org.springframework.web.reactive.HandlerResult";

	@Override
	protected ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		for (int i = 0; i < outcomes.length; i++) {
			String autoConfigurationClass = autoConfigurationClasses[i];
			if (autoConfigurationClass != null) {
				outcomes[i] = getOutcome(autoConfigurationMetadata
						.get(autoConfigurationClass, "ConditionalOnWebApplication"));
			}
		}
		return outcomes;
	}

	private ConditionOutcome getOutcome(String type) {
		if (type == null) {
			return null;
		}
		ConditionMessage.Builder message = ConditionMessage
				.forCondition(ConditionalOnWebApplication.class);
		if (ConditionalOnWebApplication.Type.SERVLET.name().equals(type)) {
			if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS,
					getBeanClassLoader())) {
				return ConditionOutcome.noMatch(
						message.didNotFind("servlet web application classes").atAll());
			}
		}
		if (ConditionalOnWebApplication.Type.REACTIVE.name().equals(type)) {
			if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS,
					getBeanClassLoader())) {
				return ConditionOutcome.noMatch(
						message.didNotFind("reactive web application classes").atAll());
			}
		}
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS,
				getBeanClassLoader())
				&& !ClassUtils.isPresent(REACTIVE_WEB_APPLICATION_CLASS,
						getBeanClassLoader())) {
			return ConditionOutcome.noMatch(message
					.didNotFind("reactive or servlet web application classes").atAll());
		}
		return null;
	}
//	其中getMatchOutcome是判断逻辑,做了5件事:
//
//	    检查是否被@ConditionalOnWebApplication 注解
//
//	    通过调用isWebApplication判断是否是Web环境,
//	        判断GenericWebApplicationContext是否在类路径中,如果不存在,则返回不匹配
//	        容器里是否有名为session的scope,如果存在,则返回匹配
//	        Environment是否为StandardServletEnvironment,如果是的话,则返回匹配
//	        当前ResourceLoader是否为WebApplicationContext,如果是,则返回匹配
//	        其他情况,返回不匹配.
//
//	    如果有@ConditionalOnWebApplication 注解,但是不是WebApplication环境,则返回不匹配
//	    如果没有被@ConditionalOnWebApplication 注解,但是是WebApplication环境,则返回不匹配
//	    如果被@ConditionalOnWebApplication 注解,并且是WebApplication环境,则返回不匹配
//	————————————————
//	版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/qq_26000415/article/details/78917684
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		// 配置类是否标注有@ConditionalOnWebApplication注解
		boolean required = metadata
				.isAnnotated(ConditionalOnWebApplication.class.getName());
		// 调用isWebApplication方法返回匹配结果
		ConditionOutcome outcome = isWebApplication(context, metadata, required);
		// 若有标注@ConditionalOnWebApplication但不符合条件，则返回不匹配
		if (required && !outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		// 若没有标注@ConditionalOnWebApplication但符合条件，则返回不匹配
		if (!required && outcome.isMatch()) {
			return ConditionOutcome.noMatch(outcome.getConditionMessage());
		}
		// 这里返回匹配的情况，TODO 不过有个疑问：如果没有标注@ConditionalOnWebApplication注解，又不符合条件的话，也会执行到这里，返回匹配？
		return ConditionOutcome.match(outcome.getConditionMessage());
	}

	private ConditionOutcome isWebApplication(ConditionContext context,
			AnnotatedTypeMetadata metadata, boolean required) {
		// 调用deduceType方法判断是哪种类型，其中有SERVLET，REACTIVE和ANY类型，其中ANY表示了SERVLET或REACTIVE类型
		switch (deduceType(metadata)) {
		// SERVLET类型
		case SERVLET:
			return isServletWebApplication(context);
		// REACTIVE类型
		case REACTIVE:
			return isReactiveWebApplication(context);
		default:
			return isAnyWebApplication(context, required);
		}
	}

	private ConditionOutcome isAnyWebApplication(ConditionContext context,
			boolean required) {
		ConditionMessage.Builder message = ConditionMessage.forCondition(
				ConditionalOnWebApplication.class, required ? "(required)" : "");
		ConditionOutcome servletOutcome = isServletWebApplication(context);
		if (servletOutcome.isMatch() && required) {
			return new ConditionOutcome(servletOutcome.isMatch(),
					message.because(servletOutcome.getMessage()));
		}
		ConditionOutcome reactiveOutcome = isReactiveWebApplication(context);
		if (reactiveOutcome.isMatch() && required) {
			return new ConditionOutcome(reactiveOutcome.isMatch(),
					message.because(reactiveOutcome.getMessage()));
		}
		return new ConditionOutcome(servletOutcome.isMatch() || reactiveOutcome.isMatch(),
				message.because(servletOutcome.getMessage()).append("and")
						.append(reactiveOutcome.getMessage()));
	}

	private ConditionOutcome isServletWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		// 若classpath中不存在org.springframework.web.context.support.GenericWebApplicationContext.class，则返回不匹配
		if (!ClassNameFilter.isPresent(SERVLET_WEB_APPLICATION_CLASS,
				context.getClassLoader())) {
			return ConditionOutcome.noMatch(
					message.didNotFind("servlet web application classes").atAll());
		}
		// 若classpath中存在org.springframework.web.context.support.GenericWebApplicationContext.class，那么又分为以下几种匹配的情况
		// session
		if (context.getBeanFactory() != null) {
			String[] scopes = context.getBeanFactory().getRegisteredScopeNames();
			if (ObjectUtils.containsElement(scopes, "session")) {
				return ConditionOutcome.match(message.foundExactly("'session' scope"));
			}
		}
		// ConfigurableWebEnvironment
		if (context.getEnvironment() instanceof ConfigurableWebEnvironment) {
			return ConditionOutcome
					.match(message.foundExactly("ConfigurableWebEnvironment"));
		}
		// WebApplicationContext
		if (context.getResourceLoader() instanceof WebApplicationContext) {
			return ConditionOutcome.match(message.foundExactly("WebApplicationContext"));
		}
		// 若以上三种都不匹配的话，则说明不是一个servlet web application
		return ConditionOutcome.noMatch(message.because("not a servlet web application"));
	}

	private ConditionOutcome isReactiveWebApplication(ConditionContext context) {
		ConditionMessage.Builder message = ConditionMessage.forCondition("");
		// 若classpath中不存在org.springframework.web.reactive.HandlerResult.class
		if (!ClassNameFilter.isPresent(REACTIVE_WEB_APPLICATION_CLASS,
				context.getClassLoader())) {
			return ConditionOutcome.noMatch(
					message.didNotFind("reactive web application classes").atAll());
		}
		if (context.getEnvironment() instanceof ConfigurableReactiveWebEnvironment) {
			return ConditionOutcome
					.match(message.foundExactly("ConfigurableReactiveWebEnvironment"));
		}
		if (context.getResourceLoader() instanceof ReactiveWebApplicationContext) {
			return ConditionOutcome
					.match(message.foundExactly("ReactiveWebApplicationContext"));
		}
		return ConditionOutcome
				.noMatch(message.because("not a reactive web application"));
	}

	private Type deduceType(AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata
				.getAnnotationAttributes(ConditionalOnWebApplication.class.getName());
		if (attributes != null) {
			return (Type) attributes.get("type");
		}
		return Type.ANY;
	}

}
