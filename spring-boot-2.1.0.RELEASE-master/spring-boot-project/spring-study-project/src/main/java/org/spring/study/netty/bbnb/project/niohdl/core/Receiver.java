package org.spring.study.netty.bbnb.project.niohdl.core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {
    void setReceiveListener(ioArgs.IoArgsEventProcessor processor);
    boolean postReceiveAsync() throws IOException;
}
