/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign.ribbon;

import java.io.IOException;
import java.net.URI;

import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import feign.Client;
import feign.Request;
import feign.Response;

import org.springframework.cloud.netflix.ribbon.SpringClientFactory;

/**
 * @author Dave Syer
 *
 */
public class LoadBalancerFeignClient implements Client {

	static final Request.Options DEFAULT_OPTIONS = new Request.Options();

	private final Client delegate;

	private CachingSpringLoadBalancerFactory lbClientFactory;

	private SpringClientFactory clientFactory;

	public LoadBalancerFeignClient(Client delegate,
			CachingSpringLoadBalancerFactory lbClientFactory,
			SpringClientFactory clientFactory) {
		this.delegate = delegate;
		this.lbClientFactory = lbClientFactory;
		this.clientFactory = clientFactory;
	}

	static URI cleanUrl(String originalUrl, String host) {
		String newUrl = originalUrl;
		if (originalUrl.startsWith("https://")) {
			newUrl = originalUrl.substring(0, 8)
					+ originalUrl.substring(8 + host.length());
		}
		else if (originalUrl.startsWith("http")) {
			newUrl = originalUrl.substring(0, 7)
					+ originalUrl.substring(7 + host.length());
		}
		StringBuffer buffer = new StringBuffer(newUrl);
		if ((newUrl.startsWith("https://") && newUrl.length() == 8)
				|| (newUrl.startsWith("http://") && newUrl.length() == 7)) {
			buffer.append("/");
		}
		return URI.create(buffer.toString());
	}

	@Override
	public Response execute(Request request, Request.Options options) throws IOException {
		try {
			// 创建uriWithoutHost http:///getStockByMpId/2
			URI asUri = URI.create(request.url());
			String clientName = asUri.getHost();
			URI uriWithoutHost = cleanUrl(request.url(), clientName);
			// 封装Client、Request和uriWithoutHost为RibbonRequest
			FeignLoadBalancer.RibbonRequest ribbonRequest = new FeignLoadBalancer.RibbonRequest(
					this.delegate, request, uriWithoutHost);
			// 一段古怪的逻辑，根据options是否是自动配置的options，获取IClientConfigs
			IClientConfig requestConfig = getClientConfig(options, clientName);
			// 通过CachingSpringLoadBalancerFactory的create方法
			// 获取FeignLoadBalancer继承ribbon的AbstractLoadBalancerAwareClient
			return lbClient(clientName)
					.executeWithLoadBalancer(ribbonRequest, requestConfig).toResponse();
		}
		catch (ClientException e) {
			IOException io = findIOException(e);
			if (io != null) {
				throw io;
			}
			throw new RuntimeException(e);
		}
	}

	IClientConfig getClientConfig(Request.Options options, String clientName) {
		IClientConfig requestConfig;
		if (options == DEFAULT_OPTIONS) {
			// 如果使用默认配置（FeignRibbonClientAutoConfiguration#feignRequestOptions全局容器默认注入的）
			// 整个IClientConfig取Ribbon的DefaultClientConfigImpl，也就是说超时时间啥的配置都是走Ribbon配置

			requestConfig = this.clientFactory.getClientConfig(clientName);
		}
		else {
			requestConfig = new FeignOptionsClientConfig(options);
		}
		return requestConfig;
	}

	protected IOException findIOException(Throwable t) {
		if (t == null) {
			return null;
		}
		if (t instanceof IOException) {
			return (IOException) t;
		}
		return findIOException(t.getCause());
	}

	public Client getDelegate() {
		return this.delegate;
	}

	private FeignLoadBalancer lbClient(String clientName) {
		return this.lbClientFactory.create(clientName);
	}

	static class FeignOptionsClientConfig extends DefaultClientConfigImpl {

		FeignOptionsClientConfig(Request.Options options) {
			setProperty(CommonClientConfigKey.ConnectTimeout,
					options.connectTimeoutMillis());
			setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
		}

		@Override
		public void loadProperties(String clientName) {

		}

		@Override
		public void loadDefaultValues() {

		}

	}

}
