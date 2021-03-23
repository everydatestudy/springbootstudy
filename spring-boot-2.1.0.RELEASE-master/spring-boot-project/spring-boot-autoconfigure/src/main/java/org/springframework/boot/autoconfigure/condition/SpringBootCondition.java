/*
 * Copyright 2012-2017 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 
 * 注解								处理类						处理逻辑															实例
@Conditional						OnBeanCondition				当给定的类型、类名、注解、昵称在beanFactory中存在时返回true.各类型间是or的关系	@ConditionalOnBean
(CacheManager.class)
@ConditionalOnSingleCandidate		OnBeanCondition				当给定类型的bean存在并且指定为Primary的给定类型存在时,返回true				@ConditionalOnSingleCandidate
(DataSource.class)
@ConditionalOnMissingBean			OnBeanCondition				当给定的类型、类名、注解、昵称在beanFactory中不存在时返回true.各类型间是or的关系	@ConditionalOnMissingBean
@ConditionalOnClass					OnClassCondition			当给定的类型、类名在类路径上存在时返回true,各类型间是and的关系					@ConditionalOnClass({ EnableAspectJAutoProxy.class, Aspect.class, Advice.class })
@ ConditionalOnMissingClass			OnClassCondition			当给定的类名在类路径上不存在时返回true,各类型间是and的关系					@ConditionalOnMissingClass(“org.thymeleaf.templatemode.TemplateMode”)
@ConditionalOnCloudPlatform			OnCloudPlatformCondition	当所配置的CloudPlatform为激活时返回true								@ConditionalOnCloudPlatform(CloudPlatform.CLOUD_FOUNDRY)
@ConditionalOnExpression			OnExpressionCondition		如果该表达式返回true则代表匹配,否则返回不匹配								@ConditionalOnExpression(“true”)
@ConditionalOnJava					OnJavaCondition				运行时的java版本号是否包含给定的版本号.如果包含,返回匹配,否则,返回不匹配			@ConditionalOnJava(ConditionalOnJava.JavaVersion.EIGHT)
@ConditionalOnJndi					OnJndiCondition				给定的jndi的Location 必须存在一个.否则,返回不匹配							@ConditionalOnJndi({ “java:comp/TransactionManager”})
@ConditionalOnNotWebApplication		OnWebApplicationCondition	不在web环境时返回匹配												@ConditionalOnNotWebApplication
@ConditionalOnWebApplication		OnWebApplicationCondition	不在web环境时返回匹配												@ConditionalOnWebApplication
@ConditionalOnProperty				OnPropertyCondition			配置的属性存在时匹配													@ConditionalOnProperty(prefix = “spring.aop”, name = “auto”, havingValue = “true”, matchIfMissing = true)
@ConditionalOnResource				OnResourceCondition			指定的资源必须存在,否则返回不匹配											@ConditionalOnResource(resources = “classpath:META-INF/build-info.properties”)
————————————————
版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/qq_26000415/article/details/79008745
 * 
 * 我们知道了SpringBootCondition抽象了所有其具体实现类OnXXXCondition的共有逻辑--condition评估信息打印，
 * 最重要的是封装了一个模板方法getMatchOutcome(context,
 * metadata)，留给各个OnXXXCondition具体子类去覆盖实现属于自己的判断逻辑，
 * 然后再返回相应的匹配结果给SpringBootCondition用于日志打印。
 * Base of all {@link Condition} implementations used with Spring Boot. Provides
 * sensible logging to help the user diagnose what classes are loaded.
 *
 * @author Phillip Webb
 * @author Greg Turnquist
 */
public abstract class SpringBootCondition implements Condition {

	private final Log logger = LogFactory.getLog(getClass());

