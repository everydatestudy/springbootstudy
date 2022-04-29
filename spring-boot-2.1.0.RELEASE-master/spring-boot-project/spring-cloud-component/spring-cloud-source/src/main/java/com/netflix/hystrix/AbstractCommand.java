/**
 * Copyright 2013 Netflix, Inc.
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
 */
package com.netflix.hystrix;

import com.netflix.hystrix.HystrixCircuitBreaker.NoOpCircuitBreaker;
import com.netflix.hystrix.HystrixCommandProperties.ExecutionIsolationStrategy;
import com.netflix.hystrix.exception.ExceptionNotWrappedByHystrix;
import com.netflix.hystrix.exception.HystrixBadRequestException;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.exception.HystrixRuntimeException.FailureType;
import com.netflix.hystrix.exception.HystrixTimeoutException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixContextRunnable;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestContext;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import com.netflix.hystrix.strategy.executionhook.HystrixCommandExecutionHook;
import com.netflix.hystrix.strategy.metrics.HystrixMetricsPublisherFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesFactory;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesStrategy;
import com.netflix.hystrix.strategy.properties.HystrixProperty;
import com.netflix.hystrix.util.HystrixTimer;
import com.netflix.hystrix.util.HystrixTimer.TimerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;
import rx.subscriptions.CompositeSubscription;

