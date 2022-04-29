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

import com.netflix.util.Pair;

/**
 * Class that represents a typical Server (or an addressable Node) i.e. a
 * Host:port identifier
 * 
 * @author stonse
 * 
 */
public class Server {

	/**
	 * Additional meta information of a server, which contains information of the
	 * targeting application, as well as server identification specific for a
	 * deployment environment, for example, AWS.
	 */
	public static interface MetaInfo {
		/**
		 * @return the name of application that runs on this server, null if not
		 *         available
		 */
		public String getAppName();

		/**
		 * @return the group of the server, for example, auto scaling group ID in AWS.
		 *         Null if not available
		 */
		public String getServerGroup();

		/**
		 * @return A virtual address used by the server to register with discovery
		 *         service. Null if not available
		 */
		public String getServiceIdForDiscovery();

		/**
		 * @return ID of the server
		 */
		public String getInstanceId();
	}
	// 未知Zone区域，这是每台Server的默认区域
	public static final String UNKNOWN_ZONE = "UNKNOWN";
	private String host;
	private int port = 80;
	// 有可能是http/https  也有可能是tcp、udp等
	private String scheme;
	// id表示唯一。host + ":" + port -> localhost:8080  
	// 注意没有http://前缀    只有host和端口
	// getInstanceId实例id使用的就是它。因为ip+端口可以唯一确定一个实例
	private volatile String id;
	// 标记是否这台机器是否是活着的
   	// =========请注意：它的默认值是false=========
	private volatile boolean isAliveFlag;
	private String zone = UNKNOWN_ZONE;
	 // 标记这台机器是否可以准好可以提供服务了（活着并不代表可以提供服务了）
	private volatile boolean readyToServe = true;

	private MetaInfo simpleMetaInfo = new MetaInfo() {
		@Override
		public String getAppName() {
			return null;
		}

		@Override
		public String getServerGroup() {
			return null;
		}

		@Override
		public String getServiceIdForDiscovery() {
			return null;
		}

		@Override
		public String getInstanceId() {
			return id;
		}
	};

	public Server(String host, int port) {
		this(null, host, port);
	}

	public Server(String scheme, String host, int port) {
		this.scheme = scheme;
		this.host = host;
		this.port = port;
		this.id = host + ":" + port;
		isAliveFlag = false;
	}

	/* host:port combination */
	public Server(String id) {
		setId(id);
		isAliveFlag = false;
	}

	// No reason to synchronize this, I believe.
	// The assignment should be atomic, and two setAlive calls
	// with conflicting results will still give nonsense(last one wins)
	// synchronization or no.
	// 此方法并非是synchronization同步的，所以其实存在线程不安全的情况
		// （volatile解决不了线程同步问题）
		// 官方解释是：遵照last win的原则也是合理的
	public void setAlive(boolean isAliveFlag) {
		this.isAliveFlag = isAliveFlag;
	}

	public boolean isAlive() {
		return isAliveFlag;
	}

	@Deprecated
	public void setHostPort(String hostPort) {
		setId(hostPort);
	}
	// 规范化id，依赖于上面的getHostPort()方法
		// 任何uri（id）最终都会被规范为 ip + ":" + port的方式
	static public String normalizeId(String id) {
		Pair<String, Integer> hostPort = getHostPort(id);
		if (hostPort == null) {
			return null;
		} else {
			return hostPort.first() + ":" + hostPort.second();
		}
	}

	private static String getScheme(String id) {
		if (id != null) {
			if (id.toLowerCase().startsWith("http://")) {
				return "http";
			} else if (id.toLowerCase().startsWith("https://")) {
				return "https";
			}
		}
		return null;
	}
	// 从字符串里解析传ip和端口号
	// http://www.baidu.com -> www.baidu.com + 80
	// https://www.baidu.com/api/v1/node -> www.baidu.com + 443
	// localhost:8080 -> localhost + 8080
	static Pair<String, Integer> getHostPort(String id) {
		if (id != null) {
			String host = null;
			int port = 80;

			if (id.toLowerCase().startsWith("http://")) {
				id = id.substring(7);
				port = 80;
			} else if (id.toLowerCase().startsWith("https://")) {
				id = id.substring(8);
				port = 443;
			}

			if (id.contains("/")) {
				int slash_idx = id.indexOf("/");
				id = id.substring(0, slash_idx);
			}

			int colon_idx = id.indexOf(':');

			if (colon_idx == -1) {
				host = id; // default
			} else {
				host = id.substring(0, colon_idx);
				try {
					port = Integer.parseInt(id.substring(colon_idx + 1));
				} catch (NumberFormatException e) {
					throw e;
				}
			}
			return new Pair<String, Integer>(host, port);
		} else {
			return null;
		}

	}

	public void setId(String id) {
		Pair<String, Integer> hostPort = getHostPort(id);
		if (hostPort != null) {
			this.id = hostPort.first() + ":" + hostPort.second();
			this.host = hostPort.first();
			this.port = hostPort.second();
			this.scheme = getScheme(id);
		} else {
			this.id = null;
		}
	}

	public void setSchemea(String scheme) {
		this.scheme = scheme;
	}

	public void setPort(int port) {
		this.port = port;

		if (host != null) {
			id = host + ":" + port;
		}
	}

	public void setHost(String host) {
		if (host != null) {
			this.host = host;
			id = host + ":" + port;
		}
	}

	public String getId() {
		return id;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getScheme() {
		return scheme;
	}

	public String getHostPort() {
		return host + ":" + port;
	}

	public MetaInfo getMetaInfo() {
		return simpleMetaInfo;
	}

	public String toString() {
		return this.getId();
	}

	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof Server))
			return false;
		Server svc = (Server) obj;
		return svc.getId().equals(this.getId());

	}

	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + (null == this.getId() ? 0 : this.getId().hashCode());
		return hash;
	}

	public final String getZone() {
		return zone;
	}

	public final void setZone(String zone) {
		this.zone = zone;
	}

	public final boolean isReadyToServe() {
		return readyToServe;
	}

	public final void setReadyToServe(boolean readyToServe) {
		this.readyToServe = readyToServe;
	}
}
