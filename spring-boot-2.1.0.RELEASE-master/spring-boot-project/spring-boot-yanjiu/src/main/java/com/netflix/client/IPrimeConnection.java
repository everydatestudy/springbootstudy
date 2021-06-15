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

import com.netflix.loadbalancer.Server;

/**
 * prime：首要的，主要的，上乘的，优异的；priming a connection：启动连接； 该接口定义了启动连接的操作。 Interface
 * that defines operation for priming a connection.
 * 该接口在ribbon-loadbalancer包内并无任何实现类，在ribbon-httpclient包里有一个实现类HttpPrimeConnection：基于http协议实现链接到Server。因为ribbon-httpclient并不是本系列所要讲述的内容，但是呢它却作为默认的IPrimeConnection实现，并且Spring
 * Cloud里也使用它来启动连接，所以唠一唠。
 * 
 * @author awang
 *
 */
public interface IPrimeConnection extends IClientConfigAware {

	/**
	 * 子类应该实现协议特定的连接操作到服务器。 // server：待连接的服务器 // uriPath：进行连接时使用的uri。如/api/v1/ping
	 * Sub classes should implement protocol specific operation to connect to a
	 * server.
	 * 
	 * @param server  Server to connect
	 * @param uriPath URI to use in server connection
	 * @return if the priming is successful
	 * @throws Exception Any network errors
	 */
	public boolean connect(Server server, String uriPath) throws Exception;

}
