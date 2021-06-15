/*
 *
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer.reactive;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Func1;
import rx.functions.Func2;

import com.netflix.client.ClientException;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.loadbalancer.reactive.ExecutionListener.AbortExecutionException;
import com.netflix.servo.monitor.Stopwatch;

/**
 * 在介绍完了前置知识后，下面来到本文主菜：LoadBalancerCommand负载均衡命令。熟悉的Command命令模式有木有，它用于用于从负载均衡器执行中生成可观察对象Observable<T>。主要负责完成如下事情：

选中一个Server（最核心的逻辑，通过负载均衡器完成选择）
执行ServerOperation#call(server)方法得到一个Observable<T>结果
若有ExecutionListener，会执行监听器们
借助RetryHandler对发生异常是会进行重试
向LoadBalancerStats负载结果对象提供指标反馈
本篇内容丰富，特别是详细讲解了Ribbon对重试机制的实现，解释了很多小伙伴解释不清的：MaxAutoRetries和MaxAutoRetriesNextServer的区别和联系。
 * A command that is used to produce the Observable from the load balancer execution. The load balancer is responsible for
 * the following:
 *
 * <ul>
 * <li>Choose a server</li>
 * <li>Invoke the {@link #call(com.netflix.loadbalancer.Server)} method</li>
 * <li>Invoke the {@link ExecutionListener} if any</li>
 * <li>Retry on exception, controlled by {@link com.netflix.client.RetryHandler}</li>
 * <li>Provide feedback to the {@link com.netflix.loadbalancer.LoadBalancerStats}</li>
 * </ul>
 *
 * @author Allen Wang
 */
public class LoadBalancerCommand<T> {
    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerCommand.class);

    public static class Builder<T> {
        private RetryHandler        retryHandler;
        private ILoadBalancer       loadBalancer;
        private IClientConfig       config;
        private LoadBalancerContext loadBalancerContext;
        private List<? extends ExecutionListener<?, T>> listeners;
        private Object              loadBalancerKey;
        private ExecutionContext<?> executionContext;
        //负责各个执行阶段中监听器的执行，比较简单
        private ExecutionContextListenerInvoker invoker;
        //请求的URI。作为original原始uri去负载均衡器里获取一个Server
        private URI                 loadBalancerURI;
        private Server              server;
        
        private Builder() {}
    
        public Builder<T> withLoadBalancer(ILoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
            return this;
        }
    
        public Builder<T> withLoadBalancerURI(URI loadBalancerURI) {
            this.loadBalancerURI = loadBalancerURI;
            return this;
        }
        
        public Builder<T> withListeners(List<? extends ExecutionListener<?, T>> listeners) {
            if (this.listeners == null) {
                this.listeners = new LinkedList<ExecutionListener<?, T>>(listeners);
            } else {
                this.listeners.addAll((Collection) listeners);
            }
            return this;
        }
    
        public Builder<T> withRetryHandler(RetryHandler retryHandler) {
            this.retryHandler = retryHandler;
            return this;
        }
    
        public Builder<T> withClientConfig(IClientConfig config) {
            this.config = config;
            return this;
        }
    
        /**
         * Pass in an optional key object to help the load balancer to choose a specific server among its
         * server list, depending on the load balancer implementation.
         */
        public Builder<T> withServerLocator(Object key) {
            this.loadBalancerKey = key;
            return this;
        }
    
        public Builder<T> withLoadBalancerContext(LoadBalancerContext loadBalancerContext) {
            this.loadBalancerContext = loadBalancerContext;
            return this;
        }
    
        public Builder<T> withExecutionContext(ExecutionContext<?> executionContext) {
            this.executionContext = executionContext;
            return this;
        }
        
        /**
         * Pin the operation to a specific server.  Otherwise run on any server returned by the load balancer
         * 
         * @param server
         */
        public Builder<T> withServer(Server server) {
            this.server = server;
            return this;
        }
        
        public LoadBalancerCommand<T> build() {
            if (loadBalancerContext == null && loadBalancer == null) {
                throw new IllegalArgumentException("Either LoadBalancer or LoadBalancerContext needs to be set");
            }
            
            if (listeners != null && listeners.size() > 0) {
                this.invoker = new ExecutionContextListenerInvoker(executionContext, listeners, config);
            }
            
            if (loadBalancerContext == null) {
                loadBalancerContext = new LoadBalancerContext(loadBalancer, config);
            }
            
            return new LoadBalancerCommand<T>(this);
        }
    }
    
    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    private final URI    loadBalancerURI;
    //用于去负载均衡器获取一个Server
    private final Object loadBalancerKey;
