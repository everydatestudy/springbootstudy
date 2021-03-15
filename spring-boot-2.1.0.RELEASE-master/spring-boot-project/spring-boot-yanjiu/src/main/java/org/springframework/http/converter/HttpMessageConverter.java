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

package org.springframework.http.converter;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;


//HttpMessageConverter
//在具体讲解之前，先对所有的转换器来个概述：
//
//名称							作用	读支持MediaType	写支持MediaType	备注
//FormHttpMessageConverter	        表单与MultiValueMap的相互转换	application/x-www-form-urlencoded	application/x-www-form-urlencoded和multipart/form-data	可用于处理下载
//XmlAwareFormHttpMessageConverter	Spring3.2后已过期，使用下面AllEnc…代替	略	略	
//AllEncompassingFormHttpMessageConverter	对FormHttp…的扩展，提供了对xml和json的支持	同上	同上	
//SourceHttpMessageConverter	数据与javax.xml.transform.Source的相互转换	application/xml和text/xml和application/*+xml	同read	和Sax/Dom等有关
//ResourceHttpMessageConverter	数据与org.springframework.core.io.Resource	*/*	*/*	
//ByteArrayHttpMessageConverter	数据与字节数组的相互转换	*/*	application/octet-stream	
//ObjectToStringHttpMessageConverter	内部持有一个StringHttpMessageConverter和ConversionService	他俩的&&	他俩的&&	
//RssChannelHttpMessageConverter	处理RSS <channel> 元素	application/rss+xml	application/rss+xml	很少接触
//MappingJackson2HttpMessageConverter	使用Jackson的ObjectMapper转换Json数据	application/json和application/*+json	application/json和application/*+json	默认编码UTF-8
//MappingJackson2XmlHttpMessageConverter	使用Jackson的XmlMapper转换XML数据	application/xml和text/xml	application/xml和text/xml	需要额外导包Jackson-dataformat-XML才能生效。从Spring4.1后才有
//GsonHttpMessageConverter	使用Gson处理Json数据	application/json	application/json	默认编码UTF-8
//ResourceRegionHttpMessageConverter	数据和org.springframework.core.io.support.ResourceRegion的转换	application/octet-stream	application/octet-stream	Spring4.3才提供此类
//ProtobufHttpMessageConverter	转换com.google.protobuf.Message数据	application/x-protobuf和text/plain和application/json和application/xml	同read	@since 4.1
//StringHttpMessageConverter	数据与String类型的相互转换	*/*	*/*	转成字符串的默认编码为ISO-8859-1
//BufferedImageHttpMessageConverter	数据与java.awt.image.BufferedImage的相互转换	Java I/O API支持的所有类型	Java I/O API支持的所有类型	
//FastJsonHttpMessageConverter	使用FastJson处理Json数据	*/*	*/*	需要导入Jar包和自己配置，Spring并不默认内置
//————————————————
//版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
//原文链接：https://blog.csdn.net/f641385712/article/details/89891245

/**
 * Strategy interface that specifies a converter that can convert from and to HTTP requests and responses.
 *
 * @author Arjen Poutsma
 * @author Juergen Hoeller
 * @since 3.0
 * @param <T> the converted object type
 */
public interface HttpMessageConverter<T> {

	/**判断数据类型是否可读
	 * 	// 指定转换器可以读取的对象类型，即转换器可将请求信息转换为clazz类型的对象
	// 同时支持指定的MIME类型(text/html、application/json等)
	 * Indicates whether the given class can be read by this converter.
	 * @param clazz the class to test for readability
	 * @param mediaType the media type to read (can be {@code null} if not specified);
	 * typically the value of a {@code Content-Type} header.
	 * @return {@code true} if readable; {@code false} otherwise
	 */
	boolean canRead(Class<?> clazz, @Nullable MediaType mediaType);

	/**判断数据是否可写
	 * // 指定转换器可以将clazz类型的对象写到响应流当中，响应流支持的媒体类型在mediaType中定义
	 * Indicates whether the given class can be written by this converter.
	 * @param clazz the class to test for writability
	 * @param mediaType the media type to write (can be {@code null} if not specified);
	 * typically the value of an {@code Accept} header.
	 * @return {@code true} if writable; {@code false} otherwise
	 */
	boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType);

	/**获取支持的数据类型
	 * Return the list of {@link MediaType} objects supported by this converter.
	 * @return the list of supported media types
	 */
	List<MediaType> getSupportedMediaTypes();

	/**对参数值进行读，转换为需要的类型	// 将请求信息转换为T类型的对象 流对象为：HttpInputMessage

	 * Read an object of the given type from the given input message, and returns it.
	 * @param clazz the type of object to return. This type must have previously been passed to the
	 * {@link #canRead canRead} method of this interface, which must have returned {@code true}.
	 * @param inputMessage the HTTP input message to read from
	 * @return the converted object
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotReadableException in case of conversion errors
	 */
	T read(Class<? extends T> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException;

	/**将返回值发送给请求者	// 将T类型的对象写到响应流当中，同事指定响应的媒体类型为contentType 输出流为：HttpOutputMessage 

	 * Write an given object to the given output message.
	 * @param t the object to write to the output message. The type of this object must have previously been
	 * passed to the {@link #canWrite canWrite} method of this interface, which must have returned {@code true}.
	 * @param contentType the content type to use when writing. May be {@code null} to indicate that the
	 * default content type of the converter must be used. If not {@code null}, this media type must have
	 * previously been passed to the {@link #canWrite canWrite} method of this interface, which must have
	 * returned {@code true}.
	 * @param outputMessage the message to write to
	 * @throws IOException in case of I/O errors
	 * @throws HttpMessageNotWritableException in case of conversion errors
	 */
	void write(T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException;

}
