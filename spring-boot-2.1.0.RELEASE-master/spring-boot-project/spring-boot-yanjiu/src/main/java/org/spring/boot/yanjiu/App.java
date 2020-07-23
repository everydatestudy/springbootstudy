package org.spring.boot.yanjiu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * https://blog.csdn.net/qq_26000415/category_9271293.html
 * 这个springboot的源码是1.5版本的 spring boot的源码分析
 * 
 * Hello world! 然后研究，spring的cloud的内容，nacos数据处理https://me.csdn.net/u010634066
 */
@SpringBootApplication
public class App {
	public static void main(String[] args) {
		
		SpringApplication.run(App.class, args);
	}
}
//如果应该跳过加载的话,就直接return. 判断逻辑如下:
//
//public boolean shouldSkip(AnnotatedTypeMetadata metadata, ConfigurationPhase phase) {
//// 如果这个类没有被@Conditional注解所修饰，不会skip
//if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
//    return false;
//}
//
//// 如果参数中沒有设置条件注解的生效阶段
//if (phase == null) {
//    // 是配置类的话直接使用PARSE_CONFIGURATION阶段
//    if (metadata instanceof AnnotationMetadata &&
//            ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
//        return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
//    }
//    // 否则使用REGISTER_BEAN阶段
//    return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
//}
//
//List<Condition> conditions = new ArrayList<Condition>(); // 要解析的配置类的条件集合
//// 获取配置类的条件注解得到条件数据，并添加到集合中
//for (String[] conditionClasses : getConditionClasses(metadata)) {
//    for (String conditionClass : conditionClasses) {
//        Condition condition = getCondition(conditionClass, this.context.getClassLoader());
//        conditions.add(condition);
//    }
//}
//
//AnnotationAwareOrderComparator.sort(conditions);
//
//for (Condition condition : conditions) {
//    ConfigurationPhase requiredPhase = null;
//    if (condition instanceof ConfigurationCondition) {
//        requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
//    }
//    if (requiredPhase == null || requiredPhase == phase) {
//        // 阶段不满足条件的话，返回true并跳过这个bean的解析
//        if (!condition.matches(this.context, metadata)) {
//            return true;
//        }
//    }
//}
//
//return false;
//}

//    
//
//4件事:
//
//    如果这个类没有被@Conditional注解所修饰，不会skip
//
//    如果参数中沒有设置条件注解的生效阶段
//        是配置类的话直接使用PARSE_CONFIGURATION阶段
//        否则使用REGISTER_BEAN阶段
//
//    递归调用shouldSkip进行判断.对于当前阶段 ConfigurationPhase为ConfigurationPhase.PARSE_CONFIGURATION.
//    获取配置类的条件注解得到条件数据，并添加到集合中.排序
//    遍历conditions,进行判断,阶段不满足条件的话,返回true并跳过这个bean的解析 
//
//调用AnnotationScopeMetadataResolver#resolveScopeMetadata解析ScopeMetadata. 代码如下:
//
//public ScopeMetadata resolveScopeMetadata(BeanDefinition definition) {
//ScopeMetadata metadata = new ScopeMetadata();
//if (definition instanceof AnnotatedBeanDefinition) {
//    AnnotatedBeanDefinition annDef = (AnnotatedBeanDefinition) definition;
//    AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(
//            annDef.getMetadata(), this.scopeAnnotationType);
//    if (attributes != null) {
//        metadata.setScopeName(attributes.getString("value"));
//        ScopedProxyMode proxyMode = attributes.getEnum("proxyMode");
//        if (proxyMode == null || proxyMode == ScopedProxyMode.DEFAULT) {
//            proxyMode = this.defaultProxyMode;
//        }
//        metadata.setScopedProxyMode(proxyMode);
//    }
//}
//return metadata;
//}
// 
//
//2件事:
//
//    实例化ScopeMetadata.其默认scopeName为singleton,scopedProxyMode为NO.
//    如果BeanDefinition是AnnotatedBeanDefinition实例的话,调用AnnotationConfigUtils#attributesFor获得AnnotationAttributes.如果AnnotationAttributes不会null的话,就为metadata设置ScopeName,设置ScopedProxyMode.此处返回的null.
//    如果不是的话,就返回ScopeMetadata.
//
//因此设置AnnotatedGenericBeanDefinition的scope为singleton
//————————————————
//版权声明：本文为CSDN博主「一个努力的码农」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/qq_26000415/article/details/78915211