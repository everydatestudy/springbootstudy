package cglib;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.cglib.core.ClassGenerator;
import org.springframework.cglib.core.DefaultGeneratorStrategy;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.CallbackHelper;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.transform.impl.UndeclaredThrowableStrategy;
import org.springframework.lang.Nullable;
import org.springframework.objenesis.Objenesis;
import org.springframework.objenesis.SpringObjenesis;

public class TestEnhancer {
	public static void main(String[] args) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(MyDemo.class);

		// 如国实用createClass方式来创建代理的实例 是不能直接添加callback得
		// enhancer.setCallback();
		enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
		enhancer.setStrategy(new DefaultGeneratorStrategy());
		enhancer.setCallbackFilter(new CallbackFilter() {

			@Override
			public int accept(Method var1) {
				System.out.println("aaaaaaaaaaaaaaaaaaaaaaaaaaas");
				return 0;
			}
		});
		enhancer.setCallbackTypes(new Class[] { MethodInterceptor.class });

		// 这里我们只生成Class字节码，并不去创建对象
		Class<MyDemo> clazz = enhancer.createClass();
		// 这里我们只生成Class字节码，并不去创建对象

		// 创建对象的操作交给
		Callback[] mainCallbacks = new Callback[] { new MyMethodInterceptor() };
		Objenesis objenesis = new SpringObjenesis();
		MyDemo myDemo = (MyDemo) objenesis.newInstance(clazz);
		((Factory) myDemo).setCallbacks(mainCallbacks);
		myDemo.toString();
	}

	protected Enhancer createEnhancer() {
		return new Enhancer();
	}

}
