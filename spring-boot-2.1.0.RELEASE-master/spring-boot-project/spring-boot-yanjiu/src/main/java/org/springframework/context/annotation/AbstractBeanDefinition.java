package org.springframework.context.annotation;

import java.util.LinkedHashMap;
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
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MethodOverrides;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor
		implements BeanDefinition, Cloneable {
	/**
	 * Constant for the default scope name: {@code ""}, equivalent to singleton
	 * status unless overridden from a parent bean definition (if applicable).
	 */
	public static final String SCOPE_DEFAULT = "";
	/**
	 * Constant that indicates no autowiring at all.
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_NO = AutowireCapableBeanFactory.AUTOWIRE_NO;

	/**
	 * Constant that indicates autowiring bean properties by name.
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_BY_NAME = AutowireCapableBeanFactory.AUTOWIRE_BY_NAME;

	/**
	 * Constant that indicates autowiring bean properties by type.
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_BY_TYPE = AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE;

	/**
	 * Constant that indicates autowiring a constructor.
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_CONSTRUCTOR = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;


	@Nullable
	private volatile Object beanClass;
	/**
	 * bean的作用范围，对应bean属性scope
	 */
	@Nullable
	private String scope = SCOPE_DEFAULT;
	/**
	 * 是否是抽象，对应bean属性abstract
	 */
	private boolean abstractFlag = false;
	/**
	 * 是否延迟加载，对应bean属性lazy-init
	 */
	private boolean lazyInit = false;
	/**
	 * 自动注入模式，对应bean属性autowire
	 */
	private int autowireMode = AUTOWIRE_NO;
	/**
	 * 依赖检查，Spring 3.0后弃用这个属性
	 */
	private int dependencyCheck = 0;
	/**
	 * 用来表示一个bean的实例化依靠另一个bean先实例化，对应bean属性depend-on
	 */
	@Nullable
	private String[] dependsOn;
	/**
	 * autowire-candidate属性设置为false，这样容器在查找自动装配对象时，
	 * 将不考虑该bean，即它不会被考虑作为其他bean自动装配的候选者， 但是该bean本身还是可以使用自动装配来注入其他bean的
	 */
	private boolean autowireCandidate = true;
	/**
	 * 自动装配时出现多个bean候选者时，将作为首选者，对应bean属性primary
	 */
	private boolean primary = false;
	/**
	 * 用于记录Qualifier，对应子元素qualifier
	 */
	private final Map<String, AutowireCandidateQualifier> qualifiers = new LinkedHashMap<>(0);

	@Nullable
	private Supplier<?> instanceSupplier;
	/**
	 * 允许访问非公开的构造器和方法，程序设置
	 */
	private boolean nonPublicAccessAllowed = true;
	/**
	 * 是否以一种宽松的模式解析构造函数，默认为true， 如果为false，则在以下情况 interface ITest{} class ITestImpl
	 * implements ITest{}; class Main { Main(ITest i) {} Main(ITestImpl i) {} }
	 * 抛出异常，因为Spring无法准确定位哪个构造函数程序设置
	 */
	private boolean lenientConstructorResolution = true;
	/**
	 * 对应bean属性factory-bean，用法： <bean id = "instanceFactoryBean" class =
	 * "example.chapter3.InstanceFactoryBean" />
	 * <bean id = "currentTime" factory-bean = "instanceFactoryBean" factory-method
	 * = "createTime" />
	 */
	@Nullable
	private String factoryBeanName;
	/**
	 * 对应bean属性factory-method
	 */
	@Nullable
	private String factoryMethodName;
	/**
	 * 记录构造函数注入属性，对应bean属性constructor-arg
	 */
	@Nullable
	private ConstructorArgumentValues constructorArgumentValues;
	/**
	 * 普通属性集合
	 */
	@Nullable
	private MutablePropertyValues propertyValues;
	/**
	 * 方法重写的持有者，记录lookup-method、replaced-method元素
	 */
	@Nullable
	private MethodOverrides methodOverrides;
	/**
	 * 初始化方法，对应bean属性init-method
	 */
	@Nullable
	private String initMethodName;
	/**
	 * 销毁方法，对应bean属性destroy-method
	 */
	@Nullable
	private String destroyMethodName;
	/**
	 * 是否执行init-method，程序设置
	 */
	private boolean enforceInitMethod = true;
	/**
	 * 是否执行destroy-method，程序设置
	 */
	private boolean enforceDestroyMethod = true;
	/**
	 * 是否是用户定义的而不是应用程序本身定义的，创建AOP时候为true，程序设置
	 */
	private boolean synthetic = false;
	/**
	 * 定义这个bean的应用，APPLICATION：用户，INFRASTRUCTURE：完全内部使用，与用户无关， SUPPORT：某些复杂配置的一部分
	 * 程序设置
	 */
	private int role = BeanDefinition.ROLE_APPLICATION;
	/**
	 * bean的描述信息
	 */
	@Nullable
	private String description;
	/**
	 * 这个bean定义的资源
	 */
	@Nullable
	private Resource resource;
}