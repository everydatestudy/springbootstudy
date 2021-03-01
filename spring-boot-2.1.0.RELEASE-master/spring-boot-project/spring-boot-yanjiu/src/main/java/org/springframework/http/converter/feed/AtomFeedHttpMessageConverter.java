//package org.springframework.http.converter.feed;
//
//
//
//
//import java.io.IOException;
//
//import org.springframework.http.HttpOutputMessage;
//import org.springframework.http.MediaType;
//import org.springframework.http.converter.HttpMessageNotWritableException;
//
///**
// * Implementation of {@link org.springframework.http.converter.HttpMessageConverter}
// * that can read and write Atom feeds. Specifically, this converter can handle {@link Feed}
// * objects from the <a href="https://github.com/rometools/rome">ROME</a> project.
// *
// * <p>><b>NOTE: As of Spring 4.1, this is based on the {@code com.rometools}
// * variant of ROME, version 1.5. Please upgrade your build dependency.</b>
// *
// * <p>By default, this converter reads and writes the media type ({@code application/atom+xml}).
// * This can be overridden through the {@link #setSupportedMediaTypes supportedMediaTypes} property.
// *
// * @author Arjen Poutsma
// * @param <T>
// * @since 3.0.2
// * @see Feed
// */
//public class AtomFeedHttpMessageConverter extends AbstractWireFeedHttpMessageConverter {
//
//	public AtomFeedHttpMessageConverter() {
//		super(new MediaType("application", "atom+xml"));
//	}
//
//	@Override
//	protected boolean supports(Class<?> clazz) {
//		return true;
//	}
//
//	@Override
//	protected void writeInternal(Object t, HttpOutputMessage outputMessage)
//			throws IOException, HttpMessageNotWritableException {
//		// TODO Auto-generated method stub
//		
//	}
//
//}