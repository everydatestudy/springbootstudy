package com.hmy.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
//https://blog.csdn.net/shenchaohao12321/article/details/86667511 非常透彻的spring
@Controller
//@EnableWebMvc
public class UserController {

	@RequestMapping("/test")
	@ResponseBody
	public Map<String, String> test(HttpServletRequest request,Integer str) {
		
		Map<String, String> parts = new HashMap<>();
//		 parts.add("field 1", "value 1");
	 
		System.out.println(request);
		System.out.println("***************");
		Map<String, String> map = new HashMap<>();
		map.put("fdsafd", "fsdaa");
		return parts;
	}
}