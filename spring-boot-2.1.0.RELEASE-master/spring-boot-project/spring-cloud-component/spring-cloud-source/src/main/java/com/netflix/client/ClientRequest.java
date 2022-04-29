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
package com.netflix.client;

import java.net.URI;

import com.netflix.client.config.IClientConfig;

/**
 * An object that represents a common client request that is suitable for all communication protocol. 
 * It is expected that this object is immutable.
 * 
 * @author awang
 *
 */
public class ClientRequest implements Cloneable {
	// 请求的URI
    protected URI uri;
    protected Object loadBalancerKey = null;
    // 是否是可重试。true：该请求可重试   false：该请求不可重试
    protected Boolean isRetriable = null;
    // 外部传进来的配置，可以覆盖内置的IClientConfig配置哦
    protected IClientConfig overrideConfig;
        
    public ClientRequest() {
    }
    
    public ClientRequest(URI uri) {
        this.uri = uri;
    }

    /**
     * Constructor to set all fields. 
     * @deprecated request configuration should be now be passed 
     *            as a method parameter to client's execution API 
     *
     * 
     * @param uri  URI to set
     * @param loadBalancerKey the object that is used by {@code com.netflix.loadbalancer.ILoadBalancer#chooseServer(Object)}, can be null
     * @param isRetriable if the operation is retriable on failures
     * @param overrideConfig client configuration that is used for this specific request. can be null. 
     */
    @Deprecated
    public ClientRequest(URI uri, Object loadBalancerKey, boolean isRetriable, IClientConfig overrideConfig) {
        this.uri = uri;
        this.loadBalancerKey = loadBalancerKey;
        this.isRetriable = isRetriable;
        this.overrideConfig = overrideConfig;
    }

    public ClientRequest(URI uri, Object loadBalancerKey, boolean isRetriable) {
        this.uri = uri;
        this.loadBalancerKey = loadBalancerKey;
        this.isRetriable = isRetriable;
    }
    
    public ClientRequest(ClientRequest request) {
        this.uri = request.uri;
        this.loadBalancerKey = request.loadBalancerKey;
        this.overrideConfig = request.overrideConfig;
        this.isRetriable = request.isRetriable;
    }

    public final URI getUri() {
        return uri;
    }
    

    protected final ClientRequest setUri(URI uri) {
        this.uri = uri;
        return this;
    }

    public final Object getLoadBalancerKey() {
        return loadBalancerKey;
    }

    protected final ClientRequest setLoadBalancerKey(Object loadBalancerKey) {
        this.loadBalancerKey = loadBalancerKey;
        return this;
    }

    public boolean isRetriable() {
        return (Boolean.TRUE.equals(isRetriable));
    }

    protected final ClientRequest setRetriable(boolean isRetriable) {
        this.isRetriable = isRetriable;
        return this;
    }

    /**
     * @deprecated request configuration should be now be passed 
     *            as a method parameter to client's execution API 
     */
    @Deprecated
    public final IClientConfig getOverrideConfig() {
        return overrideConfig;
    }

    /**
     * @deprecated request configuration should be now be passed 
     *            as a method parameter to client's execution API 
     */
    @Deprecated
    protected final ClientRequest setOverrideConfig(IClientConfig overrideConfig) {
        this.overrideConfig = overrideConfig;
        return this;
    }
    
    /**使用新的URI创建一个**新的**ClientRequest
	// 它会先用clone方法去克隆一个，若抛错那就new ClientRequest(this) new一个实例
	// 推荐子类复写此方法，提供更多、更有效的实施。
     * Create a client request using a new URI. This is used by {@code com.netflix.client.AbstractLoadBalancerAwareClient#computeFinalUriWithLoadBalancer(ClientRequest)}.
     * It first tries to clone the request and if that fails it will use the copy constructor {@link #ClientRequest(ClientRequest)}.
     * Sub classes are recommended to override this method to provide more efficient implementation.
     * 
     * @param newURI
     */
    public ClientRequest replaceUri(URI newURI) {
        ClientRequest req;
        try {
            req = (ClientRequest) this.clone();
        } catch (CloneNotSupportedException e) {
            req = new ClientRequest(this);
        }
        req.uri = newURI;
        return req;
    }
}
