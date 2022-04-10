/**
 * Copyright 2012 Netflix, Inc.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.hystrix.HystrixCommandMetrics.HealthCounts;

/**
 * Circuit-breaker logic that is hooked into {@link HystrixCommand} execution
 * and will stop allowing executions if failures have gone past the defined
 * threshold.
 * <p>
 * It will then allow single retries after a defined sleepWindow until the
 * execution succeeds at which point it will again close the circuit and allow
 * executions again.
 */
public interface HystrixCircuitBreaker {

	/**
	 * Every {@link HystrixCommand} requests asks this if it is allowed to proceed
	 * or not.
	 * <p>
	 * This takes into account the half-open logic which allows some requests
	 * through when determining if it should be closed again.
	 * 
	 * @return boolean whether a request should be permitted
	 */
	public boolean allowRequest();

	/**
	 * Whether the circuit is currently open (tripped).
	 * 
	 * @return boolean state of circuit breaker
	 */
	public boolean isOpen();

	/**
	 * Invoked on successful executions from {@link HystrixCommand} as part of
	 * feedback mechanism when in a half-open state.
	 */
	/* package */void markSuccess();

	/**
	 * @ExcludeFromJavadoc
	 * @ThreadSafe
	 */
	public static class Factory {
		// String is HystrixCommandKey.name() (we can't use HystrixCommandKey directly
		// as we can't guarantee it implements hashcode/equals correctly)
		private static ConcurrentHashMap<String, HystrixCircuitBreaker> circuitBreakersByCommand = new ConcurrentHashMap<String, HystrixCircuitBreaker>();

		/**
		 * Get the {@link HystrixCircuitBreaker} instance for a given
		 * {@link HystrixCommandKey}.
		 * <p>
		 * This is thread-safe and ensures only 1 {@link HystrixCircuitBreaker} per
		 * {@link HystrixCommandKey}.
		 * 
		 * @param key        {@link HystrixCommandKey} of {@link HystrixCommand}
		 *                   instance requesting the {@link HystrixCircuitBreaker}
		 * @param group      Pass-thru to {@link HystrixCircuitBreaker}
		 * @param properties Pass-thru to {@link HystrixCircuitBreaker}
		 * @param metrics    Pass-thru to {@link HystrixCircuitBreaker}
		 * @return {@link HystrixCircuitBreaker} for {@link HystrixCommandKey}
		 */
		public static HystrixCircuitBreaker getInstance(HystrixCommandKey key, HystrixCommandGroupKey group,
				HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
			// this should find it for all but the first time
			HystrixCircuitBreaker previouslyCached = circuitBreakersByCommand.get(key.name());
			if (previouslyCached != null) {
				return previouslyCached;
			}

			// if we get here this is the first time so we need to initialize

			// Create and add to the map ... use putIfAbsent to atomically handle the
			// possible race-condition of
			// 2 threads hitting this point at the same time and let ConcurrentHashMap
			// provide us our thread-safety
			// If 2 threads hit here only one will get added and the other will get a
			// non-null response instead.
			HystrixCircuitBreaker cbForCommand = circuitBreakersByCommand.putIfAbsent(key.name(),
					new HystrixCircuitBreakerImpl(key, group, properties, metrics));
			if (cbForCommand == null) {
				// this means the putIfAbsent step just created a new one so let's retrieve and
				// return it
				return circuitBreakersByCommand.get(key.name());
			} else {
				// this means a race occurred and while attempting to 'put' another one got
				// there before
				// and we instead retrieved it and will now return it
				return cbForCommand;
			}
		}

		/**
		 * Get the {@link HystrixCircuitBreaker} instance for a given
		 * {@link HystrixCommandKey} or null if none exists.
		 * 
		 * @param key {@link HystrixCommandKey} of {@link HystrixCommand} instance
		 *            requesting the {@link HystrixCircuitBreaker}
		 * @return {@link HystrixCircuitBreaker} for {@link HystrixCommandKey}
		 */
		public static HystrixCircuitBreaker getInstance(HystrixCommandKey key) {
			return circuitBreakersByCommand.get(key.name());
		}

