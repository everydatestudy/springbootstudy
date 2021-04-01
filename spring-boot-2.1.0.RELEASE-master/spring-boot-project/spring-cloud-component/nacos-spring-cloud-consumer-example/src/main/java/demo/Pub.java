package demo;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;

public class Pub {
	public static void main(String[] args) throws NacosException, InterruptedException {
		// 发布的服务名
		String serviceName = "helloworld.services";
		// 构造一个nacos实例，入参是nacos server的ip和服务端口
		 //	   namingService = NacosFactory.createNamingService(getNacosProperties());
		NamingService naming = NamingFactory.createNamingService("66.10.111.33:8848");
		// 发布一个服务，该服务对外提供的ip为127.0.0.1，端口为8888
		naming.registerInstance(serviceName, "127.0.0.1", 8888);
		Thread.sleep(Integer.MAX_VALUE);
	}
}
