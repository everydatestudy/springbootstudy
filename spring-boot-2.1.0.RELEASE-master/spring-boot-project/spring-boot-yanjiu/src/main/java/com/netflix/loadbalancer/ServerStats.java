/*
*
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
*
*/
package com.netflix.loadbalancer;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.config.CachedDynamicIntProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.stats.distribution.DataDistribution;
import com.netflix.stats.distribution.DataPublisher;
import com.netflix.stats.distribution.Distribution;
import com.netflix.util.MeasuredRate;

/**
 * Capture various stats per Server(node) in the LoadBalancer
 * @author stonse
 *
 */
public class ServerStats {
	// 默认60s（1分钟）publish一次数据
    private static final int DEFAULT_PUBLISH_INTERVAL =  60 * 1000; // = 1 minute
    // 缓冲区大小。这个默认大小可谓非常大呀，就算你QPS是1000，也能抗1分钟
    private static final int DEFAULT_BUFFER_SIZE = 60 * 1000; // = 1000 requests/sec for 1 minute
    //连接失败阈值，默认值3（超过就熔断） 
    private final CachedDynamicIntProperty connectionFailureThreshold;
    //断路器超时因子，默认值10s。 
    private final CachedDynamicIntProperty circuitTrippedTimeoutFactor;
    //断路器最大超时秒数（默认使用超时因子计算出来），默认值是30s。 
    private final CachedDynamicIntProperty maxCircuitTrippedTimeout;
    private static final DynamicIntProperty activeRequestsCountTimeout = 
        DynamicPropertyFactory.getInstance().getIntProperty("niws.loadbalancer.serverStats.activeRequestsCount.effectiveWindowSeconds", 60 * 10);
    //百分比，可参见枚举类Percent：[10,20…,90…,99.5]
    private static final double[] PERCENTS = makePercentValues();
    //它是一个DataAccumulator，数据累加器。dataDist按照时间窗口统计
    private DataDistribution dataDist = new DataDistribution(1, PERCENTS); // in case
    //定时publish发布数据，默认1分钟发布一次
    private DataPublisher publisher = null;
    // 它是个Distribution类型，因为它仅仅只需要持续累加数据，然后提供最大最小值、平均值的访问而已
    //oteResponseTime(double msecs)来记录每个请求的响应时间，dataDist按照时间窗口统计，responseTimeDist一直累加。
    private final Distribution responseTimeDist = new Distribution();
    
    int bufferSize = DEFAULT_BUFFER_SIZE;
    int publishInterval = DEFAULT_PUBLISH_INTERVAL;
    
    
    long failureCountSlidingWindowInterval = 1000; 
    
    private MeasuredRate serverFailureCounts = new MeasuredRate(failureCountSlidingWindowInterval);
   // 一个窗口期内的请求总数，窗口期默认为5分钟（300秒） 
    private MeasuredRate requestCountInWindow = new MeasuredRate(300000L);
    
    Server server;
    //总请求数量。每次请求结束/错误时就会+1。
    AtomicLong totalRequests = new AtomicLong();
    //连续（successive）请求异常数量（这个连续发生在Retry重试期间）。 ,
    //对失败的判断逻辑
    @VisibleForTesting
    AtomicInteger successiveConnectionFailureCount = new AtomicInteger(0);
//    活跃请求数量（正在请求的数量，它能反应该Server的负载、压力）。 
//    但凡只要开始执行Sever了，就+1
//    但凡只要请求完成了/出错了，就-1
    @VisibleForTesting
    AtomicInteger activeRequestsCount = new AtomicInteger(0);
    //暂无任何使用处，可忽略。
    @VisibleForTesting
    AtomicInteger openConnectionsCount = new AtomicInteger(0);
    //最后一次失败的时间戳。至于什么叫失败，参考
    private volatile long lastConnectionFailedTimestamp;
    //简单的说就是activeRequestsCount的值最后变化的时间戳
    private volatile long lastActiveRequestsCountChangeTimestamp;
    //断路器断电总时长（连续失败>=3次，增加20~30秒。具体增加多少秒，后面有计算逻辑）。
    private AtomicLong totalCircuitBreakerBlackOutPeriod = new AtomicLong(0);
   // 最后访问时间戳。和lastActiveRequestsCountChangeTimestamp的区别是，它增/减都update一下，而lastAccessedTimestamp只有在增的时候才会update一下。
    private volatile long lastAccessedTimestamp;
    //首次连接时间戳，只会记录首次请求进来时的时间。
    private volatile long firstConnectionTimestamp = 0;
	// 默认构造器：connectionFailureThreshold等参数均使用默认值 该构造器默认无人调用

