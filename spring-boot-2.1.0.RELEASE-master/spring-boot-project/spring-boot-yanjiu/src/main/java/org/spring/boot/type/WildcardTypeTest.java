package org.spring.boot.type;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

//List<? extends Fruit>定义了上界,?类型是Fruit的子类
//List<? super Apple>定义了下界,?类型是Apple的父类
//
//遵循PECS原则
//
//如果要从集合中读取类型T的数据，并且不能写入，可以使用 ? extends 通配符；(Producer Extends)
//如果要从集合中写入类型T的数据，并且不需要读取，可以使用 ? super 通配符；(Consumer Super)
//如果既要存又要取，那么就不要使用任何通配符。
//
//通常会在传参的时候会用到该方式,可以视作为一种约束条件
//
//作者：兴浩
//链接：https://www.jianshu.com/p/ac1b6dd211d0
//来源：简书
//著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
public class WildcardTypeTest {

	private List<? extends String> listNum;

	private List<? super String> b;

	public static void testGetBounds(String field) throws NoSuchFieldException {

		System.out.println(field);
		Field fieldNum = WildcardTypeTest.class.getDeclaredField(field);
		Type typeNum = fieldNum.getGenericType();
		ParameterizedType parameterizedTypeTypeNum = (ParameterizedType) typeNum;
		Type[] typesNum = parameterizedTypeTypeNum.getActualTypeArguments();

		WildcardType wildcardType = (WildcardType) typesNum[0];
		{
			Type[] types = wildcardType.getUpperBounds();
			for (Type type : types) {
				System.out.println(type);
			}

			types = wildcardType.getLowerBounds();
			for (Type type : types) {
				System.out.println(type);
			}
		}

	}

	public static void testGetUpperBounds() throws NoSuchFieldException {
//		testGetBounds("listNum");
		testGetBounds("b");
	}

	public static void main(String[] args) throws NoSuchFieldException {
		testGetUpperBounds();
	}
}
