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

package org.springframework.boot.context.properties;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * {@link ConfigurationPropertiesBindingPostProcessor}处理绑定数据
 * 
 * @ConfigurationProperties这个注解的作用就是将外部配置的配置值绑定到其注解的类的属性上， 可以作用于配置类或配置类的方法上。
 * 可以看到@ConfigurationProperties注解除了有设置前缀， 是否忽略一些不存在或无效的配置等属性等外， 这个注解没有其他任何的处理逻辑，
 * 可以看到@ConfigurationProperties是一个标志性的注解
 * @EnableConfigurationProperties注解的主要作用就是为@ConfigurationProperties注解标注的类提供支持，
 * 即对将外部配置属性值（比如application.properties配置值）绑定到@ConfigurationProperties标注的类的属性中。
 * 
 * Annotation for externalized configuration. Add this to a class definition or
 * a {@code @Bean} method in a {@code @Configuration} class if you want to bind
 * and validate some external Properties (e.g. from a .properties file).
 * <p>
 * Note that contrary to {@code @Value}, SpEL expressions are not evaluated
 * since property values are externalized.
 *
 * @author Dave Syer
 * @see ConfigurationPropertiesBindingPostProcessor
 * @see EnableConfigurationProperties
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigurationProperties {

	/**
	 * The name prefix of the properties that are valid to bind to this object.
	 * Synonym for {@link #prefix()}. A valid prefix is defined by one or more words
	 * separated with dots (e.g. {@code "acme.system.feature"}).
	 * 
	 * @return the name prefix of the properties to bind
	 */
	@AliasFor("prefix")
	String value() default "";

	/**
	 * The name prefix of the properties that are valid to bind to this object.
	 * Synonym for {@link #value()}. A valid prefix is defined by one or more words
	 * separated with dots (e.g. {@code "acme.system.feature"}).
	 * 
	 * @return the name prefix of the properties to bind
	 */
	@AliasFor("value")
	String prefix() default "";

	/**
	 * Flag to indicate that when binding to this object invalid fields should be
	 * ignored. Invalid means invalid according to the binder that is used, and
	 * usually this means fields of the wrong type (or that cannot be coerced into
	 * the correct type).
	 * 
	 * @return the flag value (default false)
	 */
	boolean ignoreInvalidFields() default false;

	/**
	 * Flag to indicate that when binding to this object unknown fields should be
	 * ignored. An unknown field could be a sign of a mistake in the Properties.
	 * 
	 * @return the flag value (default true)
	 */
	boolean ignoreUnknownFields() default true;
}
