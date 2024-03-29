/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.lang.Nullable;

/**
 *
 * spring当中用来描述bean的一个接口 A BeanDefinition describes a bean instance, which has
 * property values, constructor argument values, and further information
 * supplied by concrete implementations.
 *
 * <p>
 * This is just a minimal interface: The main intention is to allow a
 * {@link BeanFactoryPostProcessor} such as
 * {@link PropertyPlaceholderConfigurer} to introspect and modify property
 * values and other bean metadata.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 19.03.2004
 * @see ConfigurableListableBeanFactory#getBeanDefinition
 * @see org.springframework.beans.factory.support.RootBeanDefinition
 * @see org.springframework.beans.factory.support.ChildBeanDefinition
 */
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {

	/**
	 * // 我们可以看到，默认只提供 sington 和 prototype 两种，   很多读者可能知道还有 request, session,
	 * globalSession, application, websocket 这几种，  不过，它们属于基于 web 的扩展。
	 * 标准单例作用域的作用域标识符：“singleton”。 Scope identifier for the standard singleton
	 * scope: "singleton".
	 * <p>
	 * Note that extended bean factories might support further scopes.
	 * 
	 * @see #setScope
	 */
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

	/**
	 * Scope identifier for the standard prototype scope: "prototype".
	 * <p>
	 * Note that extended bean factories might support further scopes.
	 * 
	 * @see #setScope
	 */
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;

	/** //应用程序重要组成部分
	 * Role hint indicating that a {@code BeanDefinition} is a major part of the
	 * application. Typically corresponds to a user-defined bean.
	 *
	 */
	int ROLE_APPLICATION = 0;

	/**
	 * Role hint indicating that a {@code BeanDefinition} is a supporting part of
	 * some larger configuration, typically an outer
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 * {@code SUPPORT} beans are considered important enough to be aware of when
	 * looking more closely at a particular
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition}, but
	 * not when looking at the overall configuration of an application. ROLE_SUPPORT
	 * =1实际上就是说，我这个Bean是用户的，是从配置文件中过来的
	 */
	int ROLE_SUPPORT = 1;

	/**  指内部工作的基础构造  实际上是说我这Bean是Spring自己的，和你用户没有一毛钱关系
	 * Role hint indicating that a {@code BeanDefinition} is providing an entirely
	 * background role and has no relevance to the end-user. This hint is used when
	 * registering beans that are completely part of the internal workings of a
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 * 就是我这Bean是Spring自己的，和你用户没有一毛钱关系
	 */
	int ROLE_INFRASTRUCTURE = 2;

	// Modifiable attributes

	/**parent definition（若存在父类的话，就设置进去）
	 * Set the name of the parent definition of this bean definition, if any.
	 */
	void setParentName(@Nullable String parentName);

	/**
	 * Return the name of the parent definition of this bean definition, if any.
	 */
	@Nullable
	String getParentName();

	/** 指定Class类型。需要注意的是该类型还有可能被改变在Bean post-processing阶段
	// 若是getFactoryBeanName  getFactoryMethodName这种情况下会改变
	 * Specify the bean class name of this bean definition.
	 * <p>
	 * The class name can be modified during bean factory post-processing, typically
	 * replacing the original class name with a parsed variant of it.
	 * 
	 * @see #setParentName
	 * @see #setFactoryBeanName
	 * @see #setFactoryMethodName
	 */
	void setBeanClassName(@Nullable String beanClassName);

	/**
	 * Return the current bean class name of this bean definition.
	 * <p>
	 * Note that this does not have to be the actual class name used at runtime, in
	 * case of a child definition overriding/inheriting the class name from its
	 * parent. Also, this may just be the class that a factory method is called on,
	 * or it may even be empty in case of a factory bean reference that a method is
	 * called on. Hence, do <i>not</i> consider this to be the definitive bean type
	 * at runtime but rather only use it for parsing purposes at the individual bean
	 * definition level.
	 * 
	 * @see #getParentName()
	 * @see #getFactoryBeanName()
	 * @see #getFactoryMethodName()
	 */
	@Nullable
	String getBeanClassName();

	/**SCOPE_SINGLETON或者SCOPE_PROTOTYPE两种
	 * Override the target scope of this bean, specifying a new scope name.
	 *  //ROLE_APPLICATION
    //ROLE_SUPPORT
    //ROLE_INFRASTRUCTURE
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	void setScope(@Nullable String scope);

	/**
	 * Return the name of the current target scope for this bean, or {@code null} if
	 * not known yet.
	 */
	@Nullable
	String getScope();

	/** @Lazy 是否需要懒加载（默认都是立马加载的）
	 * Set whether this bean should be lazily initialized.
	 * <p>
	 * If {@code false}, the bean will get instantiated on startup by bean factories
	 * that perform eager initialization of singletons.
	 */
	void setLazyInit(boolean lazyInit);

	/**
	 * Return whether this bean should be lazily initialized, i.e. not eagerly
	 * instantiated on startup. Only applicable to a singleton bean.
	 */
	boolean isLazyInit();

	/** 此Bean定义需要依赖的Bean（显然可以有多个）
	 * Set the names of the beans that this bean depends on being initialized. The
	 * bean factory will guarantee that these beans get initialized first.
	 */
	void setDependsOn(@Nullable String... dependsOn);

	/** 设置该 Bean 依赖的所有的 Bean，注意，这里的依赖不是指属性依赖(如 @Autowire 标记的)，
  // 是 depends-on="" 属性设置的值。
	 * Return the bean names that this bean depends on.
	 */
	@Nullable
	String[] getDependsOn();

	/**	// 这个Bean是否允许被自动注入到别的地方去（默认都是被允许的）
	// 注意：此标志只影响按类型装配，不影响byName的注入方式的~~~~
	 * Set whether this bean is a candidate for getting autowired into some other
	 * bean.
	 * <p>
	 * Note that this flag is designed to only affect type-based autowiring. It does
	 * not affect explicit references by name, which will get resolved even if the
	 * specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 */
	void setAutowireCandidate(boolean autowireCandidate);

	/**
	 * Return whether this bean is a candidate for getting autowired into some other
	 * bean.
	 */
	boolean isAutowireCandidate();

	/**
	 * Set whether this bean is a primary autowire candidate.
	 * <p>
	 * If this value is {@code true} for exactly one bean among multiple matching
	 * candidates, it will serve as a tie-breaker.
	 */
	void setPrimary(boolean primary);

	/** 是否是首选的  @Primary
	 * Return whether this bean is a primary autowire candidate.
	 */
	boolean isPrimary();

	/**
	 * Specify the factory bean to use, if any. This the name of the bean to call
	 * the specified factory method on.
	 * 
	 * @see #setFactoryMethodName
	 */
	void setFactoryBeanName(@Nullable String factoryBeanName);

	/**
	 * Return the factory bean name, if any.
	 */
	@Nullable
	String getFactoryBeanName();

	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified. The method
	 * will be invoked on the specified factory bean, if any, or otherwise as a
	 * static method on the local bean class.
	 * 
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	void setFactoryMethodName(@Nullable String factoryMethodName);

	/**
	 * Return a factory method, if any.
	 */
	@Nullable
	String getFactoryMethodName();

	/** 
	 * 获取此Bean的构造函数参数值们  ConstructorArgumentValues：持有构造函数们的 
	// 绝大多数情况下是空对象 new ConstructorArgumentValues出来的一个对象
	// 当我们Scan实例化Bean的时候，可能用到它的非空构造，这里就会有对应的值了，然后后面就会再依赖注入了
		————————————————
		版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
		原文链接：https://blog.csdn.net/f641385712/article/details/88683596
	 * Return the constructor argument values for this bean.
	 * <p>
	 * The returned instance can be modified during bean factory post-processing.
	 * 
	 * @return the ConstructorArgumentValues object (never {@code null})
	 */
	ConstructorArgumentValues getConstructorArgumentValues();

	/**
	 * Return if there are constructor argument values defined for this bean.
	 * 
	 * @since 5.0.2
	 */
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	/**
	 * Return the property values to be applied to a new instance of the bean.
	 * <p>
	 * The returned instance can be modified during bean factory post-processing.
	 * 
	 * @return the MutablePropertyValues object (never {@code null})
	 */
	MutablePropertyValues getPropertyValues();

	/**
	 * Return if there are property values values defined for this bean.
	 * 
	 * @since 5.0.2
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	// Read-only attributes

	/**
	 * Return whether this a <b>Singleton</b>, with a single, shared instance
	 * returned on all calls.
	 * 
	 * @see #SCOPE_SINGLETON
	 */
	boolean isSingleton();

	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance returned
	 * for each call.
	 * 
	 * @since 3.0
	 * @see #SCOPE_PROTOTYPE
	 */
	boolean isPrototype();

	/**
	 * Return whether this bean is "abstract", that is, not meant to be
	 * instantiated.
	 */
	boolean isAbstract();

	/**
	 * Get the role hint for this {@code BeanDefinition}. The role hint provides the
	 * frameworks as well as tools with an indication of the role and importance of
	 * a particular {@code BeanDefinition}.
	 * 
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	int getRole();

	/**
	 * Return a human-readable description of this bean definition.
	 */
	@Nullable
	String getDescription();

	/**
	 * Return a description of the resource that this bean definition came from (for
	 * the purpose of showing context in case of errors).
	 */
	@Nullable
	String getResourceDescription();

	/**
	 * Return the originating BeanDefinition, or {@code null} if none. Allows for
	 * retrieving the decorated bean definition, if any.
	 * <p>
	 * Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 */
	@Nullable
	BeanDefinition getOriginatingBeanDefinition();

}
