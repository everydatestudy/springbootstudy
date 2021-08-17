package org.spring.study.netty.bbnb.project.niohdl;

import java.nio.channels.SocketChannel;

import org.spring.study.netty.bbnb.project.niohdl.core.ReceiveDispatcher;
import org.spring.study.netty.bbnb.project.niohdl.core.ReceivePacket;
import org.spring.study.netty.bbnb.project.niohdl.core.SendDispatcher;
import org.spring.study.netty.bbnb.project.niohdl.core.ioArgs;
import org.spring.study.netty.bbnb.project.niohdl.core.ioContext;
import org.spring.study.netty.bbnb.project.niohdl.core.ioProvider;

public abstract class API implements ioProvider.IOCallback {
    private SocketChannel socketChannel;
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;
    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        //接收到消息的回调
        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {

        }
    };

    @Override
    public void onInput(ioArgs args) {
        onReceiveFromCore(args);
    }

    protected void onReceiveFromCore(ioArgs args){
    }

    @Override
    public void onOutput() {

    }

    @Override
    public void onChannelClosed() {
        ioContext.getIoSelector().unRegisterInput(socketChannel);
        ioContext.getIoSelector().unRegisterOutput(socketChannel);
    }
}
