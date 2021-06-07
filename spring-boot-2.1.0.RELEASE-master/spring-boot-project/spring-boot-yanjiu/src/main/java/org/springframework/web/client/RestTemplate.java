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

package org.springframework.web.client;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.InterceptingHttpAccessor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.cbor.MappingJackson2CborHttpMessageConverter;
import org.springframework.http.converter.feed.RssChannelHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.http.converter.json.JsonbHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.smile.MappingJackson2SmileHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriTemplateHandler;

/**RestTemplate简化了与http服务的通信，程序代码可以给它提供URL，并提取结果。它默认使用的JDK 的HttpURLConnection进行通信，
 * 然而我们是可以通过RestTemplate.setRequestFactory切换到不同的HTTP源：如Apache HttpComponents、Netty、OkHttp等等。
————————————————
版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/f641385712/article/details/100753981
 * Synchronous client to perform HTTP requests, exposing a simple, template
 * method API over underlying HTTP client libraries such as the JDK
 * {@code HttpURLConnection}, Apache HttpComponents, and others.
 *
 * <p>The RestTemplate offers templates for common scenarios by HTTP method, in
 * addition to the generalized {@code exchange} and {@code execute} methods that
 * support of less frequent cases.
 *
 * <p><strong>NOTE:</strong> As of 5.0, the non-blocking, reactive
 * {@code org.springframework.web.reactive.client.WebClient} offers a
 * modern alternative to the {@code RestTemplate} with efficient support for
 * both sync and async, as well as streaming scenarios. The {@code RestTemplate}
 * will be deprecated in a future version and will not have major new features
 * added going forward.
 *
 *xxxForObject：返回响应体（也就直接是body体力的内容） （T）
xxxForEntity：返回的相应行、响应头、响应码、响应体等等 （ResponseEntity）
xxxForLocation：提交成功之后，返回新资源的URI。这个只需要服务提供者返回一个 URI 即可，该 URI 表示新资源的位置，可谓非常轻量。 （URI）
————————————————
版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/f641385712/article/details/100753981
 *
 * @author Arjen Poutsma
 * @author Brian Clozel
 * @author Roy Clarkson
 * @author Juergen Hoeller
 * @since 3.0
 * @see HttpMessageConverter
 * @see RequestCallback
 * @see ResponseExtractor
 * @see ResponseErrorHandler
 */
public class RestTemplate extends InterceptingHttpAccessor implements RestOperations {
	// 去classpath探测  是否有这些消息转换器相关的jar~
	private static boolean romePresent =
			ClassUtils.isPresent("com.rometools.rome.feed.WireFeed",
					RestTemplate.class.getClassLoader());

	private static final boolean jaxb2Present =
			ClassUtils.isPresent("javax.xml.bind.Binder",
					RestTemplate.class.getClassLoader());

