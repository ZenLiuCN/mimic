package cn.zenliu.java.mimic;

import lombok.val;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class MimicTest {
    static HashMap<Class<?>, Mimic.Factory<?>> cache = new HashMap<>();
    //loader cache simulation
    final static AtomicReference<Function<Class<?>, Mimic.Factory<?>>> supplier = new AtomicReference<>();

    static {
        supplier.set(c -> cache.computeIfAbsent(c, supplier.get()));
    }

    @Mimic.Configuring.Mimicked(fluent = true, concurrent = true)
    interface Simple extends Mimic<Simple> {
        Integer integer();

        Simple integer(int integer);
    }

    @Test
    void simpleFluent() {
        val f = Mimic.Factory.factory(Simple.class, supplier.get());
        val instance = f.build(null);
        instance.integer(1);
        val m = instance.underlyingMap();
        m.put(2, 3);
        assertEquals(1, instance.integer());
        assertEquals(m, instance.integer(2).underlyingMap());
        assertEquals(new ArrayList<>(Collections.singletonList("integer")), instance.underlyingNaming());
        assertEquals(Simple.class.getSimpleName() + "@" + instance.hashCode() + "@" + m.toString(), instance.toString());
        assertEquals(Simple.class, instance.underlyingType());
        assertNotEquals(f.build(null).integer(2), instance);
        m.remove(2);
        assertEquals(f.build(null).integer(2), instance);
    }

    @Mimic.Configuring.Mimicked
    interface SimpleBean extends Mimic<SimpleBean> {
        Integer getInteger();

        SimpleBean setInteger(Integer integer);
    }

    @Test
    void simpleJavaBean() {
        val f = Mimic.Factory.factory(SimpleBean.class, supplier.get());
        val instance = f.build(null);
        instance.setInteger(1);
        val m = instance.underlyingMap();
        m.put(2, 3);
        assertEquals(1, instance.getInteger());
        assertEquals(m, instance.setInteger(2).underlyingMap());
        assertEquals(new ArrayList<>(Collections.singletonList("integer")), instance.underlyingNaming());
        assertEquals(SimpleBean.class.getSimpleName() + "@" + instance.hashCode() + "@" + m.toString(), instance.toString());
        assertEquals(SimpleBean.class, instance.underlyingType());
        assertNotEquals(f.build(null).setInteger(2), instance);
        m.remove(2);
        assertEquals(f.build(null).setInteger(2), instance);
    }

    @Mimic.Configuring.Mimicked(fluent = true)
    interface SimpleValidate extends Mimic<SimpleValidate> {
        Validating.Validator<Integer> validate = i -> i != null && i > 0;

        Integer integer();

        @Validating.Validate(value = SimpleValidate.class, message = "must positive")
        SimpleValidate integer(int integer);
    }

    @Test
    void simpleValidate() {
        val f = Mimic.Factory.factory(SimpleValidate.class, supplier.get());
        val instance = f.build(null);
        instance.integer(1);
        val m = instance.underlyingMap();
        m.put(2, 3);
        assertEquals(1, instance.integer());
        assertEquals(m, instance.integer(2).underlyingMap());
        assertEquals(new ArrayList<>(Collections.singletonList("integer")), instance.underlyingNaming());
        assertEquals(SimpleValidate.class.getSimpleName() + "@" + instance.hashCode() + "@" + m.toString(), instance.toString());
        assertEquals(SimpleValidate.class, instance.underlyingType());
        assertNotEquals(f.build(null).integer(2), instance);
        m.remove(2);
        assertEquals(f.build(null).integer(2), instance);
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                instance.integer(-2);
            } catch (Exception e) {
                // e.printStackTrace();
                throw e;
            }
        });
        assertDoesNotThrow(instance::validate);
    }

    @Mimic.Configuring.Mimicked(fluent = true)
    @Mimic.Converting.Converter(value = Integer.class, holder = SimpleConvValidate.class)
    interface SimpleConvValidate extends Mimic<SimpleConvValidate> {
        Validating.Validator<Integer> validate = i -> i != null && i > 0;
        //        Converting.Deserialize<Integer> deserialize=i-> i == Converting.NULL ?null:Integer.parseInt(new String(i),16);
//        Converting.Serialize<Integer> serialize=i->i==null?Converting.NULL:Integer.toHexString(i).getBytes(StandardCharsets.UTF_8);
        Converting.Deserialize<Integer> deserialize = i -> i.equals(Converting.NULL) ? null : Integer.parseInt(i, 16);
        Converting.Serialize<Integer> serialize = i -> i == null ? Converting.NULL : Integer.toHexString(i);

        Integer integer();

        @Validating.Validate(value = SimpleConvValidate.class, message = "must positive")
        SimpleConvValidate integer(int integer);
    }

    @Test
    void converterValidate() {
        val f = Mimic.Factory.factory(SimpleConvValidate.class, supplier.get());
        val instance = f.build(null);
        instance.integer(1);
        val m = instance.underlyingMap();
        m.put(2, 3);
        assertEquals(1, instance.integer());
        assertEquals(m, instance.integer(2).underlyingMap());
        assertEquals(new ArrayList<>(Collections.singletonList("integer")), instance.underlyingNaming());
        assertEquals(SimpleConvValidate.class.getSimpleName() + "@" + instance.hashCode() + "@" + m.toString(), instance.toString());
        assertEquals(SimpleConvValidate.class, instance.underlyingType());
        assertNotEquals(f.build(null).integer(2), instance);
        m.remove(2);
        //TODO bytes dose not equal:
        // option one: use String <- current choose
        // option two: just not equal
        assertEquals(f.build(null).integer(2), instance);
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                instance.integer(-2);
            } catch (Exception e) {
                // e.printStackTrace();
                throw e;
            }
        });
        assertDoesNotThrow(instance::validate);
    }

    /**
     * execute benchmark via junit
     * <p><b>result for simple </b>
     * <pre>
     *   Benchmark                              Mode  Cnt   Score    Error  Units
     * MimicTest.benchmarkCreateClassInstant  avgt  100   0.016 ±  0.001  us/op
     * MimicTest.benchmarkCreateInstant       avgt  100   1.688 ±  0.080  us/op
     * MimicTest.benchmarkFactory             avgt  100  38.970 ±  1.395  us/op
     * MimicTest.benchmarkReadClassValue      avgt  100   0.016 ±  0.001  us/op
     * MimicTest.benchmarkReadValue           avgt  100   0.180 ±  0.008  us/op
     * MimicTest.benchmarkSetClassValue       avgt  100   0.013 ±  0.001  us/op
     * MimicTest.benchmarkSetValue            avgt  100   0.306 ±  0.016  us/op
     *
     * Benchmark                              Mode  Cnt   Score   Error  Units
     * MimicTest.benchmarkCreateClassInstant  avgt  100   0.016 ± 0.001  us/op
     * MimicTest.benchmarkCreateInstant       avgt  100   1.531 ± 0.175  us/op
     * MimicTest.benchmarkFactory             avgt  100  37.271 ± 3.475  us/op
     * MimicTest.benchmarkReadClassValue      avgt  100   0.015 ± 0.001  us/op
     * MimicTest.benchmarkReadValue           avgt  100   0.180 ± 0.009  us/op
     * MimicTest.benchmarkSetClassValue       avgt  100   0.012 ± 0.001  us/op
     * MimicTest.benchmarkSetValue            avgt  100   0.229 ± 0.013  us/op
     * <b>int map slightly improve performance</b>
     * Benchmark                              Mode  Cnt   Score   Error  Units
     * MimicTest.benchmarkCreateClassInstant  avgt  100   0.017 ± 0.003  us/op
     * MimicTest.benchmarkCreateInstant       avgt  100   1.695 ± 0.145  us/op
     * MimicTest.benchmarkFactory             avgt  100  34.026 ± 2.288  us/op
     * MimicTest.benchmarkReadClassValue      avgt  100   0.015 ± 0.002  us/op
     * MimicTest.benchmarkReadValue           avgt  100   0.185 ± 0.026  us/op
     * MimicTest.benchmarkSetClassValue       avgt  100   0.013 ± 0.001  us/op
     * MimicTest.benchmarkSetValue            avgt  100   0.193 ± 0.012  us/op
     * </pre>
     */
    @Test
    void benchmark() throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(this.getClass().getName() + ".benchmark*")

            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.MICROSECONDS)

            .warmupTime(TimeValue.seconds(1))
            .warmupIterations(2)

            .measurementTime(TimeValue.microseconds(1))
            .measurementIterations(100)

            .threads(2)
            .forks(1)

            .shouldFailOnError(true)
            .shouldDoGC(true)

            //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
            //.addProfiler(WinPerfAsmProfiler.class)

            .build()).run();
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        Function<Class<?>, Mimic.Factory<?>> supplier;
        Mimic.Factory<Simple> factory;
        Simple instance;
        SimpleImpl classInstance;

        @Setup(Level.Trial)
        public void
        initialize() {
            //noinspection unchecked
            supplier = c -> Mimic.Factory.factory((Class) c, supplier);
            factory = (Mimic.Factory<Simple>) supplier.apply(Simple.class);
            instance = factory.build(null);
            classInstance = new SimpleImpl();
        }
    }

    static class SimpleImpl implements Simple {
        private int i;

        @Override
        public Integer integer() {
            return i;
        }

        @Override
        public Simple integer(int integer) {
            i = integer;
            return this;
        }
    }

    /**
     * annotations for directly invoked with IDE plugins
     */
    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkFactory(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(Mimic.Factory.factory(SimpleConvValidate.class, state.supplier));
    }

    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkCreateInstant(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(state.factory.build(null));
    }

    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkCreateClassInstant(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(new SimpleImpl());
    }

    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkSetValue(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(state.instance.integer(i));
    }

    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkSetClassValue(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(state.classInstance.integer(i));
    }

    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkReadValue(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(state.instance.integer());
    }

    @Warmup(iterations = 2, batchSize = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Fork(value = 2)
    @Measurement(iterations = 20, time = 300, timeUnit = TimeUnit.MILLISECONDS)
    @OperationsPerInvocation(100)
    @Benchmark
    public void benchmarkReadClassValue(BenchmarkState state, Blackhole bh) {
        for (int i = 0; i < 100; i++)
            bh.consume(state.classInstance.integer());
    }

    @Mimic.Configuring.Mimicked(fluent = true)
    @Mimic.Converting.Converter(value = Integer.class, holder = SimpleConvValidate.class)
    interface SimpleConvertedValidated<T extends SimpleConvertedValidated<T>> extends Mimic<T> {
        Integer integer();

        @Validating.Validate(value = SimpleValidate.class, message = "must positive")
        T integer(int integer);
    }

    interface SimpleValidateInherit extends SimpleConvertedValidated<SimpleValidateInherit> {

    }

    @Test
    void testInherit() {
        val f = Mimic.Factory.factory(SimpleValidateInherit.class, supplier.get());
        val instance = f.build(null);
        instance.integer(1);
        val m = instance.underlyingMap();
        m.put(2, 3);
        assertEquals(1, instance.integer());
        assertEquals(m, instance.integer(2).underlyingMap());
        assertEquals(new ArrayList<>(Collections.singletonList("integer")), instance.underlyingNaming());
        assertEquals(SimpleValidateInherit.class.getSimpleName() + "@" + instance.hashCode() + "@" + m.toString(), instance.toString());
        assertEquals(SimpleValidateInherit.class, instance.underlyingType());
        assertNotEquals(f.build(null).integer(2), instance);
        m.remove(2);
        assertEquals(f.build(null).integer(2), instance);
        assertThrows(IllegalArgumentException.class, () -> {
            try {
                instance.integer(-2);
            } catch (Exception e) {
                // e.printStackTrace();
                throw e;
            }
        });
        assertDoesNotThrow(instance::validate);

    }
}