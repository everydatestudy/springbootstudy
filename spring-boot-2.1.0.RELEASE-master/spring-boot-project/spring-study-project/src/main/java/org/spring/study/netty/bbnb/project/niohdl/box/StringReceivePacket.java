package org.spring.study.netty.bbnb.project.niohdl.box;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.spring.study.netty.bbnb.project.niohdl.core.ReceivePacket;

public class StringReceivePacket extends ReceivePacket<ByteArrayOutputStream> {
    private String string;

    public StringReceivePacket(int len) {
        length = len;
    }

    public String toString(){
        return string;
    }

    @Override
    protected void closeStream(ByteArrayOutputStream stream) throws IOException {
        super.closeStream(stream);
        string = new String(stream.toByteArray());
    }

    @Override
    protected ByteArrayOutputStream createStream() {
        return new ByteArrayOutputStream((int)length);
    }
}