import java.lang.ref.Reference;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/* package */abstract class AbstractCommand<R> implements HystrixInvokableInfo<R>, HystrixObservable<R> {
	private static final Logger logger = LoggerFactory.getLogger(AbstractCommand.class);
	// 熔断器。若你使用配置显示enable=false了
	// 其实现就是NoOpCircuitBreaker，否则就是默认实现
	protected final HystrixCircuitBreaker circuitBreaker;
	// 线程池。默认实现是HystrixThreadPoolDefault
	// 线程池参数使用HystrixThreadPoolProperties配置
	// 通过HystrixConcurrencyStrategy#getThreadPool()得到ThreadPoolExecutor执行器
	// 说明：每次getThreadPool()一下都会用最新的配置配置执行器。所以可以达到动态化的目的
	// 比如说CorePoolSize、MaximumPoolSize等等都可以在properties里动态改变
	protected final HystrixThreadPool threadPool;
	// 线程池分组名（理论上不同的Command可以共用一个线程池，节约资源嘛）
	protected final HystrixThreadPoolKey threadPoolKey;
	protected final HystrixCommandProperties properties;

	protected enum TimedOutStatus {
		NOT_EXECUTED, COMPLETED, TIMED_OUT
	}

	protected enum CommandState {
		NOT_STARTED, OBSERVABLE_CHAIN_CREATED, USER_CODE_EXECUTED, UNSUBSCRIBED, TERMINAL
	}

	protected enum ThreadState {
		NOT_USING_THREAD, STARTED, UNSUBSCRIBED, TERMINAL
	}

	protected final HystrixCommandMetrics metrics;
	// command的id
	protected final HystrixCommandKey commandKey;
	protected final HystrixCommandGroupKey commandGroup;

	/**
	 * Plugin implementations
	 */
	protected final HystrixEventNotifier eventNotifier;
	protected final HystrixConcurrencyStrategy concurrencyStrategy;
	protected final HystrixCommandExecutionHook executionHook;

	/* FALLBACK Semaphore */
	// 执行时候时使用的信号量（每个key一个信号量控制哦）
	// 说明：它并没有使用JDK的java.util.concurrent.Semaphore，而是自己的实现
	// 信号量的实现非常简单，所以就略喽。
	// 当没开启信号量隔离的时候，该实现类使用的是TryableSemaphoreNoOp
	protected final TryableSemaphore fallbackSemaphoreOverride;
	/* each circuit has a semaphore to restrict concurrent fallback execution */
	protected static final ConcurrentHashMap<String, TryableSemaphore> fallbackSemaphorePerCircuit = new ConcurrentHashMap<String, TryableSemaphore>();
	/* END FALLBACK Semaphore */

	/* EXECUTION Semaphore */
	// 发生fallabck时的信号量，也是每个key一个。至于fallback都需要信号量隔离，前面有详细说明
	// 它哥俩均只有在隔离策略是SEMAPHORE才有效。它哥俩信号量默认值都是10
	protected final TryableSemaphore executionSemaphoreOverride;
	/* each circuit has a semaphore to restrict concurrent fallback execution */
	protected static final ConcurrentHashMap<String, TryableSemaphore> executionSemaphorePerCircuit = new ConcurrentHashMap<String, TryableSemaphore>();
	/* END EXECUTION Semaphore */

	protected final AtomicReference<Reference<TimerListener>> timeoutTimer = new AtomicReference<Reference<TimerListener>>();

	protected AtomicReference<CommandState> commandState = new AtomicReference<CommandState>(CommandState.NOT_STARTED);
	protected AtomicReference<ThreadState> threadState = new AtomicReference<ThreadState>(ThreadState.NOT_USING_THREAD);

	/*
	 * {@link ExecutionResult} refers to what happened as the user-provided code
	 * ran. If request-caching is used, then multiple command instances will have a
	 * reference to the same {@link ExecutionResult}. So all values there should be
	 * the same, even in the presence of request-caching.
	 *
	 * If some values are not properly shareable, then they belong on the command
	 * instance, so they are not visible to other commands.
	 *
	 * Examples: RESPONSE_FROM_CACHE, CANCELLED HystrixEventTypes
	 */
	protected volatile ExecutionResult executionResult = ExecutionResult.EMPTY; // state on shared execution

	protected volatile boolean isResponseFromCache = false;
	protected volatile ExecutionResult executionResultAtTimeOfCancellation;
	protected volatile long commandStartTimestamp = -1L;

	/* If this command executed and timed-out */
	protected final AtomicReference<TimedOutStatus> isCommandTimedOut = new AtomicReference<TimedOutStatus>(
			TimedOutStatus.NOT_EXECUTED);
	protected volatile Action0 endCurrentThreadExecutingCommand;

	/**
	 * Instance of RequestCache logic
	 */
	protected final HystrixRequestCache requestCache;
	protected final HystrixRequestLog currentRequestLog;

	// this is a micro-optimization but saves about 1-2microseconds (on 2011 MacBook
	// Pro)
	// on the repetitive string processing that will occur on the same classes over
	// and over again
	private static ConcurrentHashMap<Class<?>, String> defaultNameCache = new ConcurrentHashMap<Class<?>, String>();

	protected static ConcurrentHashMap<HystrixCommandKey, Boolean> commandContainsFallback = new ConcurrentHashMap<HystrixCommandKey, Boolean>();

	/* package */static String getDefaultNameFromClass(Class<?> cls) {
		String fromCache = defaultNameCache.get(cls);
		if (fromCache != null) {
			return fromCache;
		}
		// generate the default
		// default HystrixCommandKey to use if the method is not overridden
		String name = cls.getSimpleName();
		if (name.equals("")) {
			// we don't have a SimpleName (anonymous inner class) so use the full class name
			name = cls.getName();
			name = name.substring(name.lastIndexOf('.') + 1, name.length());
		}
		defaultNameCache.put(cls, name);
		return name;
	}

	protected AbstractCommand(HystrixCommandGroupKey group, HystrixCommandKey key, HystrixThreadPoolKey threadPoolKey,
			HystrixCircuitBreaker circuitBreaker, HystrixThreadPool threadPool,
			HystrixCommandProperties.Setter commandPropertiesDefaults,
			HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults, HystrixCommandMetrics metrics,
			TryableSemaphore fallbackSemaphore, TryableSemaphore executionSemaphore,
			HystrixPropertiesStrategy propertiesStrategy, HystrixCommandExecutionHook executionHook) {

		this.commandGroup = initGroupKey(group);
		this.commandKey = initCommandKey(key, getClass());
		this.properties = initCommandProperties(this.commandKey, propertiesStrategy, commandPropertiesDefaults);
		this.threadPoolKey = initThreadPoolKey(threadPoolKey, this.commandGroup,
				this.properties.executionIsolationThreadPoolKeyOverride().get());
		this.metrics = initMetrics(metrics, this.commandGroup, this.threadPoolKey, this.commandKey, this.properties);
		this.circuitBreaker = initCircuitBreaker(this.properties.circuitBreakerEnabled().get(), circuitBreaker,
				this.commandGroup, this.commandKey, this.properties, this.metrics);
		this.threadPool = initThreadPool(threadPool, this.threadPoolKey, threadPoolPropertiesDefaults);

		// Strategies from plugins
		this.eventNotifier = HystrixPlugins.getInstance().getEventNotifier();
		this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
		HystrixMetricsPublisherFactory.createOrRetrievePublisherForCommand(this.commandKey, this.commandGroup,
				this.metrics, this.circuitBreaker, this.properties);
		this.executionHook = initExecutionHook(executionHook);

		this.requestCache = HystrixRequestCache.getInstance(this.commandKey, this.concurrencyStrategy);
		this.currentRequestLog = initRequestLog(this.properties.requestLogEnabled().get(), this.concurrencyStrategy);

		/* fallback semaphore override if applicable */
		this.fallbackSemaphoreOverride = fallbackSemaphore;

		/* execution semaphore override if applicable */
		this.executionSemaphoreOverride = executionSemaphore;
	}

	private static HystrixCommandGroupKey initGroupKey(final HystrixCommandGroupKey fromConstructor) {
		if (fromConstructor == null) {
			throw new IllegalStateException("HystrixCommandGroup can not be NULL");
		} else {
			return fromConstructor;
		}
	}

	private static HystrixCommandKey initCommandKey(final HystrixCommandKey fromConstructor, Class<?> clazz) {
		if (fromConstructor == null || fromConstructor.name().trim().equals("")) {
			final String keyName = getDefaultNameFromClass(clazz);
			return HystrixCommandKey.Factory.asKey(keyName);
		} else {
			return fromConstructor;
		}
	}

	private static HystrixCommandProperties initCommandProperties(HystrixCommandKey commandKey,
			HystrixPropertiesStrategy propertiesStrategy, HystrixCommandProperties.Setter commandPropertiesDefaults) {
		if (propertiesStrategy == null) {
			return HystrixPropertiesFactory.getCommandProperties(commandKey, commandPropertiesDefaults);
		} else {
			// used for unit testing
			return propertiesStrategy.getCommandProperties(commandKey, commandPropertiesDefaults);
		}
	}

	/*
	 * ThreadPoolKey
	 *
	 * This defines which thread-pool this command should run on.
	 *
	 * It uses the HystrixThreadPoolKey if provided, then defaults to use
	 * HystrixCommandGroup.
	 *
	 * It can then be overridden by a property if defined so it can be changed at
	 * runtime.
	 */
	private static HystrixThreadPoolKey initThreadPoolKey(HystrixThreadPoolKey threadPoolKey,
			HystrixCommandGroupKey groupKey, String threadPoolKeyOverride) {
		if (threadPoolKeyOverride == null) {
			// we don't have a property overriding the value so use either
			// HystrixThreadPoolKey or HystrixCommandGroup
			if (threadPoolKey == null) {
				/* use HystrixCommandGroup if HystrixThreadPoolKey is null */
				return HystrixThreadPoolKey.Factory.asKey(groupKey.name());
			} else {
				return threadPoolKey;
			}
		} else {
			// we have a property defining the thread-pool so use it instead
			return HystrixThreadPoolKey.Factory.asKey(threadPoolKeyOverride);
		}
	}

	private static HystrixCommandMetrics initMetrics(HystrixCommandMetrics fromConstructor,
			HystrixCommandGroupKey groupKey, HystrixThreadPoolKey threadPoolKey, HystrixCommandKey commandKey,
			HystrixCommandProperties properties) {
		if (fromConstructor == null) {
			return HystrixCommandMetrics.getInstance(commandKey, groupKey, threadPoolKey, properties);
		} else {
			return fromConstructor;
		}
	}

	private static HystrixCircuitBreaker initCircuitBreaker(boolean enabled, HystrixCircuitBreaker fromConstructor,
			HystrixCommandGroupKey groupKey, HystrixCommandKey commandKey, HystrixCommandProperties properties,
			HystrixCommandMetrics metrics) {
		if (enabled) {
			if (fromConstructor == null) {
				// get the default implementation of HystrixCircuitBreaker
				return HystrixCircuitBreaker.Factory.getInstance(commandKey, groupKey, properties, metrics);
			} else {
				return fromConstructor;
			}
		} else {
			return new NoOpCircuitBreaker();
		}
	}

	private static HystrixCommandExecutionHook initExecutionHook(HystrixCommandExecutionHook fromConstructor) {
		if (fromConstructor == null) {
			return new ExecutionHookDeprecationWrapper(HystrixPlugins.getInstance().getCommandExecutionHook());
		} else {
			// used for unit testing
			if (fromConstructor instanceof ExecutionHookDeprecationWrapper) {
				return fromConstructor;
			} else {
				return new ExecutionHookDeprecationWrapper(fromConstructor);
			}
		}
	}

	private static HystrixThreadPool initThreadPool(HystrixThreadPool fromConstructor,
			HystrixThreadPoolKey threadPoolKey, HystrixThreadPoolProperties.Setter threadPoolPropertiesDefaults) {
		if (fromConstructor == null) {
			// get the default implementation of HystrixThreadPool
			return HystrixThreadPool.Factory.getInstance(threadPoolKey, threadPoolPropertiesDefaults);
		} else {
			return fromConstructor;
		}
	}

	private static HystrixRequestLog initRequestLog(boolean enabled, HystrixConcurrencyStrategy concurrencyStrategy) {
		if (enabled) {
			/* store reference to request log regardless of which thread later hits it */
			return HystrixRequestLog.getCurrentRequest(concurrencyStrategy);
		} else {
			return null;
		}
	}

	/**
	 * Allow the Collapser to mark this command instance as being used for a
	 * collapsed request and how many requests were collapsed.
	 * 
	 * @param sizeOfBatch number of commands in request batch
	 */
	/* package */void markAsCollapsedCommand(HystrixCollapserKey collapserKey, int sizeOfBatch) {
		eventNotifier.markEvent(HystrixEventType.COLLAPSED, this.commandKey);
		executionResult = executionResult.markCollapsed(collapserKey, sizeOfBatch);
	}

	/**
	 * Used for asynchronous execution of command with a callback by subscribing to
	 * the {@link Observable}.
	 * <p>
	 * This eagerly starts execution of the command the same as
	 * {@link HystrixCommand#queue()} and {@link HystrixCommand#execute()}.
	 * <p>
	 * A lazy {@link Observable} can be obtained from {@link #toObservable()}.
	 * <p>
	 * See https://github.com/Netflix/RxJava/wiki for more information.
	 * 
	 * @return {@code Observable<R>} that executes and calls back with the result of
	 *         command execution or a fallback if the command fails for any reason.
	 * @throws HystrixRuntimeException    if a fallback does not exist
	 *                                    <p>
	 *                                    <ul>
	 *                                    <li>via {@code Observer#onError} if a
	 *                                    failure occurs</li>
	 *                                    <li>or immediately if the command can not
	 *                                    be queued (such as short-circuited,
	 *                                    thread-pool/semaphore rejected)</li>
	 *                                    </ul>
	 * @throws HystrixBadRequestException via {@code Observer#onError} if invalid
	 *                                    arguments or state were used representing
	 *                                    a user failure, not a system failure
	 * @throws IllegalStateException      if invoked more than once
	 */
	public Observable<R> observe() {
		// us a ReplaySubject to buffer the eagerly subscribed-to Observable
		ReplaySubject<R> subject = ReplaySubject.create();
		// eagerly kick off subscription
		final Subscription sourceSubscription = toObservable().subscribe(subject);
		// return the subject that can be subscribed to later while the execution has
		// already started
		return subject.doOnUnsubscribe(new Action0() {
			@Override
			public void call() {
				sourceSubscription.unsubscribe();
			}
		});
	}

	protected abstract Observable<R> getExecutionObservable();

	protected abstract Observable<R> getFallbackObservable();

	/**
	 * Used for asynchronous execution of command with a callback by subscribing to
	 * the {@link Observable}.
	 * <p>
	 * This lazily starts execution of the command once the {@link Observable} is
	 * subscribed to.
	 * <p>
	 * An eager {@link Observable} can be obtained from {@link #observe()}.
	 * <p>
	 * See https://github.com/ReactiveX/RxJava/wiki for more information.
	 * 
	 * @return {@code Observable<R>} that executes and calls back with the result of
	 *         command execution or a fallback if the command fails for any reason.
	 * @throws HystrixRuntimeException    if a fallback does not exist
	 *                                    <p>
	 *                                    <ul>
	 *                                    <li>via {@code Observer#onError} if a
	 *                                    failure occurs</li>
	 *                                    <li>or immediately if the command can not
	 *                                    be queued (such as short-circuited,
	 *                                    thread-pool/semaphore rejected)</li>
	 *                                    </ul>
	 * @throws HystrixBadRequestException via {@code Observer#onError} if invalid
	 *                                    arguments or state were used representing
	 *                                    a user failure, not a system failure
	 * @throws IllegalStateException      if invoked more than once
	 */
//	执行步骤文字描述
//	判断线程状态是否是NOT_STARTED，否则抛出HystrixRuntimeException异常：一个命令只能执行一次
//	命令开始，使用HystrixRequestLog记录该命令的执行（显示配置requestLogEnabled = false可关闭日志的记录）
//	若开启了请求缓存，那就先从缓存里找结果（不会执行目标方法） 
//	缓存开启的条件是：requestCacheEnabled = true且且且getCacheKey() != null。所以你若想要请求缓存有效，请重写此方法并不要返回null
//	没开启缓存（缓存没命中），则需要执行目标命令获得结果， 
//	Observable.defer()保证了目标方法此时并不会被执行，而是订阅时才异步执行（交给调用者决定嘛）
//	applyHystrixSemantics()方法为执行目标方法最最最核心逻辑，后有详解
//	若开启了缓存，把结果放进缓存里
//	返回结果。并且注册上相关清理动作： 
//	terminateCommandCleanup：把线程状态标记为TERMINAL。分为两种情况： 
//	目标代码没有被执行（比如从缓存里拿的结果）：清空定时监听、记录执行耗时、HystrixCommandMetrics#markCommandDone()，触发执行完成后的函数回调（若endCurrentThreadExecutingCommand不为null的话）
//	目标代码执行了。逻辑完全同上，只是markCommandDone(true)此处传true而已
//	unsubscribeCommandCleanup：把线程状态标记为UNSUBSCRIBED。触发executionHook.onUnsubscribe等动作，并且，并且重复和上步骤一模一样的动作
//	fireOnCompletedHook： 仅触发动作executionHook.onSuccess(_cmd)
//	这里主要是套了一层缓存，以及清理相关动作。但其实最为核心的还是在applyHystrixSemantics()这个函数里，它才是真正的关键。
	public Observable<R> toObservable() {
		final AbstractCommand<R> _cmd = this;

		// doOnCompleted handler already did all of the SUCCESS work
		// doOnError handler already did all of the
		// FAILURE/TIMEOUT/REJECTION/BAD_REQUEST work
		final Action0 terminateCommandCleanup = new Action0() {

			@Override
			public void call() {
				if (_cmd.commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.TERMINAL)) {
					handleCommandEnd(false); // user code never ran
				} else if (_cmd.commandState.compareAndSet(CommandState.USER_CODE_EXECUTED, CommandState.TERMINAL)) {
					handleCommandEnd(true); // user code did run
				}
			}
		};

		// mark the command as CANCELLED and store the latency (in addition to standard
		// cleanup)
		final Action0 unsubscribeCommandCleanup = new Action0() {
			@Override
			public void call() {
				if (_cmd.commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.UNSUBSCRIBED)) {
					if (!_cmd.executionResult.containsTerminalEvent()) {
						_cmd.eventNotifier.markEvent(HystrixEventType.CANCELLED, _cmd.commandKey);
						try {
							executionHook.onUnsubscribe(_cmd);
						} catch (Throwable hookEx) {
							logger.warn("Error calling HystrixCommandExecutionHook.onUnsubscribe", hookEx);
						}
						_cmd.executionResultAtTimeOfCancellation = _cmd.executionResult.addEvent(
								(int) (System.currentTimeMillis() - _cmd.commandStartTimestamp),
								HystrixEventType.CANCELLED);
					}
					handleCommandEnd(false); // user code never ran
				} else if (_cmd.commandState.compareAndSet(CommandState.USER_CODE_EXECUTED,
						CommandState.UNSUBSCRIBED)) {
					if (!_cmd.executionResult.containsTerminalEvent()) {
						_cmd.eventNotifier.markEvent(HystrixEventType.CANCELLED, _cmd.commandKey);
						try {
							executionHook.onUnsubscribe(_cmd);
						} catch (Throwable hookEx) {
							logger.warn("Error calling HystrixCommandExecutionHook.onUnsubscribe", hookEx);
						}
						_cmd.executionResultAtTimeOfCancellation = _cmd.executionResult.addEvent(
								(int) (System.currentTimeMillis() - _cmd.commandStartTimestamp),
								HystrixEventType.CANCELLED);
					}
					handleCommandEnd(true); // user code did run
				}
			}
		};

		final Func0<Observable<R>> applyHystrixSemantics = new Func0<Observable<R>>() {
			@Override
			public Observable<R> call() {
				if (commandState.get().equals(CommandState.UNSUBSCRIBED)) {
					return Observable.never();
				}
				return applyHystrixSemantics(_cmd);
			}
		};

		final Func1<R, R> wrapWithAllOnNextHooks = new Func1<R, R>() {
			@Override
			public R call(R r) {
				R afterFirstApplication = r;

				try {
					afterFirstApplication = executionHook.onComplete(_cmd, r);
				} catch (Throwable hookEx) {
					logger.warn("Error calling HystrixCommandExecutionHook.onComplete", hookEx);
				}

				try {
					return executionHook.onEmit(_cmd, afterFirstApplication);
				} catch (Throwable hookEx) {
					logger.warn("Error calling HystrixCommandExecutionHook.onEmit", hookEx);
					return afterFirstApplication;
				}
			}
		};

		final Action0 fireOnCompletedHook = new Action0() {
			@Override
			public void call() {
				try {
					executionHook.onSuccess(_cmd);
				} catch (Throwable hookEx) {
					logger.warn("Error calling HystrixCommandExecutionHook.onSuccess", hookEx);
				}
			}
		};
		// 需要注意的是：这里使用的是defer()，所以里面内容是不会立马执行的，知道有订阅者订阅了就执行
		// 也就是说observable = command.toObservable()是不会执行
		// observable.subscribe(xxx)订阅后，才会开始执行
		return Observable.defer(new Func0<Observable<R>>() {
			@Override
			public Observable<R> call() {
				/* this is a stateful object so can only be used once */
				if (!commandState.compareAndSet(CommandState.NOT_STARTED, CommandState.OBSERVABLE_CHAIN_CREATED)) {
					IllegalStateException ex = new IllegalStateException(
							"This instance can only be executed once. Please instantiate a new instance.");
					// TODO make a new error type for this
					throw new HystrixRuntimeException(FailureType.BAD_REQUEST_EXCEPTION, _cmd.getClass(),
							getLogMessagePrefix() + " command executed multiple times - this is not permitted.", ex,
							null);
				}
				// 检验线程状态CommandState，每个command只能被执行一次，否则额抛出HystrixRuntimeException异常
				commandStartTimestamp = System.currentTimeMillis();

				if (properties.requestLogEnabled().get()) {
					// log this command execution regardless of what happened
					if (currentRequestLog != null) {
						currentRequestLog.addExecutedCommand(_cmd);
					}
				}

				final boolean requestCacheEnabled = isRequestCachingEnabled();
				final String cacheKey = getCacheKey();

				/* try from cache first */
				if (requestCacheEnabled) {
					HystrixCommandResponseFromCache<R> fromCache = (HystrixCommandResponseFromCache<R>) requestCache
							.get(cacheKey);
					if (fromCache != null) {
						isResponseFromCache = true;
						return handleRequestCacheHitAndEmitValues(fromCache, _cmd);
					}
				}

				Observable<R> hystrixObservable = Observable.defer(applyHystrixSemantics).map(wrapWithAllOnNextHooks);

				Observable<R> afterCache;

				// put in cache
				if (requestCacheEnabled && cacheKey != null) {
					// wrap it for caching
					HystrixCachedObservable<R> toCache = HystrixCachedObservable.from(hystrixObservable, _cmd);
					HystrixCommandResponseFromCache<R> fromCache = (HystrixCommandResponseFromCache<R>) requestCache
							.putIfAbsent(cacheKey, toCache);
					if (fromCache != null) {
						// another thread beat us so we'll use the cached value instead
						toCache.unsubscribe();
						isResponseFromCache = true;
						return handleRequestCacheHitAndEmitValues(fromCache, _cmd);
					} else {
						// we just created an ObservableCommand so we cast and return it
						afterCache = toCache.toObservable();
					}
				} else {
					afterCache = hystrixObservable;
				}

				return afterCache.doOnTerminate(terminateCommandCleanup) // perform cleanup once (either on normal
																			// terminal state (this line), or
																			// unsubscribe (next line))
						.doOnUnsubscribe(unsubscribeCommandCleanup) // perform cleanup once
						.doOnCompleted(fireOnCompletedHook);
			}
		});
	}

	private Observable<R> applyHystrixSemantics(final AbstractCommand<R> _cmd) {
		// mark that we're starting execution on the ExecutionHook
		// if this hook throws an exception, then a fast-fail occurs with no fallback.
		// No state is left inconsistent
		executionHook.onStart(_cmd);

		/* determine if we're allowed to execute */
		// 若断路器放行（断路器闭合状态，当然喽也可能是半开状态）
		if (circuitBreaker.allowRequest()) {
			// 若你实现的是线程池隔离，那么此处实现就是TryableSemaphoreNoOp
			// 若你使用信号量隔离，它就会生效啦
			final TryableSemaphore executionSemaphore = getExecutionSemaphore();
			final AtomicBoolean semaphoreHasBeenReleased = new AtomicBoolean(false);
			final Action0 singleSemaphoreRelease = new Action0() {
				@Override
				public void call() {
					if (semaphoreHasBeenReleased.compareAndSet(false, true)) {
						executionSemaphore.release();
					}
				}
			};

			final Action1<Throwable> markExceptionThrown = new Action1<Throwable>() {
				@Override
				public void call(Throwable t) {
					eventNotifier.markEvent(HystrixEventType.EXCEPTION_THROWN, commandKey);
				}
			};
			// 信号量的控制
			if (executionSemaphore.tryAcquire()) {
				try {
					/* used to track userThreadExecutionTime */
					executionResult = executionResult.setInvocationStartTime(System.currentTimeMillis());
					// 如果都成功的话会执行executeCommandAndObserve
					return executeCommandAndObserve(_cmd).doOnError(markExceptionThrown)
							.doOnTerminate(singleSemaphoreRelease).doOnUnsubscribe(singleSemaphoreRelease);
				} catch (RuntimeException e) {
					return Observable.error(e);
				}
			} else {
				return handleSemaphoreRejectionViaFallback();
			}
		} else {
			return handleShortCircuitViaFallback();
		}
	}

	protected boolean commandIsScalar() {
		return false;
	}

	/**
	 * This decorates "Hystrix" functionality around the run() Observable.
	 *
	 * @return R
	 */
	private Observable<R> executeCommandAndObserve(final AbstractCommand<R> _cmd) {
		// 执行上下文。保证线程池内亦能获取到主线程里的参数
		final HystrixRequestContext currentRequestContext = HystrixRequestContext.getContextForCurrentThread();

		final Action1<R> markEmits = new Action1<R>() {
			@Override
			public void call(R r) {
				// 是否应该在onNext这步报告数据
				// HystrixCommand -> false
				// HystrixObservableCommand -> true
				if (shouldOutputOnNextEvents()) {
					executionResult = executionResult.addEvent(HystrixEventType.EMIT);
					eventNotifier.markEvent(HystrixEventType.EMIT, commandKey);
				}
				if (commandIsScalar()) {
					long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
					eventNotifier.markCommandExecution(getCommandKey(), properties.executionIsolationStrategy().get(),
							(int) latency, executionResult.getOrderedList());
					eventNotifier.markEvent(HystrixEventType.SUCCESS, commandKey);
					executionResult = executionResult.addEvent((int) latency, HystrixEventType.SUCCESS);
					// 当半开半闭状态下，如果这次请求成功而了，则把熔断器设为false,且让统计指标reset
					circuitBreaker.markSuccess();
				}
			}
		};

		final Action0 markOnCompleted = new Action0() {
			@Override
			public void call() {
				if (!commandIsScalar()) {
					long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
					eventNotifier.markCommandExecution(getCommandKey(), properties.executionIsolationStrategy().get(),
							(int) latency, executionResult.getOrderedList());
					eventNotifier.markEvent(HystrixEventType.SUCCESS, commandKey);
					executionResult = executionResult.addEvent((int) latency, HystrixEventType.SUCCESS);
					circuitBreaker.markSuccess();
				}
			}
		};

		final Func1<Throwable, Observable<R>> handleFallback = new Func1<Throwable, Observable<R>>() {
			@Override
			public Observable<R> call(Throwable t) {
				// 把Throwable t强转为Exception e（若不是Exception类型就包装为Exception类型）
				// 比如若t是NPE异常，那么t和e是完全一样的。
				// 只有当t是error类时，t才和e不相等
				Exception e = getExceptionFromThrowable(t);
				executionResult = executionResult.setExecutionException(e);
				// 若异常类型是RejectedExecutionException：线程池拒绝
				// 若异常类型是HystrixTimeoutException：目标方法执行超时
				// 若异常类型是HystrixBadRequestException：下文详细分解
				if (e instanceof RejectedExecutionException) {
					return handleThreadPoolRejectionViaFallback(e);
				} else if (t instanceof HystrixTimeoutException) {
					return handleTimeoutViaFallback();
				} else if (t instanceof HystrixBadRequestException) {
					return handleBadRequestByEmittingError(e);
				} else {
					/*
					 * Treat HystrixBadRequestException from ExecutionHook like a plain
					 * HystrixBadRequestException.
					 */
					// 什么时候会进入到这里？只有当子类复写了getExceptionFromThrowable()方法的时候才有可能进入到这里
					// 这里算是一种兜底：保证不管咋样HystrixBadRequestException都不会触发熔断
					// 其实我倒觉得，不让getExceptionFromThrowable这个方法被复写也行的
					if (e instanceof HystrixBadRequestException) {
						eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, commandKey);
						return Observable.error(e);
					}

					return handleFailureViaFallback(e);
				}
			}
		};

		final Action1<Notification<? super R>> setRequestContext = new Action1<Notification<? super R>>() {
			@Override
			public void call(Notification<? super R> rNotification) {
				setRequestContextIfNeeded(currentRequestContext);
			}
		};

		Observable<R> execution;
		// 二者的唯一却别是：若开启了超时支持的话，就只可观察对象的结果处
		// lift一个HystrixObservableTimeoutOperator实例，以监控超时情况
		// 关于Hystrix是如何实现超时的，这在后面专文讲解~~是个较大的，也较难的话题
		if (properties.executionTimeoutEnabled().get()) {
			execution = executeCommandWithSpecifiedIsolation(_cmd).lift(new HystrixObservableTimeoutOperator<R>(_cmd));
		} else {
			execution = executeCommandWithSpecifiedIsolation(_cmd);
		}

		return execution.doOnNext(markEmits).doOnCompleted(markOnCompleted).onErrorResumeNext(handleFallback)
				.doOnEach(setRequestContext);
	}

	private Observable<R> executeCommandWithSpecifiedIsolation(final AbstractCommand<R> _cmd) {
		// 标记我们正在一个线程中执行(即使我们最终被拒绝
		// 我们仍然是一个线程执行，而不是信号量)
		if (properties.executionIsolationStrategy().get() == ExecutionIsolationStrategy.THREAD) {
			// mark that we are executing in a thread (even if we end up being rejected we
			// still were a THREAD execution and not SEMAPHORE)
			// 默认是一个defer延迟执行的可观察对象
			// 注意：进到这个回调里面来后，就是使用的线程池的资源去执行了（获取到了线程池资源）
			// 比如此处线程号就是：hystrix-fallbackDemoGroup-1
			return Observable.defer(new Func0<Observable<R>>() {
				@Override
				public Observable<R> call() {
					// 记录：目标方法已经执行（不管出异常与否，反正就是执行了）
					// 应为如果被熔断了，或者线程池拒绝了它是不会被执行的
					executionResult = executionResult.setExecutionOccurred();
					// 线程状态必须是OBSERVABLE_CHAIN_CREATED时才让执行
					// 而此状态是由toObservable()方法设置过来的
					if (!commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED,
							CommandState.USER_CODE_EXECUTED)) {
						return Observable.error(new IllegalStateException(
								"execution attempted while in state : " + commandState.get().name()));
					}
					// 收集指标信息：开始执行
					metrics.markCommandStart(commandKey, threadPoolKey, ExecutionIsolationStrategy.THREAD);
					// 这个判断非常的有意思：如果在run方法还没执行之前
					// 也就是在线程切换之间就超时了，那就直接返回一个错误
					if (isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT) {
						// the command timed out in the wrapping thread so we will return immediately
						// and not increment any of the counters below or other such logic
						return Observable.error(new RuntimeException("timed out before executing run()"));
					} // 把执行的线程状态标记为STARTED：启动
					if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.STARTED)) {
						// we have not been unsubscribed, so should proceed
						// 全局计数器+1
						// 此处是唯一调用处。信号量里是木有此调用的哦
						HystrixCounters.incrementGlobalConcurrentThreads();
						threadPool.markThreadExecution();
						// store the command that is being run
						// 这个保存使用的ThreadLocal<ConcurrentStack<HystrixCommandKey>>和当前线程绑定
						// 这样确保了命令在执行时的线程安全~~~~~~~
						endCurrentThreadExecutingCommand = Hystrix.startCurrentThreadExecutingCommand(getCommandKey());
						executionResult = executionResult.setExecutedInThread();
						/**
						 * If any of these hooks throw an exception, then it appears as if the actual
						 * execution threw an error
						 */
						// 执行钩子程序，以及执行目标run方法程序
						// getUserExecutionObservable：getExecutionObservable()抽象方法获取到目标方法
						// 本处也是该抽象方法的唯一调用处哦
						// 若这里面任何一个方法抛出异常（哪怕是hook方法），就原样抛出
						try {
							executionHook.onThreadStart(_cmd);
							executionHook.onRunStart(_cmd);
							executionHook.onExecutionStart(_cmd);
							return getUserExecutionObservable(_cmd);
						} catch (Throwable ex) {
							return Observable.error(ex);
						}
					} else {
						// command has already been unsubscribed, so return immediately
						return Observable.error(new RuntimeException("unsubscribed before executing run()"));
					}
				}
			}).doOnTerminate(new Action0() {
				@Override
				public void call() {
					if (threadState.compareAndSet(ThreadState.STARTED, ThreadState.TERMINAL)) {
						handleThreadEnd(_cmd);
					}
					if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.TERMINAL)) {
						// if it was never started and received terminal, then no need to clean up (I
						// don't think this is possible)
					}
					// if it was unsubscribed, then other cleanup handled it
				}
			}).doOnUnsubscribe(new Action0() {
				@Override
				public void call() {
					if (threadState.compareAndSet(ThreadState.STARTED, ThreadState.UNSUBSCRIBED)) {
						handleThreadEnd(_cmd);
					}
					if (threadState.compareAndSet(ThreadState.NOT_USING_THREAD, ThreadState.UNSUBSCRIBED)) {
						// if it was never started and was cancelled, then no need to clean up
					}
					// if it was terminal, then other cleanup handled it
				}
			}).subscribeOn(threadPool.getScheduler(new Func0<Boolean>() {
				@Override
				public Boolean call() {
					return properties.executionIsolationThreadInterruptOnTimeout().get()
							&& _cmd.isCommandTimedOut.get() == TimedOutStatus.TIMED_OUT;
				}
			}));
		} else {
			return Observable.defer(new Func0<Observable<R>>() {
				@Override
				public Observable<R> call() {
					executionResult = executionResult.setExecutionOccurred();
					if (!commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED,
							CommandState.USER_CODE_EXECUTED)) {
						return Observable.error(new IllegalStateException(
								"execution attempted while in state : " + commandState.get().name()));
					}

					metrics.markCommandStart(commandKey, threadPoolKey, ExecutionIsolationStrategy.SEMAPHORE);
					// semaphore isolated
					// store the command that is being run
					endCurrentThreadExecutingCommand = Hystrix.startCurrentThreadExecutingCommand(getCommandKey());
					try {
						executionHook.onRunStart(_cmd);
						executionHook.onExecutionStart(_cmd);
						return getUserExecutionObservable(_cmd); // the getUserExecutionObservable method already wraps
																	// sync exceptions, so this shouldn't throw
					} catch (Throwable ex) {
						// If the above hooks throw, then use that as the result of the run method
						return Observable.error(ex);
					}
				}
			});
		}
	}
	// _cmd：命令对象
	// eventType：事件类型
	// failureType：失败类型枚举（BAD_REQUEST_EXCEPTION...） 见下面枚举定义
	// message：失败的消息。如timed-out、failed、short-circuited等
	// Exception：导致失败的异常（一定只有异常才能导致失败），如java.util.concurrent.TimeoutException

	/**
	 * Execute <code>getFallback()</code> within protection of a semaphore that
	 * limits number of concurrent executions.
	 * <p>
	 * Fallback implementations shouldn't perform anything that can be blocking, but
	 * we protect against it anyways in case someone doesn't abide by the contract.
	 * <p>
	 * If something in the <code>getFallback()</code> implementation is latent (such
	 * as a network call) then the semaphore will cause us to start rejecting
	 * requests rather than allowing potentially all threads to pile up and block.
	 *
	 * @return K
	 * @throws UnsupportedOperationException if getFallback() not implemented
	 * @throws HystrixRuntimeException       if getFallback() fails (throws an
	 *                                       Exception) or is rejected by the
	 *                                       semaphore
	 */
