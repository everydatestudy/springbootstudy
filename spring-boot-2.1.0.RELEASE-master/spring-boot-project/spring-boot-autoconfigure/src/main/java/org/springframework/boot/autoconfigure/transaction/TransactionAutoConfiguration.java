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

package org.springframework.boot.autoconfigure.transaction;

import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AbstractTransactionManagementConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link org.springframework.boot.autoconfigure.EnableAutoConfiguration
 * Auto-configuration} for Spring transaction.
 *
 * @author Stephane Nicoll
 * @since 1.3.0
 */
@Configuration
@ConditionalOnClass(PlatformTransactionManager.class)
@AutoConfigureAfter({ JtaAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
		DataSourceTransactionManagerAutoConfiguration.class,
		Neo4jDataAutoConfiguration.class })
@EnableConfigurationProperties(TransactionProperties.class)
public class TransactionAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TransactionManagerCustomizers platformTransactionManagerCustomizers(
			ObjectProvider<PlatformTransactionManagerCustomizer<?>> customizers) {
		return new TransactionManagerCustomizers(
				customizers.orderedStream().collect(Collectors.toList()));
	}
	//	ConditionalOnSingleCandidate(PlatformTransactionManager.class)–> 
	//当PlatformTransactionManager类型的bean存在并且当存在多个bean时指定为Primary的PlatformTransactionManager存在时,该配置类才进行解析
	//	由于TransactionAutoConfiguration是在DataSourceTransactionManagerAutoConfiguration之后
	//才被解析处理的,而在DataSourceTransactionManagerAutoConfiguration中配置了transactionManager,因此, TransactionTemplateConfiguration 会被处理.

	@Configuration
	@ConditionalOnSingleCandidate(PlatformTransactionManager.class)
	public static class TransactionTemplateConfiguration {

		private final PlatformTransactionManager transactionManager;

		public TransactionTemplateConfiguration(
				PlatformTransactionManager transactionManager) {
			this.transactionManager = transactionManager;
		}

		@Bean
		@ConditionalOnMissingBean
		public TransactionTemplate transactionTemplate() {
			return new TransactionTemplate(this.transactionManager);
		}

	}

	@Configuration
	@ConditionalOnBean(PlatformTransactionManager.class)
	@ConditionalOnMissingBean(AbstractTransactionManagementConfiguration.class)
	public static class EnableTransactionManagementConfiguration {
//		@Configuration–> 配置类
//		@EnableTransactionManagement(proxyTargetClass = false)–>启用TransactionManagement, proxyTargetClass = false,表示是面向接口代理.关于这个注解,我们后面会分析.
//		@ConditionalOnProperty(prefix = “spring.aop”, name = “proxy-target-class”, havingValue = “false”, matchIfMissing = false)–> 配置有spring.aop.proxy-target-class = false时生效,如果没配置,则不生效
//		————————————————
//		版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq_26000415/article/details/79021958
		@Configuration
		@EnableTransactionManagement(proxyTargetClass = false)
		@ConditionalOnProperty(prefix = "spring.aop", name = "proxy-target-class", havingValue = "false", matchIfMissing = false)
		public static class JdkDynamicAutoProxyConfiguration {

		}
//		@Configuration–> 配置类
//		@EnableTransactionManagement(proxyTargetClass = false)–>启用TransactionManagement, proxyTargetClass = false,表示是面向接口代理.关于这个注解,我们后面会分析.
//		@ConditionalOnProperty(prefix = “spring.aop”, name = “proxy-target-class”, havingValue = “false”, matchIfMissing = false)–> 配置有spring.aop.proxy-target-class = false时生效,如果没配置,则不生效
//		————————————————
//		版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq_26000415/article/details/79021958
		@Configuration
		@EnableTransactionManagement(proxyTargetClass = true)
		@ConditionalOnProperty(prefix = "spring.aop", name = "proxy-target-class", havingValue = "true", matchIfMissing = true)
		public static class CglibAutoProxyConfiguration {

		}

	}

}
