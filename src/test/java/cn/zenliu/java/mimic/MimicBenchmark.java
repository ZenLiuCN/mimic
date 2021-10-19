package cn.zenliu.java.mimic;

import lombok.AllArgsConstructor;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static cn.zenliu.java.mimic.MimicBenchmark.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(
    batchSize = warmUpBatchSize,
    iterations = warmUpIterations,
    time = warmUpTime,
    timeUnit = TimeUnit.MILLISECONDS)
@Measurement(
    iterations = measurementIterations,
    time = measurementTime,
    timeUnit = TimeUnit.MILLISECONDS)
@Timeout(
    time = timeoutTime,
    timeUnit = TimeUnit.SECONDS)
@Fork(fork)
@Threads(threads)
public class MimicBenchmark {
    final static int warmUpBatchSize = 5;
    final static int warmUpIterations = 10;
    final static int warmUpTime = 10;
    final static int measurementTime = 5;
    final static int measurementIterations = 10;
    final static int timeoutTime = 60;
    final static int fork = 5;
    final static int threads = 5;


    /**
     * <pre>
     * <b>I7-4790K@4.00GHz 16G</b>
     *
     * Benchmark                                   Mode  Cnt      Score      Error  Units
     * MimicBenchmark.mimicBenchAsmBuild           avgt   50    792.614 ±  109.759  ns/op
     * MimicBenchmark.mimicBenchAsmGet             avgt   50      3.190 ±    0.156  ns/op
     * MimicBenchmark.mimicBenchAsmGetConv         avgt   50      3.818 ±    0.185  ns/op
     * MimicBenchmark.mimicBenchAsmSet             avgt   50     15.776 ±    0.665  ns/op
     * MimicBenchmark.mimicBenchAsmSetConv         avgt   50     18.637 ±    0.825  ns/op
     * MimicBenchmark.mimicBenchPojoBuild          avgt   50     71.325 ±   11.814  ns/op
     * MimicBenchmark.mimicBenchPojoGet            avgt   50      3.179 ±    0.168  ns/op
     * MimicBenchmark.mimicBenchPojoGetConv        avgt   50     14.621 ±    0.500  ns/op
     * MimicBenchmark.mimicBenchPojoSet            avgt   50      9.926 ±    0.495  ns/op
     * MimicBenchmark.mimicBenchPojoSetConv        avgt   50     55.036 ±    6.174  ns/op
     * MimicBenchmark.mimicBenchProxyBuild         avgt   50    385.281 ±   71.375  ns/op
     * MimicBenchmark.mimicBenchProxyGet           avgt   50     21.378 ±    1.250  ns/op
     * MimicBenchmark.mimicBenchProxyGetConv       avgt   50     35.102 ±    1.934  ns/op
     * MimicBenchmark.mimicBenchProxySet           avgt   50     55.353 ±    4.208  ns/op
     * MimicBenchmark.mimicBenchProxySetConv       avgt   50     90.640 ±    6.061  ns/op
     * MimicBenchmark.mimicBenchAsmBuildOneShot      ss   50  24174.228 ± 4984.977  ns/op
     * MimicBenchmark.mimicBenchPojoBuildOneShot     ss   50   1399.364 ±  250.398  ns/op
     * MimicBenchmark.mimicBenchProxyBuildOneShot    ss   50  20351.224 ± 3494.686  ns/op
     * </pre>
     */

