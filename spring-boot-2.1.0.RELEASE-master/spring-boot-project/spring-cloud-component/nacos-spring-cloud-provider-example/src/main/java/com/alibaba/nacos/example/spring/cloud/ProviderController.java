package com.alibaba.nacos.example.spring.cloud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProviderController {

   
    private String name="调用成功了feign";

    @GetMapping("send")
    public String send(){
        return name;
    }
}