//    负载均衡器上下文。提供执行过程中各种组件的访问和获取，如： 
//    loadBalancerContext.getServerFromLoadBalancer()获取一台Server
//    loadBalancerContext.getServerStats(server)：得到Server的状态信息
//    loadBalancerContext.noteOpenConnection(stats) / noteRequestCompletion()：收集stats信息
    private final LoadBalancerContext loadBalancerContext;
    
    //重试处理器。若构建时没有指定，就会选用loadBalancerContext里的。它负载完成IClient执行时的重试操作
    private final RetryHandler retryHandler;
    private volatile ExecutionInfo executionInfo;
   // 若构建时传入了server就使用这台Server执行。否则交给负载均衡器自己去选择
    private final Server server;

    private final ExecutionContextListenerInvoker<?, T> listenerInvoker;
    
    private LoadBalancerCommand(Builder<T> builder) {
        this.loadBalancerURI     = builder.loadBalancerURI;
        this.loadBalancerKey     = builder.loadBalancerKey;
        this.loadBalancerContext = builder.loadBalancerContext;
        this.retryHandler        = builder.retryHandler != null ? builder.retryHandler : loadBalancerContext.getRetryHandler();
        this.listenerInvoker     = builder.invoker;
        this.server              = builder.server;
    }
    
    /**
     * Return an Observable that either emits only the single requested server
     * or queries the load balancer for the next server on each subscription
     */
    private Observable<Server> selectServer() {
        return Observable.create(new OnSubscribe<Server>() {
            @Override
            public void call(Subscriber<? super Server> next) {
                try {
                    Server server = loadBalancerContext.getServerFromLoadBalancer(loadBalancerURI, loadBalancerKey);   
                    next.onNext(server);
                    next.onCompleted();
                } catch (Exception e) {
                    next.onError(e);
                }
            }
        });
    }
    
    class ExecutionInfoContext {
        Server      server;
        int         serverAttemptCount = 0;
        int         attemptCount = 0;
        
        public void setServer(Server server) {
            this.server = server;
            this.serverAttemptCount++;
            
            this.attemptCount = 0;
        }
        
        public void incAttemptCount() {
            this.attemptCount++;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public Server getServer() {
            return server;
        }

        public int getServerAttemptCount() {
            return this.serverAttemptCount;
        }

        public ExecutionInfo toExecutionInfo() {
            return ExecutionInfo.create(server, attemptCount-1, serverAttemptCount-1);
        }

        public ExecutionInfo toFinalExecutionInfo() {
            return ExecutionInfo.create(server, attemptCount, serverAttemptCount-1);
        }

    }
 // same：是否是同一台Server
 	// true：在当前这台Server上还能否重试（受MaxAutoRetries控制）
 	// false：换一台Server还能否重试（受MaxAutoRetriesNextServer控制）
//    若异常类型是AbortExecutionException类型，那啥都不说了，不要再重试了 
//    AbortExecutionException异常类型 是ribbon自定义的类型，在ExecutionListener监听器执行时可能会抛出
//    so，可以通过监听器的方式，认为的控制、干预目标方法的执行~
//    若当前重试总此处已经超过了最大次数，那还有什么好说的呢，拒绝再次重试呗
//    若1,2都不满足，那就交给retryHandler去判断，让它来决定你的这个异常类型是否应该重试吧 
    private Func2<Integer, Throwable, Boolean> retryPolicy(final int maxRetrys, final boolean same) {
        return new Func2<Integer, Throwable, Boolean>() {
            @Override
            public Boolean call(Integer tryCount, Throwable e) {
                if (e instanceof AbortExecutionException) {
                    return false;
                }

                if (tryCount > maxRetrys) {
                    return false;
                }
                
                if (e.getCause() != null && e instanceof RuntimeException) {
                    e = e.getCause();
                }
                
                return retryHandler.isRetriableException(e, same);
            }
        };
    }

    /**maxRetrysNext它控制的是请求级别的（切换不同Server）的重试次数。本步骤属于最外层逻辑：

选择一台Server执行请求，若抛错了，进入重试策略
若配置了maxRetrysNext请求级别的重试（默认值是1），就继续重复再找一台Server（至于是哪台就看LB策略喽），再试一次Server执行请求
只要在重试范畴内，任何一次成功了，就把执行的结果返回。否则（重试范围内都没成功）就抛出对应的异常错误~
本步骤属于最外层控制，但其实它还有针对同一Server更精细化的重试策略，这就是下面这个步骤所完成的内容。
     * Create an {@link Observable} that once subscribed execute network call asynchronously with a server chosen by load balancer.
     * If there are any errors that are indicated as retriable by the {@link RetryHandler}, they will be consumed internally by the
     * function and will not be observed by the {@link Observer} subscribed to the returned {@link Observable}. If number of retries has
     * exceeds the maximal allowed, a final error will be emitted by the returned {@link Observable}. Otherwise, the first successful
     * result during execution and retries will be emitted.
     */
    public Observable<T> submit(final ServerOperation<T> operation) {
    	// 每次执行开始，就创建一个执行info的上下文，用于记录有用信息
        final ExecutionInfoContext context = new ExecutionInfoContext();
        
        if (listenerInvoker != null) {
            try {
                listenerInvoker.onExecutionStart();
            } catch (AbortExecutionException e) {
                return Observable.error(e);
            }
        }
     // 这两个参数对重试策略非常重要，默认
     		// MaxAutoRetries：0  在单台机器上不重试
     		// MaxAutoRetriesNextServer：1 最大向下试一一台机器
        final int maxRetrysSame = retryHandler.getMaxRetriesOnSameServer();
        final int maxRetrysNext = retryHandler.getMaxRetriesOnNextServer();

        // Use the load balancer
     // 若你指定了server就用指定的，否则通过lb去根据负载均衡策略选择一台Server出来
        Observable<T> o = 
                (server == null ? selectServer() : Observable.just(server))
             // 针对选出来的实例（同一台），执行concatMap里面的操作（Server级别重试）
                .concatMap(new Func1<Server, Observable<T>>() {
                    @Override
                    // Called for each server being selected
                    public Observable<T> call(Server server) {
                    	// 记录下当前Server到上下文
                        context.setServer(server);
                     // 拿到此Server所属的状态stats
                        final ServerStats stats = loadBalancerContext.getServerStats(server);
                        
                        // Called for each attempt and retry
                        Observable<T> o = Observable
                                .just(server)
                                .concatMap(new Func1<Server, Observable<T>>() {
                                    @Override
                                    public Observable<T> call(final Server server) {
                                        context.incAttemptCount();
                                        loadBalancerContext.noteOpenConnection(stats);
                                        
                                        if (listenerInvoker != null) {
                                            try {
                                                listenerInvoker.onStartWithServer(context.toExecutionInfo());
                                            } catch (AbortExecutionException e) {
                                                return Observable.error(e);
                                            }
                                        }
                                        
                                        final Stopwatch tracer = loadBalancerContext.getExecuteTracer().start();
                                     // call执行目标方法：也就是发送execute发送请求
                        				// call执行目标方法：也就是发送execute发送请求
                        				// call执行目标方法：也就是发送execute发送请求
                        				// doOnEach：Observable每发射一个数据的时候就会触发这个回调，不仅包括onNext还包括onError和onCompleted
                        				// 这里的doOnEach主要是为了触发监听器行为
                                        return operation.call(server).doOnEach(new Observer<T>() {
                                            private T entity;
                                            @Override
                                            public void onCompleted() {
                                                recordStats(tracer, stats, entity, null);
                                                // TODO: What to do if onNext or onError are never called?
                                            }

                                            @Override
                                            public void onError(Throwable e) {
                                                recordStats(tracer, stats, null, e);
                                                logger.debug("Got error {} when executed on server {}", e, server);
                                                if (listenerInvoker != null) {
                                                    listenerInvoker.onExceptionWithServer(e, context.toExecutionInfo());
                                                }
                                            }

                                            @Override
                                            public void onNext(T entity) {
                                                this.entity = entity;
                                                if (listenerInvoker != null) {
                                                    listenerInvoker.onExecutionSuccess(entity, context.toExecutionInfo());
                                                }
                                            }                            
                                            
                                            private void recordStats(Stopwatch tracer, ServerStats stats, Object entity, Throwable exception) {
                                                tracer.stop();
                                                loadBalancerContext.noteRequestCompletion(stats, entity, exception, tracer.getDuration(TimeUnit.MILLISECONDS), retryHandler);
                                            }
                                        });
                                    }
                                });
                     // 绑定针对同一Server实例的重试策略，所以第二参数传true表示在同一实例上
            			// 注意：这里使用的是oo，是内层重试逻辑
                        if (maxRetrysSame > 0) 
                            o = o.retry(retryPolicy(maxRetrysSame, true));
                        return o;
                    }
                });
     // 内部决定每台Server去重试多少次  所以这里控制的去重试多少台Server
     		// 说明：第一台Server也不计入在内
        if (maxRetrysNext > 0 && server == null) 
            o = o.retry(retryPolicy(maxRetrysNext, false));
    	// 当最终重试都还不行时，仍旧还抛错，就会触发此函数
        return o.onErrorResumeNext(new Func1<Throwable, Observable<T>>() {
            @Override
            public Observable<T> call(Throwable e) {
            	// 执行过（并不能说重试过）
    			// 只要执行过，就得看看是啥异常呢，到底是重试不够还是咋滴
                if (context.getAttemptCount() > 0) {
                	// 重试的机器数超过了maxRetrysNext的值时，抛出此异常
                    if (maxRetrysNext > 0 && context.getServerAttemptCount() == (maxRetrysNext + 1)) {
                        e = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_NEXTSERVER_EXCEEDED,
                                "Number of retries on next server exceeded max " + maxRetrysNext
                                + " retries, while making a call for: " + context.getServer(), e);
                    }
                	// 可能maxRetrysNext=0，由单台机器重试次数的异常
                    else if (maxRetrysSame > 0 && context.getAttemptCount() == (maxRetrysSame + 1)) {
                        e = new ClientException(ClientException.ErrorType.NUMBEROF_RETRIES_EXEEDED,
                                "Number of retries exceeded max " + maxRetrysSame
                                + " retries, while making a call for: " + context.getServer(), e);
                    }
                }
                if (listenerInvoker != null) {
                    listenerInvoker.onExecutionFailed(e, context.toFinalExecutionInfo());
                }
                return Observable.error(e);
            }
        });
    }
}
