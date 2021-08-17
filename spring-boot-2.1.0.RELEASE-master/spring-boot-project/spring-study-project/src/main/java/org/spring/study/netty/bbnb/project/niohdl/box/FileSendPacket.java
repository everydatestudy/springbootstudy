package org.spring.study.netty.bbnb.project.niohdl.box;

import java.io.File;
import java.io.FileInputStream;

import org.spring.study.netty.bbnb.project.niohdl.core.SendPacket;

public class FileSendPacket extends SendPacket {

    public FileSendPacket(File file){
        this.length = file.length();
    }
    @Override
    protected FileInputStream createStream() {
        return null;
    }
}
