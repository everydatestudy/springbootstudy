
package feign;

import java.lang.annotation.Retention;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Expands the uri template supplied in the {@code value}, permitting path and
 * query variables, or just the http method. Templates should conform to
 * <a href="https://tools.ietf.org/html/rfc6570">RFC 6570</a>. Support is
 * limited to Simple String expansion and Reserved Expansion (Level 1 and Level
 * 2) expressions.
 */
@java.lang.annotation.Target(METHOD)
@Retention(RUNTIME)
public @interface RequestLine {
	// 书写请求方法 + URI
	String value();

	//是否编码/符号，默认是会编码的,也就是转义的意思
	boolean decodeSlash() default true;

	//默认支持URL传多值，是通过key来传输的。形如：key=value1&key=value2&key=value3
	// CollectionFormat不同的取值对应不同的分隔符，一般不建议改
	CollectionFormat collectionFormat() default CollectionFormat.EXPLODED;
}