//	首先需要明确：执行此fallabck步骤肯定是发生了Exception异常的（当然有可能是Error错误），所以异常类型很关键，此处用e来表示源生异常类型（如目标方法自己产生的NPE）。
//
//	若e不需要被包装，那就不用使用HystrixRuntimeException去包它了，直接返回：Observable.error(e); 
//	ExceptionNotWrappedByHystrix是个标记接口：若你的异常类型实现了此接口，那么抛出此类型的异常将不会再被 HystrixRuntimeException包起来了
//	若e是不可恢复的异常类型如：StackOverflowError/VirtualMachineError/ThreadDeath/LinkageError，那就直接包装为HystrixRuntimeException类型抛出。
//	到这一步，e的类型“过关”了，着手执行fallabck逻辑。若禁用了fallabck，就执行handleFallbackDisabledByEmittingError()方法 -> 抛出HystrixRuntimeException异常，否则继续。
//	准备执行fallabck函数，先请求fallabck专用的信号量。若无资源了，那就执行handleFallbackRejectionByEmittingError()犯法 -> 抛出HystrixRuntimeException异常，否则继续。否则继续
//	通过抽象方法getFallbackObservable()拿到被观察对象Observable<R>，然后便可开始执行目标fallabck函数了。其中执行目标fallback函数时分为成功or失败。 
//	成功： 执行doOnCompleted放，整成记录FALLBACK_SUCCESS事件到结果即可
//	失败：分为未提供fallabck函数和fallback函数内部抛出了异常两种case -> 均抛出HystrixRuntimeException异常，对应异常消息是著名的：and no fallback available.和and fallback failed.。
	private Observable<R> getFallbackOrThrowException(final AbstractCommand<R> _cmd, final HystrixEventType eventType,
			final FailureType failureType, final String message, final Exception originalException) {
		// 因为fallabck也是在线程池里面执行，所以也是需要传递数据的
		final HystrixRequestContext requestContext = HystrixRequestContext.getContextForCurrentThread();
		long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
		// record the executionResult
		// do this before executing fallback so it can be queried from within
		// getFallback (see See https://github.com/Netflix/Hystrix/pull/144)
		// 统计相关事件的次数：详见事件计数器
		executionResult = executionResult.addEvent((int) latency, eventType);

		if (shouldNotBeWrapped(originalException)) {
			/* executionHook for all errors */
			Exception e = wrapWithOnErrorHook(failureType, originalException);
			return Observable.error(e);
			// 是否是不可恢复的异常类型：比如StackOverflowError/VirtualMachineError...
			// 因为并不是所有的Error都不会恢复，所以这里例举出来。如果是不可恢复的错误，就包装一下抛出
		} else if (isUnrecoverable(originalException)) {
			logger.error(
					"Unrecoverable Error for HystrixCommand so will throw HystrixRuntimeException and not apply fallback. ",
					originalException);

			/* executionHook for all errors */
			Exception e = wrapWithOnErrorHook(failureType, originalException);
			return Observable.error(new HystrixRuntimeException(failureType, this.getClass(),
					getLogMessagePrefix() + " " + message + " and encountered unrecoverable error.", e, null));
		} else {
			// 若是可自己恢复的Error，如IOError，那就输入一句日志即可
			if (isRecoverableError(originalException)) {
				logger.warn("Recovered from java.lang.Error by serving Hystrix fallback", originalException);
			}
			// =======普通异常类型（比如NPE之类的），那就开始执行fallabck函数========
			if (properties.fallbackEnabled().get()) {
				/* fallback behavior is permitted so attempt */

				final Action1<Notification<? super R>> setRequestContext = new Action1<Notification<? super R>>() {
					@Override
					public void call(Notification<? super R> rNotification) {
						setRequestContextIfNeeded(requestContext);
					}
				};

				final Action1<R> markFallbackEmit = new Action1<R>() {
					@Override
					public void call(R r) {
						if (shouldOutputOnNextEvents()) {
							executionResult = executionResult.addEvent(HystrixEventType.FALLBACK_EMIT);
							eventNotifier.markEvent(HystrixEventType.FALLBACK_EMIT, commandKey);
						}
					}
				};

				final Action0 markFallbackCompleted = new Action0() {
					@Override
					public void call() {
						long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
						eventNotifier.markEvent(HystrixEventType.FALLBACK_SUCCESS, commandKey);
						executionResult = executionResult.addEvent((int) latency, HystrixEventType.FALLBACK_SUCCESS);
					}
				};

				final Func1<Throwable, Observable<R>> handleFallbackError = new Func1<Throwable, Observable<R>>() {
					@Override
					public Observable<R> call(Throwable t) {
						Exception e = originalException;
						Exception fe = getExceptionFromThrowable(t);

						if (fe instanceof UnsupportedOperationException) {
							long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
							logger.debug("No fallback for HystrixCommand. ", fe); // debug only since we're throwing the
																					// exception and someone higher will
																					// do something with it
							eventNotifier.markEvent(HystrixEventType.FALLBACK_MISSING, commandKey);
							executionResult = executionResult.addEvent((int) latency,
									HystrixEventType.FALLBACK_MISSING);

							/* executionHook for all errors */
							e = wrapWithOnErrorHook(failureType, e);

							return Observable.error(new HystrixRuntimeException(failureType, _cmd.getClass(),
									getLogMessagePrefix() + " " + message + " and no fallback available.", e, fe));
						} else {
							long latency = System.currentTimeMillis() - executionResult.getStartTimestamp();
							logger.debug("HystrixCommand execution " + failureType.name() + " and fallback failed.",
									fe);
							eventNotifier.markEvent(HystrixEventType.FALLBACK_FAILURE, commandKey);
							executionResult = executionResult.addEvent((int) latency,
									HystrixEventType.FALLBACK_FAILURE);

							/* executionHook for all errors */
							e = wrapWithOnErrorHook(failureType, e);

							return Observable.error(new HystrixRuntimeException(failureType, _cmd.getClass(),
									getLogMessagePrefix() + " " + message + " and fallback failed.", e, fe));
						}
					}
				};
				// 若你没指定，默认使用的是TryableSemaphoreActual 10个信号量
				// 你可以通过fallbackIsolationSemaphoreMaxConcurrentRequests指定这个值
				// 该信号量用于杜绝你的fallabck还非常耗时的case，以防万一
				final TryableSemaphore fallbackSemaphore = getFallbackSemaphore();
				final AtomicBoolean semaphoreHasBeenReleased = new AtomicBoolean(false);
				final Action0 singleSemaphoreRelease = new Action0() {
					@Override
					public void call() {
						if (semaphoreHasBeenReleased.compareAndSet(false, true)) {
							fallbackSemaphore.release();
						}
					}
				};
				// 最终return的可观察对象
				// getFallbackObservable是个抽象方法，由子类提供
				// 比如command它就是使用Observable.just(getFallback())把任意对象转换为Observable
				// 当然它是个defer的Observable
				Observable<R> fallbackExecutionChain;

				// acquire a permit
				// 若还有信号量资源就继续执行（否则会会做异常处理，见下面）
				if (fallbackSemaphore.tryAcquire()) {
					try {
						// 用户是否自定义了fallabck函数
						// 也就是是否重写了getFallabck()方法嘛
						if (isFallbackUserDefined()) {
							executionHook.onFallbackStart(this);
							// 区别是：若没有复写这个方法，就不会触发onFallbackStart()动作
							fallbackExecutionChain = getFallbackObservable();
						} else {
							// same logic as above without the hook invocation
							fallbackExecutionChain = getFallbackObservable();
						}
					} catch (Throwable ex) {
						// If hook or user-fallback throws, then use that as the result of the fallback
						// lookup
						fallbackExecutionChain = Observable.error(ex);
					}

					// 绑定固定的处理函数
					return fallbackExecutionChain
							// 作用于每个iter身上：setRequestContextIfNeeded(requestContext);
							// 确保上下文是同一个，数据是通的
							.doOnEach(setRequestContext)
							// 主要是为了触发executionHook相关回调
							.lift(new FallbackHookApplication(_cmd)).lift(new DeprecatedOnFallbackHookApplication(_cmd))
							// 发布事件：HystrixEventType.FALLBACK_EMIT
							// executionResult.addEvent(HystrixEventType.FALLBACK_EMIT)
							.doOnNext(markFallbackEmit)
							// 完成了。发布事件：HystrixEventType.FALLBACK_SUCCESS
							// 证明fallabck成功
							.doOnCompleted(markFallbackCompleted)
							// fallabck失败（出现异常了），发布事件
							// HystrixEventType.FALLBACK_MISSING、FALLBACK_FAILURE
							.onErrorResumeNext(handleFallbackError)
							// 释放信号量：fallbackSemaphore.release();
							.doOnTerminate(singleSemaphoreRelease)
							// fallbackSemaphore.release();
							.doOnUnsubscribe(singleSemaphoreRelease);
				} else {
					return handleFallbackRejectionByEmittingError();
				}
			} else {
				return handleFallbackDisabledByEmittingError(originalException, failureType, message);
			}
		}
	}

	private Observable<R> getUserExecutionObservable(final AbstractCommand<R> _cmd) {
		Observable<R> userObservable;

		try {
			userObservable = getExecutionObservable();
		} catch (Throwable ex) {
			// the run() method is a user provided implementation so can throw instead of
			// using Observable.onError
			// so we catch it here and turn it into Observable.error
			userObservable = Observable.error(ex);
		}

		return userObservable.lift(new ExecutionHookApplication(_cmd)).lift(new DeprecatedOnRunHookApplication(_cmd));
	}

	private Observable<R> handleRequestCacheHitAndEmitValues(final HystrixCommandResponseFromCache<R> fromCache,
			final AbstractCommand<R> _cmd) {
		try {
			executionHook.onCacheHit(this);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onCacheHit", hookEx);
		}

		return fromCache.toObservableWithStateCopiedInto(this).doOnTerminate(new Action0() {
			@Override
			public void call() {
				if (commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.TERMINAL)) {
					cleanUpAfterResponseFromCache(false); // user code never ran
				} else if (commandState.compareAndSet(CommandState.USER_CODE_EXECUTED, CommandState.TERMINAL)) {
					cleanUpAfterResponseFromCache(true); // user code did run
				}
			}
		}).doOnUnsubscribe(new Action0() {
			@Override
			public void call() {
				if (commandState.compareAndSet(CommandState.OBSERVABLE_CHAIN_CREATED, CommandState.UNSUBSCRIBED)) {
					cleanUpAfterResponseFromCache(false); // user code never ran
				} else if (commandState.compareAndSet(CommandState.USER_CODE_EXECUTED, CommandState.UNSUBSCRIBED)) {
					cleanUpAfterResponseFromCache(true); // user code did run
				}
			}
		});
	}

	private void cleanUpAfterResponseFromCache(boolean commandExecutionStarted) {
		Reference<TimerListener> tl = timeoutTimer.get();
		if (tl != null) {
			tl.clear();
		}

		final long latency = System.currentTimeMillis() - commandStartTimestamp;
		executionResult = executionResult.addEvent(-1, HystrixEventType.RESPONSE_FROM_CACHE)
				.markUserThreadCompletion(latency).setNotExecutedInThread();
		ExecutionResult cacheOnlyForMetrics = ExecutionResult.from(HystrixEventType.RESPONSE_FROM_CACHE)
				.markUserThreadCompletion(latency);
		metrics.markCommandDone(cacheOnlyForMetrics, commandKey, threadPoolKey, commandExecutionStarted);
		eventNotifier.markEvent(HystrixEventType.RESPONSE_FROM_CACHE, commandKey);
	}

	private void handleCommandEnd(boolean commandExecutionStarted) {
		Reference<TimerListener> tl = timeoutTimer.get();
		if (tl != null) {
			tl.clear();
		}

		long userThreadLatency = System.currentTimeMillis() - commandStartTimestamp;
		executionResult = executionResult.markUserThreadCompletion((int) userThreadLatency);
		if (executionResultAtTimeOfCancellation == null) {
			metrics.markCommandDone(executionResult, commandKey, threadPoolKey, commandExecutionStarted);
		} else {
			metrics.markCommandDone(executionResultAtTimeOfCancellation, commandKey, threadPoolKey,
					commandExecutionStarted);
		}

		if (endCurrentThreadExecutingCommand != null) {
			endCurrentThreadExecutingCommand.call();
		}
	}

	private Observable<R> handleSemaphoreRejectionViaFallback() {
		Exception semaphoreRejectionException = new RuntimeException("could not acquire a semaphore for execution");
		executionResult = executionResult.setExecutionException(semaphoreRejectionException);
		eventNotifier.markEvent(HystrixEventType.SEMAPHORE_REJECTED, commandKey);
		logger.debug("HystrixCommand Execution Rejection by Semaphore."); // debug only since we're throwing the
																			// exception and someone higher will do
																			// something with it
		// retrieve a fallback or throw an exception if no fallback available
		return getFallbackOrThrowException(this, HystrixEventType.SEMAPHORE_REJECTED,
				FailureType.REJECTED_SEMAPHORE_EXECUTION, "could not acquire a semaphore for execution",
				semaphoreRejectionException);
	}

	// 可以看到异常是它内部new出来的，然后调用
	private Observable<R> handleShortCircuitViaFallback() {
		// record that we are returning a short-circuited fallback
		eventNotifier.markEvent(HystrixEventType.SHORT_CIRCUITED, commandKey);
		// short-circuit and go directly to fallback (or throw an exception if no
		// fallback implemented)
		Exception shortCircuitException = new RuntimeException("Hystrix circuit short-circuited and is OPEN");
		// 设置结果：异常类型为RuntimeException类型
		executionResult = executionResult.setExecutionException(shortCircuitException);
		try {
			return getFallbackOrThrowException(this, HystrixEventType.SHORT_CIRCUITED, FailureType.SHORTCIRCUIT,
					"short-circuited", shortCircuitException);
		} catch (Exception e) {
			return Observable.error(e);
		}
	}

	private Observable<R> handleThreadPoolRejectionViaFallback(Exception underlying) {
		eventNotifier.markEvent(HystrixEventType.THREAD_POOL_REJECTED, commandKey);
		threadPool.markThreadRejection();
		// use a fallback instead (or throw exception if not implemented)
		return getFallbackOrThrowException(this, HystrixEventType.THREAD_POOL_REJECTED,
				FailureType.REJECTED_THREAD_EXECUTION, "could not be queued for execution", underlying);
	}

	private Observable<R> handleTimeoutViaFallback() {
		return getFallbackOrThrowException(this, HystrixEventType.TIMEOUT, FailureType.TIMEOUT, "timed-out",
				new TimeoutException());
	}

	private Observable<R> handleBadRequestByEmittingError(Exception underlying) {
		Exception toEmit = underlying;

		try {
			long executionLatency = System.currentTimeMillis() - executionResult.getStartTimestamp();
			eventNotifier.markEvent(HystrixEventType.BAD_REQUEST, commandKey);
			executionResult = executionResult.addEvent((int) executionLatency, HystrixEventType.BAD_REQUEST);
			Exception decorated = executionHook.onError(this, FailureType.BAD_REQUEST_EXCEPTION, underlying);

			if (decorated instanceof HystrixBadRequestException) {
				toEmit = decorated;
			} else {
				logger.warn(
						"ExecutionHook.onError returned an exception that was not an instance of HystrixBadRequestException so will be ignored.",
						decorated);
			}
		} catch (Exception hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onError", hookEx);
		}
		/*
		 * HystrixBadRequestException is treated differently and allowed to propagate
		 * without any stats tracking or fallback logic
		 */
		return Observable.error(toEmit);
	}

	private Observable<R> handleFailureViaFallback(Exception underlying) {
		/**
		 * All other error handling
		 */
		logger.debug("Error executing HystrixCommand.run(). Proceeding to fallback logic ...", underlying);

		// report failure
		eventNotifier.markEvent(HystrixEventType.FAILURE, commandKey);

		// record the exception
		executionResult = executionResult.setException(underlying);
		return getFallbackOrThrowException(this, HystrixEventType.FAILURE, FailureType.COMMAND_EXCEPTION, "failed",
				underlying);
	}

	private Observable<R> handleFallbackRejectionByEmittingError() {
		long latencyWithFallback = System.currentTimeMillis() - executionResult.getStartTimestamp();
		eventNotifier.markEvent(HystrixEventType.FALLBACK_REJECTION, commandKey);
		executionResult = executionResult.addEvent((int) latencyWithFallback, HystrixEventType.FALLBACK_REJECTION);
		logger.debug("HystrixCommand Fallback Rejection."); // debug only since we're throwing the exception and someone
															// higher will do something with it
		// if we couldn't acquire a permit, we "fail fast" by throwing an exception
		return Observable.error(new HystrixRuntimeException(FailureType.REJECTED_SEMAPHORE_FALLBACK, this.getClass(),
				getLogMessagePrefix() + " fallback execution rejected.", null, null));
	}

	private Observable<R> handleFallbackDisabledByEmittingError(Exception underlying, FailureType failureType,
			String message) {
		/* fallback is disabled so throw HystrixRuntimeException */
		logger.debug("Fallback disabled for HystrixCommand so will throw HystrixRuntimeException. ", underlying); // debug
																													// only
																													// since
																													// we're
																													// throwing
																													// the
																													// exception
																													// and
																													// someone
																													// higher
																													// will
																													// do
																													// something
																													// with
																													// it

		/* executionHook for all errors */
		Exception wrapped = wrapWithOnErrorHook(failureType, underlying);
		return Observable.error(new HystrixRuntimeException(failureType, this.getClass(),
				getLogMessagePrefix() + " " + message + " and fallback disabled.", wrapped, null));
	}

	protected boolean shouldNotBeWrapped(Throwable underlying) {
		return underlying instanceof ExceptionNotWrappedByHystrix;
	}

	/**
	 * Returns true iff the t was caused by a java.lang.Error that is unrecoverable.
	 * Note: not all java.lang.Errors are unrecoverable.
	 * 
	 * @see <a href="https://github.com/Netflix/Hystrix/issues/713"></a> for more
	 *      context Solution taken from
	 *      <a href="https://github.com/ReactiveX/RxJava/issues/748"></a>
	 *
	 *      The specific set of Error that are considered unrecoverable are:
	 *      <ul>
	 *      <li>{@code StackOverflowError}</li>
	 *      <li>{@code VirtualMachineError}</li>
	 *      <li>{@code ThreadDeath}</li>
	 *      <li>{@code LinkageError}</li>
	 *      </ul>
	 *
	 * @param t throwable to check
	 * @return true iff the t was caused by a java.lang.Error that is unrecoverable
	 */
	private boolean isUnrecoverable(Throwable t) {
		if (t != null && t.getCause() != null) {
			Throwable cause = t.getCause();
			if (cause instanceof StackOverflowError) {
				return true;
			} else if (cause instanceof VirtualMachineError) {
				return true;
			} else if (cause instanceof ThreadDeath) {
				return true;
			} else if (cause instanceof LinkageError) {
				return true;
			}
		}
		return false;
	}

	private boolean isRecoverableError(Throwable t) {
		if (t != null && t.getCause() != null) {
			Throwable cause = t.getCause();
			if (cause instanceof java.lang.Error) {
				return !isUnrecoverable(t);
			}
		}
		return false;
	}

	protected void handleThreadEnd(AbstractCommand<R> _cmd) {
		HystrixCounters.decrementGlobalConcurrentThreads();
		threadPool.markThreadCompletion();
		try {
			executionHook.onThreadComplete(_cmd);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onThreadComplete", hookEx);
		}
	}

	/**
	 *
	 * @return if onNext events should be reported on This affects
	 *         {@link HystrixRequestLog}, and {@link HystrixEventNotifier}
	 *         currently.
	 */
	protected boolean shouldOutputOnNextEvents() {
		return false;
	}

