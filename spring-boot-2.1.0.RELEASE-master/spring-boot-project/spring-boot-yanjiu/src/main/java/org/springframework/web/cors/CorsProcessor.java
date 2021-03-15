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

package org.springframework.web.cors;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;

/**
 * 
 * 使用框架来处理跨域的好处便是：兼容性很强且灵活。它的处理过程如下：

	若不是跨域请求，不处理（注意是return true后面拦截器还得执行呢）。若是跨域请求继续处理。（是否是跨域请求就看请求头是否有Origin这个头）
	判断response是否有Access-Control-Allow-Origin这个响应头，若有说明已经被处理过，那本处理器就不再处理了
	判断是否是同源：即使有Origin请求头，但若是同源的也不处理
	是否配置了CORS规则，若没有配置：
	1. 若是预检请求，直接决绝403，return false
	2. 若不是预检请求，则本处理器不处理
	正常处理CORS请求，大致是如下步骤：
	1. 判断 origin 是否合法
	2. 判断 method 是否合法
	3. 判断 header是否合法
	4. 若其中有一项不合法，直接决绝掉403并return false。都合法的话：就在response设置上一些头信息~~~
 
 * 
 * A strategy that takes a request and a {@link CorsConfiguration} and updates
 * the response.
 *
 * <p>This component is not concerned with how a {@code CorsConfiguration} is
 * selected but rather takes follow-up actions such as applying CORS validation
 * checks and either rejecting the response or adding CORS headers to the
 * response.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 4.2
 * @see <a href="http://www.w3.org/TR/cors/">CORS W3C recommendation</a>
 * @see org.springframework.web.servlet.handler.AbstractHandlerMapping#setCorsProcessor
 */
public interface CorsProcessor {

	/**根据所给的`CorsConfiguration`来处理请求
	 * Process a request given a {@code CorsConfiguration}.
	 * @param configuration the applicable CORS configuration (possibly {@code null})
	 * @param request the current request
	 * @param response the current response
	 * @return {@code false} if the request is rejected, {@code true} otherwise
	 */
	boolean processRequest(@Nullable CorsConfiguration configuration, HttpServletRequest request,
			HttpServletResponse response) throws IOException;

}
