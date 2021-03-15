/*
 * Copyright 2002-2012 the original author or authors.
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

/**最后，此注解的使用的注意事项我把它总结如下，供各位使用过程中参考：

@InitBinder标注的方法执行是多次的，一次请求来就执行一次（第一次惩罚）
Controller实例中的所有@InitBinder只对当前所在的Controller有效
@InitBinder的value属性控制的是模型Model里的key，而不是方法名（不写代表对所有的生效）
@InitBinder标注的方法不能有返回值（只能是void或者returnValue=null）
@InitBinder对@RequestBody这种基于消息转换器的请求参数无效
1. 因为@InitBinder它用于初始化DataBinder数据绑定、类型转换等功能，而@RequestBody它的数据解析、转换时消息转换器来完成的，所以即使你自定义了属性编辑器，对它是不生效的（它的WebDataBinder只用于数据校验，不用于数据绑定和数据转换。它的数据绑定转换若是json，一般都是交给了jackson来完成的）
只有AbstractNamedValueMethodArgumentResolver才会调用binder.convertIfNecessary进行数据转换，从而属性编辑器才会生效
————————————————
版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/f641385712/article/details/95473929
 * Annotation that identifies methods which initialize the
 * {@link org.springframework.web.bind.WebDataBinder} which
 * will be used for populating command and form object arguments
 * of annotated handler methods.
 *
 * <p>Such init-binder methods support all arguments that {@link RequestMapping}
 * supports, except for command/form objects and corresponding validation result
 * objects. Init-binder methods must not have a return value; they are usually
 * declared as {@code void}.
 *
 * <p>Typical arguments are {@link org.springframework.web.bind.WebDataBinder}
 * in combination with {@link org.springframework.web.context.request.WebRequest}
 * or {@link java.util.Locale}, allowing to register context-specific editors.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see org.springframework.web.bind.WebDataBinder
 * @see org.springframework.web.context.request.WebRequest
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InitBinder {

	/**
	 * The names of command/form attributes and/or request parameters
	 * that this init-binder method is supposed to apply to.
	 * <p>Default is to apply to all command/form attributes and all request parameters
	 * processed by the annotated handler class. Specifying model attribute names or
	 * request parameter names here restricts the init-binder method to those specific
	 * attributes/parameters, with different init-binder methods typically applying to
	 * different groups of attributes or parameters.
	 */
	String[] value() default {};

}