	private static final boolean jackson2Present =
			ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper",
					RestTemplate.class.getClassLoader()) &&
			ClassUtils.isPresent("com.fasterxml.jackson.core.JsonGenerator",
					RestTemplate.class.getClassLoader());

	private static final boolean jackson2XmlPresent =
			ClassUtils.isPresent("com.fasterxml.jackson.dataformat.xml.XmlMapper",
					RestTemplate.class.getClassLoader());

	private static final boolean jackson2SmilePresent =
			ClassUtils.isPresent("com.fasterxml.jackson.dataformat.smile.SmileFactory",
					RestTemplate.class.getClassLoader());

	private static final boolean jackson2CborPresent =
			ClassUtils.isPresent("com.fasterxml.jackson.dataformat.cbor.CBORFactory",
					RestTemplate.class.getClassLoader());

	private static final boolean gsonPresent =
			ClassUtils.isPresent("com.google.gson.Gson",
					RestTemplate.class.getClassLoader());

	private static final boolean jsonbPresent =
			ClassUtils.isPresent("javax.json.bind.Jsonb",
					RestTemplate.class.getClassLoader());

	// 消息转换器们（显然对JSON格式默认是支持得最好的）
	private final List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
	// 默认的请求异常处理器，Spring5.0后其实可以使用它ExtractingResponseErrorHandler
	// 它能够利用消息换换气提取你的错误内容。并且还支持自定义错误码、错误序列等等~
	private ResponseErrorHandler errorHandler = new DefaultResponseErrorHandler();
	// 用于URL的构建
	private UriTemplateHandler uriTemplateHandler = new DefaultUriBuilderFactory();
	// 默认的返回值提取器~~~~
	private final ResponseExtractor<HttpHeaders> headersExtractor = new HeadersExtractor();


	/** 空构造，应该是平时使用得最多的了：一切都使用默认的组件配置Resource等等
	 * Create a new instance of the {@link RestTemplate} using default settings.
	 * Default {@link HttpMessageConverter}s are initialized.
	 */
	public RestTemplate() {
		// 这个几个消息转换器是支持的。字节数组、字符串、
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(new StringHttpMessageConverter());
		this.messageConverters.add(new ResourceHttpMessageConverter(false));
		this.messageConverters.add(new SourceHttpMessageConverter<>());
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());

		

		if (jackson2XmlPresent) {
			this.messageConverters.add(new MappingJackson2XmlHttpMessageConverter());
		}
		else if (jaxb2Present) {
			this.messageConverters.add(new Jaxb2RootElementHttpMessageConverter());
		}
		// 接下里便是一些列的判断，若类路径上有才会加进来
		if (jackson2Present) {
			this.messageConverters.add(new MappingJackson2HttpMessageConverter());
		}
		else if (gsonPresent) {
			this.messageConverters.add(new GsonHttpMessageConverter());
		}
		else if (jsonbPresent) {
			this.messageConverters.add(new JsonbHttpMessageConverter());
		}

		if (jackson2SmilePresent) {
			this.messageConverters.add(new MappingJackson2SmileHttpMessageConverter());
		}
		if (jackson2CborPresent) {
			this.messageConverters.add(new MappingJackson2CborHttpMessageConverter());
		}
	}

	/* 你懂的，若想用OkHttp，也可以在构造时就指定
	 * Create a new instance of the {@link RestTemplate} based on the given {@link ClientHttpRequestFactory}.
	 * @param requestFactory the HTTP request factory to use
	 * @see org.springframework.http.client.SimpleClientHttpRequestFactory
	 * @see org.springframework.http.client.HttpComponentsClientHttpRequestFactory
	 */
	public RestTemplate(ClientHttpRequestFactory requestFactory) {
		this();
		setRequestFactory(requestFactory);
	}

	/**
	 * Create a new instance of the {@link RestTemplate} using the given list of
	 * {@link HttpMessageConverter} to use.
	 * @param messageConverters the list of {@link HttpMessageConverter} to use
	 * @since 3.2.7
	 */
	public RestTemplate(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "At least one HttpMessageConverter required");
		this.messageConverters.addAll(messageConverters);
	}


	/**
	 * Set the message body converters to use.
	 * <p>These converters are used to convert from and to HTTP requests and responses.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		Assert.notEmpty(messageConverters, "At least one HttpMessageConverter required");
		// Take getMessageConverters() List as-is when passed in here
		if (this.messageConverters != messageConverters) {
			this.messageConverters.clear();
			this.messageConverters.addAll(messageConverters);
		}
	}

	/**
	 * Return the list of message body converters.
	 * <p>The returned {@link List} is active and may get appended to.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Set the error handler.
	 * <p>By default, RestTemplate uses a {@link DefaultResponseErrorHandler}.
	 */
	public void setErrorHandler(ResponseErrorHandler errorHandler) {
		Assert.notNull(errorHandler, "ResponseErrorHandler must not be null");
		this.errorHandler = errorHandler;
	}

	/**
	 * Return the error handler.
	 */
	public ResponseErrorHandler getErrorHandler() {
		return this.errorHandler;
	}

	/**
	 * Configure default URI variable values. This is a shortcut for:
	 * <pre class="code">
	 * DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory();
	 * handler.setDefaultUriVariables(...);
	 *
	 * RestTemplate restTemplate = new RestTemplate();
	 * restTemplate.setUriTemplateHandler(handler);
	 * </pre>
	 * @param uriVars the default URI variable values
	 * @since 4.3
	 */
	@SuppressWarnings("deprecation")
	public void setDefaultUriVariables(Map<String, ?> uriVars) {
		if (this.uriTemplateHandler instanceof DefaultUriBuilderFactory) {
			((DefaultUriBuilderFactory) this.uriTemplateHandler).setDefaultUriVariables(uriVars);
		}
		else if (this.uriTemplateHandler instanceof org.springframework.web.util.AbstractUriTemplateHandler) {
			((org.springframework.web.util.AbstractUriTemplateHandler) this.uriTemplateHandler)
					.setDefaultUriVariables(uriVars);
		}
		else {
			throw new IllegalArgumentException(
					"This property is not supported with the configured UriTemplateHandler.");
		}
	}

	/**
	 * Customize how URI templates are expanded into URI instances.
	 * <p>By default {@link DefaultUriBuilderFactory} with default settings is
	 * used. You can supply a {@code DefaultUriBuilderFactory} configured
	 * differently, or an entirely different implementation, for example that
	 * plugs in a 3rd party URI template library.
	 * <p><strong>Note:</strong> in 5.0 the switch from
	 * {@link org.springframework.web.util.DefaultUriTemplateHandler
	 * DefaultUriTemplateHandler} (deprecated in 4.3), as the default to use, to
	 * {@link DefaultUriBuilderFactory} brings in a different default for the
	 * {@code parsePath} property (switching from false to true).
	 * @param handler the URI template handler to use
	 */
	public void setUriTemplateHandler(UriTemplateHandler handler) {
		Assert.notNull(handler, "UriTemplateHandler must not be null");
		this.uriTemplateHandler = handler;
	}

	/**
	 * Return the configured URI template handler.
	 */
	public UriTemplateHandler getUriTemplateHandler() {
		return this.uriTemplateHandler;
	}


	// GET

	@Override
	@Nullable
	public <T> T getForObject(String url, Class<T> responseType, Object... uriVariables) throws RestClientException {
		//1、new AcceptHeaderRequestCallback(responseType)  它能在发送请求的之前这样一件事：
				// request.getHeaders().setAccept(allSupportedMediaTypes)
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		// 最终调用的是execute方法，此时URL是个字符串
		// responseExtractor返回值提取器使用的是消息转换器去读取body哒~
		// 返回值就是返回的body本身（不含有返回的响应头等等信息~）
		return execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T getForObject(String url, Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T getForObject(URI url, Class<T> responseType) throws RestClientException {
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.GET, requestCallback, responseExtractor);
	}
	// 它返回的是ResponseEntity，不会返回null的  最终调用的依旧是execute方法
		// 此时候用的就不是消息转换器的提取器了，而是内部类`ResponseEntityResponseExtractor`（底层还是依赖消息转换器）
		// 但是这个提取器，提取出来的可都是ResponseEntity<T>实例~

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.GET, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> getForEntity(URI url, Class<T> responseType) throws RestClientException {
		RequestCallback requestCallback = acceptHeaderRequestCallback(responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.GET, requestCallback, responseExtractor));
	}


	// HEAD

	@Override
	public HttpHeaders headForHeaders(String url, Object... uriVariables) throws RestClientException {
		return nonNull(execute(url, HttpMethod.HEAD, null, headersExtractor(), uriVariables));
	}

	@Override
	public HttpHeaders headForHeaders(String url, Map<String, ?> uriVariables) throws RestClientException {
		return nonNull(execute(url, HttpMethod.HEAD, null, headersExtractor(), uriVariables));
	}

	@Override
	public HttpHeaders headForHeaders(URI url) throws RestClientException {
		return nonNull(execute(url, HttpMethod.HEAD, null, headersExtractor()));
	}


	// POST

	@Override
	@Nullable
	public URI postForLocation(String url, @Nullable Object request, Object... uriVariables)
			throws RestClientException {
		// 1、HttpEntityRequestCallback  适配：把request适配成一个HttpEntity
		// 然后执行前，通过消息转换器把头信息、body信息等等都write进去
		RequestCallback requestCallback = httpEntityCallback(request);
		// 因为需要拿到URI，所以此处使用headersExtractor提取器先拿到响应的header即可~~~
		HttpHeaders headers = execute(url, HttpMethod.POST, requestCallback, headersExtractor(), uriVariables);
		return (headers != null ? headers.getLocation() : null);
	}

	@Override
	@Nullable
	public URI postForLocation(String url, @Nullable Object request, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		HttpHeaders headers = execute(url, HttpMethod.POST, requestCallback, headersExtractor(), uriVariables);
		return (headers != null ? headers.getLocation() : null);
	}

	@Override
	@Nullable
	public URI postForLocation(URI url, @Nullable Object request) throws RestClientException {
		RequestCallback requestCallback = httpEntityCallback(request);
		HttpHeaders headers = execute(url, HttpMethod.POST, requestCallback, headersExtractor());
		return (headers != null ? headers.getLocation() : null);
	}
	// 除了httpEntityCallback()不一样，其余和get请求一样
	@Override
	@Nullable
	public <T> T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Object... uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T postForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T postForObject(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters());
		return execute(url, HttpMethod.POST, requestCallback, responseExtractor);
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request,
			Class<T> responseType, Object... uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(String url, @Nullable Object request,
			Class<T> responseType, Map<String, ?> uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.POST, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> postForEntity(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, HttpMethod.POST, requestCallback, responseExtractor));
	}


	// PUT

	@Override
	public void put(String url, @Nullable Object request, Object... uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		execute(url, HttpMethod.PUT, requestCallback, null, uriVariables);
	}

	@Override
	public void put(String url, @Nullable Object request, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request);
		execute(url, HttpMethod.PUT, requestCallback, null, uriVariables);
	}

	@Override
	public void put(URI url, @Nullable Object request) throws RestClientException {
		RequestCallback requestCallback = httpEntityCallback(request);
		execute(url, HttpMethod.PUT, requestCallback, null);
	}


	// PATCH

	@Override
	@Nullable
	public <T> T patchForObject(String url, @Nullable Object request, Class<T> responseType,
			Object... uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.PATCH, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T patchForObject(String url, @Nullable Object request, Class<T> responseType,
			Map<String, ?> uriVariables) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
		return execute(url, HttpMethod.PATCH, requestCallback, responseExtractor, uriVariables);
	}

	@Override
	@Nullable
	public <T> T patchForObject(URI url, @Nullable Object request, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(request, responseType);
		HttpMessageConverterExtractor<T> responseExtractor =
				new HttpMessageConverterExtractor<>(responseType, getMessageConverters());
		return execute(url, HttpMethod.PATCH, requestCallback, responseExtractor);
	}


	// DELETE
	// DELETE请求：也是木有返回值的。
		// 并且请注意：DELETE请求这里可都是不能接收body的，不能给请求设置请求体的
		// （虽然可能底层httpCLient支持，但这里不支持，请遵守规范）
	@Override
	public void delete(String url, Object... uriVariables) throws RestClientException {
		execute(url, HttpMethod.DELETE, null, null, uriVariables);
	}

	@Override
	public void delete(String url, Map<String, ?> uriVariables) throws RestClientException {
		execute(url, HttpMethod.DELETE, null, null, uriVariables);
	}

	@Override
	public void delete(URI url) throws RestClientException {
		execute(url, HttpMethod.DELETE, null, null);
	}


	// OPTIONS

	@Override
	public Set<HttpMethod> optionsForAllow(String url, Object... uriVariables) throws RestClientException {
		ResponseExtractor<HttpHeaders> headersExtractor = headersExtractor();
		HttpHeaders headers = execute(url, HttpMethod.OPTIONS, null, headersExtractor, uriVariables);
		return (headers != null ? headers.getAllow() : Collections.emptySet());
	}

	@Override
	public Set<HttpMethod> optionsForAllow(String url, Map<String, ?> uriVariables) throws RestClientException {
		ResponseExtractor<HttpHeaders> headersExtractor = headersExtractor();
		HttpHeaders headers = execute(url, HttpMethod.OPTIONS, null, headersExtractor, uriVariables);
		return (headers != null ? headers.getAllow() : Collections.emptySet());
	}

	@Override
	public Set<HttpMethod> optionsForAllow(URI url) throws RestClientException {
		ResponseExtractor<HttpHeaders> headersExtractor = headersExtractor();
		HttpHeaders headers = execute(url, HttpMethod.OPTIONS, null, headersExtractor);
		return (headers != null ? headers.getAllow() : Collections.emptySet());
	}


	// exchange

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
			@Nullable HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables)
			throws RestClientException {
		// 把请求体适配为HttpEntity
		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		// 消息提取器使用ResponseEntityResponseExtractor
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		// 从上两个部分就能看到：exchange方法的入参、出参都是非常通用的~~~
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method,
			@Nullable HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			Class<T> responseType) throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(execute(url, method, requestCallback, responseExtractor));
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Object... uriVariables) throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType, Map<String, ?> uriVariables) throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(execute(url, method, requestCallback, responseExtractor, uriVariables));
	}

	@Override
	public <T> ResponseEntity<T> exchange(URI url, HttpMethod method, @Nullable HttpEntity<?> requestEntity,
			ParameterizedTypeReference<T> responseType) throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(execute(url, method, requestCallback, responseExtractor));
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, Class<T> responseType)
			throws RestClientException {

		RequestCallback requestCallback = httpEntityCallback(requestEntity, responseType);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(responseType);
		return nonNull(doExecute(requestEntity.getUrl(), requestEntity.getMethod(), requestCallback, responseExtractor));
	}

	@Override
	public <T> ResponseEntity<T> exchange(RequestEntity<?> requestEntity, ParameterizedTypeReference<T> responseType)
			throws RestClientException {

		Type type = responseType.getType();
		RequestCallback requestCallback = httpEntityCallback(requestEntity, type);
		ResponseExtractor<ResponseEntity<T>> responseExtractor = responseEntityExtractor(type);
		return nonNull(doExecute(requestEntity.getUrl(), requestEntity.getMethod(), requestCallback, responseExtractor));
	}

	// 3个execute方法。最终调用的都是doExecute方法
		// 它做的一件事：使用UriTemplateHandler把URL的参数填进去~~~
		// 底层使用的是我上文介绍的`UriComponentsBuilder`，还是比较简单的
	// General execution
	@Override
	@Nullable
	public <T> T execute(String url, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor, Object... uriVariables) throws RestClientException {

		URI expanded = getUriTemplateHandler().expand(url, uriVariables);
		return doExecute(expanded, method, requestCallback, responseExtractor);
	}

	@Override
	@Nullable
	public <T> T execute(String url, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor, Map<String, ?> uriVariables)
			throws RestClientException {

		URI expanded = getUriTemplateHandler().expand(url, uriVariables);
		return doExecute(expanded, method, requestCallback, responseExtractor);
	}

	@Override
	@Nullable
	public <T> T execute(URI url, HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor) throws RestClientException {

		return doExecute(url, method, requestCallback, responseExtractor);
	}

	/** 如果有回调，那就先回调处理一下子请求
	 * Execute the given method on the provided URI.
	 * <p>The {@link ClientHttpRequest} is processed using the {@link RequestCallback};
	 * the response with the {@link ResponseExtractor}.
	 * @param url the fully-expanded URL to connect to
	 * @param method the HTTP method to execute (GET, POST, etc.)
	 * @param requestCallback object that prepares the request (can be {@code null})
	 * @param responseExtractor object that extracts the return value from the response (can be {@code null})
	 * @return an arbitrary object, as returned by the {@link ResponseExtractor}
	 */
	@Nullable
	protected <T> T doExecute(URI url, @Nullable HttpMethod method, @Nullable RequestCallback requestCallback,
			@Nullable ResponseExtractor<T> responseExtractor) throws RestClientException {

		Assert.notNull(url, "URI is required");
		Assert.notNull(method, "HttpMethod is required");
		ClientHttpResponse response = null;
		try {
			ClientHttpRequest request = createRequest(url, method);
			if (requestCallback != null) {
				requestCallback.doWithRequest(request);
			}
			// 真正意义上的发送请求。
			// 请注意：如果这里的request是`InterceptingClientHttpRequest`，那就回执行拦截器的intercept方法哦~~~
			// 至于什么时候是InterceptingClientHttpRequest呢？这个上面有讲的
			response = request.execute();
			// 处理结果（若有错误，那就抛出异常~~~）
			handleResponse(url, method, response);
			// 请求正常。那就使用返回值提取器responseExtractor提取出内容即可了~~~
			return (responseExtractor != null ? responseExtractor.extractData(response) : null);
		}
		catch (IOException ex) {
			String resource = url.toString();
			String query = url.getRawQuery();
			resource = (query != null ? resource.substring(0, resource.indexOf('?')) : resource);
			throw new ResourceAccessException("I/O error on " + method.name() +
					" request for \"" + resource + "\": " + ex.getMessage(), ex);
		}
		finally {// 关闭响应（ClientHttpResponse继承了Closeable接口）
			if (response != null) {
				response.close();
			}
		}
	}

	/**
	 * Handle the given response, performing appropriate logging and
	 * invoking the {@link ResponseErrorHandler} if necessary.
	 * <p>Can be overridden in subclasses.
	 * @param url the fully-expanded URL to connect to
	 * @param method the HTTP method to execute (GET, POST, etc.)
	 * @param response the resulting {@link ClientHttpResponse}
	 * @throws IOException if propagated from {@link ResponseErrorHandler}
	 * @since 4.1.6
	 * @see #setErrorHandler
	 */
	protected void handleResponse(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
		ResponseErrorHandler errorHandler = getErrorHandler();
		boolean hasError = errorHandler.hasError(response);
		if (logger.isDebugEnabled()) {
			try {
				logger.debug(method.name() + " request for \"" + url + "\" resulted in " +
						response.getRawStatusCode() + " (" + response.getStatusText() + ")" +
						(hasError ? "; invoking error handler" : ""));
			}
			catch (IOException ex) {
				// ignore
			}
		}
		if (hasError) {
			errorHandler.handleError(url, method, response);
		}
	}

	/**
	 * Return a {@code RequestCallback} that sets the request {@code Accept}
	 * header based on the given response type, cross-checked against the
	 * configured message converters.
	 */
	protected <T> RequestCallback acceptHeaderRequestCallback(Class<T> responseType) {
		return new AcceptHeaderRequestCallback(responseType);
	}

	/**
	 * Return a {@code RequestCallback} implementation that writes the given
	 * object to the request stream.
	 */
	protected <T> RequestCallback httpEntityCallback(@Nullable Object requestBody) {
		return new HttpEntityRequestCallback(requestBody);
	}

	/**
	 * Return a {@code RequestCallback} implementation that:
	 * <ol>
	 * <li>Sets the request {@code Accept} header based on the given response
	 * type, cross-checked against the configured message converters.
	 * <li>Writes the given object to the request stream.
	 * </ol>
	 */
	protected <T> RequestCallback httpEntityCallback(@Nullable Object requestBody, Type responseType) {
		return new HttpEntityRequestCallback(requestBody, responseType);
	}

	/**
	 * Return a {@code ResponseExtractor} that prepares a {@link ResponseEntity}.
	 */
	protected <T> ResponseExtractor<ResponseEntity<T>> responseEntityExtractor(Type responseType) {
		return new ResponseEntityResponseExtractor<>(responseType);
	}

	/**
	 * Return a response extractor for {@link HttpHeaders}.
	 */
	protected ResponseExtractor<HttpHeaders> headersExtractor() {
		return this.headersExtractor;
	}

	private static <T> T nonNull(@Nullable T result) {
		Assert.state(result != null, "No result");
		return result;
	}


	/**
	 * Request callback implementation that prepares the request's accept headers.
	 */
	private class AcceptHeaderRequestCallback implements RequestCallback {

		@Nullable
		private final Type responseType;

		public AcceptHeaderRequestCallback(@Nullable Type responseType) {
			this.responseType = responseType;
		}

		@Override
		public void doWithRequest(ClientHttpRequest request) throws IOException {
			if (this.responseType != null) {
				Class<?> responseClass = null;
				if (this.responseType instanceof Class) {
					responseClass = (Class<?>) this.responseType;
				}
				List<MediaType> allSupportedMediaTypes = new ArrayList<>();
				for (HttpMessageConverter<?> converter : getMessageConverters()) {
					if (responseClass != null) {
						if (converter.canRead(responseClass, null)) {
							allSupportedMediaTypes.addAll(getSupportedMediaTypes(converter));
						}
					}
					else if (converter instanceof GenericHttpMessageConverter) {
						GenericHttpMessageConverter<?> genericConverter = (GenericHttpMessageConverter<?>) converter;
						if (genericConverter.canRead(this.responseType, null, null)) {
							allSupportedMediaTypes.addAll(getSupportedMediaTypes(converter));
						}
					}
				}
				if (!allSupportedMediaTypes.isEmpty()) {
					MediaType.sortBySpecificity(allSupportedMediaTypes);
					if (logger.isDebugEnabled()) {
						logger.debug("Setting request Accept header to " + allSupportedMediaTypes);
					}
					request.getHeaders().setAccept(allSupportedMediaTypes);
				}
			}
		}

		private List<MediaType> getSupportedMediaTypes(HttpMessageConverter<?> messageConverter) {
			List<MediaType> supportedMediaTypes = messageConverter.getSupportedMediaTypes();
			List<MediaType> result = new ArrayList<>(supportedMediaTypes.size());
			for (MediaType supportedMediaType : supportedMediaTypes) {
				if (supportedMediaType.getCharset() != null) {
					supportedMediaType =
							new MediaType(supportedMediaType.getType(), supportedMediaType.getSubtype());
				}
				result.add(supportedMediaType);
			}
			return result;
		}
	}


	/**
	 * Request callback implementation that writes the given object to the request stream.
	 */
	private class HttpEntityRequestCallback extends AcceptHeaderRequestCallback {

		private final HttpEntity<?> requestEntity;

		public HttpEntityRequestCallback(@Nullable Object requestBody) {
			this(requestBody, null);
		}

		public HttpEntityRequestCallback(@Nullable Object requestBody, @Nullable Type responseType) {
			super(responseType);
			if (requestBody instanceof HttpEntity) {
				this.requestEntity = (HttpEntity<?>) requestBody;
			}
			else if (requestBody != null) {
				this.requestEntity = new HttpEntity<>(requestBody);
			}
			else {
				this.requestEntity = HttpEntity.EMPTY;
			}
		}

		@Override
		@SuppressWarnings("unchecked")
		public void doWithRequest(ClientHttpRequest httpRequest) throws IOException {
			super.doWithRequest(httpRequest);
			Object requestBody = this.requestEntity.getBody();
			if (requestBody == null) {
				HttpHeaders httpHeaders = httpRequest.getHeaders();
				HttpHeaders requestHeaders = this.requestEntity.getHeaders();
				if (!requestHeaders.isEmpty()) {
					requestHeaders.forEach((key, values) -> httpHeaders.put(key, new LinkedList<>(values)));
				}
				if (httpHeaders.getContentLength() < 0) {
					httpHeaders.setContentLength(0L);
				}
			}
			else {
				Class<?> requestBodyClass = requestBody.getClass();
				Type requestBodyType = (this.requestEntity instanceof RequestEntity ?
						((RequestEntity<?>)this.requestEntity).getType() : requestBodyClass);
				HttpHeaders httpHeaders = httpRequest.getHeaders();
				HttpHeaders requestHeaders = this.requestEntity.getHeaders();
				MediaType requestContentType = requestHeaders.getContentType();
				for (HttpMessageConverter<?> messageConverter : getMessageConverters()) {
					if (messageConverter instanceof GenericHttpMessageConverter) {
						GenericHttpMessageConverter<Object> genericConverter =
								(GenericHttpMessageConverter<Object>) messageConverter;
						if (genericConverter.canWrite(requestBodyType, requestBodyClass, requestContentType)) {
							if (!requestHeaders.isEmpty()) {
								requestHeaders.forEach((key, values) -> httpHeaders.put(key, new LinkedList<>(values)));
							}
							if (logger.isDebugEnabled()) {
								if (requestContentType != null) {
									logger.debug("Writing [" + requestBody + "] as \"" + requestContentType +
											"\" using [" + messageConverter + "]");
								}
								else {
									logger.debug("Writing [" + requestBody + "] using [" + messageConverter + "]");
								}

							}
							genericConverter.write(requestBody, requestBodyType, requestContentType, httpRequest);
							return;
						}
					}
					else if (messageConverter.canWrite(requestBodyClass, requestContentType)) {
						if (!requestHeaders.isEmpty()) {
							requestHeaders.forEach((key, values) -> httpHeaders.put(key, new LinkedList<>(values)));
						}
						if (logger.isDebugEnabled()) {
							if (requestContentType != null) {
								logger.debug("Writing [" + requestBody + "] as \"" + requestContentType +
										"\" using [" + messageConverter + "]");
							}
							else {
								logger.debug("Writing [" + requestBody + "] using [" + messageConverter + "]");
							}

						}
						((HttpMessageConverter<Object>) messageConverter).write(
								requestBody, requestContentType, httpRequest);
						return;
					}
				}
				String message = "Could not write request: no suitable HttpMessageConverter found for request type [" +
						requestBodyClass.getName() + "]";
				if (requestContentType != null) {
					message += " and content type [" + requestContentType + "]";
				}
				throw new RestClientException(message);
			}
		}
	}


	/**
	 * Response extractor for {@link HttpEntity}.
	 */
	private class ResponseEntityResponseExtractor<T> implements ResponseExtractor<ResponseEntity<T>> {

		@Nullable
		private final HttpMessageConverterExtractor<T> delegate;

		public ResponseEntityResponseExtractor(@Nullable Type responseType) {
			if (responseType != null && Void.class != responseType) {
				this.delegate = new HttpMessageConverterExtractor<>(responseType, getMessageConverters(), logger);
			}
			else {
				this.delegate = null;
			}
		}

		@Override
		public ResponseEntity<T> extractData(ClientHttpResponse response) throws IOException {
			if (this.delegate != null) {
				T body = this.delegate.extractData(response);
				return ResponseEntity.status(response.getRawStatusCode()).headers(response.getHeaders()).body(body);
			}
			else {
				return ResponseEntity.status(response.getRawStatusCode()).headers(response.getHeaders()).build();
			}
		}
	}


	/**
	 * Response extractor that extracts the response {@link HttpHeaders}.
	 */
	private static class HeadersExtractor implements ResponseExtractor<HttpHeaders> {

		@Override
		public HttpHeaders extractData(ClientHttpResponse response) {
			return response.getHeaders();
		}
	}

}
