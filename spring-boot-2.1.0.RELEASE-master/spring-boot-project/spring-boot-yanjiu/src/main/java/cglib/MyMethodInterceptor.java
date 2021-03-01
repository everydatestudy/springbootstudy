package cglib;

import java.lang.reflect.Method;

import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

public class MyMethodInterceptor implements MethodInterceptor {

	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
		System.out.println("MyMethodInterceptor");
		// 注意这里的方法调用，不是用反射哦！！！
		Object object = proxy.invokeSuper(obj, args);
		return object;
	}
}

class aaInterceptor2 implements MethodInterceptor {

	@Override
	public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
		System.out.println("aaInterceptor2！！！");
		// 注意这里的方法调用，不是用反射哦！！！
		Object object = proxy.invokeSuper(obj, args);
		return object;
	}
}