package org.spring.boot.type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.Test;

public class MethodTest {

	static class Person<T> {

	}

	static class Student extends Person<MethodTest> {

		public Student(List<CharSequence> list) {

		}

		public Integer test(List<CharSequence> list) {
			return null;
		}

	}

	@Test
	public void test1() throws NoSuchMethodException {

		{
			Method method = Student.class.getMethod("test", List.class);

			Class type1 = method.getParameterTypes()[0];
			Type type2 = method.getGenericParameterTypes()[0];

			System.out.println(type1);
			System.out.println(type2);
		}

		{
			Constructor constructor = Student.class.getConstructor(List.class);

			Class type1 = constructor.getParameterTypes()[0];
			Type type2 = constructor.getGenericParameterTypes()[0];
			System.out.println(type1);
			System.out.println(type2);
		}
	}
}
