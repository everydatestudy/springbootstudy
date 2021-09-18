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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.Lifecycle;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.Phased;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link LifecycleProcessor} strategy.
 *主要自动启动/停止BeanFactory中的所有实现 Lifecycle 的单例Bean对象
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @since 3.0
 */
public class DefaultLifecycleProcessor implements LifecycleProcessor, BeanFactoryAware {

	private final Log logger = LogFactory.getLog(getClass());
	//指定为任何阶段(具有相同'phase'值的 SmartLifecycle Bean组)的关闭分配的最大 时间(以毫秒为单位)
	private volatile long timeoutPerShutdownPhase = 30000;

	private volatile boolean running;

	@Nullable
	private volatile ConfigurableListableBeanFactory beanFactory;


	/**
	 * Specify the maximum time allotted in milliseconds for the shutdown of
	 * any phase (group of SmartLifecycle beans with the same 'phase' value).
	 * <p>The default value is 30 seconds.
	 */
	public void setTimeoutPerShutdownPhase(long timeoutPerShutdownPhase) {
		this.timeoutPerShutdownPhase = timeoutPerShutdownPhase;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
			throw new IllegalArgumentException(
					"DefaultLifecycleProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
		}
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	private ConfigurableListableBeanFactory getBeanFactory() {
		ConfigurableListableBeanFactory beanFactory = this.beanFactory;
		Assert.state(beanFactory != null, "No BeanFactory available");
		return beanFactory;
	}


	// Lifecycle implementation

	/**
	 * Start all registered beans that implement {@link Lifecycle} and are <i>not</i>
	 * already running. Any bean that implements {@link SmartLifecycle} will be
	 * started within its 'phase', and all phases will be ordered from lowest to
	 * highest value. All beans that do not implement {@link SmartLifecycle} will be
	 * started in the default phase 0. A bean declared as a dependency of another bean
	 * will be started before the dependent bean regardless of the declared phase.
	 * <p>启动所有实现生命周期且尚未运行的已注册bean。任何实现SmartLifecycle的bean都将在它的“阶段”中启动，
	 * 所有阶段都将按从低到高的顺序排列。所有没有实现SmartLifecycle的bean将在默认的阶段0启动。
	 * 声明为另一个bean的依赖项的bean将在该依赖bean之前启动，而不管声明的阶段是什么。</p>
	 */

	@Override
	public void start() {
		startBeans(false);
		this.running = true;
	}

	/**
	 * Stop all registered beans that implement {@link Lifecycle} and <i>are</i>
	 * currently running. Any bean that implements {@link SmartLifecycle} will be
	 * stopped within its 'phase', and all phases will be ordered from highest to
	 * lowest value. All beans that do not implement {@link SmartLifecycle} will be
	 * stopped in the default phase 0. A bean declared as dependent on another bean
	 * will be stopped before the dependency bean regardless of the declared phase.
	 */
	@Override
	public void stop() {
		stopBeans();
		this.running = false;
	}

	@Override
	public void onRefresh() {
		startBeans(true);
		this.running = true;
	}

	@Override
	public void onClose() {
		stopBeans();
		this.running = false;
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}


	// Internal helpers
	//autoStartupOnly 是否只包含自动启动的标记。autoStartupOnly等于true时，bean必须实现SmartLifecycle接口，并且isAutoStartup()返回true，才会被放入LifecycleGroup
	private void startBeans(boolean autoStartupOnly) {
		//检索所有适用的生命周期Bean：所有已经创建的单例Bean，以及所有 SmartLifeCycle Bean(即使它们被标记未延迟初始化)
		Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
		Map<Integer, LifecycleGroup> phases = new HashMap<>();
		// 遍历 lifecycleBeans
		lifecycleBeans.forEach((beanName, bean) -> {
			//SmartLifecycle : 生命周期接口的扩展,用于那些需要在 ApplicationContext 刷新 和/或 特定顺序关闭时 启动的对象。
			//如果不是 autoStartupOnly || (bean 是 SmartLifecycle 的实例) && bean指定在包含ApplicationContext的刷新时由
			//————————————————
			//版权声明：本文为CSDN博主「czb彬」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
			//原文链接：https://blog.csdn.net/qq_30321211/article/details/108326767
			if (!autoStartupOnly || (bean instanceof SmartLifecycle && ((SmartLifecycle) bean).isAutoStartup())) {
				//获取 bean 的生命周期阶段(相位值)
				int phase = getPhase(bean);
				//获取 phase 对应的 LifecycleGroup
				LifecycleGroup group = phases.get(phase);
				if (group == null) {
					// 新建一个 LifecycleGroup 的实例
					group = new LifecycleGroup(phase, this.timeoutPerShutdownPhase, lifecycleBeans, autoStartupOnly);
					phases.put(phase, group);
				}
				group.add(beanName, bean);
			}
		});
		if (!phases.isEmpty()) {
			List<Integer> keys = new ArrayList<>(phases.keySet());
			Collections.sort(keys);
			for (Integer key : keys) {
				phases.get(key).start();
			}
		}
	}

	/**<p>作为 lifecycleBeans 的一部分启动 dependency 对应 Lifecycle Bean对象，确保它所依赖的任何bean都首先启动。</p>
	 * Start the specified bean as part of the given set of Lifecycle beans,
	 * making sure that any beans that it depends on are started first.
	 * @param lifecycleBeans a Map with bean name as key and Lifecycle instance as value
	 * @param beanName the name of the bean to start
	 */
	private void doStart(Map<String, ? extends Lifecycle> lifecycleBeans, String beanName, boolean autoStartupOnly) {
		//移除在 lifecycleBeans 中的 beanName 映射关系，因为每个 Lifecycle 对象只能执行一次
		Lifecycle bean = lifecycleBeans.remove(beanName);
		//如果 bean 不为 null && bean不是当前对象
		if (bean != null && bean != this) {
			String[] dependenciesForBean = getBeanFactory().getDependenciesForBean(beanName);
			for (String dependency : dependenciesForBean) {
				//递归本方法，作为lifecycleBeans的一部分启动dependency对应Lifecycle Bean对象，确保它所依赖的任何bean都首先启动。
				doStart(lifecycleBeans, dependency, autoStartupOnly);
			}
			//如果 bean 不是运行中 && ( 不是只包含自动启动标记 || bean不是SmartLifecycle实例 || bean是在包含ApplicationContext
			// 		的刷新时由容器自动启动)
			if (!bean.isRunning() &&
					(!autoStartupOnly || !(bean instanceof SmartLifecycle) || ((SmartLifecycle) bean).isAutoStartup())) {
				if (logger.isTraceEnabled()) {
					logger.trace("Starting bean '" + beanName + "' of type [" + bean.getClass().getName() + "]");
				}
				try {
					bean.start();
				}
				catch (Throwable ex) {
					throw new ApplicationContextException("Failed to start bean '" + beanName + "'", ex);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Successfully started bean '" + beanName + "'");
				}
			}
		}
	}

	private void stopBeans() {
		//检索所有适用的生命周期Bean：所有已经创建的单例Bean，以及所有 SmartLifeCycle Bean(即使它们被标记未延迟初始化)
		Map<String, Lifecycle> lifecycleBeans = getLifecycleBeans();
		//各 phases 所对应的 LifecycleGroup Map
		Map<Integer, LifecycleGroup> phases = new HashMap<>();
		// 遍历 lifecycleBeans
		lifecycleBeans.forEach((beanName, bean) -> {
			//获取 bean 的生命周期阶段(相位值)
			int shutdownPhase = getPhase(bean);
			//获取 phase 对应的 LifecycleGroup
			LifecycleGroup group = phases.get(shutdownPhase);
			if (group == null) {
				// 新建一个 LifecycleGroup 的实例
				group = new LifecycleGroup(shutdownPhase, this.timeoutPerShutdownPhase, lifecycleBeans, false);
				phases.put(shutdownPhase, group);
			}
			//将beanName，bean绑定到 group 中
			group.add(beanName, bean);
		});
		// 如果 phases 不是空集
		if (!phases.isEmpty()) {
			//存放生命周期阶段(相位值)
			List<Integer> keys = new ArrayList<>(phases.keySet());
			//排序，相位值越大越靠前
			keys.sort(Collections.reverseOrder());
			for (Integer key : keys) {
				phases.get(key).stop();
			}
		}
	}

	/**
	 * Stop the specified bean as part of the given set of Lifecycle beans,
	 * making sure that any beans that depends on it are stopped first.
	 * @param lifecycleBeans a Map with bean name as key and Lifecycle instance as value
	 * @param beanName the name of the bean to stop
	 */
	private void doStop(Map<String, ? extends Lifecycle> lifecycleBeans, final String beanName,
			final CountDownLatch latch, final Set<String> countDownBeanNames) {
		//移除在 lifecycleBeans 中的 beanName 映射关系，因为每个 Lifecycle 对象只能执行一次
		Lifecycle bean = lifecycleBeans.remove(beanName);
		if (bean != null) {
			//获取 beanName 所依赖的所有bean的名称
			String[] dependentBeans = getBeanFactory().getDependentBeans(beanName);
			for (String dependentBean : dependentBeans) {
				//递归本方法，作为lifecycleBeans的一部分启动dependency对应Lifecycle Bean对象，确保它所依赖的任何bean都首先启动。
				doStop(lifecycleBeans, dependentBean, latch, countDownBeanNames);
			}
			try {
				//如果 bean 是运行中
				if (bean.isRunning()) {
					//如果bean是SmartLifecycle实例
					if (bean instanceof SmartLifecycle) {
						if (logger.isTraceEnabled()) {
							logger.trace("Asking bean '" + beanName + "' of type [" +
									bean.getClass().getName() + "] to stop");
						}
						countDownBeanNames.add(beanName);
						//指示当前正在运行的生命周期组件必须停止。
						((SmartLifecycle) bean).stop(() -> {
							latch.countDown();
							countDownBeanNames.remove(beanName);
							if (logger.isDebugEnabled()) {
								logger.debug("Bean '" + beanName + "' completed its stop procedure");
							}
						});
					}
					else {
						if (logger.isTraceEnabled()) {
							logger.trace("Stopping bean '" + beanName + "' of type [" +
									bean.getClass().getName() + "]");
						}
						//通常以同步方式停止此组件。以使该组件在返回方法后完全停止。
						bean.stop();
						if (logger.isDebugEnabled()) {
							logger.debug("Successfully stopped bean '" + beanName + "'");
						}
					}
				}
				else if (bean instanceof SmartLifecycle) {
					// Don't wait for beans that aren't running...
					latch.countDown();
				}
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Failed to stop bean '" + beanName + "'", ex);
				}
			}
		}
	}


