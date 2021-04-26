package demo;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
//https://mp.weixin.qq.com/s?__biz=MzIwMTY3NjY3MA==&mid=2247484393&idx=1&sn=838adbeb156348e082b90640c065936b&chksm=96eb01f8a19c88ee6f2d87bc9a873ad4be8dfb3e487a9c3f097e76fe050a2f1cd63e884d598a&cur_album_id=1344363372518735874&scene=189#rd
public class Pub {
	public static void main(String[] args) throws NacosException, InterruptedException {
	//	NacosServiceAutoConfiguration ffs;
		// 发布的服务名
		com.alibaba.cloud.nacos.ribbon.RibbonNacosAutoConfiguration dd;
		String serviceName = "helloworld.services";
		// 构造一个nacos实例，入参是nacos server的ip和服务端口
		// namingService = NacosFactory.createNamingService(getNacosProperties());
		NamingService naming = NamingFactory.createNamingService("127.0.0.1:8848");
		// 发布一个服务，该服务对外提供的ip为127.0.0.1，端口为8888
		naming.registerInstance(serviceName, "127.0.0.1", 8888);
		Thread.sleep(Integer.MAX_VALUE);
	}
}