		/**
		 * Clears all circuit breakers. If new requests come in instances will be
		 * recreated.
		 */
		/* package */static void reset() {
			circuitBreakersByCommand.clear();
		}
	}

	/**
	 * The default production implementation of {@link HystrixCircuitBreaker}.
	 * 
	 * @ExcludeFromJavadoc
	 * @ThreadSafe
	 */
	/* package */static class HystrixCircuitBreakerImpl implements HystrixCircuitBreaker {
		// command的配置：包括断路器的配置。如`circuitBreakerEnabled/circuitBreakerForceOpen`等等
		private final HystrixCommandProperties properties;
		// command指标信息
		private final HystrixCommandMetrics metrics;

		/*
		 * track whether this circuit is open/closed at any given point in time (default
		 * to false==closed)
		 */
		// 标记当前断路器是否是打开的（默认是关闭状态）
		private AtomicBoolean circuitOpen = new AtomicBoolean(false);

		/* when the circuit was marked open or was last allowed to try a 'singleTest' */
		private AtomicLong circuitOpenedOrLastTestedTime = new AtomicLong();
		// 唯一构造器
		// 传了4个参数，其实只用到2个参数，是因为Hystrix也希望你自己可以定制断路器的逻辑实现

		protected HystrixCircuitBreakerImpl(HystrixCommandKey key, HystrixCommandGroupKey commandGroup,
				HystrixCommandProperties properties, HystrixCommandMetrics metrics) {
			this.properties = properties;
			this.metrics = metrics;
		}

		public void markSuccess() {
			// 断路器必须是已经处于打开状态，mark才有意义嘛
			if (circuitOpen.get()) {
				// mark一下后，立马关闭断路器~~~~
				if (circuitOpen.compareAndSet(true, false)) {
					// win the thread race to reset metrics
					// Unsubscribe from the current stream to reset the health counts stream. This
					// only affects the health counts view,
					// and all other metric consumers are unaffected by the reset
					// 重置度量指标
					metrics.resetStream();
				}
			}
		}

		@Override
		public boolean allowRequest() {
			//判断是否强制打开熔断器
			if (properties.circuitBreakerForceOpen().get()) {
				// properties have asked us to force the circuit open so we will allow NO
				// requests
				return false;
			}
			if (properties.circuitBreakerForceClosed().get()) {
				// we still want to allow isOpen() to perform it's calculations so we simulate
				// normal behavior
				isOpen();
				// properties have asked us to ignore errors so we will ignore the results of
				// isOpen and just allow all traffic through
				return true;
			}
			return !isOpen() || allowSingleTest();
		}

		public boolean allowSingleTest() {
			// 最近一次打开断路器的时间
			long timeCircuitOpenedOrWasLastTested = circuitOpenedOrLastTestedTime.get();
			// 1) if the circuit is open
			// 2) and it's been longer than 'sleepWindow' since we opened the circuit
			 //熔断器是开启的，且当前时间比开启熔断器的时间加上sleepWindow时间还要长
			if (circuitOpen.get() && System.currentTimeMillis() > timeCircuitOpenedOrWasLastTested
					+ properties.circuitBreakerSleepWindowInMilliseconds().get()) {
				// We push the 'circuitOpenedTime' ahead by 'sleepWindow' since we have allowed
				// one request to try.
				// If it succeeds the circuit will be closed, otherwise another singleTest will
				// be allowed at the end of the 'sleepWindow'.
				//设置当前时间到timeCircuitOpenedOrWasLastTested，
                //如果半开半闭的状态下，如果这次请求成功了则会调用markSuccess，让熔断器状态设为false,
                //如果不成功，就不需要了。
                //案例：半开半合状态下，熔断开启时间为00:00:00,sleepWindow为10s，如果00:00:15秒的时候调用，如果调用失败，
                //在00:00:15至00:00:25秒这个区间都是熔断的，
				if (circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested,
						System.currentTimeMillis())) {
					// if this returns true that means we set the time so we'll return true to allow
					// the singleTest
					// if it returned false it means another thread raced us and allowed the
					// singleTest before we did
					return true;
				}
			}
			return false;
		}

//		该方法不仅是判断，而且还负责了断路器的打开（不负责关闭，关闭交给markSuccess方法）。
		// 这个逻辑本来很复杂，但是当从上几篇文章了解了HystrixCommandMetrics以及HealthCounts的作用后，理解起来异常顺滑，完全0障碍有木有。
//
//		该方法处理步骤总结如下：
//
//		若断路器已经是打开状态，直接返回true（表示熔断器已打开）。若是关闭状态，继续下一步
//		根据HealthCounts的指标信息进行判断： 
//		若这段时间内（一个时间窗口）内请求数低于circuitBreakerRequestVolumeThreshold（默认值是20），就直接返回false。若高于20就继续下一步判断
//		若错误率health.getErrorPercentage()小于配置的阈值circuitBreakerErrorThresholdPercentage（默认值是50%），就直接返回false，若符合条件，也就是错误率高于50%就触发熔断（断路器标记为打开），返回true
//		总而言之，默认情况下，若10s秒内请求数超过20个，并且错误率超过50%就打开熔断器，触发熔断
		@Override
		public boolean isOpen() {
			// 如果已经是打开的，那就没啥好说的喽
			// 注意：这里并不会尝试去关闭断路器，这个事交给markSuccess或者allowSingleTest去完成即可
			if (circuitOpen.get()) {
				// if we're open we immediately return true and don't bother attempting to
				// 'close' ourself as that is left to allowSingleTest and a subsequent
				// successful test to close
				return true;
			}

			// we're closed, so let's see if errors have made us so we should trip the
			// circuit open
			// ===到这是关闭状态，所以会执行目标方法。因此需要搜集是否有错误发生====

			// HealthCounts：检索总请求、错误计数和错误百分比的快照。
			// 注意也是获取最新的哦：healthCountsStream.getLatest()
			HealthCounts health = metrics.getHealthCounts();
			// 如果统计这刻的请求数都不达标，那就肯定不开断路器
			// circuitBreakerRequestVolumeThreshold默认值是20，也就是这段时间内要至少20个请求才考虑熔断的逻辑
			// 默认：10秒内必须出现20个请求
			// check if we are past the statisticalWindowVolumeThreshold
			if (health.getTotalRequests() < properties.circuitBreakerRequestVolumeThreshold().get()) {
				// we are not past the minimum volume threshold for the statisticalWindow so
				// we'll return false immediately and not calculate anything
				return false;
			}
			// 请求数超过20了，就继续判断错误率是否达标
			// circuitBreakerErrorThresholdPercentage默认错误率：50
			// 就是说10秒钟内50%的请求都失败了，那才会触发熔断
			if (health.getErrorPercentage() < properties.circuitBreakerErrorThresholdPercentage().get()) {
				return false;
			} else {
				// 错误率太高，那就打开熔断器。并且标记打开的时间，
				// circuitOpenedOrLastTestedTime表示开启一个timer的起始时刻
				// our failure rate is too high, trip the circuit
				if (circuitOpen.compareAndSet(false, true)) {
					// if the previousValue was false then we want to set the currentTime
					circuitOpenedOrLastTestedTime.set(System.currentTimeMillis());
					return true;
				} else {
					// How could previousValue be true? If another thread was going through this
					// code at the same time a race-condition could have
					// caused another thread to set it to true already even though we were in the
					// process of doing the same
					// In this case, we know the circuit is open, so let the other thread set the
					// currentTime and report back that the circuit is open
					return true;
				}
			}
		}

	}

	/**
	 * An implementation of the circuit breaker that does nothing.
	 * 
	 * @ExcludeFromJavadoc
	 */
	/* package */static class NoOpCircuitBreaker implements HystrixCircuitBreaker {

		@Override
		public boolean allowRequest() {
			return true;
		}

		@Override
		public boolean isOpen() {
			return false;
		}

		@Override
		public void markSuccess() {

		}

	}

}
