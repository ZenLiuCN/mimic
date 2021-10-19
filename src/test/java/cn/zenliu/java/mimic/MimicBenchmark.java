package cn.zenliu.java.mimic;

import lombok.val;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.profile.WinPerfAsmProfiler;
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
               /* .jvmArgs("-XX:+UnlockCommercialFeatures")
                .addProfiler(JavaFlightRecorderProfiler.class)*/
                //endregion
                ;
        new Runner(opt.build()).run();
    }

    /**
     * <pre>
     * Benchmark                            Mode  Cnt     Score      Error  Units
     * MimicBenchmark.mimicBenchAsmBuild    avgt  100  1724.891 ±  799.236  ns/op
     * MimicBenchmark.mimicBenchAsmGet      avgt  100   736.983 ±  112.711  ns/op
     * MimicBenchmark.mimicBenchAsmSet      avgt  100   994.860 ±  111.333  ns/op
     * MimicBenchmark.mimicBenchProxyBuild  avgt  100  6686.670 ± 2496.421  ns/op
     * MimicBenchmark.mimicBenchProxyGet    avgt  100   647.080 ±   79.853  ns/op
     * MimicBenchmark.mimicBenchProxySet    avgt  100   910.694 ±  101.106  ns/op
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
