package com.uco.rs.evaluator;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.uco.rs.util.Reporter;
import org.apache.mahout.cf.taste.impl.common.RunningAverageAndStdDev;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copy of the implementation of Mahout since it is private in Mahout
 */
final class StatsCallable implements Callable<Void> {

    private final Callable<Void> delegate;
    private final boolean logStats;
    private final RunningAverageAndStdDev timing;
    private final AtomicInteger noEstimateCounter;
    private final Reporter reporter;

    StatsCallable(Callable<Void> delegate, boolean logStats, RunningAverageAndStdDev timing,
                  AtomicInteger noEstimateCounter, Reporter reporter) {
        this.delegate = delegate;
        this.logStats = logStats;
        this.timing = timing;
        this.noEstimateCounter = noEstimateCounter;
        this.reporter = reporter;
    }

    @Override
    public Void call() throws Exception {
        long start = System.currentTimeMillis();
        delegate.call();
        long end = System.currentTimeMillis();
        timing.addDatum(end - start);
        if (logStats) {
            Runtime runtime = Runtime.getRuntime();
            int average = (int) timing.getAverage();
            long totalMemory = runtime.totalMemory();
            long memory = totalMemory - runtime.freeMemory();
            reporter.addStats(average, totalMemory, memory, noEstimateCounter);
        }
        return null;
    }

}