	// overridable hooks

	/**
	 * Retrieve all applicable Lifecycle beans: all singletons that have already been created,
	 * as well as all SmartLifecycle beans (even if they are marked as lazy-init).
	 * @return the Map of applicable beans, with bean names as keys and bean instances as values
	 */
	protected Map<String, Lifecycle> getLifecycleBeans() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		Map<String, Lifecycle> beans = new LinkedHashMap<>();
		//获取与 Lifecycle（包括子类）匹配的bean名称，如果是FactoryBeans会根据beanDefinition 或getObjectType的值判断
		String[] beanNames = beanFactory.getBeanNamesForType(Lifecycle.class, false, false);
		for (String beanName : beanNames) {
			//去除开头的'&'字符，返回剩余的字符串作为转换后的Bean名称【可能是全类名】
			String beanNameToRegister = BeanFactoryUtils.transformedBeanName(beanName);
			//确定具有 beanNameToRegister 的Bean是否为FactoryBean
			boolean isFactoryBean = beanFactory.isFactoryBean(beanNameToRegister);
			//如果是 FactoryBean 就对 beanName 加上'&'作为前缀 作为要检查的BeanName
			String beanNameToCheck = (isFactoryBean ? BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
			//如果 beanFactory 包含 beanNameToRegister 的单例实例 &&
			// 		(不是 FactoryBean || beanNameToCheck 在 beanFactory 的 Bean 对象 与 Lifecycle 类型匹配
			// 			|| beanNameToCheck 在 beanFactory 的 Bean 对象 与 SmartLifecycle 类型匹配)
			if ((beanFactory.containsSingleton(beanNameToRegister) &&
					(!isFactoryBean || matchesBeanType(Lifecycle.class, beanNameToCheck, beanFactory))) ||
					matchesBeanType(SmartLifecycle.class, beanNameToCheck, beanFactory)) {
				//获取在BeanFactory中 beanNameToCheck 对应的Bean对象
				Object bean = beanFactory.getBean(beanNameToCheck);
				if (bean != this && bean instanceof Lifecycle) {
					beans.put(beanNameToRegister, (Lifecycle) bean);
				}
			}
		}
		return beans;
	}
	/**
	 * 确定 beanName 在 beanFactory 的 Bean 对象 是否与 targetType 类型匹配
	 * @param targetType 目标类型
	 * @param beanName bean名
	 * @param beanFactory bean工厂
	 * @return 是否匹配
	 */
	private boolean matchesBeanType(Class<?> targetType, String beanName, BeanFactory beanFactory) {
		// 从 beanFactory 中获取 beanName 的bean类型
		Class<?> beanType = beanFactory.getType(beanName);
		// 如果 beanType 不为 null && beanType 不是 targetType 的子类或实现
		return (beanType != null && targetType.isAssignableFrom(beanType));
	}

