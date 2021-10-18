package cn.zenliu.java.mimic;

import lombok.val;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;

public class MimicBenchmark {
    final static int warmUpBatchSize = 20;
    final static int warmUpIterations = 10;
    final static int warmUpTime = 20;
    final static int measurementTime = 20;
    final static int measurementIterations = 20;
    final static int timeoutTime = 20;

    private static void benchRun(@Nullable String methodName, boolean common) throws Exception {
        val opt = new OptionsBuilder()
            .include(MimicBenchmark.class.getName() + (methodName == null || methodName.isEmpty() ? ".*" : "." + methodName));
        //region config
        if (common)
            opt.mode(Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .warmupTime(TimeValue.milliseconds(1))
                .warmupIterations(warmUpIterations)
                .warmupBatchSize(warmUpBatchSize)
                .measurementTime(TimeValue.milliseconds(1))
                .measurementIterations(measurementIterations)
                .threads(5)
                .forks(5)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                //endregion
                ;
        new Runner(opt.build()).run();
    }

    @Test
    void proxyMimic() throws Exception {
        benchRun("proxyMimicBuild", true);
    }

    @State(Scope.Thread)
    public static class BenchmarkState {

        @Setup(Level.Trial)
        public void initialize() {
            Mimic.newInstance(MimicTest.Flue.class, null);
        }
    }

    /**
     * Benchmark                       Mode  Cnt     Score      Error  Units
     * MimicBenchmark.proxyMimicBuild  avgt  100  4242.168 ± 1732.591  ns/op
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = warmUpBatchSize, batchSize = warmUpBatchSize, time = warmUpTime, timeUnit = TimeUnit.MICROSECONDS)
    @Timeout(time = timeoutTime)
    @Measurement(iterations = measurementIterations, time = measurementTime, timeUnit = TimeUnit.MICROSECONDS)
    public void proxyMimicBuild(Blackhole bh) {
        bh.consume( Mimic.newInstance(MimicTest.Flue.class, null));
    }
}
