package cn.zenliu.java.mimic;


import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.Field;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Sneaky;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.jooq.lambda.tuple.Tuple5;

import java.lang.annotation.*;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * <p> Mimic is a protocol defined to use Interface as Pojo.
 * <p> <b>Note:</b> this implement by JDK dynamic proxy, and will decrement performance for about 10 times, may never use for performance award condition.
 * It's best use for slow business logic implement that coding time is more expensive than performance requirement.
 * <p> <h3>Introduce</h3></p>
 * <p> <b>Property</b>: defined by interface Getter&Setter methods.
 * <p> <b>Getter&Setter Strategy:</b>
 * <p> <b>Common Strategy</b>:
 * <p> <b>1. getter</b> must be PUBLIC, NONE DEFAULT, NO PARAMETER and Returns NONE VOID type.
 * <p> <b>2. setter</b> must be PUBLIC, NONE DEFAULT, HAVE ONLY ONE PARAMETER and Returns VOID type Or SELF.
 * <p> <b>Fluent Strategy</b> (The default strategy): getter's name and setter's name are the property name;
 * <p> <b>Java Bean Strategy</b>: getter's name is prefixed with 'get' or 'is', setter's name is prefixed with 'set';
 * this would be enabled by annotate with {@link JavaBean} and set {@link JavaBean#value()} as false
 * <p> <b>Mix Strategy</b>: both Java Bean Strategy and Fluent Strategy are allowed;
 * this would be enabled by annotate with {@link JavaBean} and set {@link JavaBean#value()} as true
 * <p> <b>default methods</b>
 * <p> <b>underlyingMap</b>: fetch this underlying storage, which is mutable, but should careful to change its values,
 * cause of this will ignore any annotated validation or convection methods on interface.
 * <p><b>underlyingChangedProperties</b>: recoding the set action took on  properties. only effect by using {@link Dao},
 * under other conditions it always returns null.
 * <p><b>Misc</b></p>
 * <p><b>Inherit</b>: Mimic can inherit from other Mimic </p>
 * <p><b>Conversion</b>: Mimic can annotate with {@link AsString} on getter or setter to enable single property conversion.
 * <p> for Collections(LIST,SET and ARRAY), there is {@link Array} to support nested Mimicked properties. but current {@link Map} is not been supported.
 * <p><b>Validation</b>: Mimic can annotate with {@link Validation} on getter or setter to enable single property validation.
 * <p> Mimic also use overrideable method {@link Mimic#validate()} to active a Pojo validation.</p>
 * <p><b>Extension</b>: {@link Dao} is extension for use {@link Mimic} as easy Jooq repository.</p>
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-10-16
 */
public interface Mimic {
    /**
     * method to fetch internal Map,
     * <p><b>Note:</b>Never TO OVERRIDDEN
     */
    @NotNull
    Map<String, Object> underlyingMap();

    /**
     * this method only effect when {@link Dao.Entity } is marked.
     * <p><b>Note:</b>Never TO OVERRIDDEN
     */
    @Nullable
    List<String> underlyingChangedProperties();

    /**
     * method to call validate, can be overridden.
     *
     * @throws IllegalStateException if anything not match requirement
     */
    default void validate() throws IllegalStateException {

    }

    interface Validate {
        BiConsumer<String, Object> noop = (p, x) -> {
        };
        BiConsumer<String, Object> notNull = (p, x) -> {
            if (x == null) throw new IllegalStateException(p + " should not be null");
        };
    }

    /**
     * define a Mimic type use JavaBean GetterSetter protocol
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Documented
    @interface JavaBean {
        /**
         * Allow Fluent Mode
         */
        boolean value() default false;
    }

    /**
     * mark the mimic use Concurrent protection.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Inherited
    @Documented
    @interface Concurrent {
        /**
         * use lock not concurrent hashmap: not suggested.
         */
        boolean value() default false;
    }

    /**
     * define a property is converted to string, should not use with {@link Array}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Inherited
    @Documented
    @interface AsString {
        /**
         * the holder of Converter Methods;
         * If is predefined type,should keep it Void;
         * <p>current predefined type is: {@link BigDecimal} {@link Long} {@link java.time.Instant}
         */
        Class<?> value() default Void.class;

        /**
         * the static property name of convert String to T
         * <p> which must have type {@link java.util.function.Function}
         * as Function< String, Object >;
         */
        String fromProperty() default "fromString";

        /**
         * the static property name of convert T to String;
         * <p>which must have type {@link java.util.function.Function}
         * as Function< Object,String >;
         */
        String toProperty() default "toString";
    }

    /**
     * define a property is Collection of Mimic;
     * <p>Current support 1. Array 2. List 3. Set;
     * <p>should not use with {@link Array}
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Inherited
    @Documented
    @interface Array {
        /**
         * mark the value type
         */
        Class<? extends Mimic> value();
    }

    /**
     * <p>used to define Validation on Getter;
     * <p>this used to Validate on Set and on validate method;
     * <p>if current property is Annotated with {@link AsString},the Validation only happened when Setter is called.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @Inherited
    @Documented
    @interface Validation {
        /**
         * the holder of Validation Property;
         */
        Class<?> value() default Validate.class;

        /**
         * the static property name of Validate BiConsumer< PropertyName,Value >
         */
        String property() default "noop";

    }

    /**
     * use Byte ASM Mode for Mimic
     */
    interface ByteASM {
        /**
         * @param lazy use eager property initial or lazy initial, default is eager model
         */
        static void enable(boolean lazy) {
            internal.lazyMode.set(lazy);
            internal.factory.set(internal.Asm.cache::get);
        }
    }

    /**
     * use JDK Dynamic Proxy Mode for Mimic
     */
    interface DynamicProxy {
        static void enable() {
            internal.factory.set(internal.DynamicProxy.cache::get);
        }
    }

    /**
     * this effect on all internal Caffeine loading Caches
     */
    AtomicInteger cacheSize = new AtomicInteger(1024);

    @SuppressWarnings("rawtypes")
    @Slf4j
    final class internal {
        private internal() {
            throw new IllegalAccessError();
        }

        final static Factory nullFactory = new Factory() {
            @Override
            public Map<String, PropertyInfo> properties() {
                return null;
            }

            @Override
            public Mimic build(Map data) {
                return null;
            }

            @Override
            public Mimic buildLazy(Map data) {
                return null;
            }
        };

        @SuppressWarnings("rawtypes")
        final static class PropertyInfo {
            final Function<Object, Object> getterConv;
            final Function<Object, Object> setterConv;
            final BiConsumer<String, Object> setterValidate;
            final String property;
            final Class type;

            PropertyInfo(String property, Tuple4<Function<Object, Object>, Function<Object, Object>, BiConsumer<String, Object>, Class> info) {
                this.getterConv = info.v1;
                this.setterConv = info.v2;
                this.setterValidate = info.v3;
                this.property = property;
                this.type = info.v4;
            }

            Object invokeSetter(Object v) {
                return setterConv != null ? setterConv.apply(v) : v;
            }

            Object invokeGetter(Object v) {
                return getterConv != null ? getterConv.apply(v) : v;
            }

            void validateSetter(Object v) {
                if (setterValidate != null) setterValidate.accept(property, v);
            }
        }

        interface Factory<T extends Mimic> {
            List<String> defaultMethodName = Arrays.asList("underlyingMap", "underlyingChangedProperties");

            Mimic build(Map<String, Object> data);

            Mimic buildLazy(Map<String, Object> data);

            Map<String, PropertyInfo> properties();

            LoadingCache<Class, MimicInfo> infoCache = Caffeine.newBuilder()
                .softValues()
                .maximumSize(cacheSize.get())
                .build(Factory::infoBuild);

            /**
             * @return (MapBuilder, MethodPropertyNameExtract, ConcurrentMode, EntityValidation, DefaultMethods, PropertyInfos)
             */
            static MimicInfo infoBuild(Class<?> cls) {
                val strategy = nameStrategy(cls);
                val methods = cls.getMethods();
                val getters = Seq.of(methods).filter(strategy.getPred)
                    .filter(x -> !defaultMethodName.contains(x.getName()))
                    .toList();
                val setters = Seq.of(methods).filter(strategy.setPred).toList();
                val other = Seq.of(methods).filter(Method::isDefault).toList();
                val getProcessor = seq(getters)
                    .map(x -> compute(x, strategy.getName).map2(v -> tuple(x, (Method) null, v)))
                    .toMap(Tuple2::v1, Tuple2::v2);
                val setProcessor = seq(setters)
                    .map(x -> compute(x, strategy.setName).map2(v -> tuple((Method) null, x, v)))
                    .toMap(Tuple2::v1, Tuple2::v2);
                //Property->(GetterMethod,SetterMethod,GetterProcessor,SetterProcessor,Validator,PropertyType)
                val prop = new HashMap<String, Tuple3<Method, Method, Tuple4<Function<Object, Object>, Function<Object, Object>, BiConsumer<String, Object>, Class>>>();
                Consumer<Map<String, Object>> validator = null;
                for (String k : getProcessor.keySet()) {
                    val v = getProcessor.get(k);
                    prop.put(k, v.map3(Tuple5::limit4));
                    if (v.v3.v5 != null) validator = validator == null ? v.v3.v5 : validator.andThen(v.v3.v5);
                }
                for (String k : setProcessor.keySet()) {
                    val v = setProcessor.get(k);
                    val v0 = prop.putIfAbsent(k, v.map3(Tuple5::limit4));
                    if (v0 != null) {
                        var v1 = v0;
                        v1 = v1.map2($ -> v.v2);
                        if (v1.v3.v1 == null && v.v3.v1 != null) {
                            v1 = v1.map3(v3 -> v3.map1(x -> v.v3.v1));
                        }
                        if (v1.v3.v2 == null && v.v3.v2 != null) {
                            v1 = v1.map3(v3 -> v3.map2(x -> v.v3.v2));
                        }
                        if (v1.v3.v3 == null && v.v3.v3 != null) {
                            v1 = v1.map3(v3 -> v3.map3(x -> v.v3.v3));
                        }
                        prop.put(k, v1);
                    }
                    if (v.v3.v5 != null) validator = validator == null ? v.v3.v5 : validator.andThen(v.v3.v5);
                }
                val info = seq(prop)
                    .map(x -> x.map2(v -> v.map3(t -> new PropertyInfo(x.v1, t))))
                    .toMap(Tuple2::v1, Tuple2::v2);
                val cann = cls.getAnnotationsByType(Concurrent.class);
                //0 no concurrent, 1 use sync ,2 use concurrent hashmap
                val concurrent = cann.length == 0 ? 0 : cann[0].value() ? 1 : 2;
                val n = prop.size();
                val mapBuilder =
                    concurrent == 2
                        ? (Supplier<Map<String, Object>>) () -> new ConcurrentHashMap<>(n)
                        : (Supplier<Map<String, Object>>) () -> new HashMap<>(n);

                return MimicInfo.of(mapBuilder, strategy, concurrent, validator, other, info);
            }
        }

        interface DynamicProxy {
            final class ProxyFactory<T extends Mimic> implements Factory<T> {
                final AtomicReference<Constructor<MethodHandles.Lookup>> constructor = new AtomicReference<>();
                final Supplier<Map<String, Object>> mapBuilder;
                final Class<T> cls;
                final Function<String, String> extract;
                final Consumer<Object> validation;
                final Map<String, PropertyInfo> prop;
                final int concurrent;
                final boolean setterMonitor;

                @Override
                public Map<String, PropertyInfo> properties() {
                    return prop;
                }

                ProxyFactory(Supplier<Map<String, Object>> mapBuilder,
                             Class<T> cls,
                             Function<String, String> extract,
                             Consumer<Object> validation,
                             Map<String, PropertyInfo> prop,
                             int concurrent,
                             boolean setterMonitor
                ) {
                    this.mapBuilder = mapBuilder;
                    this.cls = cls;
                    this.extract = extract;
                    this.validation = validation;
                    this.prop = prop;
                    this.concurrent = concurrent;
                    this.setterMonitor = setterMonitor;
                }

                public Mimic build(Map<String, Object> data) {
                    final Object[] result = new Object[1];
                    final Map<String, Object> map = mapBuilder.get();
                    if (data != null && !data.isEmpty()) map.putAll(data);
                    final List<String> changes = setterMonitor ? new ArrayList<>() : null;
                    result[0] = Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, (p, m, args) -> {
                        val method = m.getName();
                        val field = extract.apply(method);
                        switch (method) {
                            case "toString":
                                return cls.getCanonicalName() + "@" + Integer.toHexString(map.hashCode()) + map.toString();
                            case "hashCode":
                                return map.hashCode();
                            case "equals":
                                return map.equals(args[0]);
                            case "underlyingMap": //special method
                                return map;
                            case "underlyingChangedProperties": //special method
                                return changes;
                            default:
                                if (m.isDefault()) {
                                    try {
                                        if (constructor.get() == null)
                                            constructor.set(accessible(MethodHandles.Lookup.class
                                                .getDeclaredConstructor(Class.class, int.class)));
                                        Class<?> declaringClass = m.getDeclaringClass();
                                        if (method.equals("validate") && m.getReturnType() == void.class) {
                                            if (validation != null) {
                                                validation.accept(map);
                                            }
                                        }
                                        return constructor.get()
                                            .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                                            .unreflectSpecial(m, declaringClass)
                                            .bindTo(result[0])
                                            .invokeWithArguments(args);
                                    } catch (ReflectiveOperationException e) {
                                        throw new IllegalStateException("Cannot invoke default method", e);
                                    }
                                } else {
                                    if (!field.isEmpty() && (args == null || args.length == 0) && m.getReturnType() != Void.class) { //must a getter
                                        val mx = map.get(field);
                                        if (mx == null) return null;
                                        return prop.get(field).invokeGetter(mx);
                                    } else if (!field.isEmpty() &&
                                        args != null &&
                                        args.length == 1 &&
                                        (m.getReturnType().isAssignableFrom(cls) || m.getReturnType() == Void.class)) {// must a setter
                                        var v = args[0];
                                        prop.get(field).validateSetter(v);
                                        if (v == null) {
                                            if (concurrent == 1) {
                                                synchronized (map) {
                                                    if (changes != null) changes.add(field);
                                                    map.remove(field);
                                                }
                                            } else {
                                                if (changes != null) changes.add(field);
                                                map.remove(field);
                                            }
                                            return m.getReturnType().isAssignableFrom(cls) ? p : null;
                                        }
                                        v = prop.get(field).invokeSetter(v);
                                        if (concurrent == 1) {
                                            synchronized (map) {
                                                if (changes != null) changes.add(field);
                                                map.put(field, v);
                                            }
                                        } else {
                                            if (changes != null) changes.add(field);
                                            map.put(field, v);
                                        }
                                        return m.getReturnType().isAssignableFrom(cls) ? p : null;
                                    } else {
                                        throw new IllegalStateException("can not process method" + m + ":" + Arrays.toString(args));
                                    }
                                }
                        }
                    });
                    return (Mimic) result[0];
                }

                @Override
                public Mimic buildLazy(Map<String, Object> data) {
                    return build(data);
                }
            }

            static Factory build(Class<? extends Mimic> cls) {
                if (!Mimic.class.isAssignableFrom(cls)) {
                    return nullFactory;
                }
                final boolean setterMonitor = changeDecider.get().apply(cls);
                val info = Factory.infoCache.get(cls);
                if (info == null) throw new IllegalStateException("could not generate mimic info from " + cls);
                return new ProxyFactory(
                    info.mapBuilder,
                    cls,
                    info.strategy.extract,
                    info.validation,
                    seq(info.propertyInfo)
                        .map(x -> x.map2(v -> v.v3))
                        .toMap(Tuple2::v1, Tuple2::v2),
                    info.concurrentMode,
                    setterMonitor);
            }

            LoadingCache<Class, Factory> cache = Caffeine.newBuilder()
                .softValues()
                .maximumSize(cacheSize.get())
                .build(DynamicProxy::build);
        }

        @SuppressWarnings("unchecked")
        public interface Asm {
            public abstract class Base implements Mimic {
                protected final Map<String, Object> inner;
                protected final Map<String, PropertyInfo> info;
                protected final List<String> changes;
                protected final List<String> internalChange = new ArrayList<>();
                protected final String name;

                void initialAllChanged() {
                    internalChange.addAll(info.keySet());
                }

                protected Base(Map<String, Object> inner, Map<String, PropertyInfo> info, List<String> changes, String name) {
                    this.inner = inner;
                    this.info = info;
                    this.changes = changes;
                    this.name = name;
                }

                @Override
                public @NotNull Map<String, Object> underlyingMap() {
                    return inner;
                }

                @Override
                public @Nullable List<String> underlyingChangedProperties() {
                    return changes;
                }

                public void set(String prop, Object value) {
                    info.get(prop).validateSetter(value);
                    val v = info.get(prop).invokeSetter(value);
                    inner.put(prop, v);
                    internalChange.add(prop);//add internal changes log
                    if (changes != null) changes.add(prop);
                }

                @Override
                public String toString() {
                    return name + inner.toString();
                }

                public Object get(String prop) {
                    return info.get(prop).invokeGetter(inner.get(prop));
                }

                public Object get(String prop, Object value) {
                    val i = info.get(prop);
                    if (!isDefault(value, i.type)) return value;
                    return i.invokeGetter(inner.get(prop));
                }

                void clearChanges() {
                    if (changes != null) changes.clear();
                }

                //async internal data to instance field
                public Object isChange(String field) {
                    if (internalChange.contains(field)) {
                        internalChange.remove(field);
                        return get(field);
                    }
                    return null;
                }

                //region Defaults
                private static boolean DEFAULT_BOOLEAN;
                private static byte DEFAULT_BYTE;
                private static short DEFAULT_SHORT;
                private static int DEFAULT_INT;
                private static long DEFAULT_LONG;
                private static float DEFAULT_FLOAT;
                private static double DEFAULT_DOUBLE;

                static boolean isDefault(Object n, Class clazz) {
                    if (clazz.equals(boolean.class)) {
                        return DEFAULT_BOOLEAN == (boolean) n;
                    } else if (clazz.equals(byte.class)) {
                        return DEFAULT_BYTE == (byte) n;
                    } else if (clazz.equals(short.class)) {
                        return DEFAULT_SHORT == (short) n;
                    } else if (clazz.equals(int.class)) {
                        return DEFAULT_INT == (int) n;
                    } else if (clazz.equals(long.class)) {
                        return DEFAULT_LONG == (long) n;
                    } else if (clazz.equals(float.class)) {
                        return DEFAULT_FLOAT == (float) n;
                    } else if (clazz.equals(double.class)) {
                        return DEFAULT_DOUBLE == (double) n;
                    } else {
                        return n == null;
                    }
                }

                //endregion
                static final Method GET;
                static final Method SET;


                static {
                    try {
                        GET = Base.class.getDeclaredMethod("get", String.class, Object.class);
                        SET = Base.class.getDeclaredMethod("set", String.class, Object.class);
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException(e);
                    }
                }


            }


            @SuppressWarnings("rawtypes")
            final class AsmFactory<T extends Mimic> implements Factory<T> {
                final Map<String, PropertyInfo> prop;
                final BiFunction<Map, List, Object> eager;
                final BiFunction<Map, List, Object> lazy;
                final Class type;

                AsmFactory(Map<String, PropertyInfo> prop, BiFunction<Map, List, Object> eager, BiFunction<Map, List, Object> lazy, Class type) {
                    this.prop = prop;
                    this.eager = eager;
                    this.lazy = lazy;
                    this.type = type;
                }

                @Override
                public Mimic build(Map<String, Object> data) {
                    return (Mimic) eager.apply(data, changeDecider.get().apply(type) ? new ArrayList<>() : null);
                }

                @Override
                public Mimic buildLazy(Map<String, Object> data) {
                    val v = (Mimic) lazy.apply(data, changeDecider.get().apply(type) ? new ArrayList<>() : null);
                    if (data != null && !data.isEmpty()) ((Base) v).initialAllChanged();
                    return v;
                }

                @Override
                public Map<String, PropertyInfo> properties() {
                    return prop;
                }


            }

            static <T> Optional<CallSite> generateCallSite(
                final Class<T> lambdaType,
                final String invokeName,
                final MethodType samMethodType,
                final Class<?> targetClass,
                final String methodName,
                final MethodType virtualMethodType,
                final MethodType instanceMethodType
            ) {
                final MethodHandles.Lookup lookup = MethodHandles.lookup();
                try {
                    final CallSite metaFactory = LambdaMetafactory.metafactory(
                        lookup,
                        invokeName,
                        MethodType.methodType(lambdaType),
                        samMethodType,
                        lookup.findVirtual(targetClass, methodName, virtualMethodType),
                        instanceMethodType);
                    return Optional.of(metaFactory);
                } catch (Throwable ex) {
                    log.error("error to generate call site from {} named as {}", targetClass, methodName, ex);
                    return Optional.empty();
                }
            }

            @SuppressWarnings("unchecked")
            static <O, V> Optional<Function<O, V>> getter(
                final @NotNull Class<O> target,
                final @NotNull Class<V> valueType,
                final @NotNull String getterName
            ) {
                return generateCallSite(
                    Function.class,
                    "apply",
                    MethodType.methodType(Object.class, Object.class),
                    target,
                    getterName,
                    MethodType.methodType(valueType),
                    MethodType.methodType(valueType, target)
                ).map(a -> {
                    try {
                        return (Function<O, V>) a.getTarget().invokeExact();
                    } catch (Throwable ex) {
                        log.error("error to generate getter lambda from {} named as {}", target, getterName, ex);
                        return null;
                    }
                });
            }

            @SuppressWarnings("unchecked")
            static <O, V> Optional<BiConsumer<O, V>> setter(
                final @NotNull Class<O> target,
                final @NotNull Class<V> valueType,
                final @NotNull String setterName,
                final boolean chain
            ) {
                return (
                    chain ?
                        generateCallSite(
                            BiFunction.class,
                            "apply",
                            MethodType.methodType(Object.class, Object.class, Object.class),
                            target,
                            setterName,
                            MethodType.methodType(target, valueType),
                            MethodType.methodType(target, target, valueType)
                        )
                        : generateCallSite(
                        BiConsumer.class,
                        "accept",
                        MethodType.methodType(void.class, Object.class, Object.class),
                        target,
                        setterName,
                        MethodType.methodType(void.class, valueType),
                        MethodType.methodType(void.class, target, valueType)
                    )).map(a -> {
                    try {
                        if (chain) {
                            val fn = (BiFunction<O, V, O>) a.getTarget().invokeExact();
                            return fn::apply;
                        }
                        return (BiConsumer<O, V>) a.getTarget().invokeExact();
                    } catch (Throwable ex) {
                        log.error("error to generate BeanSetter Functor from {} named as {} ", target, setterName, ex);
                        return null;
                    }
                });
            }

            LoadingCache<Class, Functor> functorCache = Caffeine
                .newBuilder()
                .softValues()
                .maximumSize(cacheSize.get())
                .build(Asm::functorBuilder);

            @AllArgsConstructor(staticName = "of")
            final class Functor {
                public final Function<String, String> extractor;
                public final Map<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> functor;
            }

            static Functor functorBuilder(Class cls) {
                val info = Factory.infoCache.get(cls);
                if (info == null) throw new IllegalStateException(cls.getCanonicalName() + " not generate info");
                Map<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> functor = new HashMap<>();
                val ifaces = new ArrayList<>(Arrays.asList(cls.getInterfaces()));
                ifaces.add(0, cls);
                final Function<Method, Class> findDeclared = Sneaky.function(x -> {
                    for (Class<?> c : ifaces) {
                        for (Method m : c.getDeclaredMethods()) {
                            if (m.equals(x))
                                return c;
                        }
                    }
                    return null;
                });
                for (Method m : cls.getMethods()) {
                    val isGetter = info.strategy.getPred.test(m);
                    val isSetter = info.strategy.setPred.test(m);
                    if (m.isDefault() ||
                        Factory.defaultMethodName.contains(m.getName()) ||
                        (!isGetter && !isSetter)) {
                        continue;
                    }
                    val declared = findDeclared.apply(m);
                    val prop = info.strategy.extract.apply(m.getName());
                    val typo = info.propertyInfo.get(prop).v3.type;
                    var v = functor.get(prop);
                    if (v == null) v = tuple(null, null);
                    if (isGetter) {
                        v = v.map1($ -> {
                            try {
                                return (Function<Object, Object>) (Function) getter(
                                    declared,
                                    typo,
                                    m.getName())
                                    .orElseThrow(() -> new IllegalStateException("create functor for Get fail"));
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    if (isSetter) {
                        v = v.map2($ -> {
                            try {
                                return (BiConsumer<Object, Object>) (BiConsumer) setter(
                                    findDeclared.apply(m),
                                    typo,
                                    m.getName(),
                                    m.getReturnType() != Void.TYPE
                                ).orElseThrow(() -> new IllegalStateException("create functor for Set fail"));
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    functor.put(prop, v);
                }
                return Functor.of(info.strategy.extract, functor);

            }

            class GetterAdvices {
                //                @Advice.OnMethodEnter(suppress = Throwable.class)
                @Advice.OnMethodExit()
                static Object after(
                    @Advice.This Object self,
                    @Advice.Exit Object value,//field value
                    @Advice.Origin(value = "#m") String method
                ) {
                    val type = self.getClass().getInterfaces()[0];
                    // val info = functorCache.get(type);
                    // String field = info.extractor.apply(method);
                    val field = commonExtract(method);
                    val base = ((Base) self).isChange(field);
                    if (base != null && value != null) {
                        functorCache.get(type).functor.get(field).v2.accept(self, base);
                        // System.out.println("returning updated");
                        return base;
                    }
                    // System.out.println("returning field");
                    return value;
                }
            }


            //cls->((lazyCtor,eagerCtor),properties)
            @SneakyThrows
            static <T extends Mimic> Tuple2<Tuple3<BiFunction<Map, List, T>, BiFunction<Map, List, T>, Integer>, Map<String, PropertyInfo>> buildInfo(Class<T> cls) {
                val typeName = cls.getCanonicalName() + "$ASM$";
                val info = Factory.infoCache.get(cls);
                if (info == null) throw new IllegalStateException("could not generate mimic info from " + cls);
                val ifaces = new ArrayList<>(Arrays.asList(cls.getInterfaces()));
                ifaces.add(0, cls);
                final BiFunction<Map, List, T> ctorLazy;
                final BiFunction<Map, List, T> ctorEager;

                DynamicType.Builder<?> lazy = new ByteBuddy()
                    .subclass(Base.class)
                    .implement(cls)
                    .name(typeName + "Lazy");
                DynamicType.Builder<?> eager = new ByteBuddy()
                    .subclass(Base.class)
                    .implement(cls)
                    .name(typeName + "Eager");
                int modifierSync = info.concurrentMode == 1 ? Modifier.PUBLIC | Modifier.SYNCHRONIZED : Modifier.PUBLIC;
                for (Map.Entry<String, Tuple3<Method, Method, PropertyInfo>> entry : info.propertyInfo.entrySet()) {
                    val prop = entry.getKey();
                    val typo = entry.getValue().v3.type;
                    lazy = lazy.defineField(prop, typo, Modifier.PUBLIC);
                    eager = eager.defineField(prop, typo, Modifier.PUBLIC);
                    //getter
                    {
                        val m = entry.getValue().v1;
                        if (m != null) {
                            eager = eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                .intercept(FieldAccessor.ofField(prop));

                            lazy = lazy.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                .intercept(
                                    Advice.to(GetterAdvices.class).wrap(FieldAccessor.ofField(prop))
                                  /*      .wrap(MethodCall
                                            .invoke(Base.GET)
                                            .with(prop).withField(prop)
                                            .setsField(named(prop))
                                            .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC)
                                            .andThen(FieldAccessor.ofField(prop)))*/
                                );
                        }
                    }
                    //setter
                    {
                        val m = entry.getValue().v2;
                        if (m != null) {
                            eager = m.getReturnType() == Void.TYPE ?
                                eager.defineMethod(m.getName(), m.getReturnType(), modifierSync)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(MethodCall
                                        .invoke(Base.SET)
                                        .with(prop).withAllArguments()
                                        .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0)))
                                :
                                eager.defineMethod(m.getName(), m.getReturnType(), modifierSync)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(
                                        MethodCall
                                            .invoke(Base.SET)
                                            .with(prop).withAllArguments()
                                            .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0))
                                            .andThen(FixedValue.self())
                                    );

                            lazy = m.getReturnType() == Void.TYPE ?
                                lazy.defineMethod(m.getName(), m.getReturnType(), modifierSync)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(
                                        MethodCall
                                            .invoke(Base.SET)
                                            .with(prop).withAllArguments()
                                            .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0))
                                    )
                                :
                                lazy.defineMethod(m.getName(), m.getReturnType(), modifierSync)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(
                                        MethodCall
                                            .invoke(Base.SET)
                                            .with(prop).withAllArguments()
                                            .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0))
                                            .andThen(FixedValue.self())
                                    );
                        }
                    }

                }
                //extra method
                for (Method m : info.defaultMethods) {
                    lazy = lazy.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                        .withParameters(Arrays.asList(m.getParameterTypes()))
                        .intercept(DefaultMethodCall.prioritize(ifaces));
                    eager = eager.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                        .withParameters(Arrays.asList(m.getParameterTypes()))
                        .intercept(DefaultMethodCall.prioritize(ifaces));
                }

                lazy = lazy.defineMethod("toString", String.class, Visibility.PUBLIC)
                    .intercept(SuperMethodCall.INSTANCE);
                lazy = lazy.defineMethod("hashCode", int.class, Visibility.PUBLIC)
                    .intercept(SuperMethodCall.INSTANCE);
                lazy = lazy.defineMethod("equals", boolean.class, Visibility.PUBLIC)
                    .withParameters(Object.class)
                    .intercept(SuperMethodCall.INSTANCE);
                eager = eager.defineMethod("toString", String.class, Visibility.PUBLIC)
                    .intercept(ToStringMethod.prefixedBy(typeName + "Eager"));
                eager = eager.defineMethod("hashCode", int.class, Visibility.PUBLIC)
                    .intercept(HashCodeMethod.usingDefaultOffset());
                eager = eager.defineMethod("equals", boolean.class, Visibility.PUBLIC)
                    .withParameters(Object.class)
                    .intercept(EqualsMethod.isolated());
                val prop = seq(info.propertyInfo)
                    .map(x -> x.map2(v -> v.v3))
                    .toMap(Tuple2::v1, Tuple2::v2);

                {
                    val ctor = lazy.make()
                        .load(cls.getClassLoader())
                        .getLoaded()
                        .getConstructor(Map.class, Map.class, List.class, String.class);
                    val builder = info.mapBuilder;
                    ctorLazy = (m, l) -> {
                        try {
                            val x = builder.get();
                            if (m != null && !m.isEmpty()) x.putAll(m);
                            return (T) ctor.newInstance(x, prop, l, typeName + "Lazy");
                        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    };
                }
                {
                    val functor = functorCache.get(cls);
                    val ctor = eager.make().load(cls.getClassLoader()).getLoaded().getConstructor(Map.class, Map.class, List.class, String.class);
                    val builder = info.mapBuilder;
                    BiConsumer<Object, Base> copier = null;
                    for (val e : functor.functor.entrySet()) {
                        val fn = e.getValue().v2;
                        val p = e.getKey();
                        BiConsumer<Object, Base> field = (t, b) -> fn.accept(t, b.get(p));
                        copier = copier == null ? field : copier.andThen(field);
                    }
                    val finalCopier = copier;
                    ctorEager = (m, l) -> {
                        try {
                            val x = builder.get();
                            if (m != null && !m.isEmpty()) {
                                x.putAll(m);
                                val t = (T) ctor.newInstance(x, prop, l, typeName + "Eager");
                                val b = (Base) t;
                                finalCopier.accept(t, b);
                                b.clearChanges();
                                return t;
                            } else {
                                return (T) ctor.newInstance(x, prop, l, typeName + "Eager");
                            }
                        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    };
                }
                return tuple(tuple(ctorLazy, ctorEager, info.concurrentMode), prop);
            }

            LoadingCache<Class, Factory> cache = Caffeine.newBuilder()
                .softValues()
                .maximumSize(cacheSize.get())
                .build(Asm::build);

            static Factory build(Class<? extends Mimic> cls) {
                if (!Mimic.class.isAssignableFrom(cls)) {
                    return nullFactory;
                }

                final Tuple2<Tuple3<BiFunction<Map, List, Object>, BiFunction<Map, List, Object>, Integer>, Map<String, PropertyInfo>> t =
                    (Tuple2<Tuple3<BiFunction<Map, List, Object>, BiFunction<Map, List, Object>, Integer>, Map<String, PropertyInfo>>)
                        (Tuple2) buildInfo(cls);
                return new AsmFactory(t.v2, t.v1.v2, t.v1.v1, cls);
            }
        }

        //region Share tools
        @AllArgsConstructor(staticName = "of")
        final static class MimicInfo {
            final Supplier<Map<String, Object>> mapBuilder;
            final NamingStrategy strategy;
            final int concurrentMode;
            final Consumer<Map<String, Object>> validation;
            final List<Method> defaultMethods;
            final Map<String, Tuple3<Method, Method, PropertyInfo>> propertyInfo;
        }

        @AllArgsConstructor(staticName = "of")
        final static class NamingStrategy {
            final Predicate<Method> getPred;
            final Predicate<Method> setPred;
            final Function<Method, String> getName;
            final Function<Method, String> setName;
            final Function<String, String> extract;
        }

        //FieldExtract,getPredicate,setPredicate,getNameExtract,setNameExtract
        static NamingStrategy nameStrategy(Class<?> cls) {
            final Predicate<Method> getPred;
            final Predicate<Method> setPred;
            final Function<Method, String> setName;
            final Function<Method, String> getName;
            final Function<String, String> extract;
            {
                val ann = cls.getAnnotationsByType(JavaBean.class);
                if (ann.length == 0) {
                    getPred = fluentGetter;
                    setPred = fluentSetter;
                    getName = fluentGetterName;
                    setName = fluentSetterName;
                    extract = Function.identity();
                } else {
                    val an = ann[0];
                    if (an.value()) {
                        getPred = mixGetter;
                        setPred = mixSetter;
                        getName = mixGetterName;
                        setName = mixSetterName;
                        extract = internal::commonExtract;
                    } else {
                        getPred = beanGetter;
                        setPred = beanSetter;
                        getName = beanGetterName;
                        setName = beanSetterName;
                        extract = internal::commonExtract;
                    }
                }
            }
            return NamingStrategy.of(getPred, setPred, getName, setName, extract);
        }

        //region Predicate
        final static Predicate<Method> isPublicNoneDefault = x -> !x.isDefault() && Modifier.isPublic(x.getModifiers());
        final static Predicate<Method> isBeanGetter = x -> (x.getParameterCount() == 0 &&
            (x.getName().startsWith("get") && x.getReturnType() != Void.TYPE ||
                (x.getReturnType() == boolean.class && x.getName().startsWith("is")))
        );
        final static Predicate<Method> isBeanSetter = x -> (x.getParameterCount() == 1 &&
            x.getName().startsWith("set") &&
            (x.getReturnType() == Void.TYPE || x.getReturnType() == x.getDeclaringClass()));

        final static Predicate<Method> isFluentSetter = x -> (
            x.getParameterCount() == 1 &&
                (x.getReturnType() == Void.TYPE || x.getReturnType() == x.getDeclaringClass())
        );
        final static Predicate<Method> isFluentGetter = x -> (x.getParameterCount() == 0 && x.getReturnType() != Void.TYPE);
        final static Predicate<Method> fluentGetter = isPublicNoneDefault.and(isFluentGetter);
        final static Predicate<Method> fluentSetter = isPublicNoneDefault.and(isFluentSetter);
        final static Predicate<Method> beanSetter = isPublicNoneDefault.and(isBeanSetter);
        final static Predicate<Method> beanGetter = isPublicNoneDefault.and(isBeanGetter);
        final static Predicate<Method> mixGetter = isPublicNoneDefault.and(isFluentGetter.or(isBeanGetter));
        final static Predicate<Method> mixSetter = isPublicNoneDefault.and(isFluentSetter.or(isBeanSetter));

        //endregion
        static <T extends AccessibleObject> T accessible(T accessible) {
            if (accessible == null) {
                return null;
            }

            if (accessible instanceof Member) {
                Member member = (Member) accessible;

                if (Modifier.isPublic(member.getModifiers()) &&
                    Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                    return accessible;
                }
            }

            // [jOOQ #3392] The accessible flag is set to false by default, also for public members.
            if (!accessible.isAccessible())
                accessible.setAccessible(true);
            return accessible;
        }

        //region Name Converter
        static String lowerFist(String val) {
            char[] c = val.toCharArray();
            c[0] = Character.toLowerCase(c[0]);
            return new String(c);
        }

        final static Function<Method, String> fluentGetterName = Method::getName;
        final static Function<Method, String> fluentSetterName = Method::getName;
        final static Function<Method, String> beanGetterName = x -> lowerFist(x.getName().startsWith("is") ? x.getName().substring(2) : x.getName().substring(3));
        final static Function<Method, String> beanSetterName = x -> lowerFist(x.getName().substring(3));
        final static Function<Method, String> mixSetterName = x -> x.getName().startsWith("set") ? lowerFist(x.getName().substring(3)) : x.getName();
        final static Function<Method, String> mixGetterName = x -> x.getName().startsWith("is") || x.getName().startsWith("get") ? beanGetterName.apply(x) : x.getName();

        //endregion
        public static String commonExtract(String x) {
            return lowerFist(x.startsWith("is") ? x.substring(2) :
                x.startsWith("get") || x.startsWith("set") ? x.substring(3) : x);
        }

        static final Consumer<Object> hold = x -> {
        };

        static <T extends Annotation> List<T> collectAnnotations(Method m, Class<T> anno, List<Class> faces) {
            val collected = new ArrayList<T>();
            for (Class cls : faces) {
                try {
                    val fn = cls.getDeclaredMethod(m.getName(), m.getParameterTypes());
                    collected.addAll(Arrays.asList(fn.getAnnotationsByType(anno)));
                } catch (NoSuchMethodException e) {

                }
            }
            return collected;
        }

        //(method,nameExtractor)->(propertyName,(GetterProcessor,SetterProcessor,ParamValidate,propertyType,ObjectValidation))
        @SuppressWarnings({"ConstantConditions", "unchecked"})
        static Tuple2<String, Tuple5<
            Function<Object, Object>,
            Function<Object, Object>,
            BiConsumer<String, Object>, Class,
            Consumer<Map<String, Object>>>>
        compute(Method m, Function<Method, String> n) {
            val type = m.getDeclaringClass();
            final List<Class> ifaces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
            ifaces.add(0, type);
            val name = n.apply(m);
            val ret = m.getReturnType();
            val param = m.getParameterCount() == 1 ? m.getParameters()[0].getType() : null;
            final Class prop = param == null ? ret : param;
            Tuple2<Function<Object, Object>, Function<Object, Object>> processor = null;
            //property validate
            BiConsumer<String, Object> pre = null;
            //instance validate
            Consumer<Map<String, Object>> valid = null;
            //property is converted
            boolean conv = false;
            //Array Annotation
            {
                val ann = collectAnnotations(m, Array.class, ifaces);
                if (!ann.isEmpty()) {
                    conv = true;
                    val typ = ann.get(0).value();
                    processor = tuple(
                        (Function<Object, Object>) v -> {
                            if (v instanceof List && !((List<?>) v).isEmpty()) {
                                return Seq.seq((List<Map<String, Object>>) v).map(x -> instance(typ, x)).toList();
                            } else if (v instanceof Set && !((Set<?>) v).isEmpty()) {
                                return Seq.seq((Set<Map<String, Object>>) v).map(x -> instance(typ, x)).toSet();
                            } else if (v.getClass().isArray()) {
                                return Seq.of((Map<String, Object>[]) v).map(x -> instance(typ, x)).toArray();
                            } else {
                                return v;
                            }
                        },
                        (Function<Object, Object>) v -> {
                            if (v instanceof List && !((List<?>) v).isEmpty()) {
                                return Seq.seq((List<Mimic>) v).map(Mimic::underlyingMap).toList();
                            } else if (v instanceof Set && !((Set<?>) v).isEmpty()) {
                                return Seq.seq((Set<Mimic>) v).map(Mimic::underlyingMap).toSet();
                            } else if (v.getClass().isArray()) {
                                return Seq.of((Mimic[]) v).map(Mimic::underlyingMap).toList();
                            } else {
                                return v;
                            }
                        }
                    );
                }
            }
            //AsString Annotation
            if (processor == null) {
                val ann = m.getAnnotationsByType(AsString.class);
                if (ann.length != 0) {
                    conv = true;
                    val v = ann[0];
                    if (v.value() == Void.class) {
                        if (prop.isAssignableFrom(BigDecimal.class)) {
                            processor = tuple(
                                x -> x instanceof String ? new BigDecimal((String) x) : x,
                                x -> x instanceof BigDecimal ? ((BigDecimal) x).toPlainString() : x);
                        } else if (prop.isAssignableFrom(Long.class)) {
                            processor = tuple(
                                x -> x instanceof String ? Long.parseLong((String) x) : x,
                                x -> x instanceof Long ? ((Long) x).toString() : x);
                        } else if (prop.isAssignableFrom(Instant.class)) {
                            processor = tuple(
                                x -> x instanceof String ? Instant.ofEpochMilli(Long.parseLong((String) x)) : x,
                                x -> x instanceof Instant ? Long.toString(((Instant) x).toEpochMilli()) : x);
                        }
                    } else {
                        processor = extract(v);
                    }
                }
            }

            //common Annotation Validate
            {
                val exists = new HashSet<Tuple2<Class, String>>();
                val va = Seq.seq(collectAnnotations(m, Validation.class, ifaces))
                    .map(x -> {
                        val id = tuple((Class) x.value(), x.property());
                        if (!exists.contains(id)) {
                            exists.add(id);
                            val field = sneakyField(x.value(), x.property());
                            if (!(field instanceof BiConsumer))
                                throw new IllegalStateException("validator is not a Consumer");
                            return (BiConsumer<String, Object>) field;
                        } else {
                            return null;
                        }
                    }).filter(Objects::nonNull).toList();

                for (BiConsumer<String, Object> v : va) {
                    pre = pre == null ? v : pre.andThen(v);
                }
                val fp = pre;
                if (!va.isEmpty() && !conv) {
                    val v = (Consumer<Map<String, Object>>) x -> fp.accept(name, x.get(name));
                    valid = valid == null ? v : valid.andThen(v);
                }

            }
            //Default convert Mimic Type
            if (processor == null && Mimic.class.isAssignableFrom(prop)) {
                processor = tuple(
                    x -> Mimic.class.isAssignableFrom(x.getClass()) ? ((Mimic) x).underlyingMap() : x,
                    x -> x instanceof Map ? instance(prop, (Map<String, Object>) x) : x
                );
                val pp = pre;
                pre = pp == null ? (na, x) -> {
                    if (x instanceof Mimic)
                        ((Mimic) x).validate();
                    else if (x instanceof Map)
                        instance(prop, (Map<String, Object>) x).validate();
                    else hold.accept(x);
                } : (na, x) -> {
                    if (x instanceof Mimic)
                        ((Mimic) x).validate();
                    else if (x instanceof Map)
                        instance(prop, (Map<String, Object>) x).validate();
                    else pp.accept(na, x);
                };
                val v = (Consumer<Map<String, Object>>) x -> instance(prop, (Map<String, Object>) x.get(name)).validate();
                valid = valid == null ? v : valid.andThen(v);

            }
            if (processor == null) {
                processor = tuple(null, null);
            }
            return tuple(name, processor.concat(pre).concat(prop).concat(valid));
        }

        @SneakyThrows
        static Object sneakyField(Class cls, String field) {
            val f = cls.getField(field);
            f.setAccessible(true);
            return f.get(null);
        }

        static AtomicReference<Function<Class, Boolean>> changeDecider = new AtomicReference<>(x -> false);

        //(asString)=>(FromString,ToString)
        @SuppressWarnings("unchecked")
        @SneakyThrows
        static Tuple2<Function<Object, Object>, Function<Object, Object>> extract(AsString an) {
            val holder = an.value();
            val from = holder.getField(an.fromProperty());
            from.setAccessible(true);
            val frm = (Function<Object, Object>) from.get(null);
            val to = holder.getField(an.toProperty());
            to.setAccessible(true);
            val tr = (Function<Object, Object>) to.get(null);
            return tuple(frm, tr);
        }

        //endregion
        static final AtomicReference<Function<Class, Factory>> factory = new AtomicReference<>(DynamicProxy.cache::get);
        static final AtomicBoolean lazyMode = new AtomicBoolean(false);

        @SuppressWarnings({"unchecked", "rawtypes"})
        static Mimic instance(Class type, Map<String, Object> map) {
            val fn = factory.get().apply(type);
            return lazyMode.get() ? fn.buildLazy(map) : fn.build(map);
        }

    }

    @SuppressWarnings("unchecked")
    static <T extends Mimic> T newInstance(Class<T> type, Map<String, Object> data) {
        return (T) internal.instance(type, data);
    }

    /**
     * <p> Dao is a Jooq repository interface for {@link Mimic}.
     * <p> <h3>Introduce</h3>
     * <p> Dao extends with Jooq Dynamic api to create Repository interface for a {@link Mimic}.
     * <p> <b>Note:</b>
     * <p>  Dao can not be inherited. that means a Dao must directly extended {@link Dao}.
     * <p>  {@link Mimic} used by Dao must directly annotate with {@link Dao.Entity}.
     * <p>  {@link Mimic} will enable Property Change Recording, which store changed Property Name in {@link Mimic#underlyingChangedProperties()}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    interface Dao<T extends Mimic> {
        /**
         * this is special case for MySQL can't use timestamp with TimeZone
         */
        DataType<Instant> InstantAsTimestamp = SQLDataType.TIMESTAMP
            .asConvertedDataType(Converter.of(Timestamp.class, Instant.class, Timestamp::toInstant, Timestamp::from));
        DataType<Long> BigIntIdentity = SQLDataType.BIGINT.identity(true);
        DataType<Instant> InstantAsTimestampDefaultNow = SQLDataType.TIMESTAMP
            .nullable(false)
            .default_(DSL.now())
            .asConvertedDataType(Converter.of(Timestamp.class, Instant.class, Timestamp::toInstant, Timestamp::from));

        Function<DataType, DataType> Identity = x -> x.identity(true);
        Function<DataType, DataType> NotNull = x -> x.nullable(false);
        Function<DataType, DataType> DefaultNull = x -> x.nullable(true).default_(null);
        Function<DataType, DataType> DefaultNow = x -> x.isTimestamp() ? x.default_(DSL.now()) : x;

        /**
         * define a Mimic is Entity,which can build a Repository;
         * <p>this must <b>directly</b> annotate on a Mimic type;
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.TYPE)
        @Documented
        @interface Entity {
            String value() default "";
        }

        /**
         * define a Field detail
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        @Inherited
        @Documented
        @interface As {
            /**
             * define field name,default is Property name convert to underscore case.
             */
            String value() default "";

            /**
             * which class hold current field DataType
             */
            Class<?> typeHolder() default Void.class;

            /**
             * which public static field in {@link As#typeHolder()} is the {@link DataType} of current field.
             * <p> see {@link Dao#BigIntIdentity} as Example.
             */
            String typeProperty() default "";

            /**
             * define a class holds the DataType Processor;
             * <p>see {@link Dao#Identity} as Example
             */
            Class<?> procHolder() default Void.class;

            /**
             * define a static property holds the DataType Processor on {@link As#procHolder()}
             * <p> the property must is type Function< DataType , DataType >
             */
            String procProperty() default "";
        }

        /**
         * this method used to fetch a DSLContext
         * <p><b>NOTE:</b> DO NOT OVERRIDE
         */
        DSLContext ctx();

        /**
         * this method used to fetch the defined Table
         * <p><b>NOTE:</b> DO NOT OVERRIDE
         */
        Table<Record> table();

        /**
         * this method used to convert database map to Mimic Instance
         * <p><b>NOTE:</b> DO NOT OVERRIDE
         */
        T instance(Map<String, Object> data);

        /**
         * this method used to convert Entity map to Database map
         * <p><b>NOTE:</b> DO NOT OVERRIDE
         */
        Map<String, Object> toDatabase(Map<String, Object> data);

        /**
         * this method used to convert Database map to Entity map
         * <p><b>NOTE:</b> DO NOT OVERRIDE
         */
        Map<String, Object> toEntity(Map<String, Object> data);

        /**
         * this method returns all fields in database order
         * <p><b>NOTE:</b> MUST OVERRIDE
         */
        List<Field<?>> allFields();

        /**
         * a light weight DDL for createTableIfNotExists
         */
        default int DDL() {
            return
                setConstants(
                    ctx()
                        .createTableIfNotExists(table())
                        .columns(allFields().toArray(new Field[0]))
                )
                    .execute();
        }

        /**
         * this method used to add constants for {@link Dao#DDL}
         */
        default CreateTableColumnStep setConstants(CreateTableColumnStep columns) {
            return columns;
        }

        /**
         * use Byte ASM Mode for DAO
         */
        interface ByteASM {
            static void enable() {
                internal.cache.set(internal.AsmFactory.cache::get);
            }
        }

        /**
         * use JDK Dynamic Proxy Mode for DAO
         */
        interface DynamicProxy {
            static void enable() {
                internal.cache.set(internal.DynamicFactory.cache::get);
            }
        }

        @SuppressWarnings("rawtypes")
        final class internal {
            static {
                //when loading internal, will change the decider to real decider for jooq must already been existed.
                Mimic.internal.changeDecider.set(x -> x.getAnnotationsByType(Entity.class).length != 0);
            }

            private internal() {
                throw new IllegalAccessError();
            }

            interface DynamicFactory {
                final class Factory implements DaoFactory {
                    final Table<Record> table;
                    final Map<String, Field> fields;
                    final Class type;
                    final AtomicReference<Constructor<MethodHandles.Lookup>> constructor = new AtomicReference<>();
                    final Class entity;
                    final Map<String, String> fieldToProperty;

                    Factory(Table<Record> table, Map<String, Field> fields, Class type, Class entity) {
                        this.table = table;
                        this.fields = fields;
                        this.type = type;
                        this.entity = entity;
                        this.fieldToProperty = fields == null ? null : seq(fields)
                            .map(x -> x.map2(Field::getName))
                            .map(Tuple2::swap).toMap(Tuple2::v1, Tuple2::v2);
                    }

                    public Map<String, Object> toProperty(Map<String, Object> dm) {
                        if (dm == null) return null;
                        val m = new HashMap<String, Object>();
                        dm.forEach((k, v) -> m.put(fieldToProperty.get(k) != null ? fieldToProperty.get(k) : k, v));
                        return m;
                    }

                    public Map<String, Object> toField(Map<String, Object> dm) {
                        if (dm == null) return null;
                        val m = new HashMap<String, Object>();
                        dm.forEach((k, v) -> m.put(fields.get(k) != null ? fields.get(k).getName() : k, v));
                        return m;
                    }

                    public Dao build(Configuration config) {
                        final Object[] result = new Object[1];
                        result[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (p, m, args) -> {
                            val method = m.getName();
                            switch (method) {
                                case "toString":
                                    return "GeneratedRepository<" + table.getName() + ">";
                                case "hashCode":
                                    return table.hashCode();
                                case "equals":
                                    return table.equals(args[0]);
                                case "table": //special method
                                    return table;
                                case "ctx": //special method
                                    return config.dsl();
                                case "instance": //special method
                                    //noinspection unchecked
                                    return Mimic.newInstance(entity, args[0] == null ? null : toProperty((Map<String, Object>) args[0]));
                                case "toDatabase": //special method
                                    //noinspection unchecked
                                    return args[0] == null ? null : toField((Map<String, Object>) args[0]);
                                case "toEntity": //special method
                                    //noinspection unchecked
                                    return args[0] == null ? null : toProperty((Map<String, Object>) args[0]);
                                default:
                                    if (m.isDefault()) {
                                        try {
                                            if (constructor.get() == null)
                                                constructor.set(Mimic.internal.accessible(MethodHandles.Lookup.class
                                                    .getDeclaredConstructor(Class.class, int.class)));
                                            Class<?> declaringClass = m.getDeclaringClass();
                                            return constructor.get()
                                                .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                                                .unreflectSpecial(m, declaringClass)
                                                .bindTo(result[0])
                                                .invokeWithArguments(args);
                                        } catch (ReflectiveOperationException e) {
                                            throw new IllegalStateException("Cannot invoke default method", e);
                                        }
                                    } else return fields.get(method);
                            }
                        });
                        return (Dao) result[0];
                    }
                }

                LoadingCache<Tuple2<Class, Class>, DaoFactory> cache = Caffeine.newBuilder()
                    .softValues()
                    .maximumSize(cacheSize.get())
                    .build(DynamicFactory::factory);

                static DaoFactory factory(Tuple2<Class, Class> type) {
                    val info = DaoFactory.repoistoryInfoCache.get(type);
                    if (info == null) throw new IllegalStateException("could not generate repository info for " + type);
                    return new Factory(info.table, info.fields, info.dao, info.entity);
                }
            }

            interface AsmFactory {
                final class Factory implements DaoFactory {
                    final Table<Record> table;
                    final Map<String, Field> fields;
                    final Class type;
                    final Class entity;
                    final Map<String, String> fieldToProperty;
                    final Function<Configuration, Dao> ctor;
                    final static Method FIELD;

                    static {
                        try {
                            FIELD = Base.class.getDeclaredMethod("getField", String.class);
                        } catch (NoSuchMethodException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    static final List<String> baseMethods = Arrays.asList("table", "ctx", "instance", "toDatabase", "toEntity");

                    public abstract class Base<T extends Mimic> implements Dao<T> {

                        final Configuration config;

                        protected Base(Configuration config) {
                            this.config = config;
                        }

                        protected Field getField(String name) {
                            return fields.get(name);
                        }

                        @Override
                        public DSLContext ctx() {
                            return config.dsl();
                        }

                        @Override
                        public Table<Record> table() {
                            return table;
                        }

                        @Override
                        public T instance(Map<String, Object> data) {
                            return (T) Mimic.newInstance(entity, toEntity(data));
                        }

                        @Override
                        public Map<String, Object> toDatabase(Map<String, Object> data) {
                            return toField(data);
                        }

                        @Override
                        public Map<String, Object> toEntity(Map<String, Object> data) {
                            return toProperty(data);
                        }
                    }

                    @SneakyThrows
                    private Function<Configuration, Dao> build() {
                        val typeName = type.getCanonicalName() + "$ASM";
                        final List<Class> faces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
                        faces.add(0, type);
                        val face = (List<Class<?>>) (List) faces;
                        DynamicType.Builder<?> builder = new ByteBuddy()
                            .subclass(Base.class)
                            .implement(type)
                            .name(typeName);
                        for (Method m : type.getMethods()) {
                            if (m.isDefault()) {
                                builder = builder.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(DefaultMethodCall.prioritize(face));
                            } else if (baseMethods.contains(m.getName())) {
                                builder = builder.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(SuperMethodCall.INSTANCE);
                            } else {
                                builder = builder.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(MethodCall
                                        .invoke(FIELD)
                                        .with(m.getName())
                                        .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));
                            }
                        }
                        builder = builder.defineMethod("toString", String.class, Visibility.PUBLIC)
                            .intercept(ToStringMethod.prefixedBy(typeName));
                        builder = builder.defineMethod("hashCode", int.class, Visibility.PUBLIC)
                            .intercept(HashCodeMethod.usingDefaultOffset());
                        builder = builder.defineMethod("equals", boolean.class, Visibility.PUBLIC)
                            .withParameters(Object.class)
                            .intercept(EqualsMethod.isolated());
                        val ctor = builder.make().load(type.getClassLoader()).getLoaded().getConstructor(Factory.class, Configuration.class);
                        Function<Configuration, Dao> invoke = (c) -> {
                            try {
                                return (Dao) ctor.newInstance(this, c);
                            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                throw new IllegalStateException(e);
                            }
                        };
                        return invoke;
                    }

                    public Factory(Table<Record> table, Map<String, Field> fields, Class type, Class entity) {
                        this.table = table;
                        this.fields = fields;
                        this.type = type;
                        this.entity = entity;
                        this.fieldToProperty = fields == null ? null : seq(fields)
                            .map(x -> x.map2(Field::getName))
                            .map(Tuple2::swap).toMap(Tuple2::v1, Tuple2::v2);
                        ;
                        this.ctor = build();
                    }

                    public Map<String, Object> toProperty(Map<String, Object> dm) {
                        if (dm == null) return null;
                        val m = new HashMap<String, Object>();
                        dm.forEach((k, v) -> m.put(fieldToProperty.get(k) != null ? fieldToProperty.get(k) : k, v));
                        return m;
                    }

                    public Map<String, Object> toField(Map<String, Object> dm) {
                        if (dm == null) return null;
                        val m = new HashMap<String, Object>();
                        dm.forEach((k, v) -> m.put(fields.get(k) != null ? fields.get(k).getName() : k, v));
                        return m;
                    }

                    @Override
                    public Dao build(Configuration config) {
                        return ctor.apply(config);
                    }
                }

                LoadingCache<Tuple2<Class, Class>, DaoFactory> cache = Caffeine.newBuilder()
                    .softValues()
                    .maximumSize(cacheSize.get())
                    .build(AsmFactory::factory);

                static DaoFactory factory(Tuple2<Class, Class> type) {
                    val info = DaoFactory.repoistoryInfoCache.get(type);
                    if (info == null) throw new IllegalStateException("could not generate repository info for " + type);
                    return new Factory(info.table, info.fields, info.dao, info.entity);
                }
            }

            interface DaoFactory {
                Map<String, Object> toProperty(Map<String, Object> dm);

                Map<String, Object> toField(Map<String, Object> dm);

                Dao build(Configuration config);

                LoadingCache<Tuple2<Class, Class>, RepoInfo> repoistoryInfoCache = Caffeine.newBuilder()
                    .softValues()
                    .maximumSize(cacheSize.get())
                    .build(DaoFactory::buildInfo);

                @AllArgsConstructor(staticName = "of")
                final class RepoInfo {
                    final Table<Record> table;
                    final Map<String, Field> fields;
                    final Class<?> dao;
                    final Class<?> entity;
                }

                //(Table,Fields,RepoType,EntityType)
                static RepoInfo buildInfo(Tuple2<Class, Class> type) {
                    val info = Mimic.internal.Factory.infoCache.get(type.v1);
                    if (info == null) {
                        throw new IllegalStateException("not found Mimic Factory");
                    }
                    final Class<?> entity = type.v1;
                    final Class<?> repo = type.v2;
                    final List<Class> faces = new ArrayList<>(Arrays.asList(repo.getInterfaces()));
                    faces.add(0, repo);
                    val ann = entity.getAnnotationsByType(Entity.class);
                    if (ann.length == 0) {
                        throw new IllegalStateException("there have no @Entity on Mimic ");
                    }
                    var tableName = ann[0].value();
                    if (tableName.isEmpty()) {
                        tableName = camelToUnderscore(entity.getSimpleName());
                    }
                    val table = DSL.table(DSL.name(tableName));
                    val fields = Seq.of(repo.getMethods())
                        .filter(x -> info.propertyInfo.containsKey(x.getName()))
                        .map(f -> buildField(table, f, info.propertyInfo.get(f.getName()).v3.type, faces))
                        .toMap(Tuple2::v1, Tuple2::v2);
                    return RepoInfo.of(table, fields, repo, entity);
                }

                @SuppressWarnings("unchecked")
                @SneakyThrows
                static Tuple2<String, Field> buildField(Table<Record> table, Method m, Class<?> type, List<Class> faces) {
                    val field = m.getName();
                    var name = camelToUnderscore(field);
                    var dataType = DefaultDataType.getDataType(lastConfig.get() == null ? SQLDialect.DEFAULT : lastConfig.get().dialect(), type, null);

                    {
                        val ann = Mimic.internal.collectAnnotations(m, As.class, faces);
                        if (!ann.isEmpty()) {
                            val an = ann.get(0);
                            if (!an.value().isEmpty()) {
                                name = an.value();
                            }
                            if (an.typeHolder() != Void.TYPE && !an.typeProperty().isEmpty()) {
                                val dt = Mimic.internal.sneakyField(an.typeHolder(), an.typeProperty());
                                if (!(dt instanceof DataType)) {
                                    throw new IllegalStateException("defined DataType with @As invalid");
                                }
                                dataType = (DataType<?>) dt;
                            }
                            if (an.procHolder() != Void.TYPE && !an.procProperty().isEmpty()) {
                                val dt = Mimic.internal.sneakyField(an.procHolder(), an.procProperty());
                                if (!(dt instanceof Function)) {
                                    throw new IllegalStateException("defined DataType Proc with @As invalid");
                                }
                                dataType = ((Function<DataType, DataType>) dt).apply(dataType);
                            }
                        }
                    }
                    if (dataType == null)
                        throw new IllegalStateException("type " + type.getCanonicalName() + " can't convert to DataType!");
                    return tuple(field, (Field) DSL.field(DSL.name(table.getName(), name), dataType));
                }

            }

            final static AtomicReference<Function<Tuple2<Class, Class>, DaoFactory>> cache = new AtomicReference<>(DynamicFactory.cache::get);

            static String camelToUnderscore(String camel) {
                val chars = camel.toCharArray();
                val join = new StringJoiner("_");
                var last = 0;
                for (int i = 0; i < chars.length; i++) {
                    if (Character.isUpperCase(chars[i])) {
                        chars[i] = Character.toLowerCase(chars[i]);
                        if (i != 0)
                            join.add(new String(chars, last, i - last));
                        last = i;
                    }
                }
                join.add(new String(chars, last, chars.length - last));
                return join.toString();
            }

            static DaoFactory factory(Tuple2<Class<Mimic>, Class<Dao>> type) {
                return cache.get().apply((Tuple2<Class, Class>) (Tuple2) type);
            }

            final static AtomicReference<Configuration> lastConfig = new AtomicReference<>();

            @SuppressWarnings("unchecked")
            static <T extends Mimic, D extends Dao<T>> D createRepo(Class<T> type, Class<D> repo, Configuration config) {
                setConfiguration(config);
                return (D) factory(tuple((Class<Mimic>) type, (Class<Dao>) (Class<?>) repo))
                    .build(config == null ? lastConfig.get() : config);
            }

            static void setConfiguration(Configuration config) {
                if (config != null) {
                    internal.lastConfig.set(config);
                }
            }
        }

        /**
         * set global JOOQ configuration to avoid pass configuration to {@link Dao#newInstance} every time.
         *
         * @param config the jooq configuration
         */
        static void setConfiguration(Configuration config) {
            internal.setConfiguration(config);
        }

        /**
         * create new Dao instance
         *
         * @param type   the Mimic type
         * @param repo   the Dao type
         * @param config the jooq configuration, if already set once or set with {@link Dao#setConfiguration}, it could be null.
         *               <b>Note:</b> everytime pass a configuration will override global configuration in Dao. but won't affect with Dao instance.
         * @return a Dao Instance
         */
        static <T extends Mimic, D extends Dao<T>> D newInstance(@NotNull Class<T> type, @NotNull Class<D> repo, Configuration config) {
            return internal.createRepo(type, repo, config);
        }

    }
}