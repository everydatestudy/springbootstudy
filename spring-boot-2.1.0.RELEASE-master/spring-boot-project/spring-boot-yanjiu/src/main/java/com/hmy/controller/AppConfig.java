package com.hmy.controller;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;

@Configuration
@ComponentScan("com.hmy.controller")
public class AppConfig implements WebMvcConfigurer {

//    public void configureViewResolvers(ViewResolverRegistry registry){
//        registry.jsp("","");
//    }
    @Bean
    public InternalResourceViewResolver internalResourceViewResolver(){
        InternalResourceViewResolver internalResourceViewResolver = new InternalResourceViewResolver();
        internalResourceViewResolver.setPrefix("/");
        internalResourceViewResolver.setSuffix(".jsp");
        return internalResourceViewResolver;
    }
    
	public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
		FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
//		List<MediaType> mediaTypes = new ArrayList<MediaType>();
//		mediaTypes.add(new MediaType(MediaType.TEXT_HTML, Charset.forName("UTF-8")));
//		mediaTypes.add(new MediaType(MediaType.APPLICATION_JSON, Charset.forName("UTF-8")));
//		mediaTypes.add(new MediaType(MediaType.APPLICATION_XML, Charset.forName("UTF-8")));
//
//		converter.setSupportedMediaTypes(mediaTypes);
//
//		FastJsonConfig fastJsonConfig = new FastJsonConfig();
//		fastJsonConfig.setSerializerFeatures(SerializerFeature.PrettyFormat);
//
//		converter.setFastJsonConfig(fastJsonConfig);

		converters.add(converter);
	}
}
