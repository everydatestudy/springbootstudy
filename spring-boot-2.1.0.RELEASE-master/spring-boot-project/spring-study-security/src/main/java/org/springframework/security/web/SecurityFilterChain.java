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
package org.springframework.security.web;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

//判断某个请求是否匹配该安全过滤器链 – boolean matches(HttpServletRequest request)
//获取该安全过滤器链所对应的安全过滤器 – List<Filter> getFilters()
//这组安全过滤器会最终被应用到所匹配的请求上
/**
 * Defines a filter chain which is capable of being matched against an
 * {@code HttpServletRequest}. in order to decide whether it applies to that
 * request.
 * <p>
 * Used to configure a {@code FilterChainProxy}.
 * SecurityFilterChain，字面意思"安全过滤器链",是Spring Security
 * Web对匹配特定HTTP请求的一组安全过滤器的抽象建模。
 * 这样的一个对象在配置阶段用于配置FilterChainProxy,
 * 而FilterChainProxy在请求到达时会使用所持有的某个SecurityFilterChain
 * 判断该请求是否匹配该SecurityFilterChain,如果匹配的话，该SecurityFilterChain会被应用到该请求上。
 * 
 * FilterChainProxy是Spring Security
 * Web添加到Servlet容器用于安全控制的一个Filter，换句话讲，
 * 从Servlet容器的角度来看，Spring Security
 * Web所提供的安全逻辑就是一个Filter,实现类为FilterChainProxy。
 * 而实际上在FilterChainProxy内部，它组合了多个SecurityFilterChain,
 * 而每个SecurityFilterChain又组合了一组Filter,这组Filter也实现了Servlet
 * Filter接口，但是它们对于整个Servlet容器来讲是不可见的。
 * 在本文中，你可以简单地将FilterChainProxy理解成多个SecurityFilterChain的一个封装。
 * ———————————————— 版权声明：本文为CSDN博主「安迪源文」的原创文章，遵循CC 4.0
 * BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/andy_zhang2007/article/details/90256168
 *
 * @author Luke Taylor
 *
 * @since 3.1
 */
public interface SecurityFilterChain {

	boolean matches(HttpServletRequest request);

	List<Filter> getFilters();
}
