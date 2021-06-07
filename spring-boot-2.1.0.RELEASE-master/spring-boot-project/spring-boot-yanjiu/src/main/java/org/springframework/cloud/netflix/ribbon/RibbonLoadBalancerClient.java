/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.netflix.ribbon;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequest;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import static org.springframework.cloud.netflix.ribbon.RibbonUtils.updateToSecureConnectionIfNeeded;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Ryan Baxter
 * @author Tim Ysewyn
 */
public class RibbonLoadBalancerClient implements LoadBalancerClient {

	private SpringClientFactory clientFactory;

	public RibbonLoadBalancerClient(SpringClientFactory clientFactory) {
		this.clientFactory = clientFactory;
	}

	@Override
	public URI reconstructURI(ServiceInstance instance, URI original) {
		Assert.notNull(instance, "instance can not be null");
		String serviceId = instance.getServiceId();
		RibbonLoadBalancerContext context = this.clientFactory
				.getLoadBalancerContext(serviceId);

		URI uri;
		Server server;
		if (instance instanceof RibbonServer) {
			RibbonServer ribbonServer = (RibbonServer) instance;
			server = ribbonServer.getServer();
			uri = updateToSecureConnectionIfNeeded(original, ribbonServer);
		} else {
			server = new Server(instance.getScheme(), instance.getHost(), instance.getPort());
			IClientConfig clientConfig = clientFactory.getClientConfig(serviceId);
			ServerIntrospector serverIntrospector = serverIntrospector(serviceId);
			uri = updateToSecureConnectionIfNeeded(original, clientConfig,
					serverIntrospector, server);
		}
		return context.reconstructURIWithServer(server, uri);
	}
//	choose方法：传入serviceId，然后通过SpringClientFactory获取负载均衡器com.netflix.loadbalancer.ILoadBalancer，
//	最终委托给它的chooseServer()方法选取到一个com.netflix.loadbalancer.Server实例，也就是说真正完成Server选取的是ILoadBalancer。
//
//	ILoadBalancer以及它相关的类是一个较为庞大的体系，本文不做更多的展开，而是只聚焦在我们的流程上
//	————————————————
//	版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/f641385712/article/details/100788040
	@Override
	public ServiceInstance choose(String serviceId) {
	    return choose(serviceId, null);
	}
	// hint：你可以理解成分组。若指定了，只会在这个偏好的分组里面去均衡选择
	// 得到一个Server后，使用RibbonServer把server适配起来~~~
	// 这样一个实例就选好了~~~真正请求会落在这个实例上~
	/**
	 * New: Select a server using a 'key'.
	 */
	public ServiceInstance choose(String serviceId, Object hint) {
		Server server = getServer(getLoadBalancer(serviceId), hint);
		if (server == null) {
			return null;
		}
		return new RibbonServer(serviceId, server, isSecure(server, serviceId),
				serverIntrospector(serviceId).getMetadata(server));
	}

	@Override
	public <T> T execute(String serviceId, LoadBalancerRequest<T> request) throws IOException {
	    return execute(serviceId, request, null);
	}

