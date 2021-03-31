/*
 * Copyright 2012-2017 the original author or authors.
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

package org.springframework.boot.jdbc.metadata;

import javax.sql.DataSource;

/**
 * Provides access meta-data that is commonly available from most pooled
 * {@link DataSource} implementations.
 *
 * @author Stephane Nicoll
 * @since 2.0.0
 */
public interface DataSourcePoolMetadata {

	/**
	 * 返回当前数据库连接池的使用量,返回值在0至1之间(或者是-1,如果当前数据库连接池没有限制的话)
	 * <li>1 --> 该数据库连接池的链接数已达到最大数目</li>
	 * <li>0 --> 该数据库连接池的链接数</li>
	 * <li>-1 -->该数据库连接池的链接数没有限制 </li>
	 * </li>
	 * </ul>
	 * 还有可能返回null，如果当前的数据库链接池不提供必要的信息进行计算的话
	 */
	/**
	 * Return the usage of the pool as value between 0 and 1 (or -1 if the pool is not
	 * limited).
	 * <ul>
	 * <li>1 means that the maximum number of connections have been allocated</li>
	 * <li>0 means that no connection is currently active</li>
	 * <li>-1 means there is not limit to the number of connections that can be allocated
	 * </li>
	 * </ul>
	 * This may also return {@code null} if the data source does not provide the necessary
	 * information to compute the poll usage.
	 * @return the usage value or {@code null}
	 */
	Float getUsage();

	/**返回当前数据库连接池中已经在使用中的(激活)链接或者返回null,如果该信息不可用的话
	 * Return the current number of active connections that have been allocated from the
	 * data source or {@code null} if that information is not available.
	 * @return the number of active connections or {@code null}
	 */
	Integer getActive();

	/**返回当前数据库连接池可分配的最大链接数, 返回-1 意味着没有限制,返回null,意味着当前信息不可用
	 * Return the maximum number of active connections that can be allocated at the same
	 * time or {@code -1} if there is no limit. Can also return {@code null} if that
	 * information is not available.
	 * @return the maximum number of active connections or {@code null}
	 */
	Integer getMax();

	/**返回当前数据库连接池可分配的最小链接数, 返回null,意味着当前信息不可用
	 * Return the minimum number of idle connections in the pool or {@code null} if that
	 * information is not available.
	 * @return the minimum number of active connections or {@code null}
	 */
	Integer getMin();

	/**返回用来检查当前链接是否可以的sql,返回null-->当前信息不可用
	 * Return the query to use to validate that a connection is valid or {@code null} if
	 * that information is not available.
	 * @return the validation query or {@code null}
	 */
	String getValidationQuery();

	/**
	 * The default auto-commit state of connections created by this pool. If not set
	 * ({@code null}), default is JDBC driver default (If set to null then the
	 * java.sql.Connection.setAutoCommit(boolean) method will not be called.)
	 * @return the default auto-commit state or {@code null}
	 */
	Boolean getDefaultAutoCommit();

}
