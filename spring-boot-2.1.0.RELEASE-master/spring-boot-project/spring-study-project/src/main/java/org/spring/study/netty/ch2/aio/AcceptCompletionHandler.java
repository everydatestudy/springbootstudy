package org.spring.study.netty.ch2.aio;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * Created by wangdecheng on 25/03/2018.
 */
public class AcceptCompletionHandler implements CompletionHandler<AsynchronousSocketChannel, ServerAio> {
    @Override
    public void completed(AsynchronousSocketChannel result, ServerAio attachment) {
        attachment.asynServerSocketChannel.accept(attachment,this);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        result.read(buffer,buffer,new ReadCompletionHandler(result));
    }

    @Override
    public void failed(Throwable exc, ServerAio attachment) {

        exc.printStackTrace();
        attachment.latch.countDown();
    }
}