    public ServerStats() {
    	
        connectionFailureThreshold = new CachedDynamicIntProperty(
                "niws.loadbalancer.default.connectionFailureCountThreshold", 3);        
        circuitTrippedTimeoutFactor = new CachedDynamicIntProperty(
                "niws.loadbalancer.default.circuitTripTimeoutFactorSeconds", 10);

        maxCircuitTrippedTimeout = new CachedDynamicIntProperty(
                "niws.loadbalancer.default.circuitTripMaxTimeoutSeconds", 30);
    }
    // 参数值来自于lbStats，可以和ClientName挂上钩
 	// 它在LoadBalancerStats#createServerStats()方法里被唯一调用
    public ServerStats(LoadBalancerStats lbStats) {
//    	默认值配置：niws.loadbalancer.default.connectionFailureCountThreshold此key指定
//    	个性化配置："niws.loadbalancer." + name + ".connectionFailureCountThreshold"
        this.maxCircuitTrippedTimeout = lbStats.getCircuitTripMaxTimeoutSeconds();
        this.circuitTrippedTimeoutFactor = lbStats.getCircuitTrippedTimeoutFactor();
        this.connectionFailureThreshold = lbStats.getConnectionFailureCountThreshold();
    }
    
    /**初始化对象，开始数据收集和报告。**请务必调用此方法** 它才是一个完整的实例
     * Initializes the object, starting data collection and reporting.
     */
    public void initialize(Server server) {
        serverFailureCounts = new MeasuredRate(failureCountSlidingWindowInterval);
        requestCountInWindow = new MeasuredRate(300000L);
        if (publisher == null) {
            dataDist = new DataDistribution(getBufferSize(), PERCENTS);
            publisher = new DataPublisher(dataDist, getPublishIntervalMillis());
            // 启动任务：开始发布数据。1分钟发布一次
            publisher.start();
        }
        // 和Server关联
        this.server = server;
    }
    // 停止数据方法
    public void close() {
        if (publisher != null)
            publisher.stop();
    }

    public Server getServer() {
        return server;
    }

    private int getBufferSize() {
        return bufferSize;
    }

    private long getPublishIntervalMillis() {
        return publishInterval;
    }
    
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public void setPublishInterval(int publishInterval) {
        this.publishInterval = publishInterval;
    }

    /**
     * The supported percentile values.
     * These correspond to the various Monitor methods defined below.
     * No, this is not pretty, but that's the way it is.
     */
    private static enum Percent {

        TEN(10), TWENTY_FIVE(25), FIFTY(50), SEVENTY_FIVE(75), NINETY(90),
        NINETY_FIVE(95), NINETY_EIGHT(98), NINETY_NINE(99), NINETY_NINE_POINT_FIVE(99.5);

        private double val;

        Percent(double val) {
            this.val = val;
        }

        public double getValue() {
            return val;
        }

    }

    private static double[] makePercentValues() {
        Percent[] percents = Percent.values();
        double[] p = new double[percents.length];
        for (int i = 0; i < percents.length; i++) {
            p[i] = percents[i].getValue();
        }
        return p;
    }

    public long getFailureCountSlidingWindowInterval() {
        return failureCountSlidingWindowInterval;
    }

    public void setFailureCountSlidingWindowInterval(
            long failureCountSlidingWindowInterval) {
        this.failureCountSlidingWindowInterval = failureCountSlidingWindowInterval;
    }

    // run time methods
    
    
    /**
     * Increment the count of failures for this Server
     * 
     */
    public void addToFailureCount(){
        serverFailureCounts.increment();
    }
    
    /**
     * Returns the count of failures in the current window
     * 
     */
    public long getFailureCount(){
        return serverFailureCounts.getCurrentCount();
    }
    
    /**收集每一次请求的响应时间
     * Call this method to note the response time after every request
     * @param msecs
     */
    public void noteResponseTime(double msecs){
        dataDist.noteValue(msecs);
        responseTimeDist.noteValue(msecs);
    }
    
    public void incrementNumRequests(){
        totalRequests.incrementAndGet();
    }
    
