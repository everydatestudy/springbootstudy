/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	@Nullable
	private volatile List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}


	/**
	 * 
     * 去容器中获取到所有的切面信息保存到缓存中
      
	 * Look for AspectJ-annotated aspect beans in the current bean factory,
	 * and return to a list of Spring AOP Advisors representing them.
	 * <p>Creates a Spring Advisor for each AspectJ advice method.
	 * @return the list of {@link org.springframework.aop.Advisor} beans
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		List<String> aspectNames = this.aspectBeanNames;
		 //缓存字段aspectNames没有值 注意实例化第一个单实例bean的时候就会触发解析切面
		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					  //用于保存所有解析出来的Advisors集合对象
					List<Advisor> advisors = new ArrayList<>();
					 //用于保存切面的名称的集合
					aspectNames = new ArrayList<>();
					 /**
                     * AOP功能中在这里传入的是Object对象，代表去容器中获取到所有的组件的名称，然后再
                     * 进行遍历，这个过程是十分的消耗性能的，所以说Spring会再这里加入了保存切面信息的缓存。
                     * 但是事务功能不一样，事务模块的功能是直接去容器中获取Advisor类型的，选择范围小，且不消耗性能。
                     * 所以Spring在事务模块中没有加入缓存来保存我们的事务相关的advisor
                     */
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);
					 // // 判断当前bean是否为子类定制的需要过滤的bean
					for (String beanName : beanNames) {
						if (!isEligibleBean(beanName)) {
							continue;
						}
						// We must be careful not to instantiate beans eagerly as in this case they
						// would be cached by the Spring container but would not have been weaved.
						//通过beanName去容器中获取到对应class对象
						Class<?> beanType = this.beanFactory.getType(beanName);
						if (beanType == null) {
							continue;
						}
						 //根据class对象判断是不是切面 @Aspect
						if (this.advisorFactory.isAspect(beanType)) {
							//是切面类
                            //加入到缓存中
							aspectNames.add(beanName);
							// 对于使用了@Aspect注解标注的bean，将其封装为一个AspectMetadata类型。
	                        // 这里在封装的过程中会解析@Aspect注解上的参数指定的切面类型，如perthis
	                        // 和pertarget等。这些被解析的注解都会被封装到其perClausePointcut属性中
							AspectMetadata amd = new AspectMetadata(beanType, beanName);
							// 判断@Aspect注解中标注的是否为singleton类型，默认的切面类都是singleton类型
							if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
								 // 将BeanFactory和当前bean封装为MetadataAwareAspectInstanceFactory对象，这里会再次将@Aspect注解中的参数都封装
	                            // 为一个AspectMetadata，并且保存在该factory中
								MetadataAwareAspectInstanceFactory factory =new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
								 // 通过封装的bean获取其Advice，如@Before，@After等等，并且将这些
	                            //TODO Advice都解析并且封装为一个个的Advisor
								List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
								// 如果切面类是singleton类型，则将解析得到的Advisor进行缓存，
	                            // 否则将当前的factory进行缓存，以便再次获取时可以通过factory直接获取
								if (this.beanFactory.isSingleton(beanName)) {
									this.advisorsCache.put(beanName, classAdvisors);
								}
								else {
									this.aspectFactoryCache.put(beanName, factory);
								}
								advisors.addAll(classAdvisors);
							}
							else {
								// Per target or per this.
								if (this.beanFactory.isSingleton(beanName)) {
									throw new IllegalArgumentException("Bean with name '" + beanName +
											"' is a singleton, but aspect instantiation model is not singleton");
								}
								MetadataAwareAspectInstanceFactory factory =
										new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
								this.aspectFactoryCache.put(beanName, factory);
								advisors.addAll(this.advisorFactory.getAdvisors(factory));
							}
						}
					}
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}
	    // 2.如果aspectNames不为null，则代表已经解析过了，则无需再次解析
	    // 2.1 如果aspectNames是空列表，则返回一个空列表。空列表也是解析过的，只要不是null都是解析过的。
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		  // 通过所有的aspectNames在缓存中获取切面对应的Advisor，这里如果是单例的，则直接从advisorsCache
	    // 获取，如果是多例类型的，则通过MetadataAwareAspectInstanceFactory立即生成一个
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
	            // 根据上面的解析，可以知道advisorsCache存的是已经解析好的增强器，直接添加到结果即可
				advisors.addAll(cachedAdvisors);
			}
			else {
	            // 如果不存在于advisorsCache缓存，则代表存在于aspectFactoryCache中，
	            // 从aspectFactoryCache中拿到缓存的factory，然后解析出增强器，添加到结果中
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
