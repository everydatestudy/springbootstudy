package org.spring.study.netty.bbnb.project.niohdl.box;

import java.io.ByteArrayInputStream;

import org.spring.study.netty.bbnb.project.niohdl.core.SendPacket;

public class StringSendPacket extends SendPacket<ByteArrayInputStream> {
    private final byte[] bytes;

    public StringSendPacket(String msg) {
        this.bytes = msg.getBytes();
        this.length = bytes.length;
    }

    @Override
    protected ByteArrayInputStream createStream() {
        return new ByteArrayInputStream(bytes);
    }
}
