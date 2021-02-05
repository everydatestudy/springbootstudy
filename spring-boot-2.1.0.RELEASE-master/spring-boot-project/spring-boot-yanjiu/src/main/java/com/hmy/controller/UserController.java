package com.hmy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class UserController {

    @RequestMapping("/test")
    @ResponseBody
    public String test() {
        System.out.println("***************");
        return "hello spring mvc";
    }
}