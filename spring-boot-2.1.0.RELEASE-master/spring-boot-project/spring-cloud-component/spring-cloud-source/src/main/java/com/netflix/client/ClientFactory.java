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

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.servo.monitor.Monitors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A factory that creates client, load balancer and client configuration instances from properties. It also keeps mappings of client names to 
 * the  created instances.
 * 
 * @author awang
 *
 */
public class ClientFactory {
	// key是ClientName  value是Client实例
    private static Map<String, IClient<?,?>> simpleClientMap = new ConcurrentHashMap<String, IClient<?,?>>();
    // key是Lb的名称  value是LB实例
    private static Map<String, ILoadBalancer> namedLBMap = new ConcurrentHashMap<String, ILoadBalancer>();
    private static ConcurrentHashMap<String, IClientConfig> namedConfig = new ConcurrentHashMap<String, IClientConfig>();
    
    private static Logger logger = LoggerFactory.getLogger(ClientFactory.class);
    
    /**  提供名称和客户端配置的实用程序方法来创建客户端和负载均衡器(如果在客户端配置中启用)
	// InitializeNFLoadBalancer默认配置值是true，开启负载均衡器的
     * Utility method to create client and load balancer (if enabled in client config) given the name and client config. 
     * Instances are created using reflection (see {@link #instantiateInstanceWithClientConfig(String, IClientConfig)}
     * 
     * @param restClientName
     * @param clientConfig
     * @throws ClientException if any errors occurs in the process, or if the client with the same name already exists
     */
    public static synchronized IClient<?, ?> registerClientFromProperties(String restClientName, IClientConfig clientConfig) throws ClientException { 
    	IClient<?, ?> client = null;
    	ILoadBalancer loadBalancer = null;
    	if (simpleClientMap.get(restClientName) != null) {
    		throw new ClientException(
    				ClientException.ErrorType.GENERAL,
    				"A Rest Client with this name is already registered. Please use a different name");
    	}
    	try {
    		String clientClassName = (String) clientConfig.getProperty(CommonClientConfigKey.ClientClassName);
    		client = (IClient<?, ?>) instantiateInstanceWithClientConfig(clientClassName, clientConfig);
    		boolean initializeNFLoadBalancer = Boolean.parseBoolean(clientConfig.getProperty(
    				CommonClientConfigKey.InitializeNFLoadBalancer, DefaultClientConfigImpl.DEFAULT_ENABLE_LOADBALANCER).toString());
    		if (initializeNFLoadBalancer) {
    			loadBalancer  = registerNamedLoadBalancerFromclientConfig(restClientName, clientConfig);
    		}
    		if (client instanceof AbstractLoadBalancerAwareClient) {
    			((AbstractLoadBalancerAwareClient) client).setLoadBalancer(loadBalancer);
    		}
    	} catch (Throwable e) {
    		String message = "Unable to InitializeAndAssociateNFLoadBalancer set for RestClient:"
    				+ restClientName;
    		logger.warn(message, e);
    		throw new ClientException(ClientException.ErrorType.CONFIGURATION, 
    				message, e);
    	}
    	simpleClientMap.put(restClientName, client);

    	Monitors.registerObject("Client_" + restClientName, client);

    	logger.info("Client Registered:" + client.toString());
    	return client;
    }

    /**
     * Return the named client from map if already created. Otherwise creates the client using the configuration returned by {@link #getNamedConfig(String)}.
     * 
     * @throws RuntimeException if an error occurs in creating the client.
     */
    public static synchronized IClient getNamedClient(String name) {
        return getNamedClient(name, DefaultClientConfigImpl.class);
    }

    /**
     * Return the named client from map if already created. Otherwise creates the client using the configuration returned by {@link #createNamedClient(String, Class)}.
     * 
     * @throws RuntimeException if an error occurs in creating the client.
     */
    public static synchronized IClient getNamedClient(String name, Class<? extends IClientConfig> configClass) {
    	if (simpleClientMap.get(name) != null) {
    	    return simpleClientMap.get(name);
    	}
        try {
            return createNamedClient(name, configClass);
        } catch (ClientException e) {
            throw new RuntimeException("Unable to create client", e);
        }
    }
    
    /**
     * Creates a named client using a IClientConfig instance created off the configClass class object passed in as the parameter.
     *  
     * @throws ClientException if any error occurs, or if the client with the same name already exists
     */
    public static synchronized IClient createNamedClient(String name, Class<? extends IClientConfig> configClass) throws ClientException {
    	IClientConfig config = getNamedConfig(name, configClass);
        return registerClientFromProperties(name, config);
    }
    
    /**
     * Get the load balancer associated with the name, or create one with an instance {@link DefaultClientConfigImpl} if does not exist
     * 
     * @throws RuntimeException if any error occurs
     */
    public static synchronized ILoadBalancer getNamedLoadBalancer(String name) {
    	return getNamedLoadBalancer(name, DefaultClientConfigImpl.class);
    }
    
    /**
     * Get the load balancer associated with the name, or create one with an instance of configClass if does not exist
     * 
     * @throws RuntimeException if any error occurs
     * @see #registerNamedLoadBalancerFromProperties(String, Class)
     */
    public static synchronized ILoadBalancer getNamedLoadBalancer(String name, Class<? extends IClientConfig> configClass) {
        ILoadBalancer lb = namedLBMap.get(name);
        if (lb != null) {
            return lb;
        } else {
            try {
                lb = registerNamedLoadBalancerFromProperties(name, configClass);
            } catch (ClientException e) {
                throw new RuntimeException("Unable to create load balancer", e);
            }
            return lb;
        }
    }

