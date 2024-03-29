package org.feign.spring.cloud.demo;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;

/**https://juejin.cn/post/6878568367259975694 feign源码分析
 * @author gaopanhong
 * @description: TODO
 * @since 2020-01-20
 */
@FeignClient("service-provider")
public interface ProviderFeign {

	@RequestMapping("send")
	String send();
}