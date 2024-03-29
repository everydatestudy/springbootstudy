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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * {@link Condition} that checks if properties are defined in environment.
 *
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @since 1.1.0
 * @see ConditionalOnProperty
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
class OnPropertyCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		List<AnnotationAttributes> allAnnotationAttributes = annotationAttributesFromMultiValueMap(
				metadata.getAllAnnotationAttributes(
						ConditionalOnProperty.class.getName())); // 这里拿到@ConditionalOnProperty注解的所有值，比如@ConditionalOnProperty(name = { "property1", "property2" })可以拿到name = { "property1", "property2" }的值
		List<ConditionMessage> noMatch = new ArrayList<>();
		List<ConditionMessage> match = new ArrayList<>();
		for (AnnotationAttributes annotationAttributes : allAnnotationAttributes) { // 遍历@ConditionalOnProperty注解里面的属性值
			ConditionOutcome outcome = determineOutcome(annotationAttributes, // 将@ConditionalOnProperty注解定义的属性值与外部配置环境变量比较，若匹配，则将结果添加到match集合，若不匹配，则添加到noMatch集合
					context.getEnvironment());
			(outcome.isMatch() ? match : noMatch).add(outcome.getConditionMessage());
		}
		if (!noMatch.isEmpty()) { // 如果外部配置属性不匹配
			return ConditionOutcome.noMatch(ConditionMessage.of(noMatch));
		} // 如果外部配置属性均匹配
		return ConditionOutcome.match(ConditionMessage.of(match));
	}

	private List<AnnotationAttributes> annotationAttributesFromMultiValueMap(
			MultiValueMap<String, Object> multiValueMap) {
		List<Map<String, Object>> maps = new ArrayList<>();
		multiValueMap.forEach((key, value) -> {
			for (int i = 0; i < value.size(); i++) {
				Map<String, Object> map;
				if (i < maps.size()) {
					map = maps.get(i);
				}
				else {
					map = new HashMap<>();
					maps.add(map);
				}
				map.put(key, value.get(i));
			}
		});
		List<AnnotationAttributes> annotationAttributes = new ArrayList<>(maps.size());
		for (Map<String, Object> map : maps) {
			annotationAttributes.add(AnnotationAttributes.fromMap(map));
		}
		return annotationAttributes;
	}

	private ConditionOutcome determineOutcome(AnnotationAttributes annotationAttributes,
			PropertyResolver resolver) {
		Spec spec = new Spec(annotationAttributes); // Spec是OnPropertyCondition的静态内部类，定义了name,matchesIfMissing等属性
		List<String> missingProperties = new ArrayList<>();
		List<String> nonMatchingProperties = new ArrayList<>();
		spec.collectProperties(resolver, missingProperties, nonMatchingProperties); // 收集配置的环境变量中的不符合@ConditionalOnProperty注解的havingValue及matchIfMissing值的环境变量
		if (!missingProperties.isEmpty()) { // 如果没有配置某个属性，则返回不匹配
			return ConditionOutcome.noMatch(
					ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
							.didNotFind("property", "properties")
							.items(Style.QUOTE, missingProperties));
		}
		if (!nonMatchingProperties.isEmpty()) { // 如果配置的某个值不匹配，则返回不匹配
			return ConditionOutcome.noMatch(
					ConditionMessage.forCondition(ConditionalOnProperty.class, spec)
							.found("different value in property",
									"different value in properties")
							.items(Style.QUOTE, nonMatchingProperties));
		} // 执行到这里说明属性均匹配
		return ConditionOutcome.match(ConditionMessage
				.forCondition(ConditionalOnProperty.class, spec).because("matched"));
	}

	private static class Spec {

		private final String prefix;

		private final String havingValue;

		private final String[] names;

		private final boolean matchIfMissing;

		Spec(AnnotationAttributes annotationAttributes) {
			String prefix = annotationAttributes.getString("prefix").trim();
			if (StringUtils.hasText(prefix) && !prefix.endsWith(".")) {
				prefix = prefix + ".";
			}
			this.prefix = prefix;
			this.havingValue = annotationAttributes.getString("havingValue");
			this.names = getNames(annotationAttributes);
			this.matchIfMissing = annotationAttributes.getBoolean("matchIfMissing");
		}

		private String[] getNames(Map<String, Object> annotationAttributes) {
			String[] value = (String[]) annotationAttributes.get("value");
			String[] name = (String[]) annotationAttributes.get("name");
			Assert.state(value.length > 0 || name.length > 0,
					"The name or value attribute of @ConditionalOnProperty must be specified");
			Assert.state(value.length == 0 || name.length == 0,
					"The name and value attributes of @ConditionalOnProperty are exclusive");
			return (value.length > 0) ? value : name;
		}

		private void collectProperties(PropertyResolver resolver, List<String> missing,
				List<String> nonMatching) {
			for (String name : this.names) { // 逐个遍历@ConditionalOnProperty注解里的name的值
				//key等于 如果支持松散匹配,则使用所配置的name,否则等于prefix+name
				String key = this.prefix + name;
				//// 这里resolver封装了外部配置的环境变量，判断环境变量中是否包含@ConditionalOnProperty注解里的name的值
				if (resolver.containsProperty(key)) { 
					// 若定义了havingValue的值，这里判断是否每个配置的属性值均为havingValue（忽略大小写），
					// 只要有一个属性值不为havingValue，则添加到nonMatching集合中，注意havingValue的值不能为false
					if (!isMatch(resolver.getProperty(key), this.havingValue)) {
						nonMatching.add(name);
					}
				} // 若外部环境变量没有设置@ConditionalOnProperty中的某个属性值，因为matchIfMissing默认为false，则添加到missing集合中
				else {
					if (!this.matchIfMissing) {
						missing.add(name);
					}
				}
			}
		}

		private boolean isMatch(String value, String requiredValue) {
			if (StringUtils.hasLength(requiredValue)) {
				return requiredValue.equalsIgnoreCase(value);
			}
			return !"false".equalsIgnoreCase(value);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			result.append("(");
			result.append(this.prefix);
			if (this.names.length == 1) {
				result.append(this.names[0]);
			}
			else {
				result.append("[");
				result.append(StringUtils.arrayToCommaDelimitedString(this.names));
				result.append("]");
			}
			if (StringUtils.hasLength(this.havingValue)) {
				result.append("=").append(this.havingValue);
			}
			result.append(")");
			return result.toString();
		}

	}

}
