package cglib;

import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;

public class CgLibProxy {
	public static void main(String[] args) {
		// 在指定目录下生成动态代理类，我们可以反编译看一下里面到底是一些什么东西
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "D:\\home\\cglib");

		// 创建Enhancer对象，类似于JDK动态代理的Proxy类，下一步就是设置几个参数
		Enhancer enhancer = new Enhancer();
		// 设置目标类的字节码文件
		enhancer.setSuperclass(Dog.class);
		// 设置回调函数
		Callback[] callbacks = new Callback[] {

				new aaInterceptor2() };
//	 	enhancer .setCallbackFilter(new MyMethodInterceptor());
		enhancer.setCallback(new MyMethodInterceptor());
		// 这里的creat方法就是正式创建代理类
		Dog proxyDog = (Dog) enhancer.create();
		// 调用代理类的eat方法
		proxyDog.eat();
	}
}