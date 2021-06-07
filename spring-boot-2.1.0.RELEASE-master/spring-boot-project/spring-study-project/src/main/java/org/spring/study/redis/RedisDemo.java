package org.spring.study.redis;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

public class RedisDemo {
	public static void main(String[] args) {
		jedisPool() ;
	}

	public void oneJedis() {
		Jedis jedis = new Jedis("127.0.0.1", 6379);
		System.out.println(jedis.keys("*"));
		jedis.close();
	}

	public static void jedisPool() {
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
		JedisPool jedisPool = new JedisPool(poolConfig, "127.0.0.1", 6379);
		Jedis jedis = jedisPool.getResource();
		System.out.println(jedis);
		jedisPool.close();
	}

	public static void cluster() {
		Set<HostAndPort> nodes = new HashSet<>();

		nodes.add(new HostAndPort("127.0.0.1", 7001));

		nodes.add(new HostAndPort("127.0.0.1", 7002));

		nodes.add(new HostAndPort("127.0.0.1", 7003));

		nodes.add(new HostAndPort("127.0.0.1", 7004));

		nodes.add(new HostAndPort("127.0.0.1", 7005));

		nodes.add(new HostAndPort("127.0.0.1", 7006));

		JedisCluster cluster = new JedisCluster(nodes);
	}
}
