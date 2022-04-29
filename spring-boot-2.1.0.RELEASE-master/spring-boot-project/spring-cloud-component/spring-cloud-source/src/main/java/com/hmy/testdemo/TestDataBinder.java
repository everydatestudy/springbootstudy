package com.hmy.testdemo;

import java.beans.PropertyDescriptor;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;

class User {

}

public class TestDataBinder {

	public static void main(String[] args) {
		User user = new User();
		DataBinder dataBinder = new DataBinder(user, "user");

		MutablePropertyValues propertyValues = new MutablePropertyValues();
		propertyValues.addPropertyValue("id", 10);
		propertyValues.addPropertyValue("name", " jerry");
		propertyValues.addPropertyValue("age", 18);
		dataBinder.bind(propertyValues);
		System.out.println(user);

		BeanWrapper beanWrapper = new BeanWrapperImpl(User.class);
		PropertyDescriptor[] propertyDescriptors = beanWrapper.getPropertyDescriptors();
		for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
			System.out.printf("%s : %s, %s\n", propertyDescriptor.getName(), propertyDescriptor.getReadMethod(),
					propertyDescriptor.getWriteMethod());

		}
	}
}
