package cglib;

import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

public class MainTest {
	public static void main(String[] args) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(MyDemo.class);
		// 注意此处得MethodInterceptor是cglib包下的 AOP联盟里还有一个MethodInterceptor
		enhancer.setCallback((MethodInterceptor) (o, method, args1, methodProxy) -> {
			System.out.println(method.getName() + "---方法拦截前");
			// 此处千万不能调用method得invoke方法，否则会死循环的 只能使用methodProxy.invokeSuper 进行调用
			// Object result = method.invoke(o, args1);
			Object result = methodProxy.invokeSuper(o, args1);
			System.out.println(method.getName() + "---方法拦截后");
			return result;
		});

		// MyDemo myDemo = (MyDemo) enhancer.create(); // 这里是要求必须有空的构造函数的
		MyDemo myDemo = (MyDemo) enhancer.create(new Class[] { String.class }, new Object[] { "fsx" });
		// 直接打印：默认会调用toString方法以及hashCode方法 此处都被拦截了
		System.out.println(myDemo);
		// System.out.println(myDemo.code);

	}
}
