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

package org.springframework.web.accept;

import java.util.List;

import org.springframework.http.MediaType;

/**
 * Strategy to resolve {@link MediaType} to a list of file extensions.
 * For example resolve "application/json" to "json".
 *
 *MediaType和路径扩展名解析策略的接口，例如将 .json 解析成 application/json 或者反向解析
 *
 * @author Rossen Stoyanchev
 * @since 3.2
 */
public interface MediaTypeFileExtensionResolver {

	/**
	 * // 根据指定的mediaType返回一组文件扩展名
	 * Resolve the given media type to a list of path extensions.
	 * @param mediaType the media type to resolve
	 * @return a list of extensions or an empty list (never {@code null})
	 */
	List<String> resolveFileExtensions(MediaType mediaType);

	/**返回该接口注册进来的所有的扩展名
	 * Return all registered file extensions.
	 * @return a list of extensions or an empty list (never {@code null})
	 */
	List<String> getAllFileExtensions();

}
