package org.spring.study.netty.bbnb.project.niohdl.core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {
    void setSendListener(ioArgs.IoArgsEventProcessor processor);
    boolean postSendAsync() throws IOException;
}
