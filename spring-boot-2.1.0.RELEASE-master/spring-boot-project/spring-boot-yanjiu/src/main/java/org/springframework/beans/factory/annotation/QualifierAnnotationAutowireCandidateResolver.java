/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.beans.factory.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.GenericTypeAwareAutowireCandidateResolver;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link AutowireCandidateResolver} implementation that matches bean definition qualifiers
 * against {@link Qualifier qualifier annotations} on the field or parameter to be autowired.
 * Also supports suggested expression values through a {@link Value value} annotation.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.5
 * @see AutowireCandidateQualifier
 * @see Qualifier
 * @see Value
 */
public class QualifierAnnotationAutowireCandidateResolver extends GenericTypeAwareAutowireCandidateResolver {
	// 是个List，可以知道它不仅仅只支持org.springframework.beans.factory.annotation.Qualifier
	private final Set<Class<? extends Annotation>> qualifierTypes = new LinkedHashSet<>(2);

	private Class<? extends Annotation> valueAnnotationType = Value.class;


	/** 空构造：默认支持的是@Qualifier以及JSR330标准的@Qualifier
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for Spring's standard {@link Qualifier} annotation.
	 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
	 */
	@SuppressWarnings("unchecked")
	public QualifierAnnotationAutowireCandidateResolver() {
		this.qualifierTypes.add(Qualifier.class);
		try {
			this.qualifierTypes.add((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Qualifier",
							QualifierAnnotationAutowireCandidateResolver.class.getClassLoader()));
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}

	/**	// 非空构造：可自己额外指定注解类型
	// 注意：如果通过构造函数指定qualifierType，上面两种就不支持了，因此不建议使用
	// 而建议使用它提供的addQualifierType() 来添加~~~
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for the given qualifier annotation type.
	 * @param qualifierType the qualifier annotation to look for
	 */
	public QualifierAnnotationAutowireCandidateResolver(Class<? extends Annotation> qualifierType) {
		Assert.notNull(qualifierType, "'qualifierType' must not be null");
		this.qualifierTypes.add(qualifierType);
	}

	/**
	 * Create a new QualifierAnnotationAutowireCandidateResolver
	 * for the given qualifier annotation types.
	 * @param qualifierTypes the qualifier annotations to look for
	 */
	public QualifierAnnotationAutowireCandidateResolver(Set<Class<? extends Annotation>> qualifierTypes) {
		Assert.notNull(qualifierTypes, "'qualifierTypes' must not be null");
		this.qualifierTypes.addAll(qualifierTypes);
	}


	/**
	 * Register the given type to be used as a qualifier when autowiring.
	 * <p>This identifies qualifier annotations for direct use (on fields,
	 * method parameters and constructor parameters) as well as meta
	 * annotations that in turn identify actual qualifier annotations.
	 * <p>This implementation only supports annotations as qualifier types.
	 * The default is Spring's {@link Qualifier} annotation which serves
	 * as a qualifier for direct use and also as a meta annotation.
	 * @param qualifierType the annotation type to register
	 */
	public void addQualifierType(Class<? extends Annotation> qualifierType) {
		this.qualifierTypes.add(qualifierType);
	}

	/**
	 * Set the 'value' annotation type, to be used on fields, method parameters
	 * and constructor parameters.
	 * <p>The default value annotation type is the Spring-provided
	 * {@link Value} annotation.
	 * <p>This setter property exists so that developers can provide their own
	 * (non-Spring-specific) annotation type to indicate a default value
	 * expression for a specific argument.
	 */
	public void setValueAnnotationType(Class<? extends Annotation> valueAnnotationType) {
		this.valueAnnotationType = valueAnnotationType;
	}

//	在源码注释的地方，我按照步骤标出了它进行匹配的一个执行步骤逻辑。需要注意如下几点：
//
//	qualifierTypes是支持调用者自己指定的（默认只支持@Qualifier类型）
//	只有类型匹配、Bean定义匹配、泛型匹配等全部Ok了，才会使用@Qualifier去更加精确的匹配
//	descriptor.getAnnotations()的逻辑是：
//	- 如果DependencyDescriptor描述的是字段（Field），那就去字段里拿注解们
//	- 若描述的是方法参数（MethodParameter），那就返回的是方法参数的注解
//	步骤3的match = true表示Field/方法参数上的限定符是匹配的~
//	说明：能走到isAutowireCandidate()方法里来，那它肯定是标注了@Autowired注解的（才能被AutowiredAnnotationBeanPostProcessor后置处理），所以descriptor.getAnnotations()返回的数组长度至少为1
//	————————————————
//	版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/f641385712/article/details/100890879
	/**这是个最重要的接口方法~~~  判断所提供的Bean-->BeanDefinitionHolder 是否是候选的
	// （返回true表示此Bean符合条件）
	 * Determine whether the provided bean definition is an autowire candidate.
	 * <p>To be considered a candidate the bean's <em>autowire-candidate</em>
	 * attribute must not have been set to 'false'. Also, if an annotation on
	 * the field or parameter to be autowired is recognized by this bean factory
	 * as a <em>qualifier</em>, the bean must 'match' against the annotation as
	 * well as any attributes it may contain. The bean definition must contain
	 * the same qualifier or match by meta attributes. A "value" attribute will
	 * fallback to match against the bean name or an alias if a qualifier or
	 * attribute does not match.
	 * @see Qualifier
	 */
	@Override
	public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		 // 1.调用父类方法判断此bean是否可以自动注入到其他bean
		// 1、先看父类：bean定义是否允许依赖注入、泛型类型是否匹配
		boolean match = super.isAutowireCandidate(bdHolder, descriptor);
		// 2、若都满足就继续判断@Qualifier注解~~~~
		if (match) {
			// 3、看看标注的@Qualifier注解和候选Bean是否匹配~~~（本处的核心逻辑）
			// descriptor 一般封装的是属性写方法的参数，即方法参数上的注解
			match = checkQualifiers(bdHolder, descriptor.getAnnotations());
			// 4、若Field/方法参数匹配，会继续去看看参数所在的方法Method的情况
			// 若是构造函数/返回void。 进一步校验标注在构造函数/方法上的@Qualifier限定符是否匹配
			if (match) {
				MethodParameter methodParam = descriptor.getMethodParameter();
				// 若是Field，methodParam就是null  所以这里是需要判空的
				if (methodParam != null) {
					Method method = methodParam.getMethod();
					// method == null表示构造函数 void.class表示方法返回void
					if (method == null || void.class == method.getReturnType()) {
						// 注意methodParam.getMethodAnnotations()方法是可能返回空的
						// 毕竟构造方法/普通方法上不一定会标注@Qualifier等注解呀~~~~
						// 同时警示我们：方法上的@Qualifier注解可不要乱标
						match = checkQualifiers(bdHolder, methodParam.getMethodAnnotations());
					}
				}
			}
		}
		return match;
	}

//	Tips：限定符不生效的效果不一定是注入失败，而是如果是单个的话还是注入成功的。只是若出现多个Bean它就无法起到区分的效果了，所以才会注入失败了~
//
//	它的fallback策略最多只能再向上再找一个层级(多了就不行了)。例如上例子中使用@B标注也是能起到@Qualifier效果的，但是若再加一个@C层级，限定符就不生效了。
//
//	注意：Class.isAnnotationPresent(Class<? extends Annotation> annotationClass)表示annotationClass是否标注在此类型上（此类型可以是任意Class类型）。
//	此方法不具有传递性：比如注解A上标注有@Qualifier，注解B上标注有@A注解，那么你用此方法判断@B上是否有@Qualifier它是返回false的（即使都写了@Inherited注解，因为和它没关系）
//	————————————————
//	版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//	原文链接：https://blog.csdn.net/f641385712/article/details/100890879
	
