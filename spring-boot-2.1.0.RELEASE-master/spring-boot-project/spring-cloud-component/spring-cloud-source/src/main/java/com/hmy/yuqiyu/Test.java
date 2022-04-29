package com.hmy.yuqiyu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Test {
	
    private static final Logger logger = LoggerFactory.getLogger(Test.class); //默认log4j对象
    private static final Logger logger1=LoggerFactory.getLogger("batch1");//batch1 log4j对象
    private static final Logger logger2=LoggerFactory.getLogger("batch2"); //batch2 log4j对象
    public static void main(String [] args){
    	org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext fdsa=null ;
    	
        logger.info("cs");//只写到test.txt
        logger1.info("cs1");//写到test.txt和test1.txt
        logger2.info("cs2");//写到test.txt和test2.txt

    }
}