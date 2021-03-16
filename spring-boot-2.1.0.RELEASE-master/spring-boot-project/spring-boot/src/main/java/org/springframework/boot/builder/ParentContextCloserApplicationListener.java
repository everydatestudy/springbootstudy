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

package org.springframework.boot.builder;

import java.lang.ref.WeakReference;

import org.springframework.beans.BeansException;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer.ParentContextAvailableEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.util.ObjectUtils;

/**在一个应用上下文的双亲应用上下文关闭时关闭该应用上下文。这个监听器监听应用上下文刷新事件并从中取出
 * 应用上下文，然后监听关闭事件在应用上下文的层级结构中往下层传播该事件。
 * Listener that closes the application context if its parent is closed. It listens for
 * refresh events and grabs the current context from there, and then listens for closed
 * events and propagates it down the hierarchy.
 *
 * @author Dave Syer
 * @author Eric Bottard
 */
public class ParentContextCloserApplicationListener
		implements ApplicationListener<ParentContextAvailableEvent>,
		ApplicationContextAware, Ordered {

	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	private ApplicationContext context;

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void setApplicationContext(ApplicationContext context) throws BeansException {
		this.context = context;
	}
	// 当一个应用上下文中发出ParentContextAvailableEvent 事件时，表明该应用上下文被设定了双亲
	// 应用上下文并且自己已经可用(refreshed),现在在该应用上下文的双亲上下文中登记一个事件监听器，
	// 用于监听双亲上下文的关闭事件，从而关闭该子应用上下文。
	@Override
	public void onApplicationEvent(ParentContextAvailableEvent event) {
		maybeInstallListenerInParent(event.getApplicationContext());
	}

	private void maybeInstallListenerInParent(ConfigurableApplicationContext child) {
		if (child == this.context
				&& child.getParent() instanceof ConfigurableApplicationContext) {
			ConfigurableApplicationContext parent = (ConfigurableApplicationContext) child
					.getParent();
			parent.addApplicationListener(createContextCloserListener(child));
		}
	}

	/**
	 * Subclasses may override to create their own subclass of ContextCloserListener. This
	 * still enforces the use of a weak reference.
	 * @param child the child context
	 * @return the {@link ContextCloserListener} to use
	 */
	protected ContextCloserListener createContextCloserListener(
			ConfigurableApplicationContext child) {
		return new ContextCloserListener(child);
	}

	/** * 定义一个应用事件监听器，关注事件ContextClosedEvent，当该事件发生时，关闭指定的孩子应用上下文。
	 * 对于指定的孩子应用上下文使用WeakReference弱引用保持。
	 * 事件ContextClosedEvent发生时，如果弱引用中保持的孩子应用上下文还在(意思就是弱引用尚未释放),
	 * 并且孩子上下文处于活跃状态，则关闭它。
	 * 注意 ： 在关闭之前会再次检查当前上下文和孩子上下文之间的父子关系，仅在父子关系成立的情况下才真正
	 * 做相应的关闭动作。
	 *  
	 * 该静态内部类仅被ParentContextCloserApplicationListener用于向双亲应用上下文登记一个关闭孩子
	 * 引用上下文的ApplicationListener

	 * {@link ApplicationListener} to close the context.
	 */
	protected static class ContextCloserListener
			implements ApplicationListener<ContextClosedEvent> {

		private WeakReference<ConfigurableApplicationContext> childContext;

		public ContextCloserListener(ConfigurableApplicationContext childContext) {
			this.childContext = new WeakReference<>(childContext);
		}

		@Override
		public void onApplicationEvent(ContextClosedEvent event) {
			ConfigurableApplicationContext context = this.childContext.get();
			if ((context != null)
					&& (event.getApplicationContext() == context.getParent())
					&& context.isActive()) {
				context.close();
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (obj instanceof ContextCloserListener) {
				ContextCloserListener other = (ContextCloserListener) obj;
				return ObjectUtils.nullSafeEquals(this.childContext.get(),
						other.childContext.get());
			}
			return super.equals(obj);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHashCode(this.childContext.get());
		}

	}

}