	/**
	 * Determine the lifecycle phase of the given bean.
	 * <p>The default implementation checks for the {@link Phased} interface, using
	 * a default of 0 otherwise. Can be overridden to apply other/further policies.
	 * @param bean the bean to introspect
	 * @return the phase (an integer value)
	 * @see Phased#getPhase()
	 * @see SmartLifecycle
	 */
	protected int getPhase(Lifecycle bean) {
		return (bean instanceof Phased ? ((Phased) bean).getPhase() : 0);
	}


	/**用于维护一组 生命周期Bean 的Helper 类，应该根据它们'phase'值(或默认值0)一起启动和
	 * 停止这些Bean
	 * Helper class for maintaining a group of Lifecycle beans that should be started
	 * and stopped together based on their 'phase' value (or the default value of 0).
	 */
	private class LifecycleGroup {
		/**
		 *  生命周期阶段(相位值)
		 */
		private final int phase;
		/**
		 * 指定为任何阶段(具有相同'phase'值的 SmartLifecycle Bean组) 的关闭分配的最大 时间(以毫秒为单位)
		 */
		private final long timeout;
		/**
		 * beanFactory中 所有的Lifecycle Bean 对象映射，key为 Bean名，value为 Lifecycle Bean 对象
		 */
		private final Map<String, ? extends Lifecycle> lifecycleBeans;
		/**
		 * 是否只包含自动启动的标记
		 */
		private final boolean autoStartupOnly;
		/**
		 * 与 phase 对应的 LifecycleGroupMember 对象集合
		 */
		private final List<LifecycleGroupMember> members = new ArrayList<>();
		/**
		 * 该 LifecycleGroup 所含有的 SmartLifecycle 数
		 */
		private int smartMemberCount;
		/**
		 * 新建一个 LifecycleGroup 对象
		 * @param phase  生命周期阶段(相位值)
		 * @param timeout 指定为任何阶段(具有相同'phase'值的 SmartLifecycle Bean组)
		 *                      的关闭分配的最大 时间(以毫秒为单位)
		 * @param lifecycleBeans 所有适用的生命周期Bean
		 * @param autoStartupOnly 是否只包含自动启动的标记
		 */

