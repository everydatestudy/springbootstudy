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
package com.netflix.stats.distribution;


/**因为Ribbon做负载均衡需要统计各维度的Server指标数据，使用的是自家的netflix-statistics模块完成的，
 * 该模块旨在简化指标数据收集、计算的逻辑，小巧精悍，因此本文就用不长的文字学习下它，
 * 并且最后附上：基于netflix-statistics手把手写个超简版监控系统。 
 * An object that collects new values incrementally.
 *数据收集，以增量方式收集新值。
 * @author netflixoss
 * @version $Revision: $
 */
public interface DataCollector {

    /**
     * Adds a value to the collected data.
     * This must run very quickly, and so can safely
     * be called in time-critical code.
     */
    void noteValue(double val);

} // DataCollector