	/**将给定的限定符注释与候选bean定义匹配。命名中你发现：这里是负数形式，表示多个注解一起匹配
	// 此处指的限定符，显然默认情况下只有@Qualifier注解
	 * Match the given qualifier annotations against the candidate bean definition.
	 */
	protected boolean checkQualifiers(BeanDefinitionHolder bdHolder, Annotation[] annotationsToSearch) {
		// 很多人疑问为何没标注注解返回的还是true？
		// 请参照上面我的解释：methodParam.getMethodAnnotations()方法是可能返回空的，so...可以理解了吧
		if (ObjectUtils.isEmpty(annotationsToSearch)) {
			return true;
		}
		SimpleTypeConverter typeConverter = new SimpleTypeConverter();
		// 遍历每个注解（一般有@Autowired+@Qualifier两个注解）
		// 本文示例的两个注解：@Autowired+@LoadBalanced两个注解~~~（@LoadBalanced上标注有@Qualifier）
		for (Annotation annotation : annotationsToSearch) {
			Class<? extends Annotation> type = annotation.annotationType();
			boolean checkMeta = true;
			boolean fallbackToMeta = false;
			// isQualifier方法逻辑见下面：是否是限定注解（默认的/开发自己指定的）
			// 本文的org.springframework.cloud.client.loadbalancer.LoadBalanced是返回true的
			if (isQualifier(type)) {
				// checkQualifier：检查当前的注解限定符是否匹配
				if (!checkQualifier(bdHolder, annotation, typeConverter)) {
					fallbackToMeta = true;// 没匹配上。那就fallback到Meta去吧
				}
				else {// 匹配上了，就没必要校验元数据了喽~~~
					checkMeta = false;
				}
			}
			// 开始检查元数据（如果上面匹配上了，就不需要检查元数据了）
			// 比如说@Autowired注解/其它自定义的注解（反正就是未匹配上的），就会进来一个个检查元数据
			// 什么时候会到checkMeta里来：如@A上标注有@Qualifier。@B上标注有@A。这个时候限定符是@B的话会fallback过来
			if (checkMeta) {
				boolean foundMeta = false;
				// type.getAnnotations()结果为元注解们：@Documented、@Retention、@Target等等
				for (Annotation metaAnn : type.getAnnotations()) {
					Class<? extends Annotation> metaType = metaAnn.annotationType();
					if (isQualifier(metaType)) {
						foundMeta = true;// 只要进来了 就标注找到了，标记为true表示从元注解中找到了
						// Only accept fallback match if @Qualifier annotation has a value...
						// Otherwise it is just a marker for a custom qualifier annotation.
						// fallback=true(是限定符但是没匹配上才为true)但没有valeu值
						// 或者根本就没有匹配上，那不好意思，直接return false~
						if ((fallbackToMeta && StringUtils.isEmpty(AnnotationUtils.getValue(metaAnn))) ||
								!checkQualifier(bdHolder, metaAnn, typeConverter)) {
							return false;
						}
					}
				}
				// fallbackToMeta =true你都没有找到匹配的，就返回false的
				if (fallbackToMeta && !foundMeta) {
					return false;
				}
			}
		}
		// 相当于：只有所有的注解都木有返回false，才会认为这个Bean是合法的~~~
		return true;
	}