    public void incrementActiveRequestsCount() {        
        activeRequestsCount.incrementAndGet();
        requestCountInWindow.increment();
        long currentTime = System.currentTimeMillis();
        lastActiveRequestsCountChangeTimestamp = currentTime;
        lastAccessedTimestamp = currentTime;
        if (firstConnectionTimestamp == 0) {
            firstConnectionTimestamp = currentTime;
        }
    }

    public void incrementOpenConnectionsCount() {
        openConnectionsCount.incrementAndGet();
    }

    public void decrementActiveRequestsCount() {
        if (activeRequestsCount.decrementAndGet() < 0) {
            activeRequestsCount.set(0);
        }
        lastActiveRequestsCountChangeTimestamp = System.currentTimeMillis();
    }

    public void decrementOpenConnectionsCount() {
        if (openConnectionsCount.decrementAndGet() < 0) {
            openConnectionsCount.set(0);
        }
    }
   // 获得当前时间的活跃请求数（也就是Server的当前负载）
    public int  getActiveRequestsCount() {
        return getActiveRequestsCount(System.currentTimeMillis());
    }
    // 强调：如果当前时间currentTime距离上一次请求进来已经超过了时间窗口60s，那就返回0
    // 简单一句话：如果上次请求距今1分钟了，那就一个请求都不算（强制归零）
    public int getActiveRequestsCount(long currentTime) {
        int count = activeRequestsCount.get();
        if (count == 0) {
            return 0;
        } else if (currentTime - lastActiveRequestsCountChangeTimestamp > activeRequestsCountTimeout.get() * 1000 || count < 0) {
            activeRequestsCount.set(0);
            return 0;            
        } else {
            return count;
        }
    }

    public int getOpenConnectionsCount() {
        return openConnectionsCount.get();
    }

    
    public long getMeasuredRequestsCount() {
        return requestCountInWindow.getCount();
    }

    @Monitor(name="ActiveRequestsCount", type = DataSourceType.GAUGE)    
    public int getMonitoredActiveRequestsCount() {
        return activeRequestsCount.get();
    }
    //此方法不仅判断了断路器的打开与否，若打开顺便打开断路器应该打开多长时间（单位s）的方法，有了这个方法的理论做支撑，判断当前断路器是否开启就非常简单了：
    @Monitor(name="CircuitBreakerTripped", type = DataSourceType.INFORMATIONAL)    
    public boolean isCircuitBreakerTripped() {
        return isCircuitBreakerTripped(System.currentTimeMillis());
    }
    
    public boolean isCircuitBreakerTripped(long currentTime) {
        long circuitBreakerTimeout = getCircuitBreakerTimeout();
        if (circuitBreakerTimeout <= 0) {
            return false;
        }
        return circuitBreakerTimeout > currentTime;
    }

    //本处的断路器解释：当有某个服务存在多个实例时，在请求的过程中，
    //负载均衡器会统计每次请求的情况（请求响应时间，是否发生网络异常等），
    //当出现了请求出现累计重试时，负载均衡器会标识当前服务实例，设置当前服务实例的断路的时间区间，
    //在此区间内，当请求过来时，负载均衡器会将此服务实例从可用服务实例列表中暂时剔除（其实就是暂时忽略此Server），优先选择其他服务实例。
    //该断路器和Hystrix无任何关系，无任何关系，无任何关系。它是ServerStats内部维护的一套熔断机制，体现在如下方法上：
    private long getCircuitBreakerTimeout() {
    	// 看看该断路器到哪个时间点戒指（关闭）的时刻时间戳
    	// 比如断路器要从0点开30s，那么返回值就是00:00:30s这个时间戳呗
        long blackOutPeriod = getCircuitBreakerBlackoutPeriod();
        if (blackOutPeriod <= 0) {
            return 0;
        }
        return lastConnectionFailedTimestamp + blackOutPeriod;
    }
//    断路器如何闭合？
//    倘若断路器打开了，它如何恢复呢？有如下3种情形它会恢复到正常状态：
//
//    不是连续失败了，也就是成功了一次，那么successiveConnectionFailureCount就会立马归0，所以熔断器就闭合了
//    即使请求失败了，但是并非是断路器类异常，即不是RetryHandler#isCircuitTrippingException这种类型的异常时（比如RuntimeException就不是这种类型的异常），那就也不算连续失败，所以也就闭合了
//    到时间了，断路器自然就自动闭合了
    private long getCircuitBreakerBlackoutPeriod() {
        int failureCount = successiveConnectionFailureCount.get();
        int threshold = connectionFailureThreshold.get();
        if (failureCount < threshold) {
            return 0;
        }
        int diff = (failureCount - threshold) > 16 ? 16 : (failureCount - threshold);
        int blackOutSeconds = (1 << diff) * circuitTrippedTimeoutFactor.get();
        if (blackOutSeconds > maxCircuitTrippedTimeout.get()) {
            blackOutSeconds = maxCircuitTrippedTimeout.get();
        }
        return blackOutSeconds * 1000L;
    }
    
