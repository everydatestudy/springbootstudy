package org.spring.study.netty.ch2.nio;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by wangdecheng on 24/03/2018.
 */
public class TimeServerNio {

    private  boolean stop;
    Selector selector = null;

    public static void main(String[] args){

        new TimeServerNio().start();
    }

    public void start(){
        ServerSocketChannel acceptorSvr = null;
        try {
            int port = 8080;
            acceptorSvr = ServerSocketChannel.open();
            acceptorSvr.configureBlocking(false);
            acceptorSvr.bind(new InetSocketAddress("127.0.0.1",port));
            selector = Selector.open();
            acceptorSvr.register(selector,SelectionKey.OP_ACCEPT);
            System.out.println("Timeserver start!!");

            SelectionKey key;
            while(!stop){
                selector.select();
                Set<SelectionKey> selectionKeySet = selector.selectedKeys();
                Iterator<SelectionKey>  iterator = selectionKeySet.iterator();
                while(iterator.hasNext()){
                    key = iterator.next();
                    iterator.remove();
                    try{
                        handleInput(key);
                    }catch (Exception e){
                        if(key != null){
                            key.cancel();
                            if(key.channel() !=null){
                                key.channel().close();
                            }
                        }
                    }
                }

            }
            if(selector !=null){
                try{
                    selector.close();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private  void handleInput(SelectionKey key) throws IOException{
        if(key.isValid()){
            if(key.isAcceptable()){
                ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                SocketChannel sc = ssc.accept();
                sc.configureBlocking(false);
                sc.register(selector,SelectionKey.OP_READ);
            }
            if(key.isReadable()){
                SocketChannel sc = (SocketChannel)key.channel();
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);
                if(readBytes>0){
                    readBuffer.flip();
                    byte[] bytes = new byte[readBuffer.remaining()];
                    readBuffer.get(bytes);
                    String body = new String(bytes,"UTF-8");
                    System.out.println("the time server receive order:"+body);
                    doWrite(sc,"the time server receive order:"+body);
                }else if(readBytes <0){
                    key.cancel();
                    sc.close();
                }
            }
        }
    }

    private void doWrite(SocketChannel sc, String response) throws IOException{
        if(StringUtils.isNotBlank(response)){
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            byteBuffer.put(response.getBytes());
            byteBuffer.flip();
            sc.write(byteBuffer);
            System.out.println("send to client:"+response);
        }
    }

    public void stop(){
        this.stop = true;
    }


}
