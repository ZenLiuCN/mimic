package cn.zenliu.java.mimic;

import cn.zenliu.units.reflect.Invoker;
import cn.zenliu.units.reflect.Ref;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.*;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Sneaky;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;
import org.jooq.lambda.tuple.Tuple5;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @since 2023-09-19
 */
@SuppressWarnings("rawtypes")
public final class mimics {
    static {
        System.setProperty("org.jooq.no-tips", "false");
    }

    private mimics() {
        throw new IllegalAccessError();
    }
    //region types and tools

    //region Types
    @AllArgsConstructor(staticName = "of")
    final static class MimicInfo {
        final Supplier<Map<String, Object>> mapBuilder;
        final NamingStrategy strategy;
        final int concurrentMode;
        final Validator validation;
        final List<Method> defaultMethods;
        final Map<String, Tuple3<Method, Method, PropertyInfo>> propertyInfo;
        final Map<String, Object> defaultValues;

        PropertiesInfo getPropertyInfo() {
            return PropertiesInfo.of(seq(propertyInfo)
                .map(x -> x.map2(v -> v.v3))
                .toMap(Tuple2::v1, Tuple2::v2));
        }
    }

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

        @SuppressWarnings("RedundantCast")
        Object invokeGetter(Object v) {
            var x = getterConv != null ? getterConv.apply(v) : v;
            if (x != null && type.isPrimitive() && (x.getClass() != type)) {
                if (Number.class.isAssignableFrom(x.getClass())) {
                    if (char.class.equals(type)) {
                        return (char) ((Number) x).byteValue();
                    } else if (byte.class.equals(type)) {
                        return ((Number) x).byteValue();
                    } else if (short.class.equals(type)) {
                        return ((Number) x).shortValue();
                    } else if (int.class.equals(type)) {
                        return ((Number) x).intValue();
                    } else if (long.class.equals(type)) {
                        return ((Number) x).longValue();
                    } else if (double.class.equals(type)) {
                        return ((Number) x).doubleValue();
                    } else if (float.class.equals(type)) {
                        return ((Number) x).floatValue();
                    }
                } else if (boolean.class.equals(type) && Boolean.class.isAssignableFrom(x.getClass())) {
                    return (boolean) (Boolean) x;
                } else {
                    throw new IllegalStateException("unknown primitive type conversion: " + x.getClass() + " to " + type);
                }
            }
            return x;
        }

