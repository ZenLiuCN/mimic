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
     * <b>Dynamic Proxy</b>
     *     Benchmark                            Mode  Cnt     Score      Error  Units
     * MimicBenchmark.proxyMimicBenchBuild  avgt  100  7745.875 ± 2441.455  ns/op
     * MimicBenchmark.proxyMimicBenchGet    avgt  100   798.277 ±   78.877  ns/op
     * MimicBenchmark.proxyMimicBenchSet    avgt  100   980.545 ±   87.045  ns/op
     * <b>ByteBuddy ASM</b>
     *     Benchmark                            Mode  Cnt     Score      Error  Units
     * MimicBenchmark.proxyMimicBenchBuild  avgt  100  7108.681 ± 2308.896  ns/op
     * MimicBenchmark.proxyMimicBenchGet    avgt  100   752.182 ±  125.494  ns/op
     * MimicBenchmark.proxyMimicBenchSet    avgt  100  2190.566 ±  297.146  ns/op
     * </pre>
     */
    @Test
    void proxyMimic() throws Exception {
        benchRun("mimicBench*", true);
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
            instance = Mimic.newInstance(MimicTest.Flue.class, data);
        }
    }

    @State(Scope.Thread)
    public static class BenchmarkAsmState {
        MimicTest.Flue instance;
        Map<String, Object> data;

        @Setup(Level.Trial)
        public void initialize() {
            data = new HashMap<>();
            data.put("id", 10L);
            data.put("identity", "10");
            data.put("idOfUser", "10");
            data.put("user", BigDecimal.valueOf(10));

            Mimic.ByteASM.enable();
            instance = Mimic.newInstance(MimicTest.Flue.class, data);
        }
    }

    @Benchmark
    public void mimicBenchProxyBuild(BenchmarkState st, Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, null));
    }

    @Benchmark
    public void mimicBenchProxySet(BenchmarkState st, Blackhole bh) {
        bh.consume(st.instance.identity(100L));
    }

    @Benchmark
    public void mimicBenchProxyGet(BenchmarkState st, Blackhole bh) {
        bh.consume(st.instance.id());
    }

    @Benchmark
    public void mimicBenchAsmBuild(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, null));
    }

    @Benchmark
    public void mimicBenchAsmSet(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(st.instance.identity(100L));
    }

    @Benchmark
    public void mimicBenchAsmGet(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(st.instance.id());
    }


}
