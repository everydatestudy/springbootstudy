/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.hystrix;

import com.netflix.hystrix.util.InternMap;

/**
 * A group name for a {@link HystrixCommand}. This is used for grouping together
 * commands such as for reporting, alerting, dashboards or team/library
 * ownership.
 * <p>
 * By default this will be used to define the {@link HystrixThreadPoolKey}
 * unless a separate one is defined.
 * <p>
 * This interface is intended to work natively with Enums so that implementing
 * code can have an enum with the owners that implements this interface.
 */
public interface HystrixCommandGroupKey extends HystrixKey {
	// 接口内部类
	class Factory {
		private Factory() {
		}

		// used to intern instances so we don't keep re-creating them millions of times
		// for the same key
		// 用于intern缓存HystrixCommandGroupDefault实例
		// 这样我们就不会为了同一个键重复创建它们数百万次，提高效率
		// InternMap它就是一个Map，内部持有ConcurrentHashMap用作缓存使用~
		// 这样：每一个String类型的key，调用interned()方法后就会被缓存进Map里~~~
		// 注意：这个只是私有方法，供内部方法使用
		private static final InternMap<String, HystrixCommandGroupDefault> intern = new InternMap<String, HystrixCommandGroupDefault>(
				new InternMap.ValueConstructor<String, HystrixCommandGroupDefault>() {
					@Override
					public HystrixCommandGroupDefault create(String key) {
						return new HystrixCommandGroupDefault(key);
					}
				});

		/**	// 根据一个字符串的key，得到一个HystrixCommandGroupKey实例
		 * Retrieve (or create) an interned HystrixCommandGroup instance for a given
		 * name.
		 *
		 * @param name command group name
		 * @return HystrixCommandGroup instance that is interned (cached) so a given
		 *         name will always retrieve the same instance.
		 */
		public static HystrixCommandGroupKey asKey(String name) {
			return intern.interned(name);
		}
		// 私有，私有静态内部类实现HystrixCommandGroupKey 接口~~~
		// 注意：该类私有，外部并不能访问和构造
		private static class HystrixCommandGroupDefault extends HystrixKey.HystrixKeyDefault
				implements HystrixCommandGroupKey {
			public HystrixCommandGroupDefault(String name) {
				super(name);
			}
		}

		/* package-private */ static int getGroupCount() {
			return intern.size();
		}
	}
}