    /**
     * Create and register a load balancer with the name and given the class of configClass.
     *
     * @throws ClientException if load balancer with the same name already exists or any error occurs
     * @see #instantiateInstanceWithClientConfig(String, IClientConfig)
     */
    public static ILoadBalancer registerNamedLoadBalancerFromclientConfig(String name, IClientConfig clientConfig) throws ClientException {
        if (namedLBMap.get(name) != null) {
            throw new ClientException("LoadBalancer for name " + name + " already exists");
        }
    	ILoadBalancer lb = null;
        try {
            String loadBalancerClassName = (String) clientConfig.getProperty(CommonClientConfigKey.NFLoadBalancerClassName);
            lb = (ILoadBalancer) ClientFactory.instantiateInstanceWithClientConfig(loadBalancerClassName, clientConfig);                                    
            namedLBMap.put(name, lb);            
            logger.info("Client: {} instantiated a LoadBalancer: {}", name, lb);
            return lb;
        } catch (Throwable e) {           
           throw new ClientException("Unable to instantiate/associate LoadBalancer with Client:" + name, e);
        }    	
    }
    
    /**
     * Create and register a load balancer with the name and given the class of configClass.
     *
     * @throws ClientException if load balancer with the same name already exists or any error occurs
     * @see #instantiateInstanceWithClientConfig(String, IClientConfig)
     */
    public static synchronized ILoadBalancer registerNamedLoadBalancerFromProperties(String name, Class<? extends IClientConfig> configClass) throws ClientException {
        if (namedLBMap.get(name) != null) {
            throw new ClientException("LoadBalancer for name " + name + " already exists");
        }
        IClientConfig clientConfig = getNamedConfig(name, configClass);
        return registerNamedLoadBalancerFromclientConfig(name, clientConfig);
    }    

    /**反射创建一个实例，并且调用其initWithNiwsConfig()方法把config传递给它
     * Creates instance related to client framework using reflection. It first checks if the object is an instance of 
     * {@link IClientConfigAware} and if so invoke {@link IClientConfigAware#initWithNiwsConfig(IClientConfig)}. If that does not
     * apply, it tries to find if there is a constructor with {@link IClientConfig} as a parameter and if so invoke that constructor. If neither applies,
     * it simply invokes the no-arg constructor and ignores the clientConfig parameter. 
     *  
     * @param className Class name of the object
     * @param clientConfig IClientConfig object used for initialization.
     */
    @SuppressWarnings("unchecked")
	public static Object instantiateInstanceWithClientConfig(String className, IClientConfig clientConfig) 
    		throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    	Class clazz = Class.forName(className);
    	if (IClientConfigAware.class.isAssignableFrom(clazz)) {
    		IClientConfigAware obj = (IClientConfigAware) clazz.newInstance();
    		obj.initWithNiwsConfig(clientConfig);
    		return obj;
    	} else {
    		try {
    		    if (clazz.getConstructor(IClientConfig.class) != null) {
    		    	return clazz.getConstructor(IClientConfig.class).newInstance(clientConfig);
    		    }
    		} catch (NoSuchMethodException ignored) {
    		    // OK for a class to not take an IClientConfig
    		} catch (SecurityException | IllegalArgumentException | InvocationTargetException e) { 
    		    logger.warn("Error getting/invoking IClientConfig constructor of {}", className, e);
    		}    		
    	}
    	logger.warn("Class " + className + " neither implements IClientConfigAware nor provides a constructor with IClientConfig as the parameter. Only default constructor will be used.");
    	return clazz.newInstance();
    }
    
    /**反射创建一个clientConfigClass类型的配置。IClientConfig接口的自带实现仅有DefaultClientConfigImpl
	// 注意config.loadProperties(name)方法会被调用哦（配置会被加载进来）
	// 最后放进缓存（缓存里就返回缓存里的）
     * Get the client configuration given the name or create one with {@link DefaultClientConfigImpl} if it does not exist.
     * 
     * @see #getNamedConfig(String, Class)
     */
    public static IClientConfig getNamedConfig(String name) {
        return 	getNamedConfig(name, DefaultClientConfigImpl.class);
    }
    
    /**
     * Get the client configuration given the name or create one with clientConfigClass if it does not exist. An instance of IClientConfig
     * is created and {@link IClientConfig#loadProperties(String)} will be called.
     */
    public static IClientConfig getNamedConfig(String name, Class<? extends IClientConfig> clientConfigClass) {
        IClientConfig config = namedConfig.get(name);
        if (config != null) {
            return config;
        } else {
            try {
                config = (IClientConfig) clientConfigClass.newInstance();
                config.loadProperties(name);
            } catch (InstantiationException | IllegalAccessException e) {
                logger.error("Unable to create named client config '{}' instance for config class {}", name,
                        clientConfigClass, e);
                return null;
            }
            config.loadProperties(name);
            IClientConfig old = namedConfig.putIfAbsent(name, config);
            if (old != null) {
                config = old;
            }
            return config;
        }
    }
}
