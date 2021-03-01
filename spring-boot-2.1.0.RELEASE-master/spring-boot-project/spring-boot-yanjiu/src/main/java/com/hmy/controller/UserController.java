package com.hmy.controller;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Controller
//@EnableWebMvc
public class UserController {

	@RequestMapping("/test")
	@ResponseBody
	public Map<String, String> test(HttpServletRequest request,String str) {
		System.out.println(request);
		System.out.println("***************");
		Map<String, String> map = new HashMap<>();
		map.put("fdsafd", "fsdaa");
		return map;
	}
}