    public void incrementSuccessiveConnectionFailureCount() {
        lastConnectionFailedTimestamp = System.currentTimeMillis();
        successiveConnectionFailureCount.incrementAndGet();
        totalCircuitBreakerBlackOutPeriod.addAndGet(getCircuitBreakerBlackoutPeriod());
    }
    
    public void clearSuccessiveConnectionFailureCount() {
        successiveConnectionFailureCount.set(0);
    }
    
    
    @Monitor(name="SuccessiveConnectionFailureCount", type = DataSourceType.GAUGE)
    public int getSuccessiveConnectionFailureCount() {
        return successiveConnectionFailureCount.get();
    }
    
    /*
     * Response total times
     */

    /**重要。获取累计的，累计的，平均响应时间
	    responseTimeDist里获得的均是所有请求累计的
     * Gets the average total amount of time to handle a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisAvg", type = DataSourceType.INFORMATIONAL,
             description = "Average total time for a request, in milliseconds")
    public double getResponseTimeAvg() {
        return responseTimeDist.getMean();
    }

    /**
     * Gets the maximum amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisMax", type = DataSourceType.INFORMATIONAL,
             description = "Max total time for a request, in milliseconds")
    public double getResponseTimeMax() {
        return responseTimeDist.getMaximum();
    }

    /**
     * Gets the minimum amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisMin", type = DataSourceType.INFORMATIONAL,
             description = "Min total time for a request, in milliseconds")
    public double getResponseTimeMin() {
        return responseTimeDist.getMinimum();
    }

    /**
     * Gets the standard deviation in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "OverallResponseTimeMillisStdDev", type = DataSourceType.INFORMATIONAL,
             description = "Standard Deviation in total time to handle a request, in milliseconds")
    public double getResponseTimeStdDev() {
        return responseTimeDist.getStdDev();
    }

    /*
     * QOS percentile performance data for most recent period
     */

    /**样本大小（每次获取的值可能不一样的哦，因为dataDist是时间窗口嘛）
     * Gets the number of samples used to compute the various response-time percentiles.
     */
    @Monitor(name = "ResponseTimePercentileNumValues", type = DataSourceType.GAUGE,
             description = "The number of data points used to compute the currently reported percentile values")
    public int getResponseTimePercentileNumValues() {
        return dataDist.getSampleSize();
    }

    /**
     * Gets the time when the varios percentile data was last updated.
     */
    @Monitor(name = "ResponseTimePercentileWhen", type = DataSourceType.INFORMATIONAL,
             description = "The time the percentile values were computed")
    public String getResponseTimePercentileTime() {
        return dataDist.getTimestamp();
    }

    /**
     * Gets the time when the varios percentile data was last updated,
     * in milliseconds since the epoch.
     */
    @Monitor(name = "ResponseTimePercentileWhenMillis", type = DataSourceType.COUNTER,
             description = "The time the percentile values were computed in milliseconds since the epoch")
    public long getResponseTimePercentileTimeMillis() {
        return dataDist.getTimestampMillis();
    }