        void validateSetter(Object v) {
            if (setterValidate != null) setterValidate.accept(property, v);
        }
    }

    public interface PropertiesInfo extends Map<String, PropertyInfo> {
        final class impl extends HashMap<String, PropertyInfo> implements PropertiesInfo {
            impl(int initialCapacity, float loadFactor) {
                super(initialCapacity, loadFactor);
            }

            impl(int initialCapacity) {
                super(initialCapacity);
            }

            impl() {
                super();
            }

            impl(Map<? extends String, ? extends PropertyInfo> m) {
                super(m);
            }
        }

        static PropertiesInfo of(int initialCapacity, float loadFactor) {
            return new PropertiesInfo.impl(initialCapacity, loadFactor);
        }

        static PropertiesInfo of(int initialCapacity) {
            return new PropertiesInfo.impl(initialCapacity);
        }

        static PropertiesInfo of(Map<? extends String, ? extends PropertyInfo> m) {
            return new PropertiesInfo.impl(m);
        }
    }

    public interface Validator extends Consumer<Map<String, Object>> {
    }

    @AllArgsConstructor(staticName = "of")
    final static class NamingStrategy {
        final Predicate<Method> getPred;
        final Predicate<Method> setPred;
        final Function<Method, String> getName;
        final Function<Method, String> setName;
        final Function<String, String> extract;
    }
    //endregion


    //FieldExtract,getPredicate,setPredicate,getNameExtract,setNameExtract
    static NamingStrategy nameStrategy(Class<?> cls) {
        final Predicate<Method> getPred;
        final Predicate<Method> setPred;
        final Function<Method, String> setName;
        final Function<Method, String> getName;
        final Function<String, String> extract;
        {
            var ann = cls.getAnnotationsByType(JavaBean.class);
            if (ann.length == 0) {
                getPred = Util.Predication.fluentGetter;
                setPred = Util.Predication.fluentSetter;
                getName = Util.Predication.fluentGetterName;
                setName = Util.Predication.fluentSetterName;
                extract = Function.identity();
            } else {
                var an = ann[0];
                if (an.value()) {
                    getPred = Util.Predication.mixGetter;
                    setPred = Util.Predication.mixSetter;
                    getName = Util.Predication.mixGetterName;
                    setName = Util.Predication.mixSetterName;
                } else {
                    getPred = Util.Predication.beanGetter;
                    setPred = Util.Predication.beanSetter;
                    getName = Util.Predication.beanGetterName;
                    setName = Util.Predication.beanSetterName;
                }
                extract = Util::methodPropertyNameExtract;
            }
        }
        return NamingStrategy.of(getPred, setPred, getName, setName, extract);
    }

    //(method,nameExtractor)->(propertyName,(GetterProcessor,SetterProcessor,ParamValidate,propertyType,ObjectValidation))
    @SuppressWarnings({"ConstantConditions", "unchecked"})
    static Tuple2<String, Tuple5<
        Function<Object, Object>,
        Function<Object, Object>,
        BiConsumer<String, Object>, Class,
        Consumer<Map<String, Object>>>>
    compute(Method m, Function<Method, String> n) {
        var type = m.getDeclaringClass();
        final List<Class<?>> faces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
        faces.add(0, type);
        var name = n.apply(m);
        var ret = m.getReturnType();
        var param = m.getParameterCount() == 1 ? m.getParameters()[0].getType() : null;
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
            var ann = Util.collectAnnotations(m, Array.class, faces);
            if (!ann.isEmpty()) {
                conv = true;
                var typ = ann.get(0).value();
                processor = tuple(
                    v -> {
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
                    v -> {
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
            var ann = m.getAnnotationsByType(AsString.class);
            if (ann.length != 0) {
                conv = true;
                var v = ann[0];
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
            var exists = new HashSet<Tuple2<Class, String>>();
            var va = Seq.seq(Util.collectAnnotations(m, Validation.class, faces))
                .map(x -> {
                    var id = tuple((Class) x.value(), x.property());
                    if (!exists.contains(id)) {
                        exists.add(id);
                        var field = Util.fetchStaticFieldValue(x.value(), x.property());
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
            var fp = pre;
            if (!va.isEmpty() && !conv) {
                var v = (Consumer<Map<String, Object>>) x -> fp.accept(name, x.get(name));
                valid = valid == null ? v : valid.andThen(v);
            }

        }
        //Default convert Mimic Type
        if (processor == null && Mimic.class.isAssignableFrom(prop)) {
            processor = tuple(
                x -> Mimic.class.isAssignableFrom(x.getClass()) ? ((Mimic) x).underlyingMap() : x,
                x -> x instanceof Map ? instance(prop, (Map<String, Object>) x) : x
            );
            var pp = pre;
            pre = pp == null ? (na, x) -> {
                if (x instanceof Mimic)
                    ((Mimic) x).validate();
                else if (x instanceof Map)
                    instance(prop, (Map<String, Object>) x).validate();
            } : (na, x) -> {
                if (x instanceof Mimic)
                    ((Mimic) x).validate();
                else if (x instanceof Map)
                    instance(prop, (Map<String, Object>) x).validate();
                else pp.accept(na, x);
            };
            var v = (Consumer<Map<String, Object>>) x -> instance(prop, (Map<String, Object>) x.get(name)).validate();
            valid = valid == null ? v : valid.andThen(v);

        }
        if (processor == null) {
            processor = tuple(null, null);
        }
        return tuple(name, processor.concat(pre).concat(prop).concat(valid));
    }


    //(asString)=>(FromString,ToString)
    @SuppressWarnings("unchecked")
    static Tuple2<Function<Object, Object>, Function<Object, Object>> extract(AsString an) {
        var holder = an.value();
        var frm = (Function<Object, Object>) Util.fetchStaticFieldValue(holder, an.fromProperty());
        var tr = (Function<Object, Object>) Util.fetchStaticFieldValue(holder, an.toProperty());
        return tuple(frm, tr);
    }

    //endregion
    //region Factory

    interface Factory {
        List<String> defaultMethodName = Arrays.asList("underlyingMap", "underlyingChangedProperties");

        Mimic build(Map<String, Object> data);


        PropertiesInfo properties();

        LoadingCache<Class, MimicInfo> infoCache = Caffeine.newBuilder()
            .softValues()
            .maximumSize(Optional.ofNullable(System.getProperty("mimic.cache")).map(Integer::parseInt).orElse(1024))
            .build(Factory::infoBuild);

        /**
         * @return (MapBuilder, MethodPropertyNameExtract, ConcurrentMode, EntityValidation, DefaultMethods, PropertyInfos)
         */
        static MimicInfo infoBuild(Class<?> cls) {
            try {
                var strategy = nameStrategy(cls);
                var methods = cls.getMethods();
                var getters = Seq.of(methods).filter(strategy.getPred)
                    .filter(x -> !defaultMethodName.contains(x.getName()))
                    .toList();
                var setters = Seq.of(methods).filter(strategy.setPred).toList();
                var other = Seq.of(methods).filter(Method::isDefault).toList();
                var getProcessor = seq(getters)
                    .map(x -> compute(x, strategy.getName).map2(v -> tuple(x, (Method) null, v)))
                    .toMap(Tuple2::v1, Tuple2::v2);
                var setProcessor = seq(setters)
                    .map(x -> compute(x, strategy.setName).map2(v -> tuple((Method) null, x, v)))
                    .toMap(Tuple2::v1, Tuple2::v2);
                //Property->(GetterMethod,SetterMethod,GetterProcessor,SetterProcessor,Validator,PropertyType)
                var prop = new HashMap<String, Tuple3<Method, Method, Tuple4<Function<Object, Object>, Function<Object, Object>, BiConsumer<String, Object>, Class>>>();
                Validator validator = null;
                for (String k : getProcessor.keySet()) {
                    var v = getProcessor.get(k);
                    prop.put(k, v.map3(Tuple5::limit4));
                    if (v.v3.v5 != null)
                        validator = validator == null ? v.v3.v5::accept : validator.andThen(v.v3.v5)::accept;
                }
                for (String k : setProcessor.keySet()) {
                    var v = setProcessor.get(k);
                    var v0 = prop.putIfAbsent(k, v.map3(Tuple5::limit4));
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
                    if (v.v3.v5 != null)
                        validator = validator == null ? v.v3.v5::accept : validator.andThen(v.v3.v5)::accept;
                }
                var info = seq(prop)
                    .map(x -> x.map2(v -> v.map3(t -> new PropertyInfo(x.v1, t))))
                    .toMap(Tuple2::v1, Tuple2::v2);
                var concurrently = cls.getAnnotationsByType(Concurrent.class);
                //0 no concurrent, 1 use sync ,2 use concurrent hashmap
                var concurrent = concurrently.length == 0 ? 0 : concurrently[0].value() ? 1 : 2;
                var n = prop.size(); //see hashMap default load factor
                var mapBuilder =
                    concurrent == 2
                        ? (Supplier<Map<String, Object>>) () -> new ConcurrentHashMap<>(n)
                        : (Supplier<Map<String, Object>>) () -> new HashMap<>(n);
                var defaultValues = seq(info).map(x -> x.map2(v -> {
                    var type = v.v3.type;
                    var conv = v.v3.setterConv;
                    if (type.isPrimitive()) {
                        if (char.class.equals(type)) {
                            return conv != null ? conv.apply((char) 0) : (char) 0;
                        } else if (byte.class.equals(type)) {
                            return conv != null ? conv.apply((byte) 0) : (byte) 0;
                        } else if (short.class.equals(type)) {
                            return conv != null ? conv.apply((short) 0) : (short) 0;
                        } else if (int.class.equals(type)) {
                            return conv != null ? conv.apply(0) : 0;
                        } else if (long.class.equals(type)) {
                            return conv != null ? conv.apply((long) 0) : (long) 0;
                        } else if (double.class.equals(type)) {
                            return conv != null ? conv.apply((double) 0) : (double) 0;
                        } else if (float.class.equals(type)) {
                            return conv != null ? conv.apply((float) 0) : (float) 0;
                        } else if (boolean.class.equals(type)) {
                            return conv != null ? conv.apply(false) : false;
                        }
                    }
                    return null;
                })).filter(x -> x.v2 != null).toMap(Tuple2::v1, Tuple2::v2);
                return MimicInfo.of(mapBuilder, strategy, concurrent, validator, other, info, defaultValues);
            } catch (Exception e) {
                Mimic.log.error("fail to build Mimic '{}' information", cls, e);
                throw e;
            }
        }
    }
    //endregion

    interface ProxyFactory {
        final class DynamicProxyFactory implements Factory {
            final AtomicReference<Constructor<MethodHandles.Lookup>> constructor = new AtomicReference<>();
            final Supplier<Map<String, Object>> mapBuilder;
            final Class cls;
            final Function<String, String> extract;
            final Validator validation;
            final PropertiesInfo prop;
            final int concurrent;
            final Map<String, Object> defaultValues;

            @Override
            public PropertiesInfo properties() {
                return prop;
            }

            DynamicProxyFactory(Supplier<Map<String, Object>> mapBuilder,
                                Class cls,
                                Function<String, String> extract,
                                Validator validation,
                                PropertiesInfo prop,
                                int concurrent,
                                Map<String, Object> defaultValues) {
                this.mapBuilder = mapBuilder;
                this.cls = cls;
                this.extract = extract;
                this.validation = validation;
                this.prop = prop;
                this.concurrent = concurrent;
                this.defaultValues = defaultValues;
            }

            public Mimic build(Map<String, Object> data) {
                final Object[] result = new Object[1];
                final Map<String, Object> map = mapBuilder.get();
                if (data != null && !data.isEmpty()) map.putAll(data);
                //default values
                if (defaultValues != null && !defaultValues.isEmpty()) {
                    defaultValues.forEach(map::putIfAbsent);
                }
                final Set<String> changes = new HashSet<>(prop.size());
                result[0] = Proxy.newProxyInstance(cls.getClassLoader(), new Class[]{cls}, (p, m, args) -> {
                    var method = m.getName();
                    var field = extract.apply(method);
                    switch (method) {
                        case "toString":
                            return cls.getCanonicalName() + "$Proxy@" + Integer.toHexString(map.hashCode()) + map;
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
                                    Class<?> declaringClass = m.getDeclaringClass();
                                    if (method.equals("validate") && m.getReturnType() == Void.TYPE) {
                                        if (validation != null) {
                                            validation.accept(map);
                                        }
                                    }
                                    return Util.fetchMethod(declaringClass, m)
                                        .bindTo(result[0])
                                        .invokeWithArguments(args);
                                } catch (ReflectiveOperationException e) {
                                    throw new IllegalStateException("Cannot invoke default method", e);
                                }
                            } else if (!field.isEmpty() && (args == null || args.length == 0) && m.getReturnType() != Void.TYPE) { //must a getter
                                var mx = map.get(field);
                                if (mx == null) return null;
                                return prop.get(field).invokeGetter(mx);
                            } else if (
                                !field.isEmpty() &&
                                args != null &&
                                args.length == 1 &&
                                (m.getReturnType().isAssignableFrom(cls) || m.getReturnType() == Void.TYPE)) {// must a setter
                                var v = args[0];
                                prop.get(field).validateSetter(v);
                                if (v == null) {
                                    if (concurrent == 1) {
                                        synchronized (map) {
                                            changes.add(field);
                                            map.remove(field);
                                        }
                                    } else {
                                        changes.add(field);
                                        map.remove(field);
                                    }
                                    return m.getReturnType().isAssignableFrom(cls) ? p : null;
                                }
                                v = prop.get(field).invokeSetter(v);
                                if (concurrent == 1) {
                                    synchronized (map) {
                                        changes.add(field);
                                        map.put(field, v);
                                    }
                                } else {
                                    changes.add(field);
                                    map.put(field, v);
                                }
                                return m.getReturnType().isAssignableFrom(cls) ? p : null;
                            } else {
                                throw new IllegalStateException("can not process method '" + m + "': with args" + Arrays.toString(args));
                            }
                    }
                });
                return (Mimic) result[0];
            }

        }

        static Factory build(Class<? extends Mimic> cls) {
            if (!Mimic.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(cls + " is not a mimic!");
            }
            var info = Factory.infoCache.get(cls);
            if (info == null) throw new IllegalStateException("could not generate mimic info from " + cls);
            return new ProxyFactory.DynamicProxyFactory(
                info.mapBuilder,
                cls,
                info.strategy.extract,
                info.validation,
                info.getPropertyInfo(),
                info.concurrentMode,
                info.defaultValues);
        }

        LoadingCache<Class, Factory> cache = Caffeine.newBuilder()
            .softValues()
            .maximumSize(Optional.ofNullable(System.getProperty("mimic.cache")).map(Integer::parseInt).orElse(1024))
            .build(ProxyFactory::build);
    }

    public interface AsmFactory {
        @FunctionalInterface
        interface AsmCreator extends Function<Map, Object> {
        }


        interface FunctorInfo extends Map<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> {
            final class impl extends HashMap<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> implements AsmFactory.FunctorInfo {
                impl(int initialCapacity, float loadFactor) {
                    super(initialCapacity, loadFactor);
                }

                impl(int initialCapacity) {
                    super(initialCapacity);
                }

                impl(Map<? extends String, ? extends Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> m) {
                    super(m);
                }
            }

            static AsmFactory.FunctorInfo of(Map<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> map) {
                return new AsmFactory.FunctorInfo.impl(map);
            }

            static AsmFactory.FunctorInfo of(int cap) {
                return new AsmFactory.FunctorInfo.impl(cap);
            }

            static AsmFactory.FunctorInfo of(int cap, float loadFactor) {
                return new AsmFactory.FunctorInfo.impl(cap, loadFactor);
            }
        }

        abstract class Base implements Mimic {
            protected final AtomicReference<Map<String, Object>> inner = new AtomicReference<>();
            protected final PropertiesInfo info;
            protected final AsmFactory.FunctorInfo functor;
            protected final Set<String> changes;
            protected final Validator validator;
            protected final String name;
            private final Object lock;
            private final AtomicInteger version = new AtomicInteger();
            private final AtomicInteger lastVersion = new AtomicInteger();

            private void trySync(Runnable action) {
                if (lock == null) {
                    action.run();
                    return;
                }
                synchronized (lock) {
                    action.run();
                }
            }

            private <R> R trySync(Supplier<R> action) {
                if (lock == null) return action.get();
                synchronized (lock) {
                    return action.get();
                }
            }

            protected abstract Object self();

            //initial for current map value to self
            void initial() {
                trySync(() -> {
                    for (var p : inner.get().keySet()) {
                        if (!info.containsKey(p)) continue; //safe guarding
                        var info = functor.get(p);
                        if (info != null && info.v2 != null) {
                            info.v2.accept(self(), get(p));
                        } else {
                            log.debug("ignore initial for field {}, cause of no setter exists", p);
                        }
                    }
                    changes.clear();
                });
            }

            //get data in map
            protected Object get(String prop) {
                return trySync(() -> info.get(prop).invokeGetter(inner.get().get(prop)));
            }

            //set data
            protected void set(String prop, Object value) {
                info.get(prop).validateSetter(value);
                version.incrementAndGet();
                changes.add(prop);
            }

            @Override
            public void validate() throws IllegalStateException {
                if (validator != null) validator.accept(underlyingMap());
            }

            protected Base(Map<String, Object> inner,
                           PropertiesInfo info,
                           AsmFactory.FunctorInfo functor,
                           String name,
                           int concurrentMode,
                           Validator validator) {
                this.validator = validator;
                this.inner.set(inner);
                this.info = info;
                this.functor = functor;
                this.name = name;
                this.changes = new HashSet<>(info.size());
                if (concurrentMode == 1) {
                    this.lock = new Object();
                } else {
                    this.lock = null;
                }
            }

            @Override
            public @NotNull Map<String, Object> underlyingMap() {
                if (!changes.isEmpty() && version.get() != lastVersion.get()) {
                    trySync(() -> {
                        if (inner.get() == null) {
                            inner.set(new HashMap<>(info.size()));
                        }
                        var m = inner.get();
                        (m.isEmpty() ? (info.keySet()) : changes)
                            .forEach((p) -> m.put(p, info
                                .get(p)
                                .invokeSetter(functor.get(p).v1.apply(self())))
                            );
                        lastVersion.set(version.get());
                    });
                }
                return inner.get();
            }

            @Override
            public @NotNull Set<String> underlyingChangedProperties() {
                return changes;
            }

            @Override
            public int hashCode() {
                return inner.get().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof AsmFactory.Base) {
                    return ((AsmFactory.Base) obj).inner.get().equals(inner.get());
                }
                return false;
            }

            @Override
            public String toString() {
                return name + "@" + Integer.toHexString(inner.get().hashCode()) + inner.get().toString();
            }

            static final Method GET;
            static final Method SET;


            static {
                try {
                    GET = AsmFactory.Base.class.getDeclaredMethod("get", String.class);
                    SET = AsmFactory.Base.class.getDeclaredMethod("set", String.class, Object.class);
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @SuppressWarnings("rawtypes")
        final class ByteBuddyFactory implements Factory {
            final PropertiesInfo prop;
            final AsmFactory.AsmCreator eager;
            final Class type;

            ByteBuddyFactory(PropertiesInfo prop, AsmFactory.AsmCreator eager, Class type) {
                this.prop = prop;
                this.eager = eager;
                this.type = type;
            }

            @Override
            public Mimic build(Map<String, Object> data) {
                return (Mimic) eager.apply(data);
            }

            @Override
            public PropertiesInfo properties() {
                return prop;
            }

        }

        Object[] ZERO = new Object[0];

        //cls->((lazyCtor,eagerCtor),properties)
        @SuppressWarnings("unchecked")
        @SneakyThrows
        static <T extends Mimic> Tuple2<AsmFactory.AsmCreator, PropertiesInfo> buildInfo(Class<T> cls) {
            try {
                var typeName = cls.getCanonicalName() + "$ASM";
                var info = Factory.infoCache.get(cls);
                if (info == null) throw new IllegalStateException("could not generate mimic info from " + cls);
                var faces = new ArrayList<>(Arrays.asList(cls.getInterfaces()));
                faces.add(0, cls);
                final AsmFactory.FunctorInfo functor = AsmFactory.FunctorInfo.of(info.propertyInfo.size());
                final Function<Method, Class> findDeclared = Sneaky.function(x -> {
                    for (Class<?> c : faces) {
                        for (Method m : c.getDeclaredMethods()) {
                            if (m.equals(x))
                                return c;
                        }
                    }
                    return null;
                });
                final AsmFactory.AsmCreator ctor;

                DynamicType.Builder<?> eager = new ByteBuddy()
                    .subclass(AsmFactory.Base.class)
                    .implement(cls)
                    .name(typeName);
                //Map.Entry<String, Tuple3<Method, Method, PropertyInfo>>
                for (var entry : info.propertyInfo.entrySet()) {
                    var prop = entry.getKey();
                    var typo = entry.getValue().v3.type;
                    eager = eager.defineField(prop, typo, Modifier.PRIVATE);
                    Tuple2<Function<Object, Object>, BiConsumer<Object, Object>> fn = tuple(null, null);
                    //getter
                    {
                        var m = entry.getValue().v1;
                        if (m != null) {
                            {
                                fn = fn.map1($ -> {
                                    var n = Invoker.make(Ref.$.lookup, m).asBiFunction();
                                    return (i) -> n.apply(i, ZERO);
                                });
                            }
                            eager = eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                .intercept(FieldAccessor.ofField(prop));
                        }
                    }
                    //setter
                    {
                        var m = entry.getValue().v2;
                        if (m != null) {
                            {
                                fn = fn.map2($ -> {
                                    if (m.getReturnType().isAssignableFrom(Void.TYPE) || Void.class.isAssignableFrom(m.getReturnType())) {
                                        var n = Invoker.make(Ref.$.lookup, m).asBiConsumer();
                                        System.out.println(m.getName());
                                        return (i, v) ->{
                                            System.out.println(m.getName());
                                            n.accept(i, new Object[]{v});
                                        };
                                    } else {
                                        var n = Invoker.make(Ref.$.lookup, m).asBiFunction();
                                        return (i, v) -> n.apply(i, new Object[]{v});
                                    }

                                });
                                functor.put(prop, fn);
                            }
                            eager = m.getReturnType() == Void.TYPE ?
                                eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(MethodCall
                                        .invoke(AsmFactory.Base.SET)
                                        .with(prop).withArgument(0)
                                        .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0)))
                                :
                                eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                    .withParameters(Arrays.asList(m.getParameterTypes()))
                                    .intercept(
                                        MethodCall
                                            .invoke(AsmFactory.Base.SET)
                                            .with(prop).withArgument(0)
                                            .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0))
                                            .andThen(FixedValue.self())
                                    );

                        }
                    }
                }
                //ignore default methods for no needs to generate
                //extra method
                eager = eager.defineMethod("validate", void.class, Visibility.PUBLIC)
                    .intercept(SuperMethodCall.INSTANCE.andThen(DefaultMethodCall.prioritize(faces)));
                eager = eager.defineMethod("self", cls, Visibility.PROTECTED)
                    .intercept(FixedValue.self());
                eager = eager.defineMethod("toString", String.class, Visibility.PUBLIC)
                    .intercept(SuperMethodCall.INSTANCE);
                eager = eager.defineMethod("hashCode", int.class, Visibility.PUBLIC)
                    .intercept(SuperMethodCall.INSTANCE);
                eager = eager.defineMethod("equals", boolean.class, Visibility.PUBLIC)
                    .withParameters(Object.class)
                    .intercept(SuperMethodCall.INSTANCE);
                var prop = info.getPropertyInfo();
                {
                    var defaultValues = info.defaultValues;

                    // var functor = functorBuilder(cls);//build functor
                    var ctorRef = eager.make()
                        .load(cls.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded()
                        .getConstructor(
                            Map.class,
                            PropertiesInfo.class,
                            AsmFactory.FunctorInfo.class,
                            String.class,
                            int.class,
                            Validator.class);
                    var builder = info.mapBuilder;
                    var con = info.concurrentMode;
                    var valid = info.validation;
                    ctor = (m) -> {
                        try {
                            var x = builder.get();
                            if (m != null && !m.isEmpty()) {
                                x.putAll(m);
                            }
                            if (defaultValues != null && !defaultValues.isEmpty()) {
                                defaultValues.forEach(x::putIfAbsent);
                            }
                            var t = (T) ctorRef.newInstance(x, prop, functor, typeName, con, valid);
                            var b = (AsmFactory.Base) t;
                            if (m != null && !m.isEmpty())
                                b.initial();
                            return t;
                        } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                            Mimic.log.error("fail to build Mimic '{}' instance", cls, e);
                            throw new RuntimeException(e);
                        }
                    };
                }
                return tuple(ctor, prop);
            } catch (Exception e) {
                Mimic.log.error("fail to build Mimic '{}' factory", cls, e);
                throw e;

            }
        }

        LoadingCache<Class, Factory> cache = Caffeine.newBuilder()
            .softValues()
            .maximumSize(Optional.ofNullable(System.getProperty("mimic.cache")).map(Integer::parseInt).orElse(1024))
            .build(AsmFactory::build);

        static Factory build(Class<? extends Mimic> cls) {
            if (!Mimic.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(cls + " is not a Mimic");
            }
            final Tuple2<AsmFactory.AsmCreator, PropertiesInfo> t = buildInfo(cls);
            return new AsmFactory.ByteBuddyFactory(t.v2, t.v1, cls);
        }
    }


    static final AtomicReference<Function<Class, Factory>> factory = new AtomicReference<>();


    @SuppressWarnings({"rawtypes"})
    static Mimic instance(Class type, Map<String, Object> map) {
        return Objects.requireNonNull(factory.get(), "not configurer Mimic Factory mode ").apply(type).build(map);
    }

}
