package org.spring.boot.type;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

public class FieldTest {

	class Person<T> {

		public List<CharSequence> charSequenceList;

		public String str;
	}

	class Student extends Person<FieldTest> {

	}

	@Test
	public void test1() throws NoSuchFieldException {

		{
			Field field = Student.class.getField("str");
			System.out.println(field.getType());
			System.out.println(field.getGenericType());
		}

		{
			Field field = Student.class.getField("charSequenceList");
			System.out.println(field.getType());
			System.out.println(field.getGenericType());
		}
	}
}
