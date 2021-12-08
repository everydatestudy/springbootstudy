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
import java.util.List;
import java.util.concurrent.TimeUnit;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.Options;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import static feign.ExceptionPropagationPolicy.UNWRAP;
import static feign.FeignException.errorExecuting;
import static feign.FeignException.errorReading;
import static feign.Util.checkNotNull;
import static feign.Util.ensureClosed;

final class SynchronousMethodHandler implements MethodHandler {

	private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;
	//方法元信息
	private final MethodMetadata metadata;
	//目标  也就是最终真正构建Http请求Request的实例 一般为HardCodedTarget
	private final Target<?> target;
	// 负责最终请求的发送 -> 默认传进来的是基于JDK源生的，效率很低，不建议直接使用
	private final Client client;
	// 负责重试 -->默认传进来的是Default，是有重试机制的哦，生产上使用请务必注意
	private final Retryer retryer;
	// 请求拦截器，它会在target.apply(template); 也就是模版 -> 请求的转换之前完成拦截
		// 说明：并不是发送请求之前那一刻哦，请务必注意啦
		// 它的作用只能是对请求模版做定制，而不能再对Request做定制了
		// 内置仅有一个实现：BasicAuthRequestInterceptor 用于鉴权
	private final List<RequestInterceptor> requestInterceptors;
	private final Logger logger;
	// 若你想在控制台看到feign的请求日志，改变此日志级别为info吧（因为一般只有info才会输出到日志文件）
	private final Logger.Level logLevel;
	// 构建请求模版的工厂
		// 对于请求模版，有多种构建方式，内部会用到可能多个编码器，下文详解
	private final RequestTemplate.Factory buildTemplateFromArgs;
	// 请求参数：比如链接超时时间、请求超时时间等
	private final Options options;
	// 解码器：用于对Response进行解码
	private final Decoder decoder;
	// 发生错误/异常时的解码器
	private final ErrorDecoder errorDecoder;
	// 是否解码404状态码？默认是不解码的
	private final boolean decode404;
	private final boolean closeAfterDecode;
	private final ExceptionPropagationPolicy propagationPolicy;
	// 唯一的构造器，并且还是私有的（所以肯定只能在本类内构建出它的实例喽）
		// 完成了对如上所有属性的赋值
	private SynchronousMethodHandler(Target<?> target, Client client, Retryer retryer,
			List<RequestInterceptor> requestInterceptors, Logger logger, Logger.Level logLevel, MethodMetadata metadata,
			RequestTemplate.Factory buildTemplateFromArgs, Options options, Decoder decoder, ErrorDecoder errorDecoder,
			boolean decode404, boolean closeAfterDecode, ExceptionPropagationPolicy propagationPolicy) {
		this.target = checkNotNull(target, "target");
		this.client = checkNotNull(client, "client for %s", target);
		this.retryer = checkNotNull(retryer, "retryer for %s", target);
		this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors for %s", target);
		this.logger = checkNotNull(logger, "logger for %s", target);
		this.logLevel = checkNotNull(logLevel, "logLevel for %s", target);
		this.metadata = checkNotNull(metadata, "metadata for %s", target);
		this.buildTemplateFromArgs = checkNotNull(buildTemplateFromArgs, "metadata for %s", target);
		this.options = checkNotNull(options, "options for %s", target);
		this.errorDecoder = checkNotNull(errorDecoder, "errorDecoder for %s", target);
		this.decoder = checkNotNull(decoder, "decoder for %s", target);
		this.decode404 = decode404;
		this.closeAfterDecode = closeAfterDecode;
		this.propagationPolicy = propagationPolicy;
	}
//	该MethodHandler实现相对复杂，用一句话描述便是：准备好所有参数后，发送Http请求，并且解析结果。它的步骤我尝试总结如下：
//
//	通过方法参数，使用工厂构建出一个RequestTemplate请求模版 
//	这里会解析@RequestLine/@Param等等注解
//	从方法参数里拿到请求选项：Options（当然参数里可能也没有此类型，那就是null喽。如果是null，那最终执行默认的选项）
//	executeAndDecode(template, options)执行发送Http请求，并且完成结果解码（包括正确状态码的解码和错误解码）。这个步骤比较复杂，拆分为如下子步骤： 
//	把请求模版转换为请求对象feign.Request 
//	执行所有的拦截器RequestInterceptor，完成对请求模版的定制
//	调用目标target，把请求模版转为Request：target.apply(template);
//	发送Http请求：client.execute(request, options)，得到一个Response对象（这里若发生IO异常，也会被包装为RetryableException重新抛出）
//	解析此Response对象，解析后return（返回Object：可能是Response实例，也可能是decode解码后的任意类型）。大致会有如下情况： 
//	Response.class == metadata.returnType()，也就是说你的方法返回值用的就是Response。若response.body() == null，也就是说服务端是返回null/void的话，就直接return response；若response.body().length() == null，就直接返回response；否则，就正常返回response.toBuilder().body(bodyData).build() body里面的内容吧
//	若200 <= 响应码 <= 300，表示正确的返回。那就对返回值解码即可：decoder.decode(response, metadata.returnType())（解码过程中有可能异常，也会被包装成FeignException向上抛出）
//	若响应码是404，并且decode404 = true，那同上也同样执行decode动作
//	其它情况（4xx或者5xx的响应码），均执行错误编码：errorDecoder.decode(metadata.configKey(), response)
//	发送http请求若一切安好，那就结束了。否则执行重试逻辑： 
//	通过retryer.continueOrPropagate(e);看看收到此异常后是否要执行重试机制
//	需要重试的话就continue（注意上面是while(true)哦~）
//	若不需要重试（或者重试次数已到），那就重新抛出此异常，向上抛出
//	处理此异常，打印日志…
	@Override
	public Object invoke(Object[] argv) throws Throwable {
		// 根据方法入参，结合工厂构建出一个请求模版
		//	// 1. 创建RequestTemplate
		RequestTemplate template = buildTemplateFromArgs.create(argv);
		// 重试机制：注意这里是克隆一个来使用
		Retryer retryer = this.retryer.clone();
		while (true) {
			try {
				return executeAndDecode(template);
			} catch (RetryableException e) {
				// 若抛出异常，那就触发重试逻辑
				try {
					 // 该逻辑是：如果不重试了，该异常会继续抛出
			       	  // 若要充值，就会走下面的continue
					retryer.continueOrPropagate(e);
				} catch (RetryableException th) {
					Throwable cause = th.getCause();
					if (propagationPolicy == UNWRAP && cause != null) {
						throw cause;
					} else {
						throw th;
					}
				}
				if (logLevel != Logger.Level.NONE) {
					logger.logRetry(metadata.configKey(), logLevel);
				}
				continue;
			}
		}
	}

