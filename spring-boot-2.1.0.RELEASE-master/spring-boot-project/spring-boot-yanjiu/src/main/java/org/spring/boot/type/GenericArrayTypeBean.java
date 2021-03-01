package org.spring.boot.type;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;

public class GenericArrayTypeBean<T> {
	// 属于 GenericArrayType
	List<String>[] pTypeArray;
	// 属于 GenericArrayType
	T[] vTypeArray;
	// 不属于 GenericArrayType
	List<String> list;
	// 不属于 GenericArrayType
	String[] strings;

	public static void main(String[] args) throws NoSuchFieldException {

		Field fieldNum = GenericArrayTypeBean.class.getDeclaredField("strings");
		Type typeNum = fieldNum.getGenericType();
		System.out.println(typeNum.getClass());
		Field field = WildcardTypeTest.class.getDeclaredField("b");
		  typeNum = field.getGenericType();
		  System.out.println(typeNum.toString());
	}
}
