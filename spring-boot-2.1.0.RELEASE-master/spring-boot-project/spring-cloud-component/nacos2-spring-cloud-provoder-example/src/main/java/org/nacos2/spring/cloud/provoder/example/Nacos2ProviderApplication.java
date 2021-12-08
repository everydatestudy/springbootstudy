package org.nacos2.spring.cloud.provoder.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author xiaojing
 */
@SpringBootApplication
@EnableDiscoveryClient
public class Nacos2ProviderApplication {

	public static void main(String[] args) {
		// NacosServiceRegistryAutoConfiguration ddd;
		SpringApplication.run(Nacos2ProviderApplication.class, args);
	}

	@RestController
	class EchoController {
		// NacosConfigBootstrapConfiguration fdsa;
		@RequestMapping(value = "/echo/{string}", method = RequestMethod.GET)
		public String echo(@PathVariable String string) {
			return "Hello Nacos Discovery " + string;
		}
	}
}