	@Override
	public final boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		// 得到metadata的类名或方法名
		String classOrMethodName = getClassOrMethodName(metadata);
		try {
			// 判断每个配置类的每个条件注解@ConditionalOnXXX是否满足条件，然后记录到ConditionOutcome结果中
			// 注意getMatchOutcome是一个抽象模板方法，交给OnXXXCondition子类去实现
			ConditionOutcome outcome = getMatchOutcome(context, metadata);
			// 打印condition评估的日志，哪些条件注解@ConditionalOnXXX是满足条件的，哪些是不满足条件的，这些日志都打印出来
			logOutcome(classOrMethodName, outcome);
			// 除了打印日志外，这些是否匹配的信息还要记录到ConditionEvaluationReport中
			recordEvaluation(context, classOrMethodName, outcome);
			// 最后返回@ConditionalOnXXX是否满足条件
			return outcome.isMatch();
		} catch (NoClassDefFoundError ex) {
			throw new IllegalStateException("Could not evaluate condition on " + classOrMethodName + " due to "
					+ ex.getMessage() + " not " + "found. Make sure your own configuration does not rely on "
					+ "that class. This can also happen if you are "
					+ "@ComponentScanning a springframework package (e.g. if you "
					+ "put a @ComponentScan in the default package by mistake)", ex);
		} catch (RuntimeException ex) {
			throw new IllegalStateException("Error processing condition on " + getName(metadata), ex);
		}
	}

	private String getName(AnnotatedTypeMetadata metadata) {
		if (metadata instanceof AnnotationMetadata) {
			return ((AnnotationMetadata) metadata).getClassName();
		}
		if (metadata instanceof MethodMetadata) {
			MethodMetadata methodMetadata = (MethodMetadata) metadata;
			return methodMetadata.getDeclaringClassName() + "." + methodMetadata.getMethodName();
		}
		return metadata.toString();
	}

	private static String getClassOrMethodName(AnnotatedTypeMetadata metadata) {
		// // 1. 如果metadata 是ClassMetadata的实例,则返回类名,否则返回全类名#方法名
		if (metadata instanceof ClassMetadata) {
			ClassMetadata classMetadata = (ClassMetadata) metadata;
			return classMetadata.getClassName();
		}
		// 如果metadata是方法，则返回方法名，形式为"类名#方法名"
		MethodMetadata methodMetadata = (MethodMetadata) metadata;
		return methodMetadata.getDeclaringClassName() + "#" + methodMetadata.getMethodName();
	}

	protected final void logOutcome(String classOrMethodName, ConditionOutcome outcome) {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace(getLogMessage(classOrMethodName, outcome));
		}
	}

	private StringBuilder getLogMessage(String classOrMethodName, ConditionOutcome outcome) {
		StringBuilder message = new StringBuilder();
		message.append("Condition ");
		message.append(ClassUtils.getShortName(getClass()));
		message.append(" on ");
		message.append(classOrMethodName);
		message.append(outcome.isMatch() ? " matched" : " did not match");
		if (StringUtils.hasLength(outcome.getMessage())) {
			message.append(" due to ");
			message.append(outcome.getMessage());
		}
		return message;
	}

	private void recordEvaluation(ConditionContext context, String classOrMethodName, ConditionOutcome outcome) {
		if (context.getBeanFactory() != null) {
			ConditionEvaluationReport.get(context.getBeanFactory()).recordConditionEvaluation(classOrMethodName, this,
					outcome);
		}
	}

	/**
	 * Determine the outcome of the match along with suitable log output.
	 * 
	 * @param context  the condition context
	 * @param metadata the annotation metadata
	 * @return the condition outcome
	 */
	public abstract ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata);

	/**
	 * Return true if any of the specified conditions match.
	 * 
	 * @param context    the context
	 * @param metadata   the annotation meta-data
	 * @param conditions conditions to test
	 * @return {@code true} if any condition matches.
	 */
	protected final boolean anyMatches(ConditionContext context, AnnotatedTypeMetadata metadata,
			Condition... conditions) {
		for (Condition condition : conditions) {
			if (matches(context, metadata, condition)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return true if any of the specified condition matches.
	 * 
	 * @param context   the context
	 * @param metadata  the annotation meta-data
	 * @param condition condition to test
	 * @return {@code true} if the condition matches.
	 */
	protected final boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata, Condition condition) {
		if (condition instanceof SpringBootCondition) {
			return ((SpringBootCondition) condition).getMatchOutcome(context, metadata).isMatch();
		}
		return condition.matches(context, metadata);
	}

}
