package cn.zenliu.java.mimic;

import lombok.val;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
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

    /**
     * <pre>
     *
     * <b>Dynamic Proxy Mode (default mode)</b>
     * Benchmark                            Mode  Cnt      Score       Error  Units
     * MimicBenchmark.proxyMimicBenchBuild  avgt  100  11371.355 ± 22272.873  ns/op
     * MimicBenchmark.proxyMimicBenchGet    avgt  100    108.670 ±   162.241  ns/op
     * MimicBenchmark.proxyMimicBenchSet    avgt  100    229.266 ±    90.482  ns/op
     * <b>ASM Lazy Mode</b>
     * Benchmark                            Mode  Cnt     Score      Error  Units
     * MimicBenchmark.proxyMimicBenchBuild  avgt  100  3412.902 ± 7204.948  ns/op
     * MimicBenchmark.proxyMimicBenchGet    avgt  100    42.158 ±   11.405  ns/op
     * MimicBenchmark.proxyMimicBenchSet    avgt  100  1727.545 ± 5428.073  ns/op
     * <b>ASM Eager Mode</b>
     * Benchmark                            Mode  Cnt     Score      Error  Units
     * MimicBenchmark.proxyMimicBenchBuild  avgt  100  1973.709 ± 2154.795  ns/op
     * MimicBenchmark.proxyMimicBenchGet    avgt  100    19.943 ±    8.123  ns/op
     * MimicBenchmark.proxyMimicBenchSet    avgt  100   115.697 ±   27.901  ns/op
     * </pre>
     */
    @Test
    void proxyMimic() throws Exception {
        benchRun("proxyMimicBench*", true);
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        MimicTest.Flue instance;
        Map<String, Object> data;

        @Setup(Level.Trial)
        public void initialize() {
            data = new HashMap<>();
            data.put("id", 10L);
            data.put("identity", "10");
            data.put("idOfUser", "10");
            data.put("user", BigDecimal.valueOf(10));

            //Mimic.ByteASM.enable(true);
            instance = Mimic.newInstance(MimicTest.Flue.class, data);
        }
    }


    /**
     * <pre>
     *     Benchmark                       Mode  Cnt     Score      Error  Units
     *      * MimicBenchmark.proxyMimicBuild  avgt  100  4242.168 ± 1732.591  ns/op
     * </pre>
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = warmUpBatchSize, batchSize = warmUpBatchSize, time = warmUpTime, timeUnit = TimeUnit.MICROSECONDS)
    @Timeout(time = timeoutTime)
    @Measurement(iterations = measurementIterations, time = measurementTime, timeUnit = TimeUnit.MICROSECONDS)
    public void proxyMimicBenchBuild(Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, null));
    }

    /**
     * <pre>
     *
     * </pre>
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = warmUpBatchSize, batchSize = warmUpBatchSize, time = warmUpTime, timeUnit = TimeUnit.MICROSECONDS)
    @Timeout(time = timeoutTime)
    @Measurement(iterations = measurementIterations, time = measurementTime, timeUnit = TimeUnit.MICROSECONDS)
    public void proxyMimicBenchSet(BenchmarkState st, Blackhole bh) {
        st.instance.id(100L);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @Warmup(iterations = warmUpBatchSize, batchSize = warmUpBatchSize, time = warmUpTime, timeUnit = TimeUnit.MICROSECONDS)
    @Timeout(time = timeoutTime)
    @Measurement(iterations = measurementIterations, time = measurementTime, timeUnit = TimeUnit.MICROSECONDS)
    public void proxyMimicBenchGet(BenchmarkState st, Blackhole bh) {
        bh.consume(st.instance.id());
    }


}
