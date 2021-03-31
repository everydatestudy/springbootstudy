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

package org.springframework.boot.autoconfigure.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.Advice;
import org.aspectj.weaver.AnnotatedElement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 
 * spring boot 中自动化配置是读取/META-INF/spring.factories 中读取
 * org.springframework.boot.autoconfigure.EnableAutoConfiguration配置的值,
 * 其中有关aop的只有一个,即org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.该类就是打开宝箱的钥匙

 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for Spring's AOP support. Equivalent to enabling
 * {@link org.springframework.context.annotation.EnableAspectJAutoProxy} in your
 * configuration.
 * <p>
 * The configuration will not be activated if {@literal spring.aop.auto=false}. The
 * {@literal proxyTargetClass} attribute will be {@literal true}, by default, but can be
 * overridden by specifying {@literal spring.aop.proxy-target-class=false}.
 *
 * @author Dave Syer
 * @author Josh Long
 * @see EnableAspectJAutoProxy
 */

//@Configuration –> 配置类
//@ConditionalOnClass({ EnableAspectJAutoProxy.class, Aspect.class, Advice.class }) –> 在当前的类路径下存在EnableAspectJAutoProxy.class, Aspect.class, Advice.class时该配置才被解析
//@ConditionalOnProperty(prefix = “spring.aop”, name = “auto”, havingValue = “true”, matchIfMissing = true) –> 当配置有spring.aop.auto= true时生效.如果没有配置,则默认生效
//————————————————
//版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/qq_26000415/article/details/79010971
@Configuration
@ConditionalOnClass({ EnableAspectJAutoProxy.class, Aspect.class, Advice.class,
		AnnotatedElement.class })
@ConditionalOnProperty(prefix = "spring.aop", name = "auto", havingValue = "true", matchIfMissing = true)
public class AopAutoConfiguration {

	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = false)
	@ConditionalOnProperty(prefix = "spring.aop", name = "proxy-target-class", havingValue = "false", matchIfMissing = false)
	public static class JdkDynamicAutoProxyConfiguration {

	}
//	@Configuration –> 配置类
//	@EnableAspectJAutoProxy(proxyTargetClass = false) –> 开启aop注解
//	@ConditionalOnProperty(prefix = “spring.aop”, name = “proxy-target-class”, havingValue = “true”, matchIfMissing = false)–> 当配置有spring.aop.proxy-target-class= true时生效,如果没有配置,默认不生效.
//	因此,我们可以知道,aop 默认生效的JdkDynamicAutoProxyConfiguration.这也符合我们关于spring aop的认知,默认是使用jdk,如果使用面向类的代理,则需要配置spring.aop.proxy-target-class=true,来使用cglib进行代理
//	————————————————
//	版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/qq_26000415/article/details/79010971
	@Configuration
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	@ConditionalOnProperty(prefix = "spring.aop", name = "proxy-target-class", havingValue = "true", matchIfMissing = true)
	public static class CglibAutoProxyConfiguration {

	}

}