	/**判断一个类型是否是限定注解   qualifierTypes：表示我所有支持的限定符
	// 本文的关键在于下面这个判断语句：类型就是限定符的类型 or @Qualifier标注在了此注解上（isAnnotationPresent）
	 * Checks whether the given annotation type is a recognized qualifier type.
	 */
	protected boolean isQualifier(Class<? extends Annotation> annotationType) {
		for (Class<? extends Annotation> qualifierType : this.qualifierTypes) {
			// 类型就是限定符的类型 or @Qualifier标注在了此注解上（isAnnotationPresent）
			if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
				return true;
			}
		}
		return false;
	}
	
	//这个方法其实就是检查候选的bean定义里有没有相关Qualifier注解，
	//先检查下bean定义有没有添加type类型的全限定类型和短类名的AutowireCandidateQualifier对象，
	//如果没有的话就看有没有设置type类型的AnnotatedElement对象，
	//还没有的话就检查bean定义的工厂方法上有没有这个type注解，我们这个刚好在这上面，
	//再没有的话就从bean装饰的定义中找注解，最后为空的话就从bean定义的目标类上去找。
	//如果AutowireCandidateQualifier存在的话，就获取他的属性和type限定注解上的属性做匹配。最后返回是否找到或者是否匹配上。
	/** 检查某一个注解限定符，是否匹配当前的Bean
	 * Match the given qualifier annotation against the candidate bean definition.
	 */
	protected boolean checkQualifier(
			BeanDefinitionHolder bdHolder, Annotation annotation, TypeConverter typeConverter) {
		// type：注解类型 bd：当前Bean的RootBeanDefinition 
		Class<? extends Annotation> type = annotation.annotationType();
		RootBeanDefinition bd = (RootBeanDefinition) bdHolder.getBeanDefinition();
		// ========下面是匹配的关键步骤=========
		// 1、Bean定义信息的qualifiers字段一般都无值了（XML时代的配置除外）
		// 长名称不行再拿短名称去试了一把。显然此处 qualifier还是为null的
		////检查bean定义有没有这个type全限定类名的AutowireCandidateQualifier对象
		AutowireCandidateQualifier qualifier = bd.getQualifier(type.getName());
		if (qualifier == null) {
			//没有的话看下有没短的类名的
			qualifier = bd.getQualifier(ClassUtils.getShortName(type));
		}
		//这里才是真真有料的地方~~~请认真看步骤
		if (qualifier == null) {
			// First, check annotation on qualified element, if any
			// 1、词方法是从bd标签里拿这个类型的注解声明，非XML配置时代此处targetAnnotation 为null
			////检查bean定义里是否设置了type类型的限定类
			Annotation targetAnnotation = getQualifiedElementAnnotation(bd, type);
			// Then, check annotation on factory method, if applicable
			// 2、若为null。去工厂方法里拿这个类型的注解。这方法里标注了两个注解@Bean和@LoadBalanced，所以此时targetAnnotation就不再为null了~~
			if (targetAnnotation == null) {
				////从工厂方法找是否有限定注解，这里frieds1的工厂方法上有Girl注解，所以得到了
				targetAnnotation = getFactoryMethodAnnotation(bd, type);
			}
			// 若本类木有，还会去父类去找一趟
			if (targetAnnotation == null) {
				////从bean装饰的定义中找注解
				RootBeanDefinition dbd = getResolvedDecoratedDefinition(bd);
				if (dbd != null) {
					targetAnnotation = getFactoryMethodAnnotation(dbd, type);
				}
			}
			// 若xml、工厂方法、父里都还没找到此方法。那好家伙，回退到还去类本身上去看
			// 也就是说，如果@LoadBalanced标注在RestTemplate上，也是阔仪的
			if (targetAnnotation == null) {
				// Look for matching annotation on the target class
				if (getBeanFactory() != null) {
					try {
						Class<?> beanType = getBeanFactory().getType(bdHolder.getBeanName());
						if (beanType != null) {
							targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(beanType), type);
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// Not the usual case - simply forget about the type check...
					}
				}
				if (targetAnnotation == null && bd.hasBeanClass()) {
					targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(bd.getBeanClass()), type);
				}
			}
			// 找到了，并且当且仅当就是这个注解的时候，就return true了~
			// Tips：这里使用的是equals，所以即使目标的和Bean都标注了@Qualifier属性，value值相同才行哟~~~~
			// 简单的说：只有value值相同，才会被选中的。否则这个Bean就是不符合条件的
	
			if (targetAnnotation != null && targetAnnotation.equals(annotation)) {
				return true;
			}
		}
		// 赞。若targetAnnotation还没找到，也就是还没匹配上。仍旧还不放弃，拿到当前这个注解的所有注解属性继续尝试匹配
		Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
		if (attributes.isEmpty() && qualifier == null) {
			// If no attributes, the qualifier must be present
			return false;
		}
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String attributeName = entry.getKey();
			Object expectedValue = entry.getValue();
			Object actualValue = null;
			// Check qualifier first
			if (qualifier != null) {
				actualValue = qualifier.getAttribute(attributeName);
			}
			if (actualValue == null) {
				// Fall back on bean definition attribute
				actualValue = bd.getAttribute(attributeName);
			}
			if (actualValue == null && attributeName.equals(AutowireCandidateQualifier.VALUE_KEY) &&
					expectedValue instanceof String && bdHolder.matchesName((String) expectedValue)) {
				// Fall back on bean name (or alias) match
				continue;
			}
			if (actualValue == null && qualifier != null) {
				// Fall back on default, but only if the qualifier is present
				actualValue = AnnotationUtils.getDefaultValue(annotation, attributeName);
			}
			if (actualValue != null) {
				actualValue = typeConverter.convertIfNecessary(actualValue, expectedValue.getClass());
			}
			if (!expectedValue.equals(actualValue)) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	protected Annotation getQualifiedElementAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
		AnnotatedElement qualifiedElement = bd.getQualifiedElement();
		return (qualifiedElement != null ? AnnotationUtils.getAnnotation(qualifiedElement, type) : null);
	}

	@Nullable
	protected Annotation getFactoryMethodAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
		Method resolvedFactoryMethod = bd.getResolvedFactoryMethod();
		return (resolvedFactoryMethod != null ? AnnotationUtils.getAnnotation(resolvedFactoryMethod, type) : null);
	}


	/**
	 * Determine whether the given dependency declares an autowired annotation,
	 * checking its required flag.
	 * @see Autowired#required()
	 */
	@Override
	public boolean isRequired(DependencyDescriptor descriptor) {
		if (!super.isRequired(descriptor)) {
			return false;
		}
		Autowired autowired = descriptor.getAnnotation(Autowired.class);
		return (autowired == null || autowired.required());
	}

	/**
	 * Determine whether the given dependency declares a value annotation.
	 * @see Value
	 */
	@Override
	@Nullable
	public Object getSuggestedValue(DependencyDescriptor descriptor) {
		Object value = findValue(descriptor.getAnnotations());
		if (value == null) {
			MethodParameter methodParam = descriptor.getMethodParameter();
			if (methodParam != null) {
				value = findValue(methodParam.getMethodAnnotations());
			}
		}
		return value;
	}

	/**
	 * Determine a suggested value from any of the given candidate annotations.
	 */
	@Nullable
	protected Object findValue(Annotation[] annotationsToSearch) {
		if (annotationsToSearch.length > 0) {   // qualifier annotations have to be local
			AnnotationAttributes attr = AnnotatedElementUtils.getMergedAnnotationAttributes(
					AnnotatedElementUtils.forAnnotations(annotationsToSearch), this.valueAnnotationType);
			if (attr != null) {
				return extractValue(attr);
			}
		}
		return null;
	}

	/**
	 * Extract the value attribute from the given annotation.
	 * @since 4.3
	 */
	protected Object extractValue(AnnotationAttributes attr) {
		Object value = attr.get(AnnotationUtils.VALUE);
		if (value == null) {
			throw new IllegalStateException("Value annotation must have a value attribute");
		}
		return value;
	}

}
