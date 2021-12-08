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
		// 创建InvocationHandler的实例很简单，直接调用FeignInvocationHandler的构造器完成。但是很有必要细读它的invoke方法，它是对方法完成正调度的核心，是所有方法调用的入口。
		// target：HardCodedTarget（FeignClientFactoryBean#getTarget里new的一个对象，[type=SpecificationStockClient,
		// name=trade-service, url=http://trade-service]）
		// dispatch：目标方法和实际方法处理器的一个mapping关系，通过变量名可以看出FeignInvocationHandler并不实际执行代理逻辑，而是根据method分发给不同的MethodHandler处理
		// 通过method实例可以直接找到MethodHandler，最后通过MethodHandler的invoke方法，实际执行代理逻辑

//	作者：阿越
//	链接：https://juejin.cn/post/6878861101455376398
//	来源：稀土掘金
//	著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。

		@Override
		public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
			return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
		}
	}
}
