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

package org.springframework.web.servlet.mvc.method.annotation;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.PathExtensionContentNegotiationStrategy;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 *
 *   命名为Processor说明它既能处理入参，也能处理返回值，当然本文的关注点是方法入参（和HttpMessageConverter相关）。
	请求body体一般是一段字符串/字节流，查询参数可以看做URL的一部分，这两个是位于请求报文的不同地方。
	表单参数可以按照一定格式放在请求体中，也可以放在url上作为查询参数。
	响应body体则是response返回的具体内容，对于一个普通的html页面，body里面就是页面的源代码。对于HttpMessage响应体里可能就是个json串（但无强制要求）。
	响应体一般都会结合Content-Type一起使用，告诉客户端只有知道这个头了才知道如何渲染。
    AbstractMessageConverterMethodProcessor源码稍显复杂，它和Http协议、内容协商有很大的关联：

 * Extends {@link AbstractMessageConverterMethodArgumentResolver} with the ability to handle
 * method return values by writing to the response with {@link HttpMessageConverter}s.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @since 3.1
 * 
 * 从上分析可以看出，这里面也提供了ResponseBodyAdvice钩子，我们可以通过实现此接口，来对接口的返回值进行干预、修改。相关注解为：@ControllerAdvice、@RestControllerAdvice
比如我下面这个可以让所有的@ResponseBody的处理器都返回固定值"hello,world"：

 * 你会发现其它的返回值处理器都是不会调用消息转换器的，而只有AbstractMessageConverterMethodProcessor它的两个子类才会这么做。
 * 而刚巧，这种方式（@ResponseBody方式）是我们当下最为流行的处理方式，因此非常有必要进行深入的了解~~~
 */
