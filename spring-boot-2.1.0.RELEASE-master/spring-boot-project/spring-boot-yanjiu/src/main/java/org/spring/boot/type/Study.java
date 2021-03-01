package org.spring.boot.type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
//比如ArrayList<String>
//
//3.1 getActualTypeArguments返回是一个数组,getActualTypeArguments[0]就是String类型
//3.2 ArrayList就是getRawType
//3.3 getOwnerType属于nested类型,使用场景比较少,不深究
import java.util.ArrayList;
import java.util.Map;

import org.junit.Test;

public class Study {
	public static void main(String[] args) {
//		ParameterizedType type = Study.class;
//		System.out.println(type);
	}

	@Test
	public void test1() {
		ArrayList<Map<String, String>> foo = new ArrayList<>();
		
		ParameterizedType mySuperClass = (ParameterizedType) foo.getClass().getGenericSuperclass();
		Type type = mySuperClass.getActualTypeArguments()[0];
		System.out.println(type);

		System.out.println(mySuperClass.getOwnerType());
	}

	interface Mymap<K, V> {
		interface entry<X, Y> {

		}
	}

	public static <A, B> void mytype(Mymap.entry<A, B> mapEntry) {

	}

	public static <T, U> void applyMethod(Map.Entry<T, U> mapEntry) {

	}

	@Test
	public void getOwnerType() throws NoSuchMethodException, SecurityException {

		Method method = new Study().getClass().getMethod("mytype", Mymap.entry.class);
		Type[] types = method.getGenericParameterTypes();
		ParameterizedType pType = (ParameterizedType) types[0];

		// 返回所有者类型，打印结果是interface java.util.Map
		System.out.println(pType.getOwnerType());

	}
	// 4.1 t的getBounds就是Number和Serializable组,getActualTypeArguments[0]就是String类型
	// 4.2 getGenericDeclaration就是TypeVariableTest
	// 4.3 getName就是变量泛型名称T

	@Test
	public void testGetBounds() throws NoSuchFieldException {
		Field fieldT = TestTypeVariable.class.getDeclaredField("t");
		TypeVariable<TestTypeVariable<TestTypeValue>> typeVariable = (TypeVariable) fieldT.getGenericType();

		Type[] types = typeVariable.getBounds();

		for (Type type : types) {
			System.out.println(type);
		}
		System.out.println(typeVariable.getName());

		 System.out.println(typeVariable.getGenericDeclaration());
	}

}
