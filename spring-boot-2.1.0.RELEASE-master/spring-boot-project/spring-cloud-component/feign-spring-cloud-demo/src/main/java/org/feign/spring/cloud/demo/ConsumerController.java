package org.feign.spring.cloud.demo;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 描述:
 *
 * @author xiaog
 * @date 2020-01-20 10:37
 */
@RestController
public class ConsumerController {

	@Autowired
	private ProviderFeign providerFeign;

	@GetMapping("consumer")
	public String send() {
		String send = providerFeign.send();
		return "consumer ========= " + send;
	}

}