public abstract class AbstractMessageConverterMethodProcessor extends AbstractMessageConverterMethodArgumentResolver
		implements HandlerMethodReturnValueHandler {

	/* Extensions associated with the built-in message converters */
	// 默认情况下：文件们后缀是这些就不弹窗下载
	private static final Set<String> WHITELISTED_EXTENSIONS = new HashSet<>(Arrays.asList(
			"txt", "text", "yml", "properties", "csv",
			"json", "xml", "atom", "rss",
			"png", "jpe", "jpeg", "jpg", "gif", "wbmp", "bmp"));

	private static final Set<String> WHITELISTED_MEDIA_BASE_TYPES = new HashSet<>(
			Arrays.asList("audio", "image", "video"));

	private static final MediaType MEDIA_TYPE_APPLICATION = new MediaType("application");

	private static final Type RESOURCE_REGION_LIST_TYPE =
			new ParameterizedTypeReference<List<ResourceRegion>>() { }.getType();

	// 用于给URL解码 decodingUrlPathHelper.decodeRequestString(servletRequest, filename);
	private static final UrlPathHelper decodingUrlPathHelper = new UrlPathHelper();

	private static final UrlPathHelper rawUrlPathHelper = new UrlPathHelper();

	static {
		rawUrlPathHelper.setRemoveSemicolonContent(false);
		rawUrlPathHelper.setUrlDecode(false);
	}

	// 内容协商管理器
	private final ContentNegotiationManager contentNegotiationManager;
	// 扩展名的内容协商策略
	private final PathExtensionContentNegotiationStrategy pathStrategy;

	private final Set<String> safeExtensions = new HashSet<>();


	/**
	 * Constructor with list of converters only.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters) {
		this(converters, null, null);
	}

	/** 可以指定内容协商管理器ContentNegotiationManager 
	 * Constructor with list of converters and ContentNegotiationManager.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager contentNegotiationManager) {

		this(converters, contentNegotiationManager, null);
	}

	/** 这个构造器才是重点
	 * Constructor with list of converters and ContentNegotiationManager as well
	 * as request/response body advice instances.
	 */
	protected AbstractMessageConverterMethodProcessor(List<HttpMessageConverter<?>> converters,
			@Nullable ContentNegotiationManager manager, @Nullable List<Object> requestResponseBodyAdvice) {

		super(converters, requestResponseBodyAdvice);
		// 可以看到：默认情况下会直接new一个
		this.contentNegotiationManager = (manager != null ? manager : new ContentNegotiationManager());
		// 若管理器里有就用管理器里的，否则new PathExtensionContentNegotiationStrategy()
		this.pathStrategy = initPathStrategy(this.contentNegotiationManager);
		// 用safeExtensions装上内容协商所支持的所有后缀
		// 并且把后缀白名单也加上去（表示是默认支持的后缀）
		this.safeExtensions.addAll(this.contentNegotiationManager.getAllFileExtensions());
		this.safeExtensions.addAll(WHITELISTED_EXTENSIONS);
	}

	private static PathExtensionContentNegotiationStrategy initPathStrategy(ContentNegotiationManager manager) {
		Class<PathExtensionContentNegotiationStrategy> clazz = PathExtensionContentNegotiationStrategy.class;
		PathExtensionContentNegotiationStrategy strategy = manager.getStrategy(clazz);
		return (strategy != null ? strategy : new PathExtensionContentNegotiationStrategy());
	}


	/** ServletServerHttpResponse是对HttpServletResponse的包装，主要是对响应头进行处理
	// 主要是处理：setContentType、setCharacterEncoding等等
	// 所以子类若要写数据，就调用此方法来向输出流里写吧~~~
	 * Creates a new {@link HttpOutputMessage} from the given {@link NativeWebRequest}.
	 * @param webRequest the web request to create an output message from
	 * @return the output message
	 */
	protected ServletServerHttpResponse createOutputMessage(NativeWebRequest webRequest) {
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		Assert.state(response != null, "No HttpServletResponse");
		return new ServletServerHttpResponse(response);
	}
	// 注意：createInputMessage()方法是父类提供的，对HttpServletRequest的包装
		// 主要处理了：getURI()、getHeaders()等方法
		// getHeaders()方法主要是处理了：getContentType()...
	/**
	 * Writes the given return value to the given web request. Delegates to
	 * {@link #writeWithMessageConverters(Object, MethodParameter, ServletServerHttpRequest, ServletServerHttpResponse)}
	 */
	protected <T> void writeWithMessageConverters(T value, MethodParameter returnType, NativeWebRequest webRequest)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);
		writeWithMessageConverters(value, returnType, inputMessage, outputMessage);
	}

	/**
	 * Writes the given return type to the given output message.
	 * @param value the value to write to the output message
	 * @param returnType the type of the value
	 * @param inputMessage the input messages. Used to inspect the {@code Accept} header.
	 * @param outputMessage the output message to write to
	 * @throws IOException thrown in case of I/O errors
	 * @throws HttpMediaTypeNotAcceptableException thrown when the conditions indicated
	 * by the {@code Accept} header on the request cannot be met by the message converters
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	protected <T> void writeWithMessageConverters(@Nullable T value, MethodParameter returnType,
			ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		Object outputValue;
		Class<?> valueType;
		Type declaredType;
		// 注意此处的特殊处理，相当于把所有的CharSequence类型的，都最终当作String类型处理的~
		if (value instanceof CharSequence) {
			outputValue = value.toString();
			valueType = String.class;
			declaredType = String.class;
		}
		// 我们本例；body为返回值对象  Person@5229
				// valueType为：class com.fsx.bean.Person
				// targetType：class com.fsx.bean.Person
		else {
			outputValue = value;
			valueType = getReturnValueType(outputValue, returnType);
			// 此处相当于兼容了泛型类型的处理
			declaredType = getGenericType(returnType);
		}
		// 若返回值是个org.springframework.core.io.Resource  就走这里  此处忽略~~
		if (isResourceType(value, returnType)) {
			outputMessage.getHeaders().set(HttpHeaders.ACCEPT_RANGES, "bytes");
			if (value != null && inputMessage.getHeaders().getFirst(HttpHeaders.RANGE) != null) {
				Resource resource = (Resource) value;
				try {
					List<HttpRange> httpRanges = inputMessage.getHeaders().getRange();
					outputMessage.getServletResponse().setStatus(HttpStatus.PARTIAL_CONTENT.value());
					outputValue = HttpRange.toResourceRegions(httpRanges, resource);
					valueType = outputValue.getClass();
					declaredType = RESOURCE_REGION_LIST_TYPE;
				}
				catch (IllegalArgumentException ex) {
					outputMessage.getHeaders().set(HttpHeaders.CONTENT_RANGE, "bytes */" + resource.contentLength());
					outputMessage.getServletResponse().setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
				}
			}
		}

		// selectedMediaType表示最终被选中的MediaType，毕竟请求放可能是接受N多种MediaType的~~~
		List<MediaType> mediaTypesToUse;
		// 一般情况下 请求方很少会指定contentType的~~~
				// 如果请求方法指定了，就以它的为准，就相当于selectedMediaType 里面就被找打了
				// 否则就靠系统自己去寻找到一个最为合适的~~~
		MediaType contentType = outputMessage.getHeaders().getContentType();
		if (contentType != null && contentType.isConcrete()) {
			mediaTypesToUse = Collections.singletonList(contentType);
		}
		else {
			HttpServletRequest request = inputMessage.getServletRequest();
			// 这里交给contentNegotiationManager.resolveMediaTypes()  找出客户端可以接受的MediaType们~~~
			// 此处是已经排序好的（根据Q值等等）
			List<MediaType> requestedMediaTypes = getAcceptableMediaTypes(request);
			// 这是服务端它所能提供出的MediaType们
			// 这个方法就是从所有已经注册的转换器里面去找，看看哪些转换器.canWrite，然后把他们所支持的MediaType都加入进来~~~
			// 比如此例只能匹配到MappingJackson2HttpMessageConverter，所以匹配上的有application/json、application/*+json两个

			List<MediaType> producibleMediaTypes = getProducibleMediaTypes(request, valueType, declaredType);
			// 这个异常应该我们经常碰到：有body体，但是并没有能够支持的转换器，就是这额原因~~~
			if (outputValue != null && producibleMediaTypes.isEmpty()) {
				throw new HttpMessageNotWritableException(
						"No converter found for return value of type: " + valueType);
			}

			// 下面相当于从浏览器可议接受的MediaType里面，最终抉择出N个来
			// 原理也非常简单：你能接受的isCompatibleWith上了我能处理的，那咱们就好说，处理就完了
			mediaTypesToUse = new ArrayList<>();
			for (MediaType requestedType : requestedMediaTypes) {
				for (MediaType producibleType : producibleMediaTypes) {
					if (requestedType.isCompatibleWith(producibleType)) {
						// 从两个中选择一个最匹配的  主要是根据q值来比较  排序
						// 比如此例，最终匹配上的有两个：application/json;q=0.8和application/*+json;q=0.8
						mediaTypesToUse.add(getMostSpecificMediaType(requestedType, producibleType));
					}
				}
			}
			// 这个异常也不少见，比如此处如果没有导入Jackson相关依赖包
			// 就会抛出这个异常了：HttpMediaTypeNotAcceptableException：Could not find acceptable representation
			if (mediaTypesToUse.isEmpty()) {
				if (outputValue != null) {
					throw new HttpMediaTypeNotAcceptableException(producibleMediaTypes);
				}
				return;
			}
			MediaType.sortBySpecificityAndQuality(mediaTypesToUse);
		}

		MediaType selectedMediaType = null;
		//匹配消息数据转换器
		for (MediaType mediaType : mediaTypesToUse) {
			if (mediaType.isConcrete()) {
				selectedMediaType = mediaType;
				break;
			}
			else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION)) {
				selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
				break;
			}
		}
		// 最终的最终 都会找到一个决定write的类型，必粗此处为：application/json;q=0.8
				//  因为最终会决策出来一个MediaType，所以此处就是要根据此MediaType找到一个合适的消息转换器，把body向outputstream写进去~~~
				// 注意此处：是RequestResponseBodyAdviceChain执行之处~~~~

		if (selectedMediaType != null) {
			selectedMediaType = selectedMediaType.removeQualityValue();
			//这里又进行了一次判断是否存在匹配的内容数据
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				// 从这个判断可以看出 ，处理body里面内容，GenericHttpMessageConverter类型的转换器是优先级更高，优先去处理的
				GenericHttpMessageConverter genericConverter = (converter instanceof GenericHttpMessageConverter ?
						(GenericHttpMessageConverter<?>) converter : null);
				if (genericConverter != null ?
						((GenericHttpMessageConverter) converter).canWrite(declaredType, valueType, selectedMediaType) :
						converter.canWrite(valueType, selectedMediaType)) {
					// 在写body之前执行~~~~  会调用我们注册的所有的合适的ResponseBodyAdvice#beforeBodyWrite方法
					// 相当于在写之前，我们可以介入对body体进行处理
					outputValue = getAdvice().beforeBodyWrite(outputValue, returnType, selectedMediaType,
							(Class<? extends HttpMessageConverter<?>>) converter.getClass(),
							inputMessage, outputMessage);
					if (outputValue != null) {
						// 给响应Response设置一个Content-Disposition的请求头（若需要的话）  若之前已经设置过了，此处将什么都不做
						// 比如我们常见的：response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(fileName, "UTF-8"));
						//Content-disposition 是 MIME 协议的扩展，MIME 协议指示 MIME 用户代理如何显示附加的文件。
						// 当 Internet Explorer 接收到头时，它会激活文件下载对话框，它的文件名框自动填充了头中指定的文件名
						addContentDispositionHeader(inputMessage, outputMessage);
						if (genericConverter != null) {
							genericConverter.write(outputValue, declaredType, selectedMediaType, outputMessage);
						}
						else {
							//将数据转换为配置的数据格式
							((HttpMessageConverter) converter).write(outputValue, selectedMediaType, outputMessage);
						}
						if (logger.isDebugEnabled()) {
							logger.debug("Written [" + outputValue + "] as \"" + selectedMediaType +
									"\" using [" + converter + "]");
						}
					}
					return;
				}
			}
		}

		if (outputValue != null) {
			throw new HttpMediaTypeNotAcceptableException(this.allSupportedMediaTypes);
		}
	}

	/**
	 * Return the type of the value to be written to the response. Typically this is
	 * a simple check via getClass on the value but if the value is null, then the
	 * return type needs to be examined possibly including generic type determination
	 * (e.g. {@code ResponseEntity<T>}).
	 */
	protected Class<?> getReturnValueType(@Nullable Object value, MethodParameter returnType) {
		return (value != null ? value.getClass() : returnType.getParameterType());
	}

	/**
	 * Return whether the returned value or the declared return type extends {@link Resource}.
	 */
	protected boolean isResourceType(@Nullable Object value, MethodParameter returnType) {
		Class<?> clazz = getReturnValueType(value, returnType);
		return clazz != InputStreamResource.class && Resource.class.isAssignableFrom(clazz);
	}

	/**
	 * Return the generic type of the {@code returnType} (or of the nested type
	 * if it is an {@link HttpEntity}).
	 */
	private Type getGenericType(MethodParameter returnType) {
		if (HttpEntity.class.isAssignableFrom(returnType.getParameterType())) {
			return ResolvableType.forType(returnType.getGenericParameterType()).getGeneric().getType();
		}
		else {
			return returnType.getGenericParameterType();
		}
	}

	/**
	 * Returns the media types that can be produced.
	 * @see #getProducibleMediaTypes(HttpServletRequest, Class, Type)
	 */
	@SuppressWarnings("unused")
	protected List<MediaType> getProducibleMediaTypes(HttpServletRequest request, Class<?> valueClass) {
		return getProducibleMediaTypes(request, valueClass, null);
	}

	/**
	 * Returns the media types that can be produced. The resulting media types are:
	 * <ul>
	 * <li>The producible media types specified in the request mappings, or
	 * <li>Media types of configured converters that can write the specific return value, or
	 * <li>{@link MediaType#ALL}
	 * </ul>
	 * @since 4.2
	 */
	@SuppressWarnings("unchecked")
	protected List<MediaType> getProducibleMediaTypes(
			HttpServletRequest request, Class<?> valueClass, @Nullable Type declaredType) {
		// 它设值的地方唯一在于：@RequestMapping.producers属性
		// 大多数情况下：我们一般都不会给此属性赋值吧~~~
		Set<MediaType> mediaTypes =	(Set<MediaType>) request.getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
		if (!CollectionUtils.isEmpty(mediaTypes)) {
			return new ArrayList<>(mediaTypes);
		}
		// 大多数情况下：都会走进这个逻辑 --> 从消息转换器中匹配一个合适的出来
		else if (!this.allSupportedMediaTypes.isEmpty()) {
			List<MediaType> result = new ArrayList<>();
			// 从所有的消息转换器中  匹配出一个/多个List<MediaType> result出来
			// 这就代表着：我服务端所能支持的所有的List<MediaType>们了
			for (HttpMessageConverter<?> converter : this.messageConverters) {
				if (converter instanceof GenericHttpMessageConverter && declaredType != null) {
					if (((GenericHttpMessageConverter<?>) converter).canWrite(declaredType, valueClass, null)) {
						result.addAll(converter.getSupportedMediaTypes());
					}
				}
				else if (converter.canWrite(valueClass, null)) {
					result.addAll(converter.getSupportedMediaTypes());
				}
			}
			return result;
		}
		else {
			return Collections.singletonList(MediaType.ALL);
		}
	}

	private List<MediaType> getAcceptableMediaTypes(HttpServletRequest request)
			throws HttpMediaTypeNotAcceptableException {

		return this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
	}

	/**
	 * Return the more specific of the acceptable and the producible media types
	 * with the q-value of the former.
	 */
	private MediaType getMostSpecificMediaType(MediaType acceptType, MediaType produceType) {
		MediaType produceTypeToUse = produceType.copyQualityValue(acceptType);
		return (MediaType.SPECIFICITY_COMPARATOR.compare(acceptType, produceTypeToUse) <= 0 ? acceptType : produceTypeToUse);
	}

	/**
	 * Check if the path has a file extension and whether the extension is
	 * either {@link #WHITELISTED_EXTENSIONS whitelisted} or explicitly
	 * {@link ContentNegotiationManager#getAllFileExtensions() registered}.
	 * If not, and the status is in the 2xx range, a 'Content-Disposition'
	 * header with a safe attachment file name ("f.txt") is added to prevent
	 * RFD exploits.
	 */
	private void addContentDispositionHeader(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		HttpHeaders headers = response.getHeaders();
		if (headers.containsKey(HttpHeaders.CONTENT_DISPOSITION)) {
			return;
		}

		try {
			int status = response.getServletResponse().getStatus();
			if (status < 200 || status > 299) {
				return;
			}
		}
		catch (Throwable ex) {
			// ignore
		}

		HttpServletRequest servletRequest = request.getServletRequest();
		String requestUri = rawUrlPathHelper.getOriginatingRequestUri(servletRequest);

		int index = requestUri.lastIndexOf('/') + 1;
		String filename = requestUri.substring(index);
		String pathParams = "";

		index = filename.indexOf(';');
		if (index != -1) {
			pathParams = filename.substring(index);
			filename = filename.substring(0, index);
		}

		filename = decodingUrlPathHelper.decodeRequestString(servletRequest, filename);
		String ext = StringUtils.getFilenameExtension(filename);

		pathParams = decodingUrlPathHelper.decodeRequestString(servletRequest, pathParams);
		String extInPathParams = StringUtils.getFilenameExtension(pathParams);

		if (!safeExtension(servletRequest, ext) || !safeExtension(servletRequest, extInPathParams)) {
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt");
		}
	}

	@SuppressWarnings("unchecked")
	private boolean safeExtension(HttpServletRequest request, @Nullable String extension) {
		if (!StringUtils.hasText(extension)) {
			return true;
		}
		extension = extension.toLowerCase(Locale.ENGLISH);
		if (this.safeExtensions.contains(extension)) {
			return true;
		}
		String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern != null && pattern.endsWith("." + extension)) {
			return true;
		}
		if (extension.equals("html")) {
			String name = HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE;
			Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(name);
			if (!CollectionUtils.isEmpty(mediaTypes) && mediaTypes.contains(MediaType.TEXT_HTML)) {
				return true;
			}
		}
		return safeMediaTypesForExtension(new ServletWebRequest(request), extension);
	}

	private boolean safeMediaTypesForExtension(NativeWebRequest request, String extension) {
		List<MediaType> mediaTypes = null;
		try {
			mediaTypes = this.pathStrategy.resolveMediaTypeKey(request, extension);
		}
		catch (HttpMediaTypeNotAcceptableException ex) {
			// Ignore
		}
		if (CollectionUtils.isEmpty(mediaTypes)) {
			return false;
		}
		for (MediaType mediaType : mediaTypes) {
			if (!safeMediaType(mediaType)) {
				return false;
			}
		}
		return true;
	}

	private boolean safeMediaType(MediaType mediaType) {
		return (WHITELISTED_MEDIA_BASE_TYPES.contains(mediaType.getType()) ||
				mediaType.getSubtype().endsWith("+xml"));
	}

}