		public LifecycleGroup(
				int phase, long timeout, Map<String, ? extends Lifecycle> lifecycleBeans, boolean autoStartupOnly) {

			this.phase = phase;
			this.timeout = timeout;
			this.lifecycleBeans = lifecycleBeans;
			this.autoStartupOnly = autoStartupOnly;
		}

		public void add(String name, Lifecycle bean) {
			//使用name,bean 构建出一个 LifecycleGroupMember 对象。然后添加到 members 中
			this.members.add(new LifecycleGroupMember(name, bean));
			if (bean instanceof SmartLifecycle) {
				this.smartMemberCount++;
			}
		}

		public void start() {
			if (this.members.isEmpty()) {
				return;
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Starting beans in phase " + this.phase);
			}
			//对 members 进行排序，生命周期阶段(相位值)越小越靠前
			Collections.sort(this.members);
			for (LifecycleGroupMember member : this.members) {
				doStart(this.lifecycleBeans, member.name, this.autoStartupOnly);
			}
		}

		public void stop() {
			if (this.members.isEmpty()) {
				return;
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Stopping beans in phase " + this.phase);
			}
			//对 members 进行排序，生命周期阶段(相位值)越大越靠前
			this.members.sort(Collections.reverseOrder());
			//新建一个计数锁，每处理一个SmartLifecycle Bean对象就-1
			CountDownLatch latch = new CountDownLatch(this.smartMemberCount);
			//Collections.synchronizedSet：用于返回一个同步的(线程安全的)有序set由指定的有序set支持。
			//存放即将处理的 SmartLifecycle Bean对象的Bean名
			Set<String> countDownBeanNames = Collections.synchronizedSet(new LinkedHashSet<>());
			//存放 beanFactory中 所有的Lifecycle Bean 对象的Bean名
			Set<String> lifecycleBeanNames = new HashSet<>(this.lifecycleBeans.keySet());
			for (LifecycleGroupMember member : this.members) {
				if (lifecycleBeanNames.contains(member.name)) {
					//停止lifecycleBeans 集合的一部分的dependency 对应 Lifecycle Bean对象，确保依赖于它的任何bean都首先停止。
					doStop(this.lifecycleBeans, member.name, latch, countDownBeanNames);
				}
				//如果 member.bean 是 SmartLifecycle 实例 && lifecycleBeanNames没有该 member.name 的 Bean 对象【这意味这该member.bean已经被处理了】
				else if (member.bean instanceof SmartLifecycle) {
					// Already removed: must have been a dependent bean from another phase
					latch.countDown();
				}
			}
			try {
				//等待所有 SmartLifecycle Bean 对象 都 完成停止工作。如果超过 timeout将不再等待
				latch.await(this.timeout, TimeUnit.MILLISECONDS);
				//如果当前 latch不为0 && countDownBeanNames不为空集 && 当前日志级别为info
				if (latch.getCount() > 0 && !countDownBeanNames.isEmpty() && logger.isInfoEnabled()) {
					logger.info("Failed to shut down " + countDownBeanNames.size() + " bean" +
							(countDownBeanNames.size() > 1 ? "s" : "") + " with phase value " +
							this.phase + " within timeout of " + this.timeout + ": " + countDownBeanNames);
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}


	/**将可比较的接口调整到生命周期阶段模型上。
	 * Adapts the Comparable interface onto the lifecycle phase model.
	 */
	private class LifecycleGroupMember implements Comparable<LifecycleGroupMember> {

		private final String name;
		// name 对应的 Lifecycle Bean 对象
		private final Lifecycle bean;

		LifecycleGroupMember(String name, Lifecycle bean) {
			this.name = name;
			this.bean = bean;
		}

		@Override
		public int compareTo(LifecycleGroupMember other) {
			int thisPhase = getPhase(this.bean);
			int otherPhase = getPhase(other.bean);
			return Integer.compare(thisPhase, otherPhase);
		}
	}

}
