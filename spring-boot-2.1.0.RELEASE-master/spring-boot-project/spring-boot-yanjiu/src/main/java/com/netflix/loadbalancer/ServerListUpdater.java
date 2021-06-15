package com.netflix.loadbalancer;



/**我们已经知道ServerList它用于提供Server列表，
 * 而ServerListFilter组件它用于对列表进行过滤，
 * 本文将介绍一个Action组件：ServerListUpdater服务列表更新器。
 * 它像是一个任务调度器，来定时触发相应的动作，它强调的是动作的开始/触发，具体实现它并不关心，
 * 所以在实现里你完全可以结合ServerList和ServerListFilter一起来完成服务列表的维护，实际上也确实是这么做的。
 * strategy for {@link com.netflix.loadbalancer.DynamicServerListLoadBalancer} to use for different ways
 * of doing dynamic server list updates.
 *
 * @author David Liu
 */
public interface ServerListUpdater {

    /**内部接口：函数式接口 实际上执行服务器列表更新的接口 也就是实际的动作
	// 一般使用匿名内部类的形式实现
     * an interface for the updateAction that actually executes a server list update
     */
    public interface UpdateAction {
        void doUpdate();
    }


    /**使用给定的更新操作启动serverList更新程序这个调用应该是幂等的
     * start the serverList updater with the given update action
     * This call should be idempotent.
     *
     * @param updateAction
     */
    void start(UpdateAction updateAction);

    /** 停止服务器列表更新程序。这个调用应该是幂等的
     * stop the serverList updater. This call should be idempotent
     */
    void stop();

    /** ============下面是一些获取执行过程中的信息方法==============
	// 最后更新的时间Date的String表示形式
     * @return the last update timestamp as a {@link java.util.Date} string
     */
    String getLastUpdate();

    /** 自上次更新以来已经过的ms数
     * @return the number of ms that has elapsed since last update
     */
    long getDurationSinceLastUpdateMs();

    /**
     * @return the number of update cycles missed, if valid
     */
    int getNumberMissedCycles();

    /**
     * @return the number of threads used, if vaid
     */
    int getCoreThreads();
}
