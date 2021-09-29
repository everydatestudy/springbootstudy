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

package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for concrete, full-fledged {@link BeanDefinition} classes,
 * factoring out common properties of {@link GenericBeanDefinition},
 * {@link RootBeanDefinition}, and {@link ChildBeanDefinition}.
 *
 * <p>
 * The autowire constants match the ones defined in the
 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface. 最终全功能BeanDefinition实现类的基类，也就是这些类的共同属性和逻辑实现：
 * GenericBeanDefinition,RootBeanDefinition,ChildBeanDefinition.
 * https://blog.csdn.net/andy_zhang2007/article/details/85413055
 * https://blog.csdn.net/wufagang/article/details/112060810
 * 
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @see GenericBeanDefinition
 * @see RootBeanDefinition
 * @see ChildBeanDefinition
 */
@SuppressWarnings("serial")
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor
		implements BeanDefinition, Cloneable {

	/**定义众多常量。这一些常量会直接影响到spring实例化Bean时的策略
	// 个人觉得这些常量的定义不是必须的，在代码里判断即可。Spring定义这些常量的原因很简单，便于维护，让读代码的人知道每个值的意义(所以以后我们在书写代码时，也可以这么来搞)
	//默认的SCOPE，默认是单例
	 * Constant for the default scope name: {@code ""}, equivalent to singleton
	 * status unless overridden from a parent bean definition (if applicable).
	 */
	public static final String SCOPE_DEFAULT = "";

	/** 自动装配的一些常量
	 * Constant that indicates no autowiring at all. 常数，指示没有自动装配。
	 * 
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_NO = AutowireCapableBeanFactory.AUTOWIRE_NO;

	/**
	 * * 常数，表示按name自动装配bean的属性。 Constant that indicates autowiring bean properties
	 * by name.
	 * 
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_BY_NAME = AutowireCapableBeanFactory.AUTOWIRE_BY_NAME;

	/**
	 * 常数，指示按类型自动装配bean的属性。 Constant that indicates autowiring bean properties by
	 * type.
	 * 
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_BY_TYPE = AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE;

	/**
	 * Constant that indicates autowiring a constructor.
	 * 
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_CONSTRUCTOR = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;

	/**
	 * Constant that indicates determining an appropriate autowire strategy through
	 * introspection of the bean class. 常数指示不依赖检查。
	 * 
	 * @see #setAutowireMode
	 * @deprecated as of Spring 3.0: If you are using mixed autowiring strategies,
	 *             use annotation-based autowiring for clearer demarcation of
	 *             autowiring needs.
	 */
	@Deprecated
	public static final int AUTOWIRE_AUTODETECT = AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT;

	/**检查依赖是否合法，在本类中，默认不进行依赖检查
	 * Constant that indicates no dependency check at all. 常数，表示为对象引用的依赖检查。
	 * // 不进行检查
	 * @see #setDependencyCheck
	 */
	public static final int DEPENDENCY_CHECK_NONE = 0;

	/**如果依赖类型为对象引用，则需要检查
	 * Constant that indicates dependency checking for object references.
	 * 
	 * @see #setDependencyCheck
	 */
	public static final int DEPENDENCY_CHECK_OBJECTS = 1;

	/** //对简单属性的依赖进行检查
	 * Constant that indicates dependency checking for "simple" properties.
	 * 
	 * @see #setDependencyCheck
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	public static final int DEPENDENCY_CHECK_SIMPLE = 2;

	/**对所有属性的依赖进行检查
	 * Constant that indicates dependency checking for all properties (object
	 * references as well as "simple" properties).
	 * 
	 * @see #setDependencyCheck
	 */
	public static final int DEPENDENCY_CHECK_ALL = 3;

	/**	若Bean未指定销毁方法，容器应该尝试推断Bean的销毁方法的名字，目前来说，推断的销毁方法的名字一般为close或是shutdown
	//（即未指定Bean的销毁方法，但是内部定义了名为close或是shutdown的方法，则容器推断其为销毁方法）
	 * Constant that indicates the container should attempt to infer the
	 * {@link #setDestroyMethodName destroy method name} for a bean as opposed to
	 * explicit specification of a method name. The value {@value} is specifically
	 * designed to include characters otherwise illegal in a method name, ensuring
	 * no possibility of collisions with legitimately named methods having the same
	 * name.
	 * <p>
	 * 常数，方法检查 Currently, the method names detected during destroy method inference
	 * are "close" and "shutdown", if present on the specific bean class.
	 */
	public static final String INFER_METHOD = "(inferred)";

	// 当前bean定义的beanClass属性，注意并不一定是最终生成的bean所使用的class，
	// 可能是 String, 也可能是 Class
	@Nullable
	private volatile Object beanClass;
	// 目标 bean 的作用域，初始化为 "", 相当于 singleton
	////默认的scope是单例
	@Nullable
	private String scope = SCOPE_DEFAULT;
	/**
	 * 是否是抽象Bean ,对应bean属性abstractFlag
	 * //默认不为抽象类
	 */
	private boolean abstractFlag = false;
	// 是否延迟加载,对应Bean属性lazy-init
	////默认不进行自动装配
	private boolean lazyInit = false;
	// 自动注入模式,,对应Bean属性autowire
	////默认不是懒加载
	private int autowireMode = AUTOWIRE_NO;
	// 依赖检查 : 初始化为不要做依赖检查
	private int dependencyCheck = DEPENDENCY_CHECK_NONE;

	// 用来表示一个Bean的实例化依靠另一个Bean先实例化,对应Bean属性depend-on
	@Nullable
	private String[] dependsOn;
	/**
	 * // autowire-candidate属性设置为false，这样容器在查找自动装配对象时，将不考虑该bean，
	// 备注：并不影响本身注入其它的Bean
	 * 
	 * autowire-candidate属性设置为false,这样容器在查找自动装配对象时,将不考虑该bean,即它不会被考虑为其他bean自动装配的候选者,
	 * 但是该bean本身还是可以使用自动装配来注入其他属性,
	 * 
	 * 对应Bean属性autowire-candidate
	 */
	private boolean autowireCandidate = true;
	// 自动装配当出现多个bean候选者,将作为首选者,是默认不是主要候选者,对应Bean属性primary
	private boolean primary = false;
	// 用于记录qualifiers ,对应Bean属性qualifier
	//用于记录Qualifier，对应子元素qualifier=======这个字段有必要解释一下
		// 唯一向这个字段放值的方法为本类的：public void addQualifier(AutowireCandidateQualifier qualifier)    copyQualifiersFrom这个不算，那属于拷贝
		// 调用处：AnnotatedBeanDefinitionReader#doRegisterBean  但是Spring所有调用处，qualifiers字段传的都是null~~~~~~~~~尴尬
		// 通过我多放跟踪发现，此处这个字段目前【永远】不会被赋值（除非我们手动调用对应方法为其赋值）   但是有可能我才疏学浅，若有知道的  请告知，非常非常感谢  我考虑到它可能是预留字段~~~~
		// 我起初以为这样可以赋值：
		//@Qualifier("aaa")
		//@Service
		//public class HelloServiceImpl   没想到，也是不好使的，Bean定义里面也不会有值
		// 因此对应的方法getQualifier和getQualifiers 目前应该基本上都返回null或者[]
	private final Map<String, AutowireCandidateQualifier> qualifiers = new LinkedHashMap<>();

	@Nullable
	private Supplier<?> instanceSupplier;
	// 是否允许访问非公开构造函数，非公开方法
	// 该属性主要用于构造函数解析，初始化方法,析构方法解析，bean属性的set/get方法不受该属性影响
	private boolean nonPublicAccessAllowed = true;
	// 调用构造函数时，是否采用宽松匹配
	/**
	 * 是否以一种宽松的模式解析构造函数,默认为true,
	 * 
	 * 如果为false,则在如下情况,
	 * 
	 * interface Person{}
	 * 
	 * class Theacher implemente Person{}
	 * 
	 * class Main{
	 * 
	 * Main(Person p){}
	 * 
	 * Main(Theacher t){} 抛出异常,因为Spring无法准确定位哪个构造函数,  程序设置  }  
	 *
	 */
	private boolean lenientConstructorResolution = true;
	/**
	 * 228 * 对应Bean的factory-bean 的属性,用法: 229 * 230 *
	 * <bean id = "factoryBean" class = "test.TestFactoryBean"> 231 * 232 *
	 * <bean id = "ct" factory-bean= "factoryBean" factory-method = "getBean"> 233 *
	 * 234 * 235
	 */
	@Nullable
	private String factoryBeanName;
	// 工厂方法名称
	@Nullable
	private String factoryMethodName;
	// 构造函数参数值
	@Nullable
	private ConstructorArgumentValues constructorArgumentValues;
	/**
	 * 普通属性的集合
	 */
	@Nullable
	private MutablePropertyValues propertyValues;
	/**
	 * 方法重写的持有者 ,记录 lookup-method,replaced-method元素
	 */
	@Nullable
	private MethodOverrides methodOverrides;

	@Nullable
	private String initMethodName;

	@Nullable
	private String destroyMethodName;
	/**
	 * 254 * 是否执行 init-method 方法,默认执行初始化方法,程序设置 255
	 */
	private boolean enforceInitMethod = true;
// 是否执行 destory-method 方法,默认执行销毁方法,程序设置
	private boolean enforceDestroyMethod = true;
	// 是否是一个合成 BeanDefinition,
	// 合成 在这里的意思表示这不是一个应用开发人员自己定义的 BeanDefinition, 而是程序
	// 自己组装而成的一个 BeanDefinition, 例子 :
	// 1. 自动代理的helper bean，一个基础设施bean，因为使用<aop:config> 被自动合成创建;
	// 2. bean errorPageRegistrarBeanPostProcessor , Spring boot 自动配置针对Web错误页面的
	// 一个bean，这个bean不需要应用开发人员定义，而是框架根据上下文自动合成组装而成；
	private boolean synthetic = false;
	/**
	269      * 定义这个bean的应用,
	270      * 
	271      * ROLE_APPLICATION:用户,
	272      * 
	273      * ROLE_INFRASTRUCTURE:完全内部使用,与用户无关;
	274      * 
	275      * ROLE_SUPPORT:某些复杂配置的一部分
	276      */
	private int role = BeanDefinition.ROLE_APPLICATION;
	// 资源描述
	@Nullable
	private String description;

	@Nullable
	private Resource resource;

	/**
	 * Create a new AbstractBeanDefinition with default settings.
	 */
	protected AbstractBeanDefinition() {
		this(null, null);
	}

	/**
	 * Create a new AbstractBeanDefinition with the given constructor argument
	 * values and property values.
	 */
	protected AbstractBeanDefinition(@Nullable ConstructorArgumentValues cargs, @Nullable MutablePropertyValues pvs) {
		this.constructorArgumentValues = cargs;
		this.propertyValues = pvs;
	}

	/**
	 * 深度复制给定的bean定义创建一个新的AbstractBeanDefinition Create a new AbstractBeanDefinition
	 * as a deep copy of the given bean definition.
	 * 
	 * @param original the original bean definition to copy from
	 */
	protected AbstractBeanDefinition(BeanDefinition original) {

//        // 抽象方法，由子类实现，设置父类名
//319         setParentName(original.getParentName());
//320         // 设置这个bean的类名称
//321         setBeanClassName(original.getBeanClassName());
//322         // 设置这个bean的工厂bean名称
//323         setFactoryBeanName(original.getFactoryBeanName());
//324         // 设置这个bean的工厂方法名称
//325         setFactoryMethodName(original.getFactoryMethodName());
//326         // 设置这个bean的作用范围，如单例的还是原型的，也有可能是其它的
//327         setScope(original.getScope());
//328         // 设置这个bean是否是抽象的
//329         setAbstract(original.isAbstract());
//330         // 设置这个bean是否开启延载初始化
//331         setLazyInit(original.isLazyInit());
//332         // 设置这个bean的角色
//333         setRole(original.getRole());
//334         // 设置这个bean的构造参数持有者
//335         setConstructorArgumentValues(new ConstructorArgumentValues(
//336                 original.getConstructorArgumentValues()));
//337         // 设置这个bean的Property持有者
//338         setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
//339         // 设置这个bean的配置源
//340         setSource(original.getSource());
//341         // 这个bean复制属性组名称
//342         copyAttributesFrom(original);
//343         // 判断origina是否是AbstractBeanDefinition子类
//344         if (original instanceof AbstractBeanDefinition) {
//345             AbstractBeanDefinition originalAbd = (AbstractBeanDefinition) original;
//346             if (originalAbd.hasBeanClass()) {
//347                 // 设置这个bean的Class类型
//348                 setBeanClass(originalAbd.getBeanClass());
//349             }
//350             // 设置这个bean的自动装配模式
//351             setAutowireMode(originalAbd.getAutowireMode());
//352             // 设置这个bean的依赖检查
//353             setDependencyCheck(originalAbd.getDependencyCheck());
//354             // 设置这个bean初始化要依赖的bean名称数组
//355             setDependsOn(originalAbd.getDependsOn());
//356             // 设置这个bean是否自动装配候选
//357             setAutowireCandidate(originalAbd.isAutowireCandidate());
//358             // 设置这个bean的qualifier
//359             copyQualifiersFrom(originalAbd);
//360             // 设置这个bean是否是主要候选者
//361             setPrimary(originalAbd.isPrimary());
//362             // 设置是否允许访问非public的构造器和方法
//363             setNonPublicAccessAllowed(originalAbd.isNonPublicAccessAllowed());
//364             // 设置是否以一种宽松的模式解析构造函数
//365             setLenientConstructorResolution(originalAbd.isLenientConstructorResolution());
//366             // 设置这个bean的初始化方法名
//367             setInitMethodName(originalAbd.getInitMethodName());
//368             // 设置是否执行初始化方法
//369             setEnforceInitMethod(originalAbd.isEnforceInitMethod());
//370             // 设置这个bean的销毁方法名
//371             setDestroyMethodName(originalAbd.getDestroyMethodName());
//372             // 设置是否执行销毁方法
//373             setEnforceDestroyMethod(originalAbd.isEnforceDestroyMethod());
//374             // 设置这个bean的方法重写持有者
//375             setMethodOverrides(new MethodOverrides(originalAbd.getMethodOverrides()));
//376             // 设置这个bean是人造的或者是应用程序本身
//377             setSynthetic(originalAbd.isSynthetic());
//378             // 设置这个Bean定义的资源
//379             setResource(originalAbd.getResource());
		// 抽象方法，由子类实现，设置父类名
		setParentName(original.getParentName());
		setBeanClassName(original.getBeanClassName());
		setScope(original.getScope());
		setAbstract(original.isAbstract());
		setLazyInit(original.isLazyInit());
		setFactoryBeanName(original.getFactoryBeanName());
		setFactoryMethodName(original.getFactoryMethodName());
		setRole(original.getRole());
		setSource(original.getSource());
		// 这个bean复制属性组名称
		copyAttributesFrom(original);
		// 判断origina是否是AbstractBeanDefinition子类
		if (original instanceof AbstractBeanDefinition) {
			AbstractBeanDefinition originalAbd = (AbstractBeanDefinition) original;
			if (originalAbd.hasBeanClass()) {
				setBeanClass(originalAbd.getBeanClass());
			}
			if (originalAbd.hasConstructorArgumentValues()) {
				setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
			}
			if (originalAbd.hasPropertyValues()) {
				setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
			}
			if (originalAbd.hasMethodOverrides()) {
				setMethodOverrides(new MethodOverrides(originalAbd.getMethodOverrides()));
			}
			setAutowireMode(originalAbd.getAutowireMode());
			setDependencyCheck(originalAbd.getDependencyCheck());
			setDependsOn(originalAbd.getDependsOn());
			setAutowireCandidate(originalAbd.isAutowireCandidate());
			setPrimary(originalAbd.isPrimary());
			copyQualifiersFrom(originalAbd);
			setInstanceSupplier(originalAbd.getInstanceSupplier());
			setNonPublicAccessAllowed(originalAbd.isNonPublicAccessAllowed());
			setLenientConstructorResolution(originalAbd.isLenientConstructorResolution());
			setInitMethodName(originalAbd.getInitMethodName());
			setEnforceInitMethod(originalAbd.isEnforceInitMethod());
			setDestroyMethodName(originalAbd.getDestroyMethodName());
			setEnforceDestroyMethod(originalAbd.isEnforceDestroyMethod());
			setSynthetic(originalAbd.isSynthetic());
			setResource(originalAbd.getResource());
		} else {
			setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
			setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
			setResourceDescription(original.getResourceDescription());
		}
	}

	/**
	 * Override settings in this bean definition (presumably a copied parent from a
	 * parent-child inheritance relationship) from the given bean definition
	 * (presumably the child). 本方法的一个主要用途是用在根据bean定义之间的父子关系生成最终merged的孩子bean定义对象:
	 * 此时先使用双亲bean定义生成一个RootBeanDefinition,然后调用该RootBeanDefinition
	 * 对象的overrideFrom(other)方法，这里other就是child bean定义，然后这个RootBeanDefinition
	 * 就是一个继承自双亲bean定义又符合原始child bean定义的一个最终被使用的BeanDefinition了。
	 * 
	 * <ul>
	 * <li>Will override beanClass if specified in the given bean definition.
	 * <li>Will always take {@code abstract}, {@code scope}, {@code lazyInit},
	 * {@code autowireMode}, {@code dependencyCheck}, and {@code dependsOn} from the
	 * given bean definition.
	 * <li>Will add {@code constructorArgumentValues}, {@code propertyValues},
	 * {@code methodOverrides} from the given bean definition to existing ones.
	 * <li>Will override {@code factoryBeanName}, {@code factoryMethodName},
	 * {@code initMethodName}, and {@code destroyMethodName} if specified in the
	 * given bean definition.
	 * </ul>
	 */
	public void overrideFrom(BeanDefinition other) {
		if (StringUtils.hasLength(other.getBeanClassName())) {
			setBeanClassName(other.getBeanClassName());
		}
		if (StringUtils.hasLength(other.getScope())) {
			setScope(other.getScope());
		}
		setAbstract(other.isAbstract());
		setLazyInit(other.isLazyInit());
		if (StringUtils.hasLength(other.getFactoryBeanName())) {
			setFactoryBeanName(other.getFactoryBeanName());
		}
		if (StringUtils.hasLength(other.getFactoryMethodName())) {
			setFactoryMethodName(other.getFactoryMethodName());
		}
		setRole(other.getRole());
		setSource(other.getSource());
		copyAttributesFrom(other);

		if (other instanceof AbstractBeanDefinition) {
			AbstractBeanDefinition otherAbd = (AbstractBeanDefinition) other;
			if (otherAbd.hasBeanClass()) {
				setBeanClass(otherAbd.getBeanClass());
			}
			if (otherAbd.hasConstructorArgumentValues()) {
				getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
			}
			if (otherAbd.hasPropertyValues()) {
				getPropertyValues().addPropertyValues(other.getPropertyValues());
			}
			if (otherAbd.hasMethodOverrides()) {
				getMethodOverrides().addOverrides(otherAbd.getMethodOverrides());
			}
			setAutowireMode(otherAbd.getAutowireMode());
			setDependencyCheck(otherAbd.getDependencyCheck());
			setDependsOn(otherAbd.getDependsOn());
			setAutowireCandidate(otherAbd.isAutowireCandidate());
			setPrimary(otherAbd.isPrimary());
			copyQualifiersFrom(otherAbd);
			setInstanceSupplier(otherAbd.getInstanceSupplier());
			setNonPublicAccessAllowed(otherAbd.isNonPublicAccessAllowed());
			setLenientConstructorResolution(otherAbd.isLenientConstructorResolution());
			if (otherAbd.getInitMethodName() != null) {
				setInitMethodName(otherAbd.getInitMethodName());
				setEnforceInitMethod(otherAbd.isEnforceInitMethod());
			}
			if (otherAbd.getDestroyMethodName() != null) {
				setDestroyMethodName(otherAbd.getDestroyMethodName());
				setEnforceDestroyMethod(otherAbd.isEnforceDestroyMethod());
			}
			setSynthetic(otherAbd.isSynthetic());
			setResource(otherAbd.getResource());
		} else {
			getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
			getPropertyValues().addPropertyValues(other.getPropertyValues());
			setResourceDescription(other.getResourceDescription());
		}
	}

	/**
	 * Apply the provided default values to this bean.
	 * 
	 * @param defaults the defaults to apply
	 */
	public void applyDefaults(BeanDefinitionDefaults defaults) {
		setLazyInit(defaults.isLazyInit());
		setAutowireMode(defaults.getAutowireMode());
		setDependencyCheck(defaults.getDependencyCheck());
		setInitMethodName(defaults.getInitMethodName());
		setEnforceInitMethod(false);
		setDestroyMethodName(defaults.getDestroyMethodName());
		setEnforceDestroyMethod(false);
	}

	/**
	 * Specify the bean class name of this bean definition.
	 */
	@Override
	public void setBeanClassName(@Nullable String beanClassName) {
		this.beanClass = beanClassName;
	}

	/**
	 * Return the current bean class name of this bean definition.
	 */
	@Override
	@Nullable
	public String getBeanClassName() {
		Object beanClassObject = this.beanClass;
		if (beanClassObject instanceof Class) {
			return ((Class<?>) beanClassObject).getName();
		} else {
			return (String) beanClassObject;
		}
	}

	/**
	 * Specify the class for this bean.
	 */
	public void setBeanClass(@Nullable Class<?> beanClass) {
		this.beanClass = beanClass;
	}

	/**
	 * Return the class of the wrapped bean, if already resolved.
	 * 
	 * @return the bean class, or {@code null} if none defined
	 * @throws IllegalStateException if the bean definition does not define a bean
	 *                               class, or a specified bean class name has not
	 *                               been resolved into an actual Class
	 */
	public Class<?> getBeanClass() throws IllegalStateException {
		Object beanClassObject = this.beanClass;
		if (beanClassObject == null) {
			throw new IllegalStateException("No bean class specified on bean definition");
		}
		if (!(beanClassObject instanceof Class)) {
			throw new IllegalStateException(
					"Bean class name [" + beanClassObject + "] has not been resolved into an actual Class");
		}
		return (Class<?>) beanClassObject;
	}

	/**
	 * Return whether this definition specifies a bean class.
	 */
	public boolean hasBeanClass() {
		return (this.beanClass instanceof Class);
	}

	/**
	 * Determine the class of the wrapped bean, resolving it from a specified class
	 * name if necessary. Will also reload a specified Class from its name when
	 * called with the bean class already resolved.
	 * 
	 * @param classLoader the ClassLoader to use for resolving a (potential) class
	 *                    name
	 * @return the resolved bean class
	 * @throws ClassNotFoundException if the class name could be resolved
	 */
	@Nullable
	public Class<?> resolveBeanClass(@Nullable ClassLoader classLoader) throws ClassNotFoundException {
		String className = getBeanClassName();
		if (className == null) {
			return null;
		}
		Class<?> resolvedClass = ClassUtils.forName(className, classLoader);
		this.beanClass = resolvedClass;
		return resolvedClass;
	}

	/**
	 * Set the name of the target scope for the bean.
	 * <p>
	 * The default is singleton status, although this is only applied once a bean
	 * definition becomes active in the containing factory. A bean definition may
	 * eventually inherit its scope from a parent bean definition. For this reason,
	 * the default scope name is an empty string (i.e., {@code ""}), with singleton
	 * status being assumed until a resolved scope is set.
	 * 
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	@Override
	public void setScope(@Nullable String scope) {
		this.scope = scope;
	}

	/**
	 * Return the name of the target scope for the bean.
	 */
	@Override
	@Nullable
	public String getScope() {
		return this.scope;
	}

	/**
	 * Return whether this a <b>Singleton</b>, with a single shared instance
	 * returned from all calls.
	 * 
	 * @see #SCOPE_SINGLETON
	 */
	@Override
	public boolean isSingleton() {
		return SCOPE_SINGLETON.equals(this.scope) || SCOPE_DEFAULT.equals(this.scope);
	}

	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance returned
	 * for each call.
	 * 
	 * @see #SCOPE_PROTOTYPE
	 */
	@Override
	public boolean isPrototype() {
		return SCOPE_PROTOTYPE.equals(this.scope);
	}

	/**
	 * Set if this bean is "abstract", i.e. not meant to be instantiated itself but
	 * rather just serving as parent for concrete child bean definitions.
	 * <p>
	 * Default is "false". Specify true to tell the bean factory to not try to
	 * instantiate that particular bean in any case.
	 */
	public void setAbstract(boolean abstractFlag) {
		this.abstractFlag = abstractFlag;
	}

	/**
	 * Return whether this bean is "abstract", i.e. not meant to be instantiated
	 * itself but rather just serving as parent for concrete child bean definitions.
	 */
	@Override
	public boolean isAbstract() {
		return this.abstractFlag;
	}

	/**
	 * Set whether this bean should be lazily initialized.
	 * <p>
	 * If {@code false}, the bean will get instantiated on startup by bean factories
	 * that perform eager initialization of singletons.
	 */
	@Override
	public void setLazyInit(boolean lazyInit) {
		this.lazyInit = lazyInit;
	}

	/**
	 * Return whether this bean should be lazily initialized, i.e. not eagerly
	 * instantiated on startup. Only applicable to a singleton bean.
	 */
	@Override
	public boolean isLazyInit() {
		return this.lazyInit;
	}

	/**
	 * Set the autowire mode. This determines whether any automagical detection and
	 * setting of bean references will happen. Default is AUTOWIRE_NO, which means
	 * there's no autowire.
	 * 
	 * @param autowireMode the autowire mode to set. Must be one of the constants
	 *                     defined in this class.
	 * @see #AUTOWIRE_NO
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @see #AUTOWIRE_AUTODETECT
	 */
	public void setAutowireMode(int autowireMode) {
		this.autowireMode = autowireMode;
	}

	/**
	 * Return the autowire mode as specified in the bean definition.
	 */
	public int getAutowireMode() {
		return this.autowireMode;
	}

	/**
	 * Return the resolved autowire code, (resolving AUTOWIRE_AUTODETECT to
	 * AUTOWIRE_CONSTRUCTOR or AUTOWIRE_BY_TYPE).
	 * 
	 * @see #AUTOWIRE_AUTODETECT
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @see #AUTOWIRE_BY_TYPE
	 */
	public int getResolvedAutowireMode() {
		if (this.autowireMode == AUTOWIRE_AUTODETECT) {
			// Work out whether to apply setter autowiring or constructor autowiring.
			// If it has a no-arg constructor it's deemed to be setter autowiring,
			// otherwise we'll try constructor autowiring.
			Constructor<?>[] constructors = getBeanClass().getConstructors();
			for (Constructor<?> constructor : constructors) {
				if (constructor.getParameterCount() == 0) {
					return AUTOWIRE_BY_TYPE;
				}
			}
			return AUTOWIRE_CONSTRUCTOR;
		} else {
			return this.autowireMode;
		}
	}

	/**
	 * Set the dependency check code.
	 * 
	 * @param dependencyCheck the code to set. Must be one of the four constants
	 *                        defined in this class.
	 * @see #DEPENDENCY_CHECK_NONE
	 * @see #DEPENDENCY_CHECK_OBJECTS
	 * @see #DEPENDENCY_CHECK_SIMPLE
	 * @see #DEPENDENCY_CHECK_ALL
	 */
	public void setDependencyCheck(int dependencyCheck) {
		this.dependencyCheck = dependencyCheck;
	}

	/**
	 * Return the dependency check code.
	 */
	public int getDependencyCheck() {
		return this.dependencyCheck;
	}

	/**
	 * Set the names of the beans that this bean depends on being initialized. The
	 * bean factory will guarantee that these beans get initialized first.
	 * <p>
	 * Note that dependencies are normally expressed through bean properties or
	 * constructor arguments. This property should just be necessary for other kinds
	 * of dependencies like statics (*ugh*) or database preparation on startup.
	 */
	@Override
	public void setDependsOn(@Nullable String... dependsOn) {
		this.dependsOn = dependsOn;
	}

	/**
	 * Return the bean names that this bean depends on.
	 */
	@Override
	@Nullable
	public String[] getDependsOn() {
		return this.dependsOn;
	}

	/**
	 * Set whether this bean is a candidate for getting autowired into some other
	 * bean.
	 * <p>
	 * Note that this flag is designed to only affect type-based autowiring. It does
	 * not affect explicit references by name, which will get resolved even if the
	 * specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 * 
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_BY_NAME
	 */
	@Override
	public void setAutowireCandidate(boolean autowireCandidate) {
		this.autowireCandidate = autowireCandidate;
	}

	/**
	 * Return whether this bean is a candidate for getting autowired into some other
	 * bean.
	 */
	@Override
	public boolean isAutowireCandidate() {
		return this.autowireCandidate;
	}

	/**
	 * Set whether this bean is a primary autowire candidate.
	 * <p>
	 * If this value is {@code true} for exactly one bean among multiple matching
	 * candidates, it will serve as a tie-breaker.
	 */
	@Override
	public void setPrimary(boolean primary) {
		this.primary = primary;
	}

	/**
	 * Return whether this bean is a primary autowire candidate.
	 */
	@Override
	public boolean isPrimary() {
		return this.primary;
	}

	/**
	 * Register a qualifier to be used for autowire candidate resolution, keyed by
	 * the qualifier's type name.
	 * 
	 * @see AutowireCandidateQualifier#getTypeName()
	 */
	public void addQualifier(AutowireCandidateQualifier qualifier) {
		this.qualifiers.put(qualifier.getTypeName(), qualifier);
	}

	/**
	 * Return whether this bean has the specified qualifier.
	 */
	public boolean hasQualifier(String typeName) {
		return this.qualifiers.keySet().contains(typeName);
	}

	/**
	 * Return the qualifier mapped to the provided type name.
	 */
	@Nullable
	public AutowireCandidateQualifier getQualifier(String typeName) {
		return this.qualifiers.get(typeName);
	}

	/**
	 * Return all registered qualifiers.
	 * 
	 * @return the Set of {@link AutowireCandidateQualifier} objects.
	 */
	public Set<AutowireCandidateQualifier> getQualifiers() {
		return new LinkedHashSet<>(this.qualifiers.values());
	}

	/**
	 * Copy the qualifiers from the supplied AbstractBeanDefinition to this bean
	 * definition.
	 * 
	 * @param source the AbstractBeanDefinition to copy from
	 */
	public void copyQualifiersFrom(AbstractBeanDefinition source) {
		Assert.notNull(source, "Source must not be null");
		this.qualifiers.putAll(source.qualifiers);
	}

	/**
	 * Specify a callback for creating an instance of the bean, as an alternative to
	 * a declaratively specified factory method.
	 * <p>
	 * If such a callback is set, it will override any other constructor or factory
	 * method metadata. However, bean property population and potential
	 * annotation-driven injection will still apply as usual.
	 * 
	 * @since 5.0
	 * @see #setConstructorArgumentValues(ConstructorArgumentValues)
	 * @see #setPropertyValues(MutablePropertyValues)
	 */
	public void setInstanceSupplier(@Nullable Supplier<?> instanceSupplier) {
		this.instanceSupplier = instanceSupplier;
	}

	/**
	 * Return a callback for creating an instance of the bean, if any.
	 * 
	 * @since 5.0
	 */
	@Nullable
	public Supplier<?> getInstanceSupplier() {
		return this.instanceSupplier;
	}

	/**
	 * Specify whether to allow access to non-public constructors and methods, for
	 * the case of externalized metadata pointing to those. The default is
	 * {@code true}; switch this to {@code false} for public access only.
	 * <p>
	 * This applies to constructor resolution, factory method resolution, and also
	 * init/destroy methods. Bean property accessors have to be public in any case
	 * and are not affected by this setting.
	 * <p>
	 * Note that annotation-driven configuration will still access non-public
	 * members as far as they have been annotated. This setting applies to
	 * externalized metadata in this bean definition only.
	 */
	public void setNonPublicAccessAllowed(boolean nonPublicAccessAllowed) {
		this.nonPublicAccessAllowed = nonPublicAccessAllowed;
	}

	/**
	 * Return whether to allow access to non-public constructors and methods.
	 */
	public boolean isNonPublicAccessAllowed() {
		return this.nonPublicAccessAllowed;
	}

	/**
	 * Specify whether to resolve constructors in lenient mode ({@code true}, which
	 * is the default) or to switch to strict resolution (throwing an exception in
	 * case of ambiguous constructors that all match when converting the arguments,
	 * whereas lenient mode would use the one with the 'closest' type matches).
	 */
	public void setLenientConstructorResolution(boolean lenientConstructorResolution) {
		this.lenientConstructorResolution = lenientConstructorResolution;
	}

	/**
	 * Return whether to resolve constructors in lenient mode or in strict mode.
	 */
	public boolean isLenientConstructorResolution() {
		return this.lenientConstructorResolution;
	}

	/**
	 * Specify the factory bean to use, if any. This the name of the bean to call
	 * the specified factory method on.
	 * 
	 * @see #setFactoryMethodName
	 */
	@Override
	public void setFactoryBeanName(@Nullable String factoryBeanName) {
		this.factoryBeanName = factoryBeanName;
	}

	/**
	 * Return the factory bean name, if any.
	 */
	@Override
	@Nullable
	public String getFactoryBeanName() {
		return this.factoryBeanName;
	}

	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified. The method
	 * will be invoked on the specified factory bean, if any, or otherwise as a
	 * static method on the local bean class.
	 * 
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	@Override
	public void setFactoryMethodName(@Nullable String factoryMethodName) {
		this.factoryMethodName = factoryMethodName;
	}

	/**
	 * Return a factory method, if any.
	 */
	@Override
	@Nullable
	public String getFactoryMethodName() {
		return this.factoryMethodName;
	}

	/**
	 * Specify constructor argument values for this bean.
	 */
	public void setConstructorArgumentValues(ConstructorArgumentValues constructorArgumentValues) {
		this.constructorArgumentValues = constructorArgumentValues;
	}

	/**
	 * Return constructor argument values for this bean (never {@code null}).
	 */
	@Override
	public ConstructorArgumentValues getConstructorArgumentValues() {
		if (this.constructorArgumentValues == null) {
			this.constructorArgumentValues = new ConstructorArgumentValues();
		}
		return this.constructorArgumentValues;
	}

	/**
	 * Return if there are constructor argument values defined for this bean.
	 */
	@Override
	public boolean hasConstructorArgumentValues() {
		return (this.constructorArgumentValues != null && !this.constructorArgumentValues.isEmpty());
	}

	/**
	 * Specify property values for this bean, if any.
	 */
	public void setPropertyValues(MutablePropertyValues propertyValues) {
		this.propertyValues = propertyValues;
	}

	/**
	 * Return property values for this bean (never {@code null}).
	 */
	@Override
	public MutablePropertyValues getPropertyValues() {
		if (this.propertyValues == null) {
			this.propertyValues = new MutablePropertyValues();
		}
		return this.propertyValues;
	}

	/**
	 * Return if there are property values values defined for this bean.
	 * 
	 * @since 5.0.2
	 */
	@Override
	public boolean hasPropertyValues() {
		return (this.propertyValues != null && !this.propertyValues.isEmpty());
	}

	/**
	 * Specify method overrides for the bean, if any.
	 */
	public void setMethodOverrides(MethodOverrides methodOverrides) {
		this.methodOverrides = methodOverrides;
	}

	/**
	 * Return information about methods to be overridden by the IoC container. This
	 * will be empty if there are no method overrides.
	 * <p>
	 * Never returns {@code null}.
	 */
	public MethodOverrides getMethodOverrides() {
		if (this.methodOverrides == null) {
			this.methodOverrides = new MethodOverrides();
		}
		return this.methodOverrides;
	}

	/**
	 * Return if there are method overrides defined for this bean.
	 * 
	 * @since 5.0.2
	 */
	public boolean hasMethodOverrides() {
		return (this.methodOverrides != null && !this.methodOverrides.isEmpty());
	}

	/**
	 * Set the name of the initializer method.
	 * <p>
	 * The default is {@code null} in which case there is no initializer method.
	 */
	public void setInitMethodName(@Nullable String initMethodName) {
		this.initMethodName = initMethodName;
	}

	/**
	 * Return the name of the initializer method.
	 */
	@Nullable
	public String getInitMethodName() {
		return this.initMethodName;
	}

	/**
	 * Specify whether or not the configured init method is the default.
	 * <p>
	 * The default value is {@code false}.
	 * 
	 * @see #setInitMethodName
	 */
	public void setEnforceInitMethod(boolean enforceInitMethod) {
		this.enforceInitMethod = enforceInitMethod;
	}

	/**
	 * Indicate whether the configured init method is the default.
	 * 
	 * @see #getInitMethodName()
	 */
	public boolean isEnforceInitMethod() {
		return this.enforceInitMethod;
	}

	/**
	 * Set the name of the destroy method.
	 * <p>
	 * The default is {@code null} in which case there is no destroy method.
	 */
	public void setDestroyMethodName(@Nullable String destroyMethodName) {
		this.destroyMethodName = destroyMethodName;
	}

	/**
	 * Return the name of the destroy method.
	 */
	@Nullable
	public String getDestroyMethodName() {
		return this.destroyMethodName;
	}

	/**
	 * Specify whether or not the configured destroy method is the default.
	 * <p>
	 * The default value is {@code false}.
	 * 
	 * @see #setDestroyMethodName
	 */
	public void setEnforceDestroyMethod(boolean enforceDestroyMethod) {
		this.enforceDestroyMethod = enforceDestroyMethod;
	}

	/**
	 * Indicate whether the configured destroy method is the default.
	 * 
	 * @see #getDestroyMethodName
	 */
	public boolean isEnforceDestroyMethod() {
		return this.enforceDestroyMethod;
	}

	/**
	 * Set whether this bean definition is 'synthetic', that is, not defined by the
	 * application itself (for example, an infrastructure bean such as a helper for
	 * auto-proxying, created through {@code <aop:config>}).
	 */
	public void setSynthetic(boolean synthetic) {
		this.synthetic = synthetic;
	}

	/**
	 * Return whether this bean definition is 'synthetic', that is, not defined by
	 * the application itself.
	 */
	public boolean isSynthetic() {
		return this.synthetic;
	}

	/**
	 * Set the role hint for this {@code BeanDefinition}.
	 */
	public void setRole(int role) {
		this.role = role;
	}

	/**
	 * Return the role hint for this {@code BeanDefinition}.
	 */
	@Override
	public int getRole() {
		return this.role;
	}

	/**
	 * Set a human-readable description of this bean definition.
	 */
	public void setDescription(@Nullable String description) {
		this.description = description;
	}

	/**
	 * Return a human-readable description of this bean definition.
	 */
	@Override
	@Nullable
	public String getDescription() {
		return this.description;
	}

	/**
	 * Set the resource that this bean definition came from (for the purpose of
	 * showing context in case of errors).
	 */
	public void setResource(@Nullable Resource resource) {
		this.resource = resource;
	}

	/**
	 * Return the resource that this bean definition came from.
	 */
	@Nullable
	public Resource getResource() {
		return this.resource;
	}

	/**
	 * Set a description of the resource that this bean definition came from (for
	 * the purpose of showing context in case of errors).
	 */
	public void setResourceDescription(@Nullable String resourceDescription) {
		this.resource = (resourceDescription != null ? new DescriptiveResource(resourceDescription) : null);
	}

	/**
	 * Return a description of the resource that this bean definition came from (for
	 * the purpose of showing context in case of errors).
	 */
	@Override
	@Nullable
	public String getResourceDescription() {
		return (this.resource != null ? this.resource.getDescription() : null);
	}

	/**
	 * Set the originating (e.g. decorated) BeanDefinition, if any.
	 */
	public void setOriginatingBeanDefinition(BeanDefinition originatingBd) {
		this.resource = new BeanDefinitionResource(originatingBd);
	}

	/**
	 * Return the originating BeanDefinition, or {@code null} if none. Allows for
	 * retrieving the decorated bean definition, if any.
	 * <p>
	 * Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 */
	@Override
	@Nullable
	public BeanDefinition getOriginatingBeanDefinition() {
		return (this.resource instanceof BeanDefinitionResource
				? ((BeanDefinitionResource) this.resource).getBeanDefinition()
				: null);
	}

	/**
	 * Validate this bean definition.
	 * 
	 * @throws BeanDefinitionValidationException in case of validation failure
	 */
	public void validate() throws BeanDefinitionValidationException {
		if (hasMethodOverrides() && getFactoryMethodName() != null) {
			throw new BeanDefinitionValidationException("Cannot combine static factory method with method overrides: "
					+ "the static factory method must create the instance");
		}

		if (hasBeanClass()) {
			prepareMethodOverrides();
		}
	}

	/**
	 * Validate and prepare the method overrides defined for this bean. Checks for
	 * existence of a method with the specified name.
	 * 
	 * @throws BeanDefinitionValidationException in case of validation failure
	 */
	public void prepareMethodOverrides() throws BeanDefinitionValidationException {
		// Check that lookup methods exists.
		if (hasMethodOverrides()) {
			Set<MethodOverride> overrides = getMethodOverrides().getOverrides();
			synchronized (overrides) {
				for (MethodOverride mo : overrides) {
					prepareMethodOverride(mo);
				}
			}
		}
	}

	/**
	 * Validate and prepare the given method override. Checks for existence of a
	 * method with the specified name, marking it as not overloaded if none found.
	 * 
	 * @param mo the MethodOverride object to validate
	 * @throws BeanDefinitionValidationException in case of validation failure
	 */
	protected void prepareMethodOverride(MethodOverride mo) throws BeanDefinitionValidationException {
		// 遍历MethodOverrides，对于一个方法的匹配来j讲，如果一个l类中存在若干个重载方法，
		//那么，在函数调用以及增强的时候还需要根据参数类型进行匹配，来最终确认当前调用的到底是哪个函数
		//，但是，spring将一部分匹配工作在这里完成了，如果当前类中的方法只有一个，那么就设置重载该方法没有被重载，
		//这样在后续调用的时候便可以直接使用找到的方法，而不需要j进行方法的参数匹配了，而且还可以提前对方法存在性进行验证。
		// 递归先后遍历：1、当前类方法遍历。2、当前类接口的方法遍历。3、当前类超类的方法遍历。

		// 最终返回的是一个对当前类的代理类。通过CGLIB生成不同的代理类。
		int count = ClassUtils.getMethodCountForName(getBeanClass(), mo.getMethodName());
		if (count == 0) {
			throw new BeanDefinitionValidationException("Invalid method override: no method with name '"
					+ mo.getMethodName() + "' on class [" + getBeanClassName() + "]");
		} else if (count == 1) {
			// Mark override as not overloaded, to avoid the overhead of arg type checking.
			mo.setOverloaded(false);
		}
	}

	/**
	 * Public declaration of Object's {@code clone()} method. Delegates to
	 * {@link #cloneBeanDefinition()}.
	 * 
	 * @see Object#clone()
	 */
	@Override
	public Object clone() {
		return cloneBeanDefinition();
	}

	/**
	 * Clone this bean definition. To be implemented by concrete subclasses.
	 * 
	 * @return the cloned bean definition object
	 */
	public abstract AbstractBeanDefinition cloneBeanDefinition();

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AbstractBeanDefinition)) {
			return false;
		}

		AbstractBeanDefinition that = (AbstractBeanDefinition) other;

		if (!ObjectUtils.nullSafeEquals(getBeanClassName(), that.getBeanClassName()))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.scope, that.scope))
			return false;
		if (this.abstractFlag != that.abstractFlag)
			return false;
		if (this.lazyInit != that.lazyInit)
			return false;

		if (this.autowireMode != that.autowireMode)
			return false;
		if (this.dependencyCheck != that.dependencyCheck)
			return false;
		if (!Arrays.equals(this.dependsOn, that.dependsOn))
			return false;
		if (this.autowireCandidate != that.autowireCandidate)
			return false;
		if (!ObjectUtils.nullSafeEquals(this.qualifiers, that.qualifiers))
			return false;
		if (this.primary != that.primary)
			return false;

		if (this.nonPublicAccessAllowed != that.nonPublicAccessAllowed)
			return false;
		if (this.lenientConstructorResolution != that.lenientConstructorResolution)
			return false;
		if (!ObjectUtils.nullSafeEquals(this.constructorArgumentValues, that.constructorArgumentValues))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.propertyValues, that.propertyValues))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.methodOverrides, that.methodOverrides))
			return false;

		if (!ObjectUtils.nullSafeEquals(this.factoryBeanName, that.factoryBeanName))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.factoryMethodName, that.factoryMethodName))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.initMethodName, that.initMethodName))
			return false;
		if (this.enforceInitMethod != that.enforceInitMethod)
			return false;
		if (!ObjectUtils.nullSafeEquals(this.destroyMethodName, that.destroyMethodName))
			return false;
		if (this.enforceDestroyMethod != that.enforceDestroyMethod)
			return false;

		if (this.synthetic != that.synthetic)
			return false;
		if (this.role != that.role)
			return false;

		return super.equals(other);
	}

	@Override
	public int hashCode() {
		int hashCode = ObjectUtils.nullSafeHashCode(getBeanClassName());
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.scope);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.constructorArgumentValues);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.propertyValues);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryBeanName);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryMethodName);
		hashCode = 29 * hashCode + super.hashCode();
		return hashCode;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("class [");
		sb.append(getBeanClassName()).append("]");
		sb.append("; scope=").append(this.scope);
		sb.append("; abstract=").append(this.abstractFlag);
		sb.append("; lazyInit=").append(this.lazyInit);
		sb.append("; autowireMode=").append(this.autowireMode);
		sb.append("; dependencyCheck=").append(this.dependencyCheck);
		sb.append("; autowireCandidate=").append(this.autowireCandidate);
		sb.append("; primary=").append(this.primary);
		sb.append("; factoryBeanName=").append(this.factoryBeanName);
		sb.append("; factoryMethodName=").append(this.factoryMethodName);
		sb.append("; initMethodName=").append(this.initMethodName);
		sb.append("; destroyMethodName=").append(this.destroyMethodName);
		if (this.resource != null) {
			sb.append("; defined in ").append(this.resource.getDescription());
		}
		return sb.toString();
	}

}
