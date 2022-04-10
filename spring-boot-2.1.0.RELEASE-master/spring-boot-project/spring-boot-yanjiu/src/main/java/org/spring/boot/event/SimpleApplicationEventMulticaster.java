package org.spring.boot.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SimpleApplicationEventMulticaster implements DemoApplicationEventMulticaster {
    // 是否异步发布事件
    private boolean async = false;
    // 线程池
    private Executor taskExecutor = new ThreadPoolExecutor(5, 5, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    // 事件监听器列表
    private List<ContextListener<?>> contextListeners = new ArrayList<ContextListener<?>>();

    
    public void addContextListener(ContextListener<?> listener) {
        contextListeners.add(listener);
    }

    public void removeContextListener(ContextListener<?> listener) {
        contextListeners.remove(listener);
    }

    public void removeAllListeners() {
        contextListeners.clear();
    }

    public void multicastEvent(AbstractContextEvent event) {
        doMulticastEvent(contextListeners, event);
    }

    private void doMulticastEvent(List<ContextListener<?>> contextListeners, AbstractContextEvent event) {
        for (ContextListener<?> contextListener : contextListeners) {
            // 异步广播事件
            if (async) {
                taskExecutor.execute(() -> invokeListener(contextListener, event));
                // new Thread(() -> invokeListener(contextListener, event)).start();
            // 同步发布事件，阻塞的方式
            } else {
                invokeListener(contextListener, event);
            }
        }
    }

    private void invokeListener(ContextListener contextListener, AbstractContextEvent event) {
        contextListener.onApplicationEvent(event);
    }

    public void setAsync(boolean async) {
        this.async = async;
    }
}

 