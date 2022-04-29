package com.hmy.recycle;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;

//定义一个新的行为接口，这个行为准备作用在目标对象上
interface IOtherInte {
	void doOther();
}

//自己定义一个IntroductionInterceptor来实现IntroductionInterceptor接口
//注意：此处也实现了接口IOtherInte（这是类似于增强器部分）  相当于这个interptor目前就只处理 IOtherInte
public class SomeInteIntroductionInterceptor implements IntroductionInterceptor, IOtherInte {
	/**
	 * 判断调用的方法是否为指定类中的方法 如果Method代表了一个方法 那么调用它的invoke就相当于执行了它代表的这个方法
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		if (implementsInterface(invocation.getMethod().getDeclaringClass())) {
			System.out.println("我是引介增强的方法体~~~invoke");
			return invocation.getMethod().invoke(this, invocation.getArguments());
		}
		return invocation.proceed();
	}

	/**
	 * 判断clazz是否为给定接口IOtherBean的实现
	 */
	@Override
	public boolean implementsInterface(Class clazz) {
		return clazz.isAssignableFrom(IOtherInte.class);
	}

	@Override
	public void doOther() {
		System.out.println("给人贴标签 doOther...");
	}

//方法测试
	public static void main(String[] args) {
		ProxyFactory factory = new ProxyFactory(new Person());
		factory.setProxyTargetClass(true); // 强制私用CGLIB 以保证我们的Person方法也能正常调用

		// 此处采用IntroductionInterceptor 这个引介增强的拦截器
		Advice advice = new SomeInteIntroductionInterceptor();

		// 切点+通知（注意：此处放的是复合切面）
		Advisor advisor = new DefaultIntroductionAdvisor((DynamicIntroductionAdvice) advice, IOtherInte.class);
		// Advisor advisor = new DefaultPointcutAdvisor(cut, advice);
		factory.addAdvisor(advisor);

		IOtherInte otherInte = (IOtherInte) factory.getProxy();
		otherInte.doOther();

		System.out.println("===============================");

		// Person本身自己的方法 也得到了保留
		Person p = (Person) factory.getProxy();
		p.run();
		p.say();
	}
}