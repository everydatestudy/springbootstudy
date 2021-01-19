package cglib;

import org.springframework.objenesis.Objenesis;
import org.springframework.objenesis.ObjenesisStd;

/**
 * Objenesis是一个Java的库，主要用来创建特定的对象。
 * 
 * 由于不是所有的类都有无参构造器又或者类构造器是private，在这样的情况下，如果我们还想实例化对象，class.newInstance是无法满足的。
 * 
 * @author Administrator
 *
 */
public class ObjenesisTest {
	public static void main(String[] args) throws Exception {
		Objenesis objenesis = new ObjenesisStd();
		// 它竟然创建成功了
		MyDemo myDemo = objenesis.newInstance(MyDemo.class);
		System.out.println(myDemo); // com.fsx.maintest.MyDemo@1f32e575
		System.out.println(myDemo.code); // null 特别注意：这里是null，而不是10

		// 若直接这样创建 就报错 java.lang.InstantiationException: com.fsx.maintest.MyDemo
		//System.out.println(MyDemo.class.newInstance());
	}
}
class MyDemo {
    public String code = "10";

    public MyDemo(String code) {
        this.code = code;
    }
}