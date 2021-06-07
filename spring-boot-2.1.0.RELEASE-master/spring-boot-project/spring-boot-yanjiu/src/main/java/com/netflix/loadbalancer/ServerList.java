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

import java.util.List;

/** T并不规定必须是Server，也可以是它的子类
// 但实际情况是：一般并不会自己去继承Server来玩，因为Server已经够抽象了
 * Interface that defines the methods sed to obtain the List of Servers
 * @author stonse
 *
 * @param <T>
 */
public interface ServerList<T extends Server> {
	// 返回初始状态的服务器列表。比如初试配置了10台那它永远是10个Server
    public List<T> getInitialListOfServers();
    
    /**返回更新后的服务列表。它会周期ping过后，返回活着的
     * Return updated list of servers. This is called say every 30 secs
     * (configurable) by the Loadbalancer's Ping cycle
     * 
     */
    public List<T> getUpdatedListOfServers();   

}
