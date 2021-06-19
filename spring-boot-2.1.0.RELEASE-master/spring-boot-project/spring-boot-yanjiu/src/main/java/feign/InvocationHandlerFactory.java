/**
 * Copyright 2012-2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Controls reflective method dispatch.
 */
public interface InvocationHandlerFactory {
	// Dispatcher：每个方法对应的MethodHandler -> SynchronousMethodHandler实例
	// 创建出来的是一个FeignInvocationHandler实例，实现了InvocationHandler接口
	InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch);

	/**
	 * Like
	 * {@link InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])},
	 * except for a single method.
	 */
	interface MethodHandler {

		Object invoke(Object[] argv) throws Throwable;
	}
	// 很简单：调用FeignInvocationHandler构造器完成实例的创建
	static final class Default implements InvocationHandlerFactory {
		//创建InvocationHandler的实例很简单，直接调用FeignInvocationHandler的构造器完成。但是很有必要细读它的invoke方法，它是对方法完成正调度的核心，是所有方法调用的入口。
		@Override
		public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
			return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
		}
	}
}
