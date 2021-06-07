/*
 * Copyright 2002-2017 the original author or authors.
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
import java.io.InputStream;
import java.io.PushbackInputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;

/**它是一个访问权限是default的类，是对其它ClientHttpResponse的一个包装
 * Implementation of {@link ClientHttpResponse} that can not only check if
 * the response has a message body, but also if its length is 0 (i.e. empty)
 * by actually reading the input stream.
 *
 * @author Brian Clozel
 * @since 4.1.5
 * @see <a href="http://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230 Section 3.3.3</a>
 */
class MessageBodyClientHttpResponseWrapper implements ClientHttpResponse {

	private final ClientHttpResponse response;

	@Nullable
	private PushbackInputStream pushbackInputStream;


	public MessageBodyClientHttpResponseWrapper(ClientHttpResponse response) throws IOException {
		this.response = response;
	}


	/**判断相应里是否有body体
	 若响应码是1xx 或者是204；或者getHeaders().getContentLength() == 0 那就返回false  否则返回true
	 * Indicates whether the response has a message body.
	 * <p>Implementation returns {@code false} for:
	 * <ul>
	 * <li>a response status of {@code 1XX}, {@code 204} or {@code 304}</li>
	 * <li>a {@code Content-Length} header of {@code 0}</li>
	 * </ul>
	 * @return {@code true} if the response has a message body, {@code false} otherwise
	 * @throws IOException in case of I/O errors
	 */
	public boolean hasMessageBody() throws IOException {
		HttpStatus status = HttpStatus.resolve(getRawStatusCode());
		if (status != null && (status.is1xxInformational() || status == HttpStatus.NO_CONTENT ||
				status == HttpStatus.NOT_MODIFIED)) {
			return false;
		}
		if (getHeaders().getContentLength() == 0) {
			return false;
		}
		return true;
	}

	/**上面是完全格局状态码（ContentLength）来判断是否有body体的~~~这里会根据流来判断
	// 如果response.getBody() == null,返回true
	// 若流里有内容，最终就用new PushbackInputStream(body)包装起来~~~
————————————————
版权声明：本文为CSDN博主「YourBatman」的原创文章，遵循CC 4.0 BY-SA版权协议，转载请附上原文出处链接及本声明。
原文链接：https://blog.csdn.net/f641385712/article/details/100713622
	 * Indicates whether the response has an empty message body.
	 * <p>Implementation tries to read the first bytes of the response stream:
	 * <ul>
	 * <li>if no bytes are available, the message body is empty</li>
	 * <li>otherwise it is not empty and the stream is reset to its start for further reading</li>
	 * </ul>
	 * @return {@code true} if the response has a zero-length message body, {@code false} otherwise
	 * @throws IOException in case of I/O errors
	 */
	public boolean hasEmptyMessageBody() throws IOException {
		InputStream body = this.response.getBody();
		if (body.markSupported()) {
			body.mark(1);
			if (body.read() == -1) {
				return true;
			}
			else {
				body.reset();
				return false;
			}
		}
		else {
			this.pushbackInputStream = new PushbackInputStream(body);
			int b = this.pushbackInputStream.read();
			if (b == -1) {
				return true;
			}
			else {
				this.pushbackInputStream.unread(b);
				return false;
			}
		}
	}


	@Override
	public HttpHeaders getHeaders() {
		return this.response.getHeaders();
	}

	@Override
	public InputStream getBody() throws IOException {
		return (this.pushbackInputStream != null ? this.pushbackInputStream : this.response.getBody());
	}

	@Override
	public HttpStatus getStatusCode() throws IOException {
		return this.response.getStatusCode();
	}

	@Override
	public int getRawStatusCode() throws IOException {
		return this.response.getRawStatusCode();
	}

	@Override
	public String getStatusText() throws IOException {
		return this.response.getStatusText();
	}

	@Override
	public void close() {
		this.response.close();
	}

}
