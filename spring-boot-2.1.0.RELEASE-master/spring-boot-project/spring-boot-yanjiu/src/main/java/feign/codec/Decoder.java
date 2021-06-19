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
package feign.codec;

import java.io.IOException;
import java.lang.reflect.Type;
import feign.Feign;
import feign.FeignException;
import feign.Response;
import feign.Util;

/**
 * Decodes an HTTP response into a single object of the given {@code type}. Invoked when
 * {@link Response#status()} is in the 2xx range and the return type is neither {@code void} nor
 * {@code
 * Response}.
 * <p/>
 * <p/>
 * Example Implementation:<br>
 * <p/>
 * 
 * <pre>
 * public class GsonDecoder implements Decoder {
 *   private final Gson gson = new Gson();
 *
 *   &#064;Override
 *   public Object decode(Response response, Type type) throws IOException {
 *     try {
 *       return gson.fromJson(response.body().asReader(), type);
 *     } catch (JsonIOException e) {
 *       if (e.getCause() != null &amp;&amp;
 *           e.getCause() instanceof IOException) {
 *         throw IOException.class.cast(e.getCause());
 *       }
 *       throw e;
 *     }
 *   }
 * }
 * </pre>
 * 
 * <br/>
 * <h3>Implementation Note</h3> The {@code type} parameter will correspond to the
 * {@link java.lang.reflect.Method#getGenericReturnType() generic return type} of an
 * {@link feign.Target#type() interface} processed by {@link feign.Feign#newInstance(feign.Target)}.
 * When writing your implementation of Decoder, ensure you also test parameterized types such as
 * {@code
 * List<Foo>}. <br/>
 * <h3>Note on exception propagation</h3> Exceptions thrown by {@link Decoder}s get wrapped in a
 * {@link DecodeException} unless they are a subclass of {@link FeignException} already, and unless
 * the client was configured with {@link Feign.Builder#decode404()}.
 */
public interface Decoder {
//	使用时需要注意：因为接口代理实例是通过Feign.newInstance（Feign.Target）来生成的，它是支持泛型处理的如List<Foo>，所以当你自定义解码器的时候也请支持泛型类型。
//
//	异常情况时（请求抛出异常，或者状态码不是2xx等），会有如下处理方案：
//
//	解码器引发的异常将包装在DecodeException中，除非它们已经是FeignException的子类
//	如果发生了404，但是没有配置Feign.Builder.decode404（）解码的话，最终也会交给此解码器处理的（否则就不会交给此解码器）。
  /**
   * Decodes an http response into an object corresponding to its
   * {@link java.lang.reflect.Method#getGenericReturnType() generic return type}. If you need to
   * wrap exceptions, please do so via {@link DecodeException}.
   *
   * @param response the response to decode
   * @param type {@link java.lang.reflect.Method#getGenericReturnType() generic return type} of the
   *        method corresponding to this {@code response}.
   * @return instance of {@code type}
   * @throws IOException will be propagated safely to the caller.
   * @throws DecodeException when decoding failed due to a checked exception besides IOException.
   * @throws FeignException when decoding succeeds, but conveys the operation failed.
   */
	// response：代表请求响应
		// type：代表方法的返回值类型
		// 它还有个特点：抛出了三种异常
		// 但其实除了IOException，其它两种都是unchecked异常
  Object decode(Response response, Type type) throws IOException, DecodeException, FeignException;

  /** Default implementation of {@code Decoder}. */
  public class Default extends StringDecoder {

    @Override
    public Object decode(Response response, Type type) throws IOException {
      if (response.status() == 404)
        return Util.emptyValueOf(type);
      if (response.body() == null)
        return null;
      if (byte[].class.equals(type)) {
        return Util.toByteArray(response.body().asInputStream());
      }
      return super.decode(response, type);
    }
  }
}