	Object executeAndDecode(RequestTemplate template) throws Throwable {
		// 1. 执行所有RequestInterceptor，最后执行Target的apply方法获得Request
		Request request = targetRequest(template);

		if (logLevel != Logger.Level.NONE) {
			logger.logRequest(metadata.configKey(), logLevel, request);
		}

		Response response;
		long start = System.nanoTime();
		try {
			// 2. 执行请求
			response = client.execute(request, options);
		} catch (IOException e) {
			if (logLevel != Logger.Level.NONE) {
				logger.logIOException(metadata.configKey(), logLevel, e, elapsedTime(start));
			}
			throw errorExecuting(request, e);
		}
		long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		boolean shouldClose = true;
		try {
			if (logLevel != Logger.Level.NONE) {
				response = logger.logAndRebufferResponse(metadata.configKey(), logLevel, response, elapsedTime);
			}
			if (Response.class == metadata.returnType()) {
				if (response.body() == null) {
					return response;
				}
				if (response.body().length() == null || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
					shouldClose = false;
					return response;
				}
				// Ensure the response body is disconnected
				byte[] bodyData = Util.toByteArray(response.body().asInputStream());
				return response.toBuilder().body(bodyData).build();
			}
			if (response.status() >= 200 && response.status() < 300) {
				if (void.class == metadata.returnType()) {
					return null;
				} else {
					Object result = decode(response);
					shouldClose = closeAfterDecode;
					return result;
				}
			} else if (decode404 && response.status() == 404 && void.class != metadata.returnType()) {
				Object result = decode(response);
				shouldClose = closeAfterDecode;
				return result;
			} else {
				throw errorDecoder.decode(metadata.configKey(), response);
			}
		} catch (IOException e) {
			if (logLevel != Logger.Level.NONE) {
				logger.logIOException(metadata.configKey(), logLevel, e, elapsedTime);
			}
			throw errorReading(request, response, e);
		} finally {
			if (shouldClose) {
				ensureClosed(response.body());
			}
		}
	}

	long elapsedTime(long start) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
	}

	Request targetRequest(RequestTemplate template) {
		for (RequestInterceptor interceptor : requestInterceptors) {
			interceptor.apply(template);
		}
		return target.apply(template);
	}

	Object decode(Response response) throws Throwable {
		try {
			return decoder.decode(response, metadata.returnType());
		} catch (FeignException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new Exception(e.getMessage(), e);
		}
	}

	static class Factory {

		private final Client client;
		private final Retryer retryer;
		private final List<RequestInterceptor> requestInterceptors;
		private final Logger logger;
		private final Logger.Level logLevel;
		private final boolean decode404;
		private final boolean closeAfterDecode;
		private final ExceptionPropagationPolicy propagationPolicy;

		Factory(Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors, Logger logger,
				Logger.Level logLevel, boolean decode404, boolean closeAfterDecode,
				ExceptionPropagationPolicy propagationPolicy) {
			this.client = checkNotNull(client, "client");
			this.retryer = checkNotNull(retryer, "retryer");
			this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors");
			this.logger = checkNotNull(logger, "logger");
			this.logLevel = checkNotNull(logLevel, "logLevel");
			this.decode404 = decode404;
			this.closeAfterDecode = closeAfterDecode;
			this.propagationPolicy = propagationPolicy;
		}

		public MethodHandler create(Target<?> target, MethodMetadata md, RequestTemplate.Factory buildTemplateFromArgs,
				Options options, Decoder decoder, ErrorDecoder errorDecoder) {
			return new SynchronousMethodHandler(target, client, retryer, requestInterceptors, logger, logLevel, md,
					buildTemplateFromArgs, options, decoder, errorDecoder, decode404, closeAfterDecode,
					propagationPolicy);
		}
	}
}
