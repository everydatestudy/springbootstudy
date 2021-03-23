/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.autoconfigure.condition;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Conditional;

/**
 * {@link Conditional} that only matches when no beans of the specified classes and/or
 * with the specified names are already contained in the {@link BeanFactory}.
 * <p>
 * When placed on a {@code @Bean} method, the bean class defaults to the return type of
 * the factory method:
 *
 * <pre class="code">
 * &#064;Configuration
 * public class MyAutoConfiguration {
 *
 *     &#064;ConditionalOnMissingBean
 *     &#064;Bean
 *     public MyService myService() {
 *         ...
 *     }
 *
 * }</pre>
 * <p>
 * In the sample above the condition will match if no bean of type {@code MyService} is
 * already contained in the {@link BeanFactory}.
 * <p>
 * The condition can only match the bean definitions that have been processed by the
 * application context so far and, as such, it is strongly recommended to use this
 * condition on auto-configuration classes only. If a candidate bean may be created by
 * another auto-configuration, make sure that the one using this condition runs after.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnBeanCondition.class)
public @interface ConditionalOnMissingBean {

	/**bean的类型,当ApplicationContext不包含给定类的bean时返回true
	 * The class types of beans that should be checked. The condition matches when no bean
	 * of each class specified is contained in the {@link BeanFactory}.
	 * @return the class types of beans to check
	 */
	Class<?>[] value() default {};

	/**bean的类型名,当ApplicationContext不包含给定的id时返回true
	 * The class type names of beans that should be checked. The condition matches when no
	 * bean of each class specified is contained in the {@link BeanFactory}.
	 * @return the class type names of beans to check
	 */
	String[] type() default {};

	/**给定的类型当进行匹配时进行忽略
	 * The class types of beans that should be ignored when identifying matching beans.
	 * @return the class types of beans to ignore
	 * @since 1.2.5
	 */
	Class<?>[] ignored() default {};

	/**给定的类型名当进行匹配时进行忽略
	 * The class type names of beans that should be ignored when identifying matching
	 * beans.
	 * @return the class type names of beans to ignore
	 * @since 1.2.5
	 */
	String[] ignoredType() default {};

	/**bean所声明的注解,当ApplicationContext中不存在声明该注解的bean时返回true
	 * The annotation type decorating a bean that should be checked. The condition matches
	 * when each annotation specified is missing from all beans in the
	 * {@link BeanFactory}.
	 * @return the class-level annotation types to check
	 */
	Class<? extends Annotation>[] annotation() default {};

	/**bean的id,,当ApplicationContext中不存在给定id的bean时返回true
	 * The names of beans to check. The condition matches when each bean name specified is
	 * missing in the {@link BeanFactory}.
	 * @return the names of beans to check
	 */
	String[] name() default {};

	/**默认是所有上下文搜索
	 * Strategy to decide if the application context hierarchy (parent contexts) should be
	 * considered.
	 * @return the search strategy
	 */
	SearchStrategy search() default SearchStrategy.ALL;

	/**
	 * Additional classes that may contain the specified bean types within their generic
	 * parameters. For example, an annotation declaring {@code value=Name.class} and
	 * {@code parameterizedContainer=NameRegistration.class} would detect both
	 * {@code Name} and {@code NameRegistration<Name>}.
	 * @return the container types
	 * @since 2.1.0
	 */
	Class<?>[] parameterizedContainer() default {};

}
