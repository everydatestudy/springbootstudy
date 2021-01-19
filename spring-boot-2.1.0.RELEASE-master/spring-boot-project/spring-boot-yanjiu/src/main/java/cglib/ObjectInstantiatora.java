package cglib;

import org.springframework.objenesis.Objenesis;
import org.springframework.objenesis.ObjenesisStd;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

public class ObjectInstantiatora {
	public static void main(String[] args) throws Exception {
		Objenesis objenesis = new ObjenesisStd();
		// 相当于生成了一个实例创建的工厂，接下来就可以很方便得创建实例了
		// 如果你要创建多个实例，建议这么来创建
		ObjectInstantiator<MyDemo> instantiator = objenesis.getInstantiatorOf(MyDemo.class);

		MyDemo myDemo1 = instantiator.newInstance();
		MyDemo myDemo2 = instantiator.newInstance();
		System.out.println(myDemo1);
		System.out.println(myDemo1.code); // null
		System.out.println(myDemo2);
	}
}
