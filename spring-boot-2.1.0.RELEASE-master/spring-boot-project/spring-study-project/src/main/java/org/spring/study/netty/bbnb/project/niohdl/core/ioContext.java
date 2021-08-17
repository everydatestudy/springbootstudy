package org.spring.study.netty.bbnb.project.niohdl.core;

import org.spring.study.netty.bbnb.project.niohdl.impl.ioSelectorProvider;

public class ioContext {
    private static ioSelectorProvider ioSelector;

    public static ioSelectorProvider getIoSelector() {
        return ioSelector;
    }

    public static void setIoSelector(ioSelectorProvider ioSelector) {
        ioContext.ioSelector = ioSelector;
    }

    public static void close(){
        ioSelector.close();
    }
}
