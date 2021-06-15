/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer;

import com.google.common.base.Strings;
import com.netflix.client.ClientException;
import com.netflix.client.ClientRequest;
import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.IClientConfigAware;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Timer;
import com.netflix.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**LoadBalancerContext作为负载均衡器的执行上下文，那必然在执行过程中使用喽。所以它的唯一使用处是在LoadBalancerCommand里，而它就代表着一个负载均衡执行命令。

另外，还有个有意思的地方是，AbstractLoadBalancerAwareClient继承自LoadBalancerContext，也就是说每个Client它自己就是个上下文，可以访问到执行时候的任何环境值。

 说明：此处说每个Client是建立在我们认为所有的Client均是AbstractLoadBalancerAwareClient的子类的基础上的
 * A class contains APIs intended to be used be load balancing client which is subclass of this class.
 * 
 * @author awang
 */
public class LoadBalancerContext implements IClientConfigAware {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerContext.class);
    //取值为clientConfig.getClientName()，若你木有指定就是default
    protected String clientName = "default";          
    // 此值通过配置，然后经过
    protected String vipAddresses;

    protected int maxAutoRetriesNextServer = DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES_NEXT_SERVER;
    protected int maxAutoRetries = DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES;

    protected RetryHandler defaultRetryHandler = new DefaultLoadBalancerRetryHandler();

    //是有允许所有操作都执行重试，默认是false 通过key：OkToRetryOnAllOperations配置
    protected boolean okToRetryOnAllOperations = DefaultClientConfigImpl.DEFAULT_OK_TO_RETRY_ON_ALL_OPERATIONS.booleanValue();
    //负载均衡器，通过构造器传入
    private ILoadBalancer lb;

    private volatile Timer tracer;

    public LoadBalancerContext(ILoadBalancer lb) {
        this.lb = lb;
    }

    /**以上属性在构造阶段/initWithNiwsConfig阶段完成初始化赋值。
     * Delegate to {@link #initWithNiwsConfig(IClientConfig)}
     * @param clientConfig
     */
    public LoadBalancerContext(ILoadBalancer lb, IClientConfig clientConfig) {
        this.lb = lb;
        initWithNiwsConfig(clientConfig);        
    }

    public LoadBalancerContext(ILoadBalancer lb, IClientConfig clientConfig, RetryHandler handler) {
        this(lb, clientConfig);
        this.defaultRetryHandler = handler;
    }

    /**
     * Set necessary parameters from client configuration and register with Servo monitors.
     */
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        if (clientConfig == null) {
            return;    
        }
        clientName = clientConfig.getClientName();
        if (clientName == null) {
            clientName = "default";
        }
        //解析而来值使用的key是：DeploymentContextBasedVipAddresses，
        //如<ClientName>.ribbon.DeploymentContextBasedVipAddresses={aaa}movieservice   无默认值，它一般用于在集合eureka使用时会配置上 
        //该值可以配置多个，使用,分隔
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        //这两个参数解释过多次，此处不再重复解释
        maxAutoRetries = clientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxAutoRetries, DefaultClientConfigImpl.DEFAULT_MAX_AUTO_RETRIES);
        maxAutoRetriesNextServer = clientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxAutoRetriesNextServer,maxAutoRetriesNextServer);

        okToRetryOnAllOperations = clientConfig.getPropertyAsBoolean(CommonClientConfigKey.OkToRetryOnAllOperations, okToRetryOnAllOperations);
        //重试处理器，默认使用的DefaultLoadBalancerRetryHandler，你可通过set方法指定
        defaultRetryHandler = new DefaultLoadBalancerRetryHandler(clientConfig);
        
        tracer = getExecuteTracer();

        Monitors.registerObject("Client_" + clientName, this);
    }

    public Timer getExecuteTracer() {
        if (tracer == null) {
            synchronized(this) {
                if (tracer == null) {
                    tracer = Monitors.newTimer(clientName + "_LoadBalancerExecutionTimer", TimeUnit.MILLISECONDS);                    
                }
            }
        } 
        return tracer;        
    }

    public String getClientName() {
        return clientName;
    }

    public ILoadBalancer getLoadBalancer() {
        return lb;    
    }

    public void setLoadBalancer(ILoadBalancer lb) {
        this.lb = lb;
    }

    /**
     * Use {@link #getRetryHandler()} 
     */
    @Deprecated
    public int getMaxAutoRetriesNextServer() {
        return maxAutoRetriesNextServer;
    }

    /**
     * Use {@link #setRetryHandler(RetryHandler)} 
     */
    @Deprecated
    public void setMaxAutoRetriesNextServer(int maxAutoRetriesNextServer) {
        this.maxAutoRetriesNextServer = maxAutoRetriesNextServer;
    }

    /**
     * Use {@link #getRetryHandler()} 
     */
    @Deprecated
    public int getMaxAutoRetries() {
        return maxAutoRetries;
    }

    /**
     * Use {@link #setRetryHandler(RetryHandler)} 
     */
    @Deprecated
    public void setMaxAutoRetries(int maxAutoRetries) {
        this.maxAutoRetries = maxAutoRetries;
    }

    protected Throwable getDeepestCause(Throwable e) {
        if(e != null) {
            int infiniteLoopPreventionCounter = 10;
            while (e.getCause() != null && infiniteLoopPreventionCounter > 0) {
                infiniteLoopPreventionCounter--;
                e = e.getCause();
            }
        }
        return e;
    }

    private boolean isPresentAsCause(Throwable throwableToSearchIn,
            Class<? extends Throwable> throwableToSearchFor) {
        return isPresentAsCauseHelper(throwableToSearchIn, throwableToSearchFor) != null;
    }

    static Throwable isPresentAsCauseHelper(Throwable throwableToSearchIn,
            Class<? extends Throwable> throwableToSearchFor) {
        int infiniteLoopPreventionCounter = 10;
        while (throwableToSearchIn != null && infiniteLoopPreventionCounter > 0) {
            infiniteLoopPreventionCounter--;
            if (throwableToSearchIn.getClass().isAssignableFrom(
                    throwableToSearchFor)) {
                return throwableToSearchIn;
            } else {
                throwableToSearchIn = throwableToSearchIn.getCause();
            }
        }
        return null;
    }

    protected ClientException generateNIWSException(String uri, Throwable e){
        ClientException niwsClientException;
        if (isPresentAsCause(e, java.net.SocketTimeoutException.class)) {
            niwsClientException = generateTimeoutNIWSException(uri, e);
        }else if (e.getCause() instanceof java.net.UnknownHostException){
            niwsClientException = new ClientException(
                    ClientException.ErrorType.UNKNOWN_HOST_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e.getCause() instanceof java.net.ConnectException){
            niwsClientException = new ClientException(
                    ClientException.ErrorType.CONNECT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e.getCause() instanceof java.net.NoRouteToHostException){
            niwsClientException = new ClientException(
                    ClientException.ErrorType.NO_ROUTE_TO_HOST_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }else if (e instanceof ClientException){
            niwsClientException = (ClientException)e;
        }else {
            niwsClientException = new ClientException(
                    ClientException.ErrorType.GENERAL,
                    "Unable to execute RestClient request for URI:" + uri,
                    e);
        }
        return niwsClientException;
    }

    private boolean isPresentAsCause(Throwable throwableToSearchIn,
            Class<? extends Throwable> throwableToSearchFor, String messageSubStringToSearchFor) {
        Throwable throwableFound = isPresentAsCauseHelper(throwableToSearchIn, throwableToSearchFor);
        if(throwableFound != null) {
            return throwableFound.getMessage().contains(messageSubStringToSearchFor);
        }
        return false;
    }
    private ClientException generateTimeoutNIWSException(String uri, Throwable e){
        ClientException niwsClientException;
        if (isPresentAsCause(e, java.net.SocketTimeoutException.class,
                "Read timed out")) {
            niwsClientException = new ClientException(
                    ClientException.ErrorType.READ_TIMEOUT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri + ":"
                            + getDeepestCause(e).getMessage(), e);
        } else {
            niwsClientException = new ClientException(
                    ClientException.ErrorType.SOCKET_TIMEOUT_EXCEPTION,
                    "Unable to execute RestClient request for URI:" + uri + ":"
                            + getDeepestCause(e).getMessage(), e);
        }
        return niwsClientException;
    }
 // 记录一个状态，需要传入rt，所以肯定代表请求已经结束了喽。让是个私有方法，旨在本类被调用
 	// 1、activeRequestsCount -1
 	// 2、totalRequests +1（注意：总请求数是在完成时候+1的，而非请求的时候哦）
 	// 3、记录rt（包括时间窗口收集dataDist以及历史统计的responseTimeDist）
    private void recordStats(ServerStats stats, long responseTime) {
    	if (stats == null) {
    		return;
    	}
        stats.decrementActiveRequestsCount();
        stats.incrementNumRequests();
        stats.noteResponseTime(responseTime);
    }
   // 这几个方法提供的是Client在执行过程中，对指标的收集。小Tips：noteError/noteResponse分别代表异常完成/正常完成，但其实都没有被调用过，而是统一调用更高级的noteRequestCompletion()方法。
    protected void noteRequestCompletion(ServerStats stats, Object response, Throwable e, long responseTime) {
    	if (stats == null) {
    		return;
    	}
        noteRequestCompletion(stats, response, e, responseTime, null);
    }
    
    
    /**========重要========此方法表示请求完成后调用
 	    请求完成：在接收到响应或从客户端抛出异常（出错）
 	  response：返回值
     * This is called after a response is received or an exception is thrown from the client
     * to update related stats.  
     */
    public void noteRequestCompletion(ServerStats stats, Object response, Throwable e, long responseTime, RetryHandler errorHandler) {
    	if (stats == null) {
    		return;
    	}
        try {
            recordStats(stats, responseTime);
    		// 很明显：callErrorHandler永远不可能为null（排除故意把重拾起set为null的情况）
            RetryHandler callErrorHandler = errorHandler == null ? getRetryHandler() : errorHandler;
            // 判断看看是否出错了，是否需要重试
    		// 有response那就是正常返回：该请求正常，那就把重复连续失败的count清零
            if (callErrorHandler != null && response != null) {
                stats.clearSuccessiveConnectionFailureCount();
            } else if (callErrorHandler != null && e != null) {
            	// 如果是熔断类型的异常，那就连续次数 + 1
    			// 失败总数也+1(窗口统计)
                if (callErrorHandler.isCircuitTrippingException(e)) {
                    stats.incrementSuccessiveConnectionFailureCount();                    
                    stats.addToFailureCount();
                 // 表示虽然失败了，但是是其它异常，比如你的业务异常，比如NPE
        		 // 那就清零。再次证明：ribbon的熔断只管它自己是被的异常（链接异常）
        		 // 并不管业务异常，业务异常交给hystrix才是合理的
                } else {
                    stats.clearSuccessiveConnectionFailureCount();
                }
            }
        } catch (Exception ex) {
            logger.error("Error noting stats for client {}", clientName, ex);
        }            
    }

    /**
     * This is called after an error is thrown from the client
     * to update related stats.  
     */
    protected void noteError(ServerStats stats, ClientRequest request, Throwable e, long responseTime) {
    	if (stats == null) {
    		return;
    	}
        try {
            recordStats(stats, responseTime);
            RetryHandler errorHandler = getRetryHandler();
            if (errorHandler != null && e != null) {
                if (errorHandler.isCircuitTrippingException(e)) {
                    stats.incrementSuccessiveConnectionFailureCount();                    
                    stats.addToFailureCount();
                } else {
                    stats.clearSuccessiveConnectionFailureCount();
                }
            }
        } catch (Exception ex) {
            logger.error("Error noting stats for client {}", clientName, ex);
        }            
    }

    /**
     * This is called after a response is received from the client
     * to update related stats.  
     */
    protected void noteResponse(ServerStats stats, ClientRequest request, Object response, long responseTime) {
    	if (stats == null) {
    		return;
    	}
        try {
            recordStats(stats, responseTime);
            RetryHandler errorHandler = getRetryHandler();
            if (errorHandler != null && response != null) {
                stats.clearSuccessiveConnectionFailureCount();
            } 
        } catch (Exception ex) {
            logger.error("Error noting stats for client {}", clientName, ex);
        }            
    }

    /** 客户端执行请求之前调用。增加一个活跃连接数
     * This is usually called just before client execute a request.
     */
    public void noteOpenConnection(ServerStats serverStats) {
        if (serverStats == null) {
            return;
        }
        try {
            serverStats.incrementActiveRequestsCount();
        } catch (Exception ex) {
            logger.error("Error noting stats for client {}", clientName, ex);
        }            
    }


    /** 仅仅根据URI就拿到端口
	// http 80  /  https 443
     * Derive scheme and port from a partial URI. For example, for HTTP based client, the URI with 
     * only path "/" should return "http" and 80, whereas the URI constructed with scheme "https" and
     * path "/" should return "https" and 443.
     * This method is called by {@link #getServerFromLoadBalancer(java.net.URI, Object)} and
     * {@link #reconstructURIWithServer(Server, java.net.URI)} methods to get the complete executable URI.
     */
    protected Pair<String, Integer> deriveSchemeAndPortFromPartialUri(URI uri) {
        boolean isSecure = false;
        String scheme = uri.getScheme();
        if (scheme != null) {
            isSecure =  scheme.equalsIgnoreCase("https");
        }
        int port = uri.getPort();
        if (port < 0 && !isSecure){
            port = 80;
        } else if (port < 0 && isSecure){
            port = 443;
        }
        if (scheme == null){
            if (isSecure) {
                scheme = "https";
            } else {
                scheme = "http";
            }
        }
        return new Pair<String, Integer>(scheme, port);
    }

    /**
     * Get the default port of the target server given the scheme of vip address if it is available. 
     * Subclass should override it to provider protocol specific default port number if any.
     * 
     * @param scheme from the vip address. null if not present.
     * @return 80 if scheme is http, 443 if scheme is https, -1 else.
     */
    protected int getDefaultPortFromScheme(String scheme) {
        if (scheme == null) {
            return -1;
        }
        if (scheme.equals("http")) {
            return 80;
        } else if (scheme.equals("https")) {
            return 443;
        } else {
            return -1;
        }
    }


    /** 从虚拟地址中尝试获取到主机的host和port
	// 如果虚拟地址确实包含实际的主机，那就直接拿。如虚拟地址就是：localhost:8080
     * Derive the host and port from virtual address if virtual address is indeed contains the actual host 
     * and port of the server. This is the final resort to compute the final URI in {@link #getServerFromLoadBalancer(java.net.URI, Object)}
     * if there is no load balancer available and the request URI is incomplete. Sub classes can override this method
     * to be more accurate or throws ClientException if it does not want to support virtual address to be the
     * same as physical server address.
     * <p>
     *  The virtual address is used by certain load balancers to filter the servers of the same function 
     *  to form the server pool. 
     *  
     */
    protected  Pair<String, Integer> deriveHostAndPortFromVipAddress(String vipAddress) 
            throws URISyntaxException, ClientException {
        Pair<String, Integer> hostAndPort = new Pair<String, Integer>(null, -1);
        URI uri = new URI(vipAddress);
        String scheme = uri.getScheme();
        if (scheme == null) {
            uri = new URI("http://" + vipAddress);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new ClientException("Unable to derive host/port from vip address " + vipAddress);
        }
        int port = uri.getPort();
        if (port < 0) {
            port = getDefaultPortFromScheme(scheme);
        }
        if (port < 0) {
            throw new ClientException("Unable to derive host/port from vip address " + vipAddress);
        }
        hostAndPort.setFirst(host);
        hostAndPort.setSecond(port);
        return hostAndPort;
    }
    // 检测你配置的vipAddress是否是被认证过的
 	//若配置了多个，只要有一个符合要求，就返回true
   // 该方法只是尝试去从虚拟地址里拿host和port，可见抛出异常的概率是很大的，因为虚拟地址我们一般写服务名（不过这里似乎告诉我们：虚拟地址写地址值也是欧克的），如果没有可用的负载均衡器，并且请求URI不完整。子类可以覆盖此方法（而实际情况是哪怕Spring Cloud下都没有复写过此方法）
    private boolean isVipRecognized(String vipEmbeddedInUri) {
		// vipAddresses是上下文中指定的，也就是你配置的，可以使用逗号分隔配置多个
        if (vipEmbeddedInUri == null) {
            return false;
        }
        if (vipAddresses == null) {
            return false;
        }
       // 配置了多个的话，就一个一个的检查
       // 只要有一个地址值是被认证过的，那就返回true
        String[] addresses = vipAddresses.split(",");
        for (String address: addresses) {
            if (vipEmbeddedInUri.equalsIgnoreCase(address.trim())) {
                return true;
            }
        }
        return false;
    }

    /**original这个URI可能是无host无ip的，如/api/v1/ping。所以此处需要处理
	// 注意：调用此方法两个入参均可能为null哦
     * Compute the final URI from a partial URI in the request. The following steps are performed:
     * <ul>
     * <li> if host is missing and there is a load balancer, get the host/port from server chosen from load balancer
     * <li> if host is missing and there is no load balancer, try to derive host/port from virtual address set with the client
     * <li> if host is present and the authority part of the URI is a virtual address set for the client, 
     * and there is a load balancer, get the host/port from server chosen from load balancer
     * <li> if host is present but none of the above applies, interpret the host as the actual physical address
     * <li> if host is missing but none of the above applies, throws ClientException
     * </ul>
     *
     * @param original Original URI passed from caller
     */
//    该方法从URI original中，最终目标是得到一个Server（比如包含ip地址、port），它会分为如下情况：
//
//    host不存在： 
//    lb存在：使用lb.chooseServer(loadBalancerKey)选出一台Server
//    lb不存在： 
//    配置了虚拟地址vipAddresses： 
//    有且仅配置了一个值：使用上面介绍的deriveHostAndPortFromVipAddress(vipAddresses)把host和port解析出来
//    ,分隔配置了多个值： throw new ClientException()
//    没配置虚拟地址vipAddresses：throw new ClientException()
//    host存在： 
//    original.getAuthority()部分就是你配置的虚拟地址值vipAddresses（这就是虚拟地址值的意义） 
//    有lb：使用lb.chooseServer(loadBalancerKey)选出一台Server
//    无lb： throw new ClientException()
//    host也不是original.getAuthority()部分，那就当ip地址用。自己new一个Server返回
    public Server getServerFromLoadBalancer(URI original,Object loadBalancerKey) throws ClientException {
        String host = null;
        int port = -1;
        if (original != null) {
            host = original.getHost();
        }
        if (original != null) {
            Pair<String, Integer> schemeAndPort = deriveSchemeAndPortFromPartialUri(original);        
            port = schemeAndPort.second();
        }

        // Various Supported Cases
        // The loadbalancer to use and the instances it has is based on how it was registered
        // In each of these cases, the client might come in using Full Url or Partial URL
        ILoadBalancer lb = getLoadBalancer();
//        host不存在：这种情况下url随意，没有任何要求，空都行…
//        case1：正如基准代码，因为host为null，所有使用lb负载均衡算法（轮询）选出Server
        if (host == null) {
            // Partial URI or no URI Case
            // well we have to just get the right instances from lb - or we fall back
//        	case2：不指定lb new LoadBalancerContext(null, config);，并且提供配置ribbon.DeploymentContextBasedVipAddresses=http://www.baiud.com:9999，再次运行程序 
//        		注意：配置里必须以协议打头，如http://绝对不能省略，否则不能被是被为正常的URI。结果抛出异常：com.netflix.client.ClientException: Unable to derive host/port from vip address www.baiud.com:9999
            if (lb != null){
                Server svc = lb.chooseServer(loadBalancerKey);
                if (svc == null){
                    throw new ClientException(ClientException.ErrorType.GENERAL,
                            "Load balancer does not have available server for client: "
                                    + clientName);
                }
                host = svc.getHost();
                if (host == null){
                    throw new ClientException(ClientException.ErrorType.GENERAL,
                            "Invalid Server for :" + svc);
                }
                logger.debug("{} using LB returned Server: {} for request {}", new Object[]{clientName, svc, original});
                return svc;
            } else {
                // No Full URL - and we dont have a LoadBalancer registered to
                // obtain a server
                // if we have a vipAddress that came with the registration, we
                // can use that else we
                // bail out
                if (vipAddresses != null && vipAddresses.contains(",")) {
                    throw new ClientException(
                            ClientException.ErrorType.GENERAL,
                            "Method is invoked for client " + clientName + " with partial URI of ("
                            + original
                            + ") with no load balancer configured."
                            + " Also, there are multiple vipAddresses and hence no vip address can be chosen"
                            + " to complete this partial uri");
                } else if (vipAddresses != null) {
                    try {
                        Pair<String,Integer> hostAndPort = deriveHostAndPortFromVipAddress(vipAddresses);
                        host = hostAndPort.first();
                        port = hostAndPort.second();
                    } catch (URISyntaxException e) {
                        throw new ClientException(
                                ClientException.ErrorType.GENERAL,
                                "Method is invoked for client " + clientName + " with partial URI of ("
                                + original
                                + ") with no load balancer configured. "
                                + " Also, the configured/registered vipAddress is unparseable (to determine host and port)");
                    }
                } else {
                    throw new ClientException(
                            ClientException.ErrorType.GENERAL,
                            this.clientName
                            + " has no LoadBalancer registered and passed in a partial URL request (with no host:port)."
                            + " Also has no vipAddress registered");
                }
            }
        } else {
            // Full URL Case
            // This could either be a vipAddress or a hostAndPort or a real DNS
            // if vipAddress or hostAndPort, we just have to consult the loadbalancer
            // but if it does not return a server, we should just proceed anyways
            // and assume its a DNS
            // For restClients registered using a vipAddress AND executing a request
            // by passing in the full URL (including host and port), we should only
            // consult lb IFF the URL passed is registered as vipAddress in Discovery
            boolean shouldInterpretAsVip = false;

            if (lb != null) {
                shouldInterpretAsVip = isVipRecognized(original.getAuthority());
            }
            if (shouldInterpretAsVip) {
                Server svc = lb.chooseServer(loadBalancerKey);
                if (svc != null){
                    host = svc.getHost();
                    if (host == null){
                        throw new ClientException(ClientException.ErrorType.GENERAL,
                                "Invalid Server for :" + svc);
                    }
                    logger.debug("using LB returned Server: {} for request: {}", svc, original);
                    return svc;
                } else {
                    // just fall back as real DNS
                    logger.debug("{}:{} assumed to be a valid VIP address or exists in the DNS", host, port);
                }
            } else {
                // consult LB to obtain vipAddress backed instance given full URL
                //Full URL execute request - where url!=vipAddress
                logger.debug("Using full URL passed in by caller (not using load balancer): {}", original);
            }
        }
        // end of creating final URL
        if (host == null){
            throw new ClientException(ClientException.ErrorType.GENERAL,"Request contains no HOST to talk to");
        }
        // just verify that at this point we have a full URL

        return new Server(host, port);
    }
    //根据一个给定的Server重构URI，让其变得完整。
    ////  使用server里的host、port等完成拼接，形成一个完整的URI返回
    public URI reconstructURIWithServer(Server server, URI original) {
    	// 若original里已经有了host、port、scheme等，那还解析个啥 你已经是完整的了
        String host = server.getHost();
        int port = server.getPort();
        String scheme = server.getScheme();
        
        if (host.equals(original.getHost()) 
                && port == original.getPort()
                && scheme == original.getScheme()) {
            return original;
        }
        if (scheme == null) {
            scheme = original.getScheme();
        }
        if (scheme == null) {
            scheme = deriveSchemeAndPortFromPartialUri(original).first();
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://");
            if (!Strings.isNullOrEmpty(original.getRawUserInfo())) {
                sb.append(original.getRawUserInfo()).append("@");
            }
            sb.append(host);
            if (port >= 0) {
                sb.append(":").append(port);
            }
            sb.append(original.getRawPath());
            if (!Strings.isNullOrEmpty(original.getRawQuery())) {
                sb.append("?").append(original.getRawQuery());
            }
            if (!Strings.isNullOrEmpty(original.getRawFragment())) {
                sb.append("#").append(original.getRawFragment());
            }
            URI newURI = new URI(sb.toString());
            return newURI;            
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    protected boolean isRetriable(T request) {
        if (request.isRetriable()) {
            return true;            
        } else {
            boolean retryOkayOnOperation = okToRetryOnAllOperations;
            IClientConfig overriddenClientConfig = request.getOverrideConfig();
            if (overriddenClientConfig != null) {
                retryOkayOnOperation = overriddenClientConfig.getPropertyAsBoolean(CommonClientConfigKey.RequestSpecificRetryOn, okToRetryOnAllOperations);
            }
            return retryOkayOnOperation;
        }
    }
     */

    protected int getRetriesNextServer(IClientConfig overriddenClientConfig) {
        int numRetries = maxAutoRetriesNextServer;
        if (overriddenClientConfig != null) {
            numRetries = overriddenClientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxAutoRetriesNextServer, maxAutoRetriesNextServer);
        }
        return numRetries;
    }

    public final ServerStats getServerStats(Server server) {
        ServerStats serverStats = null;
        ILoadBalancer lb = this.getLoadBalancer();
        if (lb instanceof AbstractLoadBalancer){
            LoadBalancerStats lbStats = ((AbstractLoadBalancer) lb).getLoadBalancerStats();
            serverStats = lbStats.getSingleServerStat(server);
        }
        return serverStats;

    }

    protected int getNumberRetriesOnSameServer(IClientConfig overriddenClientConfig) {
        int numRetries =  maxAutoRetries;
        if (overriddenClientConfig!=null){
            try {
                numRetries = overriddenClientConfig.getPropertyAsInteger(CommonClientConfigKey.MaxAutoRetries, maxAutoRetries);
            } catch (Exception e) {
                logger.warn("Invalid maxRetries requested for RestClient:" + this.clientName);
            }
        }
        return numRetries;
    }

    public boolean handleSameServerRetry(Server server, int currentRetryCount, int maxRetries, Throwable e) {
        if (currentRetryCount > maxRetries) {
            return false;
        }
        logger.debug("Exception while executing request which is deemed retry-able, retrying ..., SAME Server Retry Attempt#: {}",  
                currentRetryCount, server);
        return true;
    }

    public final RetryHandler getRetryHandler() {
        return defaultRetryHandler;
    }

    public final void setRetryHandler(RetryHandler retryHandler) {
        this.defaultRetryHandler = retryHandler;
    }

    public final boolean isOkToRetryOnAllOperations() {
        return okToRetryOnAllOperations;
    }

    public final void setOkToRetryOnAllOperations(boolean okToRetryOnAllOperations) {
        this.okToRetryOnAllOperations = okToRetryOnAllOperations;
    }
}
