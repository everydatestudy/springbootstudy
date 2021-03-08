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

package org.springframework.web.accept;

import java.util.Collections;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.context.request.NativeWebRequest;

/**
 * A strategy for resolving the requested media types for a request. Spring
 * MVC默认加载两个该策略接口的实现类：
 * ServletPathExtensionContentNegotiationStrategy–>根据文件扩展名（支持RESTful）。
 * HeaderContentNegotiationStrategy–>根据HTTP Header里的Accept字段（支持Http）。
 * ———————————————— 版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0
 * BY-SA版权协议，转载请附上原文出处链接及本声明。
 * 原文链接：https://blog.csdn.net/f641385712/article/details/100060445
 * 
 * @author Rossen Stoyanchev
 * @since 3.2
 */
@FunctionalInterface
public interface ContentNegotiationStrategy {

	/**
	 * A singleton list with {@link MediaType#ALL} that is returned from
	 * {@link #resolveMediaTypes} when no specific media types are requested.
	 * 
	 * @since 5.0.5
	 */
	List<MediaType> MEDIA_TYPE_ALL_LIST = Collections.singletonList(MediaType.ALL);

	/**
	 * // 将给定的请求解析为媒体类型列表 // 返回的 List 首先按照 specificity 参数排序，其次按照 quality 参数排序 //
	 * 如果请求的媒体类型不能被解析则抛出 HttpMediaTypeNotAcceptableException 异常
	 * 
	 * 
	 * Resolve the given request to a list of media types. The returned list is
	 * ordered by specificity first and by quality parameter second.
	 * 
	 * @param webRequest the current request
	 * @return the requested media types, or {@link #MEDIA_TYPE_ALL_LIST} if none
	 *         were requested.
	 * @throws HttpMediaTypeNotAcceptableException if the requested media types
	 *                                             cannot be parsed
	 */
	List<MediaType> resolveMediaTypes(NativeWebRequest webRequest) throws HttpMediaTypeNotAcceptableException;

}
