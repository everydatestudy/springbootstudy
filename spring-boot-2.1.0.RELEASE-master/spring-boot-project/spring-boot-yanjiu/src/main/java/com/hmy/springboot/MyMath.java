package com.hmy.springboot;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_SINGLETON,proxyMode = ScopedProxyMode.TARGET_CLASS)
public class MyMath implements Calc{

   public Integer add(int num1,int num2){
        return num1+num2;
    }
}