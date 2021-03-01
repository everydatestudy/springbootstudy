package cglib;

import java.util.Arrays;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.cglib.proxy.Proxy;
import org.springframework.core.DecoratingProxy;

public class MainCglib {
	public static void main(String[] args) {
		ProxyFactory proxyFactory = new ProxyFactory(new Demoa());
		proxyFactory.addAdvice((MethodBeforeAdvice) (method, args1, target) -> {
			System.out.println("你被拦截了：方法名为：" + method.getName() + " 参数为--" + Arrays.asList(args1));
		});

		Demoa demo = (Demoa) proxyFactory.getProxy();
		// 你被拦截了：方法名为：hello 参数为--[]
		// this demo show
		demo.hello();
	}
}

//不要再实现接口,就会用CGLIB去代理
class Demoa {
	public void hello() {
		System.out.println("this demo show");
	}
}