//	​ ObservableTimeoutOperator.call主要做了：定义了一个定时器TimerListener，里面定时的时间就是我们设置的
	// @HystrixCommand的超时的时间（体现的位置：originalCommand.properties.executionTimeoutInMilliseconds().get()），然后当超时了，会执行以下操作：
//
//	把状态从NOT_EXECUTED设置为TIMED_OUT
//	发送TIMEOUT事件
//	s.unsubscribe()取消事件订阅
//	timeoutRunnable.run();抛出timeoutRunnable异常
//	​ 简单来说就是，设置了一个定时器，定时时间是我们设置的超时时间，如果定时时间到了，我们就改变相应的状态，发送相应的内部事件，取消Obserable的订阅，抛出异常，而做到一个超时的隔离。
	private static class HystrixObservableTimeoutOperator<R> implements Operator<R, R> {

		final AbstractCommand<R> originalCommand;

		public HystrixObservableTimeoutOperator(final AbstractCommand<R> originalCommand) {
			this.originalCommand = originalCommand;
		}

		@Override
		public Subscriber<? super R> call(final Subscriber<? super R> child) {
			final CompositeSubscription s = new CompositeSubscription();
			// if the child unsubscribes we unsubscribe our parent as well
			child.add(s);

			/*
			 * Define the action to perform on timeout outside of the TimerListener to it
			 * can capture the HystrixRequestContext of the calling thread which doesn't
			 * exist on the Timer thread.
			 */
			final HystrixContextRunnable timeoutRunnable = new HystrixContextRunnable(
					originalCommand.concurrencyStrategy, new Runnable() {

						@Override
						public void run() {
							child.onError(new HystrixTimeoutException());
						}
					});
			// 设置定时调度
			TimerListener listener = new TimerListener() {
				// 定时触发的方法
				@Override
				public void tick() {
					// if we can go from NOT_EXECUTED to TIMED_OUT then we do the timeout codepath
					// otherwise it means we lost a race and the run() execution completed or did
					// not start
					if (originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED,
							TimedOutStatus.TIMED_OUT)) {
						// report timeout failure
						originalCommand.eventNotifier.markEvent(HystrixEventType.TIMEOUT, originalCommand.commandKey);

						// shut down the original request
						s.unsubscribe();

						timeoutRunnable.run();
						// if it did not start, then we need to mark a command start for concurrency
						// metrics, and then issue the timeout
					}
				}

				@Override
				public int getIntervalTimeInMilliseconds() {
					return originalCommand.properties.executionTimeoutInMilliseconds().get();
				}
			};

			final Reference<TimerListener> tl = HystrixTimer.getInstance().addTimerListener(listener);

			// set externally so execute/queue can see this
			originalCommand.timeoutTimer.set(tl);

			/**
			 * If this subscriber receives values it means the parent succeeded/completed
			 */
			Subscriber<R> parent = new Subscriber<R>() {

				@Override
				public void onCompleted() {
					if (isNotTimedOut()) {
						// stop timer and pass notification through
						tl.clear();
						child.onCompleted();
					}
				}

				@Override
				public void onError(Throwable e) {
					if (isNotTimedOut()) {
						// stop timer and pass notification through
						tl.clear();
						child.onError(e);
					}
				}

				@Override
				public void onNext(R v) {
					if (isNotTimedOut()) {
						child.onNext(v);
					}
				}

				private boolean isNotTimedOut() {
					// if already marked COMPLETED (by onNext) or succeeds in setting to COMPLETED
					return originalCommand.isCommandTimedOut.get() == TimedOutStatus.COMPLETED
							|| originalCommand.isCommandTimedOut.compareAndSet(TimedOutStatus.NOT_EXECUTED,
									TimedOutStatus.COMPLETED);
				}

			};

			// if s is unsubscribed we want to unsubscribe the parent
			s.add(parent);

			return parent;
		}

	}

	private static void setRequestContextIfNeeded(final HystrixRequestContext currentRequestContext) {
		if (!HystrixRequestContext.isCurrentThreadInitialized()) {
			// even if the user Observable doesn't have context we want it set for chained
			// operators
			HystrixRequestContext.setContextOnCurrentThread(currentRequestContext);
		}
	}

	/**
	 * Get the TryableSemaphore this HystrixCommand should use if a fallback occurs.
	 * 
	 * @return TryableSemaphore
	 */
	protected TryableSemaphore getFallbackSemaphore() {
		if (fallbackSemaphoreOverride == null) {
			TryableSemaphore _s = fallbackSemaphorePerCircuit.get(commandKey.name());
			if (_s == null) {
				// we didn't find one cache so setup
				fallbackSemaphorePerCircuit.putIfAbsent(commandKey.name(),
						new TryableSemaphoreActual(properties.fallbackIsolationSemaphoreMaxConcurrentRequests()));
				// assign whatever got set (this or another thread)
				return fallbackSemaphorePerCircuit.get(commandKey.name());
			} else {
				return _s;
			}
		} else {
			return fallbackSemaphoreOverride;
		}
	}

	/**
	 * Get the TryableSemaphore this HystrixCommand should use for execution if not
	 * running in a separate thread.
	 * 
	 * @return TryableSemaphore
	 */
	protected TryableSemaphore getExecutionSemaphore() {
		if (properties.executionIsolationStrategy().get() == ExecutionIsolationStrategy.SEMAPHORE) {
			if (executionSemaphoreOverride == null) {
				TryableSemaphore _s = executionSemaphorePerCircuit.get(commandKey.name());
				if (_s == null) {
					// we didn't find one cache so setup
					executionSemaphorePerCircuit.putIfAbsent(commandKey.name(),
							new TryableSemaphoreActual(properties.executionIsolationSemaphoreMaxConcurrentRequests()));
					// assign whatever got set (this or another thread)
					return executionSemaphorePerCircuit.get(commandKey.name());
				} else {
					return _s;
				}
			} else {
				return executionSemaphoreOverride;
			}
		} else {
			// return NoOp implementation since we're not using SEMAPHORE isolation
			return TryableSemaphoreNoOp.DEFAULT;
		}
	}

	/**
	 * Each concrete implementation of AbstractCommand should return the name of the
	 * fallback method as a String This will be used to determine if the fallback
	 * "exists" for firing the onFallbackStart/onFallbackError hooks
	 * 
	 * @deprecated This functionality is replaced by {@link #isFallbackUserDefined},
	 *             which is less implementation-aware
	 * @return method name of fallback
	 */
	@Deprecated
	protected abstract String getFallbackMethodName();

	protected abstract boolean isFallbackUserDefined();

	/**
	 * @return {@link HystrixCommandGroupKey} used to group together multiple
	 *         {@link AbstractCommand} objects.
	 *         <p>
	 *         The {@link HystrixCommandGroupKey} is used to represent a common
	 *         relationship between commands. For example, a library or team name,
	 *         the system all related commands interace with, common business
	 *         purpose etc.
	 */
	public HystrixCommandGroupKey getCommandGroup() {
		return commandGroup;
	}

	/**
	 * @return {@link HystrixCommandKey} identifying this command instance for
	 *         statistics, circuit-breaker, properties, etc.
	 */
	public HystrixCommandKey getCommandKey() {
		return commandKey;
	}

	/**
	 * @return {@link HystrixThreadPoolKey} identifying which thread-pool this
	 *         command uses (when configured to run on separate threads via
	 *         {@link HystrixCommandProperties#executionIsolationStrategy()}).
	 */
	public HystrixThreadPoolKey getThreadPoolKey() {
		return threadPoolKey;
	}

	/* package */HystrixCircuitBreaker getCircuitBreaker() {
		return circuitBreaker;
	}

	/**
	 * The {@link HystrixCommandMetrics} associated with this
	 * {@link AbstractCommand} instance.
	 *
	 * @return HystrixCommandMetrics
	 */
	public HystrixCommandMetrics getMetrics() {
		return metrics;
	}

	/**
	 * The {@link HystrixCommandProperties} associated with this
	 * {@link AbstractCommand} instance.
	 * 
	 * @return HystrixCommandProperties
	 */
	public HystrixCommandProperties getProperties() {
		return properties;
	}

	/*
	 * *****************************************************************************
	 * ***
	 */
	/*
	 * *****************************************************************************
	 * ***
	 */
	/* Operators that implement hook application */
	/*
	 * *****************************************************************************
	 * ***
	 */
	/*
	 * *****************************************************************************
	 * ***
	 */

	private class ExecutionHookApplication implements Operator<R, R> {
		private final HystrixInvokable<R> cmd;

		ExecutionHookApplication(HystrixInvokable<R> cmd) {
			this.cmd = cmd;
		}

		@Override
		public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
			return new Subscriber<R>(subscriber) {
				@Override
				public void onCompleted() {
					try {
						executionHook.onExecutionSuccess(cmd);
					} catch (Throwable hookEx) {
						logger.warn("Error calling HystrixCommandExecutionHook.onExecutionSuccess", hookEx);
					}
					subscriber.onCompleted();
				}

				@Override
				public void onError(Throwable e) {
					Exception wrappedEx = wrapWithOnExecutionErrorHook(e);
					subscriber.onError(wrappedEx);
				}

				@Override
				public void onNext(R r) {
					R wrappedValue = wrapWithOnExecutionEmitHook(r);
					subscriber.onNext(wrappedValue);
				}
			};
		}
	}

	private class FallbackHookApplication implements Operator<R, R> {
		private final HystrixInvokable<R> cmd;

		FallbackHookApplication(HystrixInvokable<R> cmd) {
			this.cmd = cmd;
		}

		@Override
		public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
			return new Subscriber<R>(subscriber) {
				@Override
				public void onCompleted() {
					try {
						executionHook.onFallbackSuccess(cmd);
					} catch (Throwable hookEx) {
						logger.warn("Error calling HystrixCommandExecutionHook.onFallbackSuccess", hookEx);
					}
					subscriber.onCompleted();
				}

				@Override
				public void onError(Throwable e) {
					Exception wrappedEx = wrapWithOnFallbackErrorHook(e);
					subscriber.onError(wrappedEx);
				}

				@Override
				public void onNext(R r) {
					R wrappedValue = wrapWithOnFallbackEmitHook(r);
					subscriber.onNext(wrappedValue);
				}
			};
		}
	}

	@Deprecated // separated out to make it cleanly removable
	private class DeprecatedOnRunHookApplication implements Operator<R, R> {

		private final HystrixInvokable<R> cmd;

		DeprecatedOnRunHookApplication(HystrixInvokable<R> cmd) {
			this.cmd = cmd;
		}

		@Override
		public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
			return new Subscriber<R>(subscriber) {
				@Override
				public void onCompleted() {
					subscriber.onCompleted();
				}

				@Override
				public void onError(Throwable t) {
					Exception e = getExceptionFromThrowable(t);
					try {
						Exception wrappedEx = executionHook.onRunError(cmd, e);
						subscriber.onError(wrappedEx);
					} catch (Throwable hookEx) {
						logger.warn("Error calling HystrixCommandExecutionHook.onRunError", hookEx);
						subscriber.onError(e);
					}
				}

				@Override
				public void onNext(R r) {
					try {
						R wrappedValue = executionHook.onRunSuccess(cmd, r);
						subscriber.onNext(wrappedValue);
					} catch (Throwable hookEx) {
						logger.warn("Error calling HystrixCommandExecutionHook.onRunSuccess", hookEx);
						subscriber.onNext(r);
					}
				}
			};
		}
	}

	@Deprecated // separated out to make it cleanly removable
	private class DeprecatedOnFallbackHookApplication implements Operator<R, R> {

		private final HystrixInvokable<R> cmd;

		DeprecatedOnFallbackHookApplication(HystrixInvokable<R> cmd) {
			this.cmd = cmd;
		}

		@Override
		public Subscriber<? super R> call(final Subscriber<? super R> subscriber) {
			return new Subscriber<R>(subscriber) {
				@Override
				public void onCompleted() {
					subscriber.onCompleted();
				}

				@Override
				public void onError(Throwable t) {
					// no need to call a hook here. FallbackHookApplication is already calling the
					// proper and non-deprecated hook
					subscriber.onError(t);
				}

				@Override
				public void onNext(R r) {
					try {
						R wrappedValue = executionHook.onFallbackSuccess(cmd, r);
						subscriber.onNext(wrappedValue);
					} catch (Throwable hookEx) {
						logger.warn("Error calling HystrixCommandExecutionHook.onFallbackSuccess", hookEx);
						subscriber.onNext(r);
					}
				}
			};
		}
	}

	private Exception wrapWithOnExecutionErrorHook(Throwable t) {
		Exception e = getExceptionFromThrowable(t);
		try {
			return executionHook.onExecutionError(this, e);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onExecutionError", hookEx);
			return e;
		}
	}

	private Exception wrapWithOnFallbackErrorHook(Throwable t) {
		Exception e = getExceptionFromThrowable(t);
		try {
			if (isFallbackUserDefined()) {
				return executionHook.onFallbackError(this, e);
			} else {
				return e;
			}
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onFallbackError", hookEx);
			return e;
		}
	}

	private Exception wrapWithOnErrorHook(FailureType failureType, Throwable t) {
		Exception e = getExceptionFromThrowable(t);
		try {
			return executionHook.onError(this, failureType, e);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onError", hookEx);
			return e;
		}
	}

	private R wrapWithOnExecutionEmitHook(R r) {
		try {
			return executionHook.onExecutionEmit(this, r);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onExecutionEmit", hookEx);
			return r;
		}
	}

	private R wrapWithOnFallbackEmitHook(R r) {
		try {
			return executionHook.onFallbackEmit(this, r);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onFallbackEmit", hookEx);
			return r;
		}
	}

	private R wrapWithOnEmitHook(R r) {
		try {
			return executionHook.onEmit(this, r);
		} catch (Throwable hookEx) {
			logger.warn("Error calling HystrixCommandExecutionHook.onEmit", hookEx);
			return r;
		}
	}

	/**
	 * Take an Exception and determine whether to throw it, its cause or a new
	 * HystrixRuntimeException.
	 * <p>
	 * This will only throw an HystrixRuntimeException, HystrixBadRequestException,
	 * IllegalStateException or any exception that implements
	 * ExceptionNotWrappedByHystrix.
	 * 
	 * @param e initial exception
	 * @return HystrixRuntimeException, HystrixBadRequestException or
	 *         IllegalStateException
	 */
	protected Throwable decomposeException(Exception e) {
		if (e instanceof IllegalStateException) {
			return (IllegalStateException) e;
		}
		if (e instanceof HystrixBadRequestException) {
			if (shouldNotBeWrapped(e.getCause())) {
				return e.getCause();
			}
			return (HystrixBadRequestException) e;
		}
		if (e.getCause() instanceof HystrixBadRequestException) {
			if (shouldNotBeWrapped(e.getCause().getCause())) {
				return e.getCause().getCause();
			}
			return (HystrixBadRequestException) e.getCause();
		}
		if (e instanceof HystrixRuntimeException) {
			return (HystrixRuntimeException) e;
		}
		// if we have an exception we know about we'll throw it directly without the
		// wrapper exception
		if (e.getCause() instanceof HystrixRuntimeException) {
			return (HystrixRuntimeException) e.getCause();
		}
		if (shouldNotBeWrapped(e)) {
			return e;
		}
		if (shouldNotBeWrapped(e.getCause())) {
			return e.getCause();
		}
		// we don't know what kind of exception this is so create a generic message and
		// throw a new HystrixRuntimeException
		String message = getLogMessagePrefix() + " failed while executing.";
		logger.debug(message, e); // debug only since we're throwing the exception and someone higher will do
									// something with it
		return new HystrixRuntimeException(FailureType.COMMAND_EXCEPTION, this.getClass(), message, e, null);

	}

	/*
	 * *****************************************************************************
	 * ***
	 */
	/*
	 * *****************************************************************************
	 * ***
	 */
	/* TryableSemaphore */
	/*
	 * *****************************************************************************
	 * ***
	 */
	/*
	 * *****************************************************************************
	 * ***
	 */

	/**
	 * Semaphore that only supports tryAcquire and never blocks and that supports a
	 * dynamic permit count.
	 * <p>
	 * Using AtomicInteger increment/decrement instead of
	 * java.util.concurrent.Semaphore since we don't need blocking and need a custom
	 * implementation to get the dynamic permit count and since AtomicInteger
	 * achieves the same behavior and performance without the more complex
	 * implementation of the actual Semaphore class using AbstractQueueSynchronizer.
	 */
	/* package */static class TryableSemaphoreActual implements TryableSemaphore {
		protected final HystrixProperty<Integer> numberOfPermits;
		private final AtomicInteger count = new AtomicInteger(0);

		public TryableSemaphoreActual(HystrixProperty<Integer> numberOfPermits) {
			this.numberOfPermits = numberOfPermits;
		}

		@Override
		public boolean tryAcquire() {
			int currentCount = count.incrementAndGet();
			if (currentCount > numberOfPermits.get()) {
				count.decrementAndGet();
				return false;
			} else {
				return true;
			}
		}

		@Override
		public void release() {
			count.decrementAndGet();
		}

		@Override
		public int getNumberOfPermitsUsed() {
			return count.get();
		}

	}

	/* package */static class TryableSemaphoreNoOp implements TryableSemaphore {

		public static final TryableSemaphore DEFAULT = new TryableSemaphoreNoOp();

		@Override
		public boolean tryAcquire() {
			return true;
		}

		@Override
		public void release() {

		}

		@Override
		public int getNumberOfPermitsUsed() {
			return 0;
		}

	}

	/* package */static interface TryableSemaphore {

		/**
		 * Use like this:
		 * <p>
		 * 
		 * <pre>
		 * if (s.tryAcquire()) {
		 * 	try {
		 * // do work that is protected by 's'
		 * 	} finally {
		 * 		s.release();
		 * 	}
		 * }
		 * </pre>
		 * 
		 * @return boolean
		 */
		public abstract boolean tryAcquire();

		/**
		 * ONLY call release if tryAcquire returned true.
		 * <p>
		 * 
		 * <pre>
		 * if (s.tryAcquire()) {
		 * 	try {
		 * // do work that is protected by 's'
		 * 	} finally {
		 * 		s.release();
		 * 	}
		 * }
		 * </pre>
		 */
		public abstract void release();

		public abstract int getNumberOfPermitsUsed();

	}

	/*
	 * *****************************************************************************
	 * ***
	 */
	/*
	 * *****************************************************************************
	 * ***
	 */
	/* RequestCache */
	/*
	 * *****************************************************************************
	 * ***
	 */
	/*
	 * *****************************************************************************
	 * ***
	 */

	/**
	 * Key to be used for request caching.
	 * <p>
	 * By default this returns null which means "do not cache".
	 * <p>
	 * To enable caching override this method and return a string key uniquely
	 * representing the state of a command instance.
	 * <p>
	 * If multiple command instances in the same request scope match keys then only
	 * the first will be executed and all others returned from cache.
	 * 
	 * @return cacheKey
	 */
	protected String getCacheKey() {
		return null;
	}

	public String getPublicCacheKey() {
		return getCacheKey();
	}

	protected boolean isRequestCachingEnabled() {
		return properties.requestCacheEnabled().get() && getCacheKey() != null;
	}

	protected String getLogMessagePrefix() {
		return getCommandKey().name();
	}

	/**
	 * Whether the 'circuit-breaker' is open meaning that <code>execute()</code>
	 * will immediately return the <code>getFallback()</code> response and not
	 * attempt a HystrixCommand execution.
	 *
	 * 4 columns are ForcedOpen | ForcedClosed | CircuitBreaker open due to health
	 * ||| Expected Result
	 *
	 * T | T | T ||| OPEN (true) T | T | F ||| OPEN (true) T | F | T ||| OPEN (true)
	 * T | F | F ||| OPEN (true) F | T | T ||| CLOSED (false) F | T | F ||| CLOSED
	 * (false) F | F | T ||| OPEN (true) F | F | F ||| CLOSED (false)
	 *
	 * @return boolean
	 */
	public boolean isCircuitBreakerOpen() {
		return properties.circuitBreakerForceOpen().get()
				|| (!properties.circuitBreakerForceClosed().get() && circuitBreaker.isOpen());
	}

	/**
	 * If this command has completed execution either successfully, via fallback or
	 * failure.
	 * 
	 * @return boolean
	 */
	public boolean isExecutionComplete() {
		return commandState.get() == CommandState.TERMINAL;
	}

	/**
	 * Whether the execution occurred in a separate thread.
	 * <p>
	 * This should be called only once execute()/queue()/fireOrForget() are called
	 * otherwise it will always return false.
	 * <p>
	 * This specifies if a thread execution actually occurred, not just if it is
	 * configured to be executed in a thread.
	 * 
	 * @return boolean
	 */
	public boolean isExecutedInThread() {
		return getCommandResult().isExecutedInThread();
	}

	/**
	 * Whether the response was returned successfully either by executing
	 * <code>run()</code> or from cache.
	 * 
	 * @return boolean
	 */
	public boolean isSuccessfulExecution() {
		return getCommandResult().getEventCounts().contains(HystrixEventType.SUCCESS);
	}

	/**
	 * Whether the <code>run()</code> resulted in a failure (exception).
	 * 
	 * @return boolean
	 */
	public boolean isFailedExecution() {
		return getCommandResult().getEventCounts().contains(HystrixEventType.FAILURE);
	}

	/**
	 * Get the Throwable/Exception thrown that caused the failure.
	 * <p>
	 * If <code>isFailedExecution() == true</code> then this would represent the
	 * Exception thrown by the <code>run()</code> method.
	 * <p>
	 * If <code>isFailedExecution() == false</code> then this would return null.
	 * 
	 * @return Throwable or null
	 */
	public Throwable getFailedExecutionException() {
		return executionResult.getException();
	}

	/**
	 * Get the Throwable/Exception emitted by this command instance prior to
	 * checking the fallback. This exception instance may have been generated via a
	 * number of mechanisms: 1) failed execution (in this case, same result as
	 * {@link #getFailedExecutionException()}. 2) timeout 3) short-circuit 4)
	 * rejection 5) bad request
	 *
	 * If the command execution was successful, then this exception instance is null
	 * (there was no exception)
	 *
	 * Note that the caller of the command may not receive this exception, as
	 * fallbacks may be served as a response to the exception.
	 *
	 * @return Throwable or null
	 */
	public Throwable getExecutionException() {
		return executionResult.getExecutionException();
	}

	/**
	 * Whether the response received from was the result of some type of failure and
	 * <code>getFallback()</code> being called.
	 * 
	 * @return boolean
	 */
	public boolean isResponseFromFallback() {
		return getCommandResult().getEventCounts().contains(HystrixEventType.FALLBACK_SUCCESS);
	}

	/**
	 * Whether the response received was the result of a timeout and
	 * <code>getFallback()</code> being called.
	 * 
	 * @return boolean
	 */
	public boolean isResponseTimedOut() {
		return getCommandResult().getEventCounts().contains(HystrixEventType.TIMEOUT);
	}

	/**
	 * Whether the response received was a fallback as result of being
	 * short-circuited (meaning <code>isCircuitBreakerOpen() == true</code>) and
	 * <code>getFallback()</code> being called.
	 * 
	 * @return boolean
	 */
	public boolean isResponseShortCircuited() {
		return getCommandResult().getEventCounts().contains(HystrixEventType.SHORT_CIRCUITED);
	}

	/**
	 * Whether the response is from cache and <code>run()</code> was not invoked.
	 * 
	 * @return boolean
	 */
	public boolean isResponseFromCache() {
		return isResponseFromCache;
	}

	/**
	 * Whether the response received was a fallback as result of being rejected via
	 * sempahore
	 *
	 * @return boolean
	 */
	public boolean isResponseSemaphoreRejected() {
		return getCommandResult().isResponseSemaphoreRejected();
	}

	/**
	 * Whether the response received was a fallback as result of being rejected via
	 * threadpool
	 *
	 * @return boolean
	 */
	public boolean isResponseThreadPoolRejected() {
		return getCommandResult().isResponseThreadPoolRejected();
	}

	/**
	 * Whether the response received was a fallback as result of being rejected
	 * (either via threadpool or semaphore)
	 *
	 * @return boolean
	 */
	public boolean isResponseRejected() {
		return getCommandResult().isResponseRejected();
	}

	/**
	 * List of HystrixCommandEventType enums representing events that occurred
	 * during execution.
	 * <p>
	 * Examples of events are SUCCESS, FAILURE, TIMEOUT, and SHORT_CIRCUITED
	 * 
	 * @return {@code List<HystrixEventType>}
	 */
	public List<HystrixEventType> getExecutionEvents() {
		return getCommandResult().getOrderedList();
	}

	private ExecutionResult getCommandResult() {
		ExecutionResult resultToReturn;
		if (executionResultAtTimeOfCancellation == null) {
			resultToReturn = executionResult;
		} else {
			resultToReturn = executionResultAtTimeOfCancellation;
		}

		if (isResponseFromCache) {
			resultToReturn = resultToReturn.addEvent(HystrixEventType.RESPONSE_FROM_CACHE);
		}

		return resultToReturn;
	}

	/**
	 * Number of emissions of the execution of a command. Only interesting in the
	 * streaming case.
	 * 
	 * @return number of <code>OnNext</code> emissions by a streaming command
	 */
	@Override
	public int getNumberEmissions() {
		return getCommandResult().getEventCounts().getCount(HystrixEventType.EMIT);
	}

	/**
	 * Number of emissions of the execution of a fallback. Only interesting in the
	 * streaming case.
	 * 
	 * @return number of <code>OnNext</code> emissions by a streaming fallback
	 */
	@Override
	public int getNumberFallbackEmissions() {
		return getCommandResult().getEventCounts().getCount(HystrixEventType.FALLBACK_EMIT);
	}

	@Override
	public int getNumberCollapsed() {
		return getCommandResult().getEventCounts().getCount(HystrixEventType.COLLAPSED);
	}

	@Override
	public HystrixCollapserKey getOriginatingCollapserKey() {
		return executionResult.getCollapserKey();
	}

	/**
	 * The execution time of this command instance in milliseconds, or -1 if not
	 * executed.
	 * 
	 * @return int
	 */
	public int getExecutionTimeInMilliseconds() {
		return getCommandResult().getExecutionLatency();
	}

	/**
	 * Time in Nanos when this command instance's run method was called, or -1 if
	 * not executed for e.g., command threw an exception
	 *
	 * @return long
	 */
	public long getCommandRunStartTimeInNanos() {
		return executionResult.getCommandRunStartTimeInNanos();
	}

	@Override
	public ExecutionResult.EventCounts getEventCounts() {
		return getCommandResult().getEventCounts();
	}

	protected Exception getExceptionFromThrowable(Throwable t) {
		Exception e;
		if (t instanceof Exception) {
			e = (Exception) t;
		} else {
			// Hystrix 1.x uses Exception, not Throwable so to prevent a breaking change
			// Throwable will be wrapped in Exception
			e = new Exception("Throwable caught while executing.", t);
		}
		return e;
	}

	private static class ExecutionHookDeprecationWrapper extends HystrixCommandExecutionHook {

		private final HystrixCommandExecutionHook actual;

		ExecutionHookDeprecationWrapper(HystrixCommandExecutionHook actual) {
			this.actual = actual;
		}

		@Override
		public <T> T onEmit(HystrixInvokable<T> commandInstance, T value) {
			return actual.onEmit(commandInstance, value);
		}

		@Override
		public <T> void onSuccess(HystrixInvokable<T> commandInstance) {
			actual.onSuccess(commandInstance);
		}

		@Override
		public <T> void onExecutionStart(HystrixInvokable<T> commandInstance) {
			actual.onExecutionStart(commandInstance);
		}

		@Override
		public <T> T onExecutionEmit(HystrixInvokable<T> commandInstance, T value) {
			return actual.onExecutionEmit(commandInstance, value);
		}

		@Override
		public <T> Exception onExecutionError(HystrixInvokable<T> commandInstance, Exception e) {
			return actual.onExecutionError(commandInstance, e);
		}

		@Override
		public <T> void onExecutionSuccess(HystrixInvokable<T> commandInstance) {
			actual.onExecutionSuccess(commandInstance);
		}

		@Override
		public <T> T onFallbackEmit(HystrixInvokable<T> commandInstance, T value) {
			return actual.onFallbackEmit(commandInstance, value);
		}

		@Override
		public <T> void onFallbackSuccess(HystrixInvokable<T> commandInstance) {
			actual.onFallbackSuccess(commandInstance);
		}

		@Override
		@Deprecated
		public <T> void onRunStart(HystrixCommand<T> commandInstance) {
			actual.onRunStart(commandInstance);
		}

		@Override
		public <T> void onRunStart(HystrixInvokable<T> commandInstance) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				onRunStart(c);
			}
			actual.onRunStart(commandInstance);
		}

		@Override
		@Deprecated
		public <T> T onRunSuccess(HystrixCommand<T> commandInstance, T response) {
			return actual.onRunSuccess(commandInstance, response);
		}

		@Override
		@Deprecated
		public <T> T onRunSuccess(HystrixInvokable<T> commandInstance, T response) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				response = onRunSuccess(c, response);
			}
			return actual.onRunSuccess(commandInstance, response);
		}

		@Override
		@Deprecated
		public <T> Exception onRunError(HystrixCommand<T> commandInstance, Exception e) {
			return actual.onRunError(commandInstance, e);
		}

		@Override
		@Deprecated
		public <T> Exception onRunError(HystrixInvokable<T> commandInstance, Exception e) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				e = onRunError(c, e);
			}
			return actual.onRunError(commandInstance, e);
		}

		@Override
		@Deprecated
		public <T> void onFallbackStart(HystrixCommand<T> commandInstance) {
			actual.onFallbackStart(commandInstance);
		}

		@Override
		public <T> void onFallbackStart(HystrixInvokable<T> commandInstance) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				onFallbackStart(c);
			}
			actual.onFallbackStart(commandInstance);
		}

		@Override
		@Deprecated
		public <T> T onFallbackSuccess(HystrixCommand<T> commandInstance, T fallbackResponse) {
			return actual.onFallbackSuccess(commandInstance, fallbackResponse);
		}

		@Override
		@Deprecated
		public <T> T onFallbackSuccess(HystrixInvokable<T> commandInstance, T fallbackResponse) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				fallbackResponse = onFallbackSuccess(c, fallbackResponse);
			}
			return actual.onFallbackSuccess(commandInstance, fallbackResponse);
		}

		@Override
		@Deprecated
		public <T> Exception onFallbackError(HystrixCommand<T> commandInstance, Exception e) {
			return actual.onFallbackError(commandInstance, e);
		}

		@Override
		public <T> Exception onFallbackError(HystrixInvokable<T> commandInstance, Exception e) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				e = onFallbackError(c, e);
			}
			return actual.onFallbackError(commandInstance, e);
		}

		@Override
		@Deprecated
		public <T> void onStart(HystrixCommand<T> commandInstance) {
			actual.onStart(commandInstance);
		}

		@Override
		public <T> void onStart(HystrixInvokable<T> commandInstance) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				onStart(c);
			}
			actual.onStart(commandInstance);
		}

		@Override
		@Deprecated
		public <T> T onComplete(HystrixCommand<T> commandInstance, T response) {
			return actual.onComplete(commandInstance, response);
		}

		@Override
		@Deprecated
		public <T> T onComplete(HystrixInvokable<T> commandInstance, T response) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				response = onComplete(c, response);
			}
			return actual.onComplete(commandInstance, response);
		}

		@Override
		@Deprecated
		public <T> Exception onError(HystrixCommand<T> commandInstance, FailureType failureType, Exception e) {
			return actual.onError(commandInstance, failureType, e);
		}

		@Override
		public <T> Exception onError(HystrixInvokable<T> commandInstance, FailureType failureType, Exception e) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				e = onError(c, failureType, e);
			}
			return actual.onError(commandInstance, failureType, e);
		}

		@Override
		@Deprecated
		public <T> void onThreadStart(HystrixCommand<T> commandInstance) {
			actual.onThreadStart(commandInstance);
		}

		@Override
		public <T> void onThreadStart(HystrixInvokable<T> commandInstance) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				onThreadStart(c);
			}
			actual.onThreadStart(commandInstance);
		}

		@Override
		@Deprecated
		public <T> void onThreadComplete(HystrixCommand<T> commandInstance) {
			actual.onThreadComplete(commandInstance);
		}

		@Override
		public <T> void onThreadComplete(HystrixInvokable<T> commandInstance) {
			HystrixCommand<T> c = getHystrixCommandFromAbstractIfApplicable(commandInstance);
			if (c != null) {
				onThreadComplete(c);
			}
			actual.onThreadComplete(commandInstance);
		}

		@Override
		public <T> void onCacheHit(HystrixInvokable<T> commandInstance) {
			actual.onCacheHit(commandInstance);
		}

		@Override
		public <T> void onUnsubscribe(HystrixInvokable<T> commandInstance) {
			actual.onUnsubscribe(commandInstance);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		private <T> HystrixCommand<T> getHystrixCommandFromAbstractIfApplicable(HystrixInvokable<T> commandInstance) {
			if (commandInstance instanceof HystrixCommand) {
				return (HystrixCommand) commandInstance;
			} else {
				return null;
			}
		}
	}
}