    @Test
    void proxyMimicBench() throws RunnerException {
        new Runner(new OptionsBuilder()
            .threads(threads)
            .forks(fork)
            .shouldFailOnError(true)
            .shouldDoGC(true)
            .build()).run();
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

    @State(Scope.Thread)
    public static class BenchmarkPojoState {
        MimicTest.Flue instance;
        long id = (10L);
        String identity = "10";
        String idOfUser = "10";
        BigDecimal user = BigDecimal.valueOf(10);

        @Setup(Level.Trial)
        public void initialize() {
            instance = FlueImpl.of(id, identity, idOfUser, user);

        }
    }

    @AllArgsConstructor(staticName = "of")
    static class FlueImpl implements MimicTest.Flue {
        private long id;
        private String identity;
        private String idOfUser;
        private BigDecimal user;
        private final Set<String> changes = new HashSet<>(4);

        @Override
        public @NotNull Map<String, Object> underlyingMap() {
            val m = new HashMap<String, Object>();
            m.put(" id", id);
            m.put("identity", identity);
            m.put("idOfUser", idOfUser);
            m.put("user", user);
            return m;
        }

        @Override
        public @NotNull Set<String> underlyingChangedProperties() {
            return changes;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public void id(long val) {
            changes.add("id");
            this.id = val;
        }

        @Override
        public MimicTest.Fluent identity(Long val) {
            identity = Long.toString(Objects.requireNonNull(val, "identity must not null"));
            changes.add("identity");
            return this;
        }

        @Override
        public Long idOfUser() {
            return idOfUser == null ? null : Long.parseLong(idOfUser);
        }

        @Override
        public MimicTest.Fluent idOfUser(Long val) {
            idOfUser = val == null ? null : Long.toString(val);
            changes.add("idOfUser");
            return this;
        }

        @Override
        public Long identity() {
            return Long.parseLong(identity);
        }

        @Override
        public BigDecimal user() {
            return user;
        }

        @Override
        public MimicTest.Flue user(BigDecimal val) {
            user = val;
            changes.add("user");
            return this;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void mimicBenchProxyBuildOneShot(BenchmarkState st, Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, st.data));
    }

    @Benchmark
    public void mimicBenchProxyBuild(BenchmarkState st, Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, st.data));
    }

    @Benchmark
    public void mimicBenchProxySet(BenchmarkState st, Blackhole bh) {
        st.instance.id(100L);
    }

    @Benchmark
    public void mimicBenchProxySetConv(BenchmarkState st, Blackhole bh) {
        bh.consume(st.instance.identity(100L));
    }

    @Benchmark
    public void mimicBenchProxyGet(BenchmarkState st, Blackhole bh) {
        bh.consume(st.instance.id());
    }

    @Benchmark
    public void mimicBenchProxyGetConv(BenchmarkState st, Blackhole bh) {
        bh.consume(st.instance.identity());
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void mimicBenchAsmBuildOneShot(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, st.data));
    }

    @Benchmark
    public void mimicBenchAsmBuild(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(Mimic.newInstance(MimicTest.Flue.class, st.data));
    }

    @Benchmark
    public void mimicBenchAsmSet(BenchmarkAsmState st, Blackhole bh) {
        st.instance.id(100L);
    }

    @Benchmark
    public void mimicBenchAsmSetConv(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(st.instance.identity(100L));
    }

    @Benchmark
    public void mimicBenchAsmGet(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(st.instance.id());
    }

    @Benchmark
    public void mimicBenchAsmGetConv(BenchmarkAsmState st, Blackhole bh) {
        bh.consume(st.instance.identity());
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public void mimicBenchPojoBuildOneShot(BenchmarkPojoState st, Blackhole bh) {
        bh.consume(FlueImpl.of(st.id, st.identity, st.idOfUser, st.user));
    }

    @Benchmark
    public void mimicBenchPojoBuild(BenchmarkPojoState st, Blackhole bh) {
        bh.consume(FlueImpl.of(st.id, st.identity, st.idOfUser, st.user));
    }

    @Benchmark
    public void mimicBenchPojoSet(BenchmarkPojoState st, Blackhole bh) {
        st.instance.id(100L);
    }

    @Benchmark
    public void mimicBenchPojoSetConv(BenchmarkPojoState st, Blackhole bh) {
        bh.consume(st.instance.identity(100L));
    }

    @Benchmark
    public void mimicBenchPojoGet(BenchmarkPojoState st, Blackhole bh) {
        bh.consume(st.instance.id());
    }

    @Benchmark
    public void mimicBenchPojoGetConv(BenchmarkPojoState st, Blackhole bh) {
        bh.consume(st.instance.identity());
    }

}