	/** 说明：LoadBalancerRequest是通过LoadBalancerRequestFactory.createRequest(request, body, execution)创建出来的
	// 它实现LoadBalancerRequest接口是用的一个匿名内部类，泛型类型是ClientHttpResponse
	// 因为最终执行的显然还是执行器：ClientHttpRequestExecution.execute()
	 * New: Execute a request by selecting server using a 'key'.
	 * The hint will have to be the last parameter to not mess with the `execute(serviceId, ServiceInstance, request)`
	 * method. This somewhat breaks the fluent coding style when using a lambda to define the LoadBalancerRequest.
	 */
	public <T> T execute(String serviceId, LoadBalancerRequest<T> request, Object hint) throws IOException {
		// 同上：拿到负载均衡器，然后拿到一个serverInstance实例
		ILoadBalancer loadBalancer = getLoadBalancer(serviceId);
		Server server = getServer(loadBalancer, hint);
		if (server == null) {
			throw new IllegalStateException("No instances available for " + serviceId);
		}
		// 把Server适配为RibbonServer  isSecure：客户端是否安全
				// serverIntrospector内省  参考配置文件：ServerIntrospectorProperties
		RibbonServer ribbonServer = new RibbonServer(serviceId, server, isSecure(server,
				serviceId), serverIntrospector(serviceId).getMetadata(server));
		//调用本类的重载接口方法~~~~~
		return execute(serviceId, ribbonServer, request);
	}
	// 接口方法：它的参数是ServiceInstance --> 已经确定了唯一的Server实例~~~
	@Override
	public <T> T execute(String serviceId, ServiceInstance serviceInstance, LoadBalancerRequest<T> request) throws IOException {
		// 拿到Server）（说白了，RibbonServer是execute时的唯一实现）
		Server server = null;
		if(serviceInstance instanceof RibbonServer) {
			server = ((RibbonServer)serviceInstance).getServer();
		}
		if (server == null) {
			throw new IllegalStateException("No instances available for " + serviceId);
		}

		RibbonLoadBalancerContext context = this.clientFactory
				.getLoadBalancerContext(serviceId);
		RibbonStatsRecorder statsRecorder = new RibbonStatsRecorder(context, server);

		try {
			// 真正的向server发送请求，得到返回值
			// 因为有拦截器，所以这里肯定说执行的是InterceptingRequestExecution.execute()方法
			// so会调用ServiceRequestWrapper.getURI()，从而就会调用reconstructURI()方法
			//
			T returnVal = request.apply(serviceInstance);
			statsRecorder.recordStats(returnVal);
			//returnVal是一个ClientHttpResponse，
			//最后交给handleResponse()方法来处理异常情况（若存在的话），
			//若无异常就交给提取器提值：responseExtractor.extractData(response)，这样整个请求就算全部完成了。
			return returnVal;
		}
		// catch IOException and rethrow so RestTemplate behaves correctly
		catch (IOException ex) {
			statsRecorder.recordStats(ex);
			throw ex;
		}
		catch (Exception ex) {
			statsRecorder.recordStats(ex);
			ReflectionUtils.rethrowRuntimeException(ex);
		}
		return null;
	}

	private ServerIntrospector serverIntrospector(String serviceId) {
		ServerIntrospector serverIntrospector = this.clientFactory.getInstance(serviceId,
				ServerIntrospector.class);
		if (serverIntrospector == null) {
			serverIntrospector = new DefaultServerIntrospector();
		}
		return serverIntrospector;
	}

	private boolean isSecure(Server server, String serviceId) {
		IClientConfig config = this.clientFactory.getClientConfig(serviceId);
		ServerIntrospector serverIntrospector = serverIntrospector(serviceId);
		return RibbonUtils.isSecure(config, serverIntrospector, server);
	}

	/**
	 * Note: This method could be removed?
	 */
	protected Server getServer(String serviceId) {
		return getServer(getLoadBalancer(serviceId), null);
	}

	protected Server getServer(ILoadBalancer loadBalancer) {
	    return getServer(loadBalancer, null);
	}

	protected Server getServer(ILoadBalancer loadBalancer, Object hint) {
		if (loadBalancer == null) {
			return null;
		}
		// Use 'default' on a null hint, or just pass it on?
		return loadBalancer.chooseServer(hint != null ? hint : "default");
	}
	//// 根据ServiceId去找到一个属于它的负载均衡器
	protected ILoadBalancer getLoadBalancer(String serviceId) {
		return this.clientFactory.getLoadBalancer(serviceId);
	}

	public static class RibbonServer implements ServiceInstance {
		private final String serviceId;
		private final Server server;
		private final boolean secure;
		private Map<String, String> metadata;

		public RibbonServer(String serviceId, Server server) {
			this(serviceId, server, false, Collections.emptyMap());
		}

		public RibbonServer(String serviceId, Server server, boolean secure,
				Map<String, String> metadata) {
			this.serviceId = serviceId;
			this.server = server;
			this.secure = secure;
			this.metadata = metadata;
		}

		@Override
		public String getInstanceId() {
			return this.server.getId();
		}

		@Override
		public String getServiceId() {
			return this.serviceId;
		}

		@Override
		public String getHost() {
			return this.server.getHost();
		}

		@Override
		public int getPort() {
			return this.server.getPort();
		}

		@Override
		public boolean isSecure() {
			return this.secure;
		}

		@Override
		public URI getUri() {
			return DefaultServiceInstance.getUri(this);
		}

		@Override
		public Map<String, String> getMetadata() {
			return this.metadata;
		}

		public Server getServer() {
			return this.server;
		}

		@Override
		public String getScheme() {
			return this.server.getScheme();
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("RibbonServer{");
			sb.append("serviceId='").append(serviceId).append('\'');
			sb.append(", server=").append(server);
			sb.append(", secure=").append(secure);
			sb.append(", metadata=").append(metadata);
			sb.append('}');
			return sb.toString();
		}
	}

}
