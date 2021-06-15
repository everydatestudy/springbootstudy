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

/**
 * 该接口用于过滤Server列表们，接口描述很简单，难的是过滤规则。 This interface allows for filtering the
 * configured or dynamically obtained List of candidate servers with desirable
 * characteristics.
 * 
 * @author stonse
 * 
 * @param <T>
 */
public interface ServerListFilter<T extends Server> {
	// 返回的是一个过滤后的列表
	// 可能是原列表，也可能是新的列表~~~~~~~
	public List<T> getFilteredListOfServers(List<T> servers);

}
