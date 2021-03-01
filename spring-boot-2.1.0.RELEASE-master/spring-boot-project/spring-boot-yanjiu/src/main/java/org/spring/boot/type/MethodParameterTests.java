package org.spring.boot.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;
import org.springframework.core.MethodParameter;

//Spring对方法的参数做了一个抽象封装,如参数和返回值都可以认为是一个MethodParameter
//可以使用MethodParameter.forMethodOrConstructor静态方法来构建一个MethodParameter
//
//作者：兴浩
//链接：https://www.jianshu.com/p/7e2f0adbd164
//来源：简书
//著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
public class MethodParameterTests {

    public static class TestItem
    {
        public TestItem(List<String> list)
        {

        }

        public void m1(List<Integer> intParam)
        {

        }
    }

    @Test
    public void test() throws NoSuchMethodException {

        {
            Method method=TestItem.class.getMethod("m1",List.class);
            MethodParameter parameter=MethodParameter.forMethodOrConstructor(method,0);
            System.out.println(parameter.getParameterType());
            System.out.println(parameter.getGenericParameterType());
        }

        {
            Constructor constructor=TestItem.class.getConstructor(List.class);
            MethodParameter parameter=MethodParameter.forMethodOrConstructor(constructor,0);
            System.out.println(parameter.getParameterType());
            System.out.println(parameter.getGenericParameterType());
        }
    }
}
