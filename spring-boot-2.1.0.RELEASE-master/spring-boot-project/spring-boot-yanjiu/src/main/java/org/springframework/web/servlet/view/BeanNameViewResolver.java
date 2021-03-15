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

package org.springframework.web.servlet.view;

import java.util.Locale;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.lang.Nullable;
import org.springframework.web.context.support.WebApplicationObjectSupport;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/**这个视图解析器跟XmlViewResolver有点类似，也是通过把返回的逻辑视图名称去匹配定义好的视图bean对象。
 * 不同点有二，一是BeanNameViewResolver要求视图bean对象都定义在Spring的application context中，
 * 而XmlViewResolver是在指定的配置文件中寻找视图bean对象，二是BeanNameViewResolver不会进行视图缓存。
 * 看一个例子，在SpringMVC的配置文件中定义了一个BeanNameViewResolver视图解析器和一个id为test的InternalResourceview bean对象。
————————————————
版权声明：本文为CSDN博主「归田」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/qq924862077/article/details/54345494
 * A simple implementation of {@link org.springframework.web.servlet.ViewResolver}
 * that interprets a view name as a bean name in the current application context,
 * i.e. typically in the XML file of the executing {@code DispatcherServlet}.
 *
 * <p>This resolver can be handy for small applications, keeping all definitions
 * ranging from controllers to views in the same place. For larger applications,
 * {@link XmlViewResolver} will be the better choice, as it separates the XML
 * view bean definitions into a dedicated views file.
 *
 * <p>Note: Neither this {@code ViewResolver} nor {@link XmlViewResolver} supports
 * internationalization. Consider {@link ResourceBundleViewResolver} if you need
 * to apply different view resources per locale.
 *
 * <p>Note: This {@code ViewResolver} implements the {@link Ordered} interface
 * in order to allow for flexible participation in {@code ViewResolver} chaining.
 * For example, some special views could be defined via this {@code ViewResolver}
 * (giving it 0 as "order" value), while all remaining views could be resolved by
 * a {@link UrlBasedViewResolver}.
 *
 * @author Juergen Hoeller
 * @since 18.06.2003
 * @see XmlViewResolver
 * @see ResourceBundleViewResolver
 * @see UrlBasedViewResolver
 */
public class BeanNameViewResolver extends WebApplicationObjectSupport implements ViewResolver, Ordered {

	private int order = Ordered.LOWEST_PRECEDENCE;  // default: same as non-Ordered


	/**
	 * Specify the order value for this ViewResolver bean.
	 * <p>The default value is {@code Ordered.LOWEST_PRECEDENCE}, meaning non-ordered.
	 * @see org.springframework.core.Ordered#getOrder()
	 */
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}


	@Override
	@Nullable
	public View resolveViewName(String viewName, Locale locale) throws BeansException {
		// 可见它和容器强关联，若容器里没有这个Bean，他就直接返回null了~~~
		ApplicationContext context = obtainApplicationContext();
		if (!context.containsBean(viewName)) {
			if (logger.isDebugEnabled()) {
				logger.debug("No matching bean found for view name '" + viewName + "'");
			}
			// Allow for ViewResolver chaining...
			return null;
		}
		if (!context.isTypeMatch(viewName, View.class)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found matching bean for view name '" + viewName +
						"' - to be ignored since it does not implement View");
			}
			// Since we're looking into the general ApplicationContext here,
			// let's accept this as a non-match and allow for chaining as well...
			return null;
		}
		// 拿出这个View就这直接返回了~~~
		return context.getBean(viewName, View.class);
	}

}
