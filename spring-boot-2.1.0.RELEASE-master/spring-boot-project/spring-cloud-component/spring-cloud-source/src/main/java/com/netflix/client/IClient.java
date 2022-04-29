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

import com.netflix.client.config.IClientConfig;


/**该接口表示可以执行单个请求的客户端：发送请求Request，获得响应Response，注意并没有绑定任何协议哦（http、tcp、udp、文件协议、本地调用都是阔仪的）。
 * A client that can execute a single request. 
 * 
 * @author awang
 *
 */
public interface IClient<S extends ClientRequest, T extends IResponse> {

	/**
	 * Execute the request and return the response. It is expected that there is no retry and all exceptions
     * are thrown directly.
	 */
    public T execute(S request, IClientConfig requestConfig) throws Exception; 
}
