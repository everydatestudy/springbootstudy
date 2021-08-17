package org.spring.study.netty.bbnb.project;

import java.io.IOException;

public class Server {
    public static void main(String[] args){

        String ConfigIp;
        int ConfigPort;

        ConfigIp = "127.0.0.1";
        ConfigPort = Integer.parseInt("8080");
        System.out.println(ConfigIp+":"+ConfigPort);

        SvrFrame svrFrame = new SvrFrame(ConfigIp, ConfigPort);


        try {
            svrFrame.InitIOSelector();
            svrFrame.InitSocket();
        } catch (IOException e) {
            System.out.println("xxxxxxxxxxxxxxxxxxx Init SERVER FAILED xxxxxxxxxxxxxxxxxxxxx");
        }

        svrFrame.start();
        System.out.println("xxxxxxxxxxxxxxxxxxx CHAT SERVER is running xxxxxxxxxxxxxxxxxxxxx");


    }
}
