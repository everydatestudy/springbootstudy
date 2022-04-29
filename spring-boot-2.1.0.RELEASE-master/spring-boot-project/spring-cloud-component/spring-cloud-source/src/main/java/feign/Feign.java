/**
 * Copyright 2012-2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import feign.Logger.NoOpLogger;
import feign.ReflectiveFeign.ParseHandlersByName;
import feign.Request.Options;
import feign.Target.HardCodedTarget;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import static feign.ExceptionPropagationPolicy.NONE;

/**
 * Feign的目的是简化针对rest的Http
 * Api的开发。在实现中，Feign是一个用于生成目标实例Feign#newInstance()的工厂，这个生成的实例便是接口的代理对象。 Feign's
 * purpose is to ease development against http apis that feign restfulness. <br>
 * In implementation, Feign is a {@link Feign#newInstance factory} for
 * generating {@link Target targeted} http apis.
 */
public abstract class Feign {

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * 工具方法，生成configKey // MethodMetadata#configKey属性的值就来自于此方法 Configuration keys
	 * are formatted as unresolved <a href=
	 * "http://docs.oracle.com/javase/6/docs/jdk/api/javadoc/doclet/com/sun/javadoc/SeeTag.html"
	 * >see tags</a>. This method exposes that format, in case you need to create
	 * the same value as {@link MethodMetadata#configKey()} for correlation
	 * purposes.
	 *
	 * <p>
	 * Here are some sample encodings:
	 *
	 * <pre>
	 * <ul>
	 *   <li>{@code Route53}: would match a class {@code route53.Route53}</li>
	 *   <li>{@code Route53#list()}: would match a method {@code route53.Route53#list()}</li>
	 *   <li>{@code Route53#listAt(Marker)}: would match a method {@code
	 * route53.Route53#listAt(Marker)}</li>
	 *   <li>{@code Route53#listByNameAndType(String, String)}: would match a method {@code
	 * route53.Route53#listAt(String, String)}</li>
	 * </ul>
	 * </pre>
	 *
	 * Note that there is no whitespace expected in a key!
	 *
	 * @param targetType {@link feign.Target#type() type} of the Feign interface.
	 * @param method     invoked method, present on {@code type} or its super.
	 * @see MethodMetadata#configKey()
	 */
	public static String configKey(Class targetType, Method method) {
		StringBuilder builder = new StringBuilder();
		builder.append(targetType.getSimpleName());
		builder.append('#').append(method.getName()).append('(');
		for (Type param : method.getGenericParameterTypes()) {
			param = Types.resolve(targetType, targetType, param);
			builder.append(Types.getRawType(param).getSimpleName()).append(',');
		}
		if (method.getParameterTypes().length > 0) {
			builder.deleteCharAt(builder.length() - 1);
		}
		return builder.append(')').toString();
	}

	/**
	 * @deprecated use {@link #configKey(Class, Method)} instead.
	 */
	@Deprecated
	public static String configKey(Method method) {
		return configKey(method.getDeclaringClass(), method);
	}

	/**
	 * 唯一的public的抽象方法，用于为目标target创建一个代理对象实例 Returns a new instance of an HTTP API,
	 * defined by annotations in the {@link Feign Contract}, for the specified
	 * {@code target}. You should cache this result.
	 */
	public abstract <T> T newInstance(Target<T> target);
	//这个Builder构建器的内容非常丰富，是对前面讲解几乎所有组件的一个总结。
	public static class Builder {
		// 请求模版的拦截器，默认的空的，木有哦，你可以自定义，在builder的时候加进来
		private final List<RequestInterceptor> requestInterceptors = new ArrayList<RequestInterceptor>();
		// 这是Feign自己的日志级别，默认不输出日志
		// 因此若你要打印请求日志，这里级别需要调高
		private Logger.Level logLevel = Logger.Level.NONE;
		// 默认使用的提取器，就是支持@RequestLine源生注解的这种
		private Contract contract = new Contract.Default();
		// 默认使用JDK的`HttpURLConnection`发送请求，并且是非SSL加密的
		private Client client = new Client.Default(null, null);
		// 请务必注意：默认情况下Feign是开启了重试的
		// 100ms重试一次，一共重试5次。最长持续1s钟
		// 在生产环境下，重试请务必慎用
		private Retryer retryer = new Retryer.Default();
		// 默认使用的日志记录器，也是不作为
		private Logger logger = new NoOpLogger();
		// 默认编码器：只支持String类型的编码
		// 请注意：编码器的生效时机哦~~~（没有标注@Param注解会交给编码器处理）
		private Encoder encoder = new Encoder.Default();
		 // 默认只能解码String类型和字节数组类型
		private Decoder decoder = new Decoder.Default();
		// 支持把@QueryMap标注在Map or Bean前面
		// Bean需要有public的get方法才算一个属性
		private QueryMapEncoder queryMapEncoder = new QueryMapEncoder.Default();
		// 把异常、错误码包装为FeignException异常向上抛出
		private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
		// 默认10s链接超时，60s读取超时
		private Options options = new Options();
		// FeignInvocationHandler是它唯一的实现
		private InvocationHandlerFactory invocationHandlerFactory = new InvocationHandlerFactory.Default();
		// 默认不会解码404
		private boolean decode404;
		private boolean closeAfterDecode = true;
		// 异常传播策略：NONE代表不包装 不处理，直接抛
		private ExceptionPropagationPolicy propagationPolicy = NONE;

		public Builder logLevel(Logger.Level logLevel) {
			this.logLevel = logLevel;
			return this;
		}

		public Builder contract(Contract contract) {
			this.contract = contract;
			return this;
		}

		public Builder client(Client client) {
			this.client = client;
			return this;
		}

		public Builder retryer(Retryer retryer) {
			this.retryer = retryer;
			return this;
		}

		public Builder logger(Logger logger) {
			this.logger = logger;
			return this;
		}

		public Builder encoder(Encoder encoder) {
			this.encoder = encoder;
			return this;
		}
		// 这两个方法调用其一，方法二是一的加强版
		public Builder decoder(Decoder decoder) {
			this.decoder = decoder;
			return this;
		}

		public Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
			this.queryMapEncoder = queryMapEncoder;
			return this;
		}

		/**
		 * Allows to map the response before passing it to the decoder.
		 */
		public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
			this.decoder = new ResponseMappingDecoder(mapper, decoder);
			return this;
		}

		/**
		 * This flag indicates that the {@link #decoder(Decoder) decoder} should process
		 * responses with 404 status, specifically returning null or empty instead of
		 * throwing {@link FeignException}.
		 *
		 * <p/>
		 * All first-party (ex gson) decoders return well-known empty values defined by
		 * {@link Util#emptyValueOf}. To customize further, wrap an existing
		 * {@link #decoder(Decoder) decoder} or make your own.
		 *
		 * <p/>
		 * This flag only works with 404, as opposed to all or arbitrary status codes.
		 * This was an explicit decision: 404 -> empty is safe, common and doesn't
		 * complicate redirection, retry or fallback policy. If your server returns a
		 * different status for not-found, correct via a custom {@link #client(Client)
		 * client}.
		 *
		 * @since 8.12
		 */
		public Builder decode404() {
			this.decode404 = true;
			return this;
		}

		public Builder errorDecoder(ErrorDecoder errorDecoder) {
			this.errorDecoder = errorDecoder;
			return this;
		}

		public Builder options(Options options) {
			this.options = options;
			return this;
		}

		/** 拦截器一个个添加，可以多次调用添加多个
		 * Adds a single request interceptor to the builder.
		 */
		public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
			this.requestInterceptors.add(requestInterceptor);
			return this;
		}

		/**请注意：如果一次性添加多个，那么此方法相当于set方法哦，并不是add
		 * Sets the full set of request interceptors for the builder, overwriting any
		 * previous interceptors.
		 */
		public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
			this.requestInterceptors.clear();
			for (RequestInterceptor requestInterceptor : requestInterceptors) {
				this.requestInterceptors.add(requestInterceptor);
			}
			return this;
		}

		/**
		 * Allows you to override how reflective dispatch works inside of Feign.
		 */
		public Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
			this.invocationHandlerFactory = invocationHandlerFactory;
			return this;
		}

		/**
		 * This flag indicates that the response should not be automatically closed upon
		 * completion of decoding the message. This should be set if you plan on
		 * processing the response into a lazy-evaluated construct, such as a
		 * {@link java.util.Iterator}.
		 *
		 * </p>
		 * Feign standard decoders do not have built in support for this flag. If you
		 * are using this flag, you MUST also use a custom Decoder, and be sure to close
		 * all resources appropriately somewhere in the Decoder (you can use
		 * {@link Util#ensureClosed} for convenience).
		 *
		 * @since 9.6
		 *
		 */
		public Builder doNotCloseAfterDecode() {
			this.closeAfterDecode = false;
			return this;
		}

		public Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
			this.propagationPolicy = propagationPolicy;
			return this;
		}

		public <T> T target(Class<T> apiType, String url) {
			return target(new HardCodedTarget<T>(apiType, url));
		}

		public <T> T target(Target<T> target) {
			return build().newInstance(target);
		}

		public Feign build() {
			SynchronousMethodHandler.Factory synchronousMethodHandlerFactory = new SynchronousMethodHandler.Factory(
					client, retryer, requestInterceptors, logger, logLevel, decode404, closeAfterDecode,
					propagationPolicy);
			ParseHandlersByName handlersByName = new ParseHandlersByName(contract, options, encoder, decoder,
					queryMapEncoder, errorDecoder, synchronousMethodHandlerFactory);
			return new ReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
		}
	}

	static class ResponseMappingDecoder implements Decoder {

		private final ResponseMapper mapper;
		private final Decoder delegate;

		ResponseMappingDecoder(ResponseMapper mapper, Decoder decoder) {
			this.mapper = mapper;
			this.delegate = decoder;
		}

		@Override
		public Object decode(Response response, Type type) throws IOException {
			return delegate.decode(mapper.map(response, type), type);
		}
	}
}
