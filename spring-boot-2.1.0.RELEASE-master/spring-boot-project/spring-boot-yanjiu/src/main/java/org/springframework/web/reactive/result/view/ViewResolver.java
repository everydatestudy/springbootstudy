package org.springframework.web.reactive.result.view;

import java.util.Locale;

import reactor.core.publisher.Mono;

/**这个接口非常简单，就一个方法:把一个逻辑视图viewName解析为一个真正的视图View，Local表示国际化相关内容~

 * Contract to resolve a view name to a {@link View} instance. The view name may
 * correspond to an HTML template or be generated dynamically.
 *
 * <p>The process of view resolution is driven through a ViewResolver-based
 * {@code HandlerResultHandler} implementation called
 * {@link ViewResolutionResultHandler
 * ViewResolutionResultHandler}.
 *
 * @author Rossen Stoyanchev
 * @since 5.0
 * @see ViewResolutionResultHandler
 */
public interface ViewResolver {

	/**
	 * Resolve the view name to a View instance.
	 * @param viewName the name of the view to resolve
	 * @param locale the locale for the request
	 * @return the resolved view or an empty stream
	 */
	Mono<View> resolveViewName(String viewName, Locale locale);

}
