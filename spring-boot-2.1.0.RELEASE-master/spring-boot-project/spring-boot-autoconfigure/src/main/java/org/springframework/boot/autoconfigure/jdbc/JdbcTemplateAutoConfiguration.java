/*
 * Copyright 2012-2017 the original author or authors.
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

package org.springframework.boot.autoconfigure.jdbc;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**	@Configuration –> 配置类
	@ConditionalOnClass({ DataSource.class, JdbcTemplate.class }) –> 在当前类路径下存在DataSource.class,JdbcTemplate.class 时该配置生效
	@ConditionalOnSingleCandidate(DataSource.class)–> 当beanFactory中存在DataSource类型的bean并且当存在多个DataSource时,
	声明为@Primary的DataSource存在时生效
	@AutoConfigureAfter(DataSourceAutoConfiguration.class) –>在DataSourceAutoConfiguration 之后进行配置,这样才能注入DataSource
 * {@link EnableAutoConfiguration Auto-configuration} for {@link JdbcTemplate} and
 * {@link NamedParameterJdbcTemplate}.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Kazuki Shimizu
 * @since 1.4.0
 */
@Configuration
@ConditionalOnClass({ DataSource.class, JdbcTemplate.class })
@ConditionalOnSingleCandidate(DataSource.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(JdbcProperties.class)
public class JdbcTemplateAutoConfiguration {

	@Configuration
	static class JdbcTemplateConfiguration {

		private final DataSource dataSource;

		private final JdbcProperties properties;

		JdbcTemplateConfiguration(DataSource dataSource, JdbcProperties properties) {
			this.dataSource = dataSource;
			this.properties = properties;
		}
		//JdbcTemplateAutoConfiguration 比较简单,没有内部类,只有两个被@bean注解的方法:
		//jdbcTemplate方法,注册了一个id为jdbcTemplate,类型为JdbcTemplate的bean,代码如下:
		//@Primary –> 当BeanFactory中有多个JdbcTemplate时,该配置的JdbcTemplate 会在自动装配(byType)时自动注入
		//@ConditionalOnMissingBean(JdbcOperations.class)–> 当BeanFactory中没有JdbcOperations类型的bean时该bean进行注册
		@Bean
		@Primary
		@ConditionalOnMissingBean(JdbcOperations.class)
		public JdbcTemplate jdbcTemplate() {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(this.dataSource);
			JdbcProperties.Template template = this.properties.getTemplate();
			jdbcTemplate.setFetchSize(template.getFetchSize());
			jdbcTemplate.setMaxRows(template.getMaxRows());
			if (template.getQueryTimeout() != null) {
				jdbcTemplate
						.setQueryTimeout((int) template.getQueryTimeout().getSeconds());
			}
			return jdbcTemplate;
		}

	}

	@Configuration
	@Import(JdbcTemplateConfiguration.class)
	static class NamedParameterJdbcTemplateConfiguration {

//		@Bean –> 注册一个id为namedParameterJdbcTemplate,类型为NamedParameterJdbcTemplate的bean
//		@Primary–> 当BeanFactory中有多个NamedParameterJdbcTemplate时,该配置的NamedParameterJdbcTemplate 会在自动装配(byType)时自动注入
//		@ConditionalOnMissingBean(NamedParameterJdbcOperations.class)–>当BeanFactory中没有NamedParameterJdbcTemplate类型的bean时该bean进行注册
//		————————————————
//		版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//		原文链接：https://blog.csdn.net/qq_26000415/article/details/79027165
		@Bean
		@Primary
		@ConditionalOnSingleCandidate(JdbcTemplate.class)
		@ConditionalOnMissingBean(NamedParameterJdbcOperations.class)
		public NamedParameterJdbcTemplate namedParameterJdbcTemplate(
				JdbcTemplate jdbcTemplate) {
			return new NamedParameterJdbcTemplate(jdbcTemplate);
		}

	}

}