    /**这段时间窗口内（1分钟）的平均响应时间
     * Gets the average total amount of time to handle a request
     * in the recent time-slice, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillisAvg", type = DataSourceType.GAUGE,
             description = "Average total time for a request in the recent time slice, in milliseconds")
    public double getResponseTimeAvgRecent() {
        return dataDist.getMean();
    }
    
    /**
     * Gets the 10-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis10Percentile", type = DataSourceType.INFORMATIONAL,
             description = "10th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime10thPercentile() {
        return getResponseTimePercentile(Percent.TEN);
    }

    /**
     * Gets the 25-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis25Percentile", type = DataSourceType.INFORMATIONAL,
             description = "25th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime25thPercentile() {
        return getResponseTimePercentile(Percent.TWENTY_FIVE);
    }

    /**
     * Gets the 50-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis50Percentile", type = DataSourceType.INFORMATIONAL,
             description = "50th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime50thPercentile() {
        return getResponseTimePercentile(Percent.FIFTY);
    }

    /**
     * Gets the 75-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis75Percentile", type = DataSourceType.INFORMATIONAL,
             description = "75th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime75thPercentile() {
        return getResponseTimePercentile(Percent.SEVENTY_FIVE);
    }

    /**
     * Gets the 90-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis90Percentile", type = DataSourceType.INFORMATIONAL,
             description = "90th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime90thPercentile() {
        return getResponseTimePercentile(Percent.NINETY);
    }

    /**
     * Gets the 95-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis95Percentile", type = DataSourceType.GAUGE,
             description = "95th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime95thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_FIVE);
    }

    /**
     * Gets the 98-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis98Percentile", type = DataSourceType.INFORMATIONAL,
             description = "98th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime98thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_EIGHT);
    }

    /**
     * Gets the 99-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis99Percentile", type = DataSourceType.GAUGE,
             description = "99th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime99thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_NINE);
    }

    /**
     * Gets the 99.5-th percentile in the total amount of time spent handling a request, in milliseconds.
     */
    @Monitor(name = "ResponseTimeMillis99_5Percentile", type = DataSourceType.GAUGE,
             description = "99.5th percentile in total time to handle a request, in milliseconds")
    public double getResponseTime99point5thPercentile() {
        return getResponseTimePercentile(Percent.NINETY_NINE_POINT_FIVE);
    }

    public long getTotalRequestsCount() {
        return totalRequests.get();
    }
    
    private double getResponseTimePercentile(Percent p) {
        return dataDist.getPercentiles()[p.ordinal()];
    }
    
    public String toString(){
        StringBuilder sb = new StringBuilder();
        
        sb.append("[Server:" + server + ";");
        sb.append("\tZone:" + server.getZone() + ";");
        sb.append("\tTotal Requests:" + totalRequests + ";");
        sb.append("\tSuccessive connection failure:" + getSuccessiveConnectionFailureCount() + ";");
        if (isCircuitBreakerTripped()) {
            sb.append("\tBlackout until: " + new Date(getCircuitBreakerTimeout()) + ";");
        }
        sb.append("\tTotal blackout seconds:" + totalCircuitBreakerBlackOutPeriod.get() / 1000 + ";");
        sb.append("\tLast connection made:" + new Date(lastAccessedTimestamp) + ";");
        if (lastConnectionFailedTimestamp > 0) {
            sb.append("\tLast connection failure: " + new Date(lastConnectionFailedTimestamp)  + ";");
        }
        sb.append("\tFirst connection made: " + new Date(firstConnectionTimestamp)  + ";");
        sb.append("\tActive Connections:" + getMonitoredActiveRequestsCount()  + ";");
        sb.append("\ttotal failure count in last (" + failureCountSlidingWindowInterval + ") msecs:" + getFailureCount()  + ";");
        sb.append("\taverage resp time:" + getResponseTimeAvg()  + ";");
        sb.append("\t90 percentile resp time:" + getResponseTime90thPercentile()  + ";");
        sb.append("\t95 percentile resp time:" + getResponseTime95thPercentile()  + ";");
        sb.append("\tmin resp time:" + getResponseTimeMin()  + ";");
        sb.append("\tmax resp time:" + getResponseTimeMax()  + ";");
        sb.append("\tstddev resp time:" + getResponseTimeStdDev());
        sb.append("]\n");
        
        return sb.toString();
    }
    
    public static void main(String[] args){
        ServerStats ss = new ServerStats();
        ss.setBufferSize(1000);
        ss.setPublishInterval(1000);
        ss.initialize(new Server("stonse", 80));
        
        Random r = new Random(1459834);
        for (int i=0; i < 99; i++){
            double rl = r.nextDouble() * 25.2;
            ss.noteResponseTime(rl);
            ss.incrementNumRequests();
            try {
                Thread.sleep(100);
                System.out.println("ServerStats:avg:" + ss.getResponseTimeAvg());
                System.out.println("ServerStats:90 percentile:" + ss.getResponseTime90thPercentile());
                System.out.println("ServerStats:90 percentile:" + ss.getResponseTimePercentileNumValues());
                
            } catch (InterruptedException e) {
                
            }
           
        }
        System.out.println("done ---");
        ss.publisher.stop();
        
        System.out.println("ServerStats:" + ss);
     
        
    }
    
}
