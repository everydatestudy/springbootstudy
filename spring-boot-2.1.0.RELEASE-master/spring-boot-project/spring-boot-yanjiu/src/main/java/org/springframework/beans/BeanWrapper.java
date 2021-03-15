/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans;

import java.beans.PropertyDescriptor;

/**
 * BeanWrapper 之所以具备编辑、类型转换 等这些功能，是由于继承了ConfigurablePropertyAccessor、PropertyAccessor, PropertyEditorRegistry, TypeConverter 这些接口.
 * 
 * 
 * BeanWrapper 是底层JavaBeans 的中央接口.
一般不会被直接调用，而是隐含的被BeanFactory 或者DataBinder使用.
具有（单独或批量）获取和设置属性值，获取属性描述符以及查询属性的可读性/可写性的能力.
该接口支持嵌套属性，可将子属性上的属性设置为无限深度.
BeanWrapper的“ extractOldValueForEditor”默认值为false，以避免由getter方法调用引起的副作用。 将此选项设置为true，即自定义编辑器公开当前属性值。
 * 
 * BeanWrapper相当于一个代理器，Spring委托BeanWrapper完成Bean属性的填充工作。关于此接口的实现类，简单的说它只有唯一实现类：BeanWrapperImpl
 * The central interface of Spring's low-level JavaBeans infrastructure.
 *
 * <p>Typically not used directly but rather implicitly via a
 * {@link org.springframework.beans.factory.BeanFactory} or a
 * {@link org.springframework.validation.DataBinder}.
 *
 * <p>Provides operations to analyze and manipulate standard JavaBeans:
 * the ability to get and set property values (individually or in bulk),
 * get property descriptors, and query the readability/writability of properties.
 *
 * <p>This interface supports <b>nested properties</b> enabling the setting
 * of properties on subproperties to an unlimited depth.
 *
 * <p>A BeanWrapper's default for the "extractOldValueForEditor" setting
 * is "false", to avoid side effects caused by getter method invocations.
 * Turn this to "true" to expose present property values to custom editors.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 13 April 2001
 * @see PropertyAccessor
 * @see PropertyEditorRegistry
 * @see PropertyAccessorFactory#forBeanPropertyAccess
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.validation.BeanPropertyBindingResult
 * @see org.springframework.validation.DataBinder#initBeanPropertyAccess()
 */
public interface BeanWrapper extends ConfigurablePropertyAccessor {

	/**
	 * /**
	 * @since 4.1
	 * 指定数组和集合自动增长的限制。在普通的BeanWrapper上，默认值是无限的。
	 
	 * Specify a limit for array and collection auto-growing.
	 * <p>Default is unlimited on a plain BeanWrapper.
	 * @since 4.1
	 */
	void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);

	/**返回数组和集合自动增长的限制
	 * Return the limit for array and collection auto-growing.
	 * @since 4.1
	 */
	int getAutoGrowCollectionLimit();

	/**返回此对象包装的bean实例
	 * Return the bean instance wrapped by this object.
	 */
	Object getWrappedInstance();

	/**返回包装的bean实例的类型
	 * Return the type of the wrapped bean instance.
	 */
	Class<?> getWrappedClass();

	/**返回包装类的属性描述(由标准JavaBean自省确定)
	 * Obtain the PropertyDescriptors for the wrapped object
	 * (as determined by standard JavaBeans introspection).
	 * @return the PropertyDescriptors for the wrapped object
	 */
	PropertyDescriptor[] getPropertyDescriptors();

	/**根据属性名称获取对应的 property descriptor 
	 * Obtain the property descriptor for a specific property
	 * of the wrapped object.
	 * @param propertyName the property to obtain the descriptor for
	 * (may be a nested path, but no indexed/mapped property)
	 * @return the property descriptor for the specified property
	 * @throws InvalidPropertyException if there is no such property
	 */
	PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException;

}
