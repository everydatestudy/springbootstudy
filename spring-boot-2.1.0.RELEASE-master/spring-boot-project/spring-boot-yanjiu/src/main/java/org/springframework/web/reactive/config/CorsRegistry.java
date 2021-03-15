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

package org.springframework.web.reactive.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.cors.CorsConfiguration;

/**这两个类虽然简单，但是在@EnableWebMvc里扩展配置时使用得较多，参见下个章节对WebMvcConfigurer扩展使用和配置

 * Assists with the registration of global, URL pattern based
 * {@link CorsConfiguration} mappings.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 5.0
 * TODO 架构是慢慢演进出来的，不是设计出来的。架构没有最好，只有最合适
 */
public class CorsRegistry {
	// 保存着全局的配置，每个CorsRegistration就是URL pattern和CorsConfiguration配置
	private final List<CorsRegistration> registrations = new ArrayList<>();


	/**
	 * Enable cross origin request handling for the specified path pattern.
	 *
	 * <p>Exact path mapping URIs (such as {@code "/admin"}) are supported as
	 * well as Ant-style path patterns (such as {@code "/admin/**"}).
	 *
	 * <p>The following defaults are applied to the {@link CorsRegistration}:
	 * <ul>
	 *     <li>Allow all origins.</li>
	 *     <li>Allow "simple" methods {@code GET}, {@code HEAD} and {@code POST}.</li>
	 *     <li>Allow all headers.</li>
	 *     <li>Set max age to 1800 seconds (30 minutes).</li>
	 * </ul>
	 */
	// 像上面List添加一个全局配置（和pathPattern绑定）
	// 它使用的是new CorsRegistration(pathPattern)
	// 可见使用配置是默认配置：new CorsConfiguration().applyPermitDefaultValues()
	// 当然它CorsRegistration return给你了，你还可以改（配置）的~~~~

	public CorsRegistration addMapping(String pathPattern) {
		CorsRegistration registration = new CorsRegistration(pathPattern);
		this.registrations.add(registration);
		return registration;
	}
	// 这个就比较简单了：把当前List专程Map。key就是PathPattern~~~~
	protected Map<String, CorsConfiguration> getCorsConfigurations() {
		Map<String, CorsConfiguration> configs = new LinkedHashMap<>(this.registrations.size());
		for (CorsRegistration registration : this.registrations) {
			configs.put(registration.getPathPattern(), registration.getCorsConfiguration());
		}
		return configs;
	}

}
