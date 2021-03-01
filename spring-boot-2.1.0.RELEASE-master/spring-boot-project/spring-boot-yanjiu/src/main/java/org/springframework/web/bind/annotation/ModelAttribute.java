/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.web.bind.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.ui.Model;

/**
 * 
@ModelAttribute一个具有如下三个作用：
①绑定请求参数到命令对象：放在功能处理方法的入参上时，用于将多个请求参数绑定到一个命令对象，从而简化绑
定流程，而且自动暴露为模型数据用于视图页面展示时使用；
②暴露表单引用对象为模型数据：放在处理器的一般方法（非功能处理方法）上时，是为表单准备要展示的表单引用
对象，如注册时需要选择的所在城市等，而且在执行功能处理方法（@RequestMapping 注解的方法）之前，自动添加
到模型对象中，用于视图页面展示时使用；
③暴露@RequestMapping 方法返回值为模型数据：放在功能处理方法的返回值上时，是暴露功能处理方法的返回值为
模型数据，用于视图页面展示时使用。
————————————————
版权声明：本文为CSDN博主「归田」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/qq924862077/article/details/53924606
 * Annotation that binds a method parameter or method return value
 * to a named model attribute, exposed to a web view. Supported
 * for controller classes with {@link RequestMapping @RequestMapping}
 * methods.
 *
 * <p>Can be used to expose command objects to a web view, using
 * specific attribute names, through annotating corresponding
 * parameters of an {@link RequestMapping @RequestMapping} method.
 *
 * <p>Can also be used to expose reference data to a web view
 * through annotating accessor methods in a controller class with
 * {@link RequestMapping @RequestMapping} methods. Such accessor
 * methods are allowed to have any arguments that
 * {@link RequestMapping @RequestMapping} methods support, returning
 * the model attribute value to expose.
 *
 * <p>Note however that reference data and all other model content is
 * not available to web views when request processing results in an
 * {@code Exception} since the exception could be raised at any time
 * making the content of the model unreliable. For this reason
 * {@link ExceptionHandler @ExceptionHandler} methods do not provide
 * access to a {@link Model} argument.
 *
 * @author Juergen Hoeller
 * @author Rossen Stoyanchev
 * @since 2.5
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ModelAttribute {

	/**
	 * Alias for {@link #name}.
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * The name of the model attribute to bind to.
	 * <p>The default model attribute name is inferred from the declared
	 * attribute type (i.e. the method parameter type or method return type),
	 * based on the non-qualified class name:
	 * e.g. "orderAddress" for class "mypackage.OrderAddress",
	 * or "orderAddressList" for "List&lt;mypackage.OrderAddress&gt;".
	 * @since 4.3
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * Allows declaring data binding disabled directly on an {@code @ModelAttribute}
	 * method parameter or on the attribute returned from an {@code @ModelAttribute}
	 * method, both of which would prevent data binding for that attribute.
	 * <p>By default this is set to {@code true} in which case data binding applies.
	 * Set this to {@code false} to disable data binding.
	 * @since 4.3
	 */
	boolean binding() default true;

}
