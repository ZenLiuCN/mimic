/*
 * Copyright 2021 Zen Liu. All Rights Reserved.
 * Licensed under the  GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.zenliu.java.mimic;


import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.var;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;

import static cn.zenliu.java.mimic.Config.cacheSize;
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
@SuppressWarnings({"DuplicatedCode", "unused"})
public interface Mimic {
    Logger log = LoggerFactory.getLogger(Mimic.class);

    //region Definitions
    /**
     * method to fetch internal Map,
     * <p><b>Note:</b>Never TO OVERRIDDEN
     */
    @NotNull
    Map<String, Object> underlyingMap();

    /**
     * this method always effect .
     * <p><b>Note:</b>Never TO OVERRIDDEN
     */
    @NotNull
    Set<String> underlyingChangedProperties();

    /**
     * method to call validate, can be overridden.
     *
     * @throws IllegalStateException if anything not match requirement
     */
    default void validate() throws IllegalStateException {

    }
    //endregion

    interface Validate {
        BiConsumer<String, Object> noop = (p, x) -> {
        };
        BiConsumer<String, Object> notNull = (p, x) -> {
            if (x == null) throw new IllegalStateException(p + " should not be null");
        };
    }

    //region Annotations
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
    //endregion

    /**
     * use Byte ASM Mode for Mimic
     */
    interface ByteASM {

        static void enable() {
            internal.factory.set(internal.AsmFactory.cache::get);
        }
    }

    /**
     * use JDK Dynamic Proxy Mode for Mimic
     */
    interface DynamicProxy {
        static void enable() {
            internal.factory.set(internal.ProxyFactory.cache::get);
        }
    }

    @SuppressWarnings("rawtypes")
    final class internal {
        private internal() {
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
                val x = getterConv != null ? getterConv.apply(v) : v;
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
                return new impl(initialCapacity, loadFactor);
            }

            static PropertiesInfo of(int initialCapacity) {
                return new impl(initialCapacity);
            }

            static PropertiesInfo of(Map<? extends String, ? extends PropertyInfo> m) {
                return new impl(m);
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
                val ann = cls.getAnnotationsByType(JavaBean.class);
                if (ann.length == 0) {
                    getPred = Util.fluentGetter;
                    setPred = Util.fluentSetter;
                    getName = Util.fluentGetterName;
                    setName = Util.fluentSetterName;
                    extract = Function.identity();
                } else {
                    val an = ann[0];
                    if (an.value()) {
                        getPred = Util.mixGetter;
                        setPred = Util.mixSetter;
                        getName = Util.mixGetterName;
                        setName = Util.mixSetterName;
                    } else {
                        getPred = Util.beanGetter;
                        setPred = Util.beanSetter;
                        getName = Util.beanGetterName;
                        setName = Util.beanSetterName;
                    }
                    extract = Util::commonPropertyNameExtract;
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
            val type = m.getDeclaringClass();
            final List<Class<?>> faces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
            faces.add(0, type);
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
                val ann = Util.collectAnnotations(m, Array.class, faces);
                if (!ann.isEmpty()) {
                    conv = true;
                    val typ = ann.get(0).value();
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
                val va = Seq.seq(Util.collectAnnotations(m, Validation.class, faces))
                    .map(x -> {
                        val id = tuple((Class) x.value(), x.property());
                        if (!exists.contains(id)) {
                            exists.add(id);
                            val field = Util.fetchStaticFieldValue(x.value(), x.property());
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


        //(asString)=>(FromString,ToString)
        @SuppressWarnings("unchecked")
        static Tuple2<Function<Object, Object>, Function<Object, Object>> extract(AsString an) {
            val holder = an.value();
            val frm = (Function<Object, Object>) Util.fetchStaticFieldValue(holder, an.fromProperty());
            val tr = (Function<Object, Object>) Util.fetchStaticFieldValue(holder, an.toProperty());
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
                .maximumSize(cacheSize.get())
                .build(Factory::infoBuild);

            /**
             * @return (MapBuilder, MethodPropertyNameExtract, ConcurrentMode, EntityValidation, DefaultMethods, PropertyInfos)
             */
            static MimicInfo infoBuild(Class<?> cls) {
                try {
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
                    Validator validator = null;
                    for (String k : getProcessor.keySet()) {
                        val v = getProcessor.get(k);
                        prop.put(k, v.map3(Tuple5::limit4));
                        if (v.v3.v5 != null)
                            validator = validator == null ? v.v3.v5::accept : validator.andThen(v.v3.v5)::accept;
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
                        if (v.v3.v5 != null)
                            validator = validator == null ? v.v3.v5::accept : validator.andThen(v.v3.v5)::accept;
                    }
                    val info = seq(prop)
                        .map(x -> x.map2(v -> v.map3(t -> new PropertyInfo(x.v1, t))))
                        .toMap(Tuple2::v1, Tuple2::v2);
                    val cann = cls.getAnnotationsByType(Concurrent.class);
                    //0 no concurrent, 1 use sync ,2 use concurrent hashmap
                    val concurrent = cann.length == 0 ? 0 : cann[0].value() ? 1 : 2;
                    val n = prop.size(); //see hashMap default load factor
                    val mapBuilder =
                        concurrent == 2
                            ? (Supplier<Map<String, Object>>) () -> new ConcurrentHashMap<>(n)
                            : (Supplier<Map<String, Object>>) () -> new HashMap<>(n);
                    val defaultValues = seq(info).map(x -> x.map2(v -> {
                        val type = v.v3.type;
                        val conv = v.v3.setterConv;
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
                    log.error("fail to build Mimic '{}' information", cls, e);
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
                        val method = m.getName();
                        val field = extract.apply(method);
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
                                    val mx = map.get(field);
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
                val info = Factory.infoCache.get(cls);
                if (info == null) throw new IllegalStateException("could not generate mimic info from " + cls);
                return new DynamicProxyFactory(
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
                .maximumSize(cacheSize.get())
                .build(internal.ProxyFactory::build);
        }

        public interface AsmFactory {
            @FunctionalInterface
            interface AsmCreator extends Function<Map, Object> {
            }


            interface FunctorInfo extends Map<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> {
                final class impl extends HashMap<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> implements FunctorInfo {
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

                static FunctorInfo of(Map<String, Tuple2<Function<Object, Object>, BiConsumer<Object, Object>>> map) {
                    return new impl(map);
                }

                static FunctorInfo of(int cap) {
                    return new impl(cap);
                }

                static FunctorInfo of(int cap, float loadFactor) {
                    return new impl(cap, loadFactor);
                }
            }

            abstract class Base implements Mimic {
                protected final AtomicReference<Map<String, Object>> inner = new AtomicReference<>();
                protected final PropertiesInfo info;
                protected final FunctorInfo functor;
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
                        for (String p : inner.get().keySet()) {
                            if (!info.containsKey(p)) continue; //safe guarding
                            val info = functor.get(p);
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
                               FunctorInfo functor,
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
                            val m = inner.get();
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
                    if (obj instanceof Base) {
                        return ((Base) obj).inner.get().equals(inner.get());
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
                        GET = Base.class.getDeclaredMethod("get", String.class);
                        SET = Base.class.getDeclaredMethod("set", String.class, Object.class);
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }

            @SuppressWarnings("rawtypes")
            final class ByteBuddyFactory implements Factory {
                final PropertiesInfo prop;
                final AsmCreator eager;
                final Class type;

                ByteBuddyFactory(PropertiesInfo prop, AsmCreator eager, Class type) {
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


            //cls->((lazyCtor,eagerCtor),properties)
            @SuppressWarnings("unchecked")
            @SneakyThrows
            static <T extends Mimic> Tuple2<AsmCreator, PropertiesInfo> buildInfo(Class<T> cls) {
                try {
                    val typeName = cls.getCanonicalName() + "$ASM";
                    val info = Factory.infoCache.get(cls);
                    if (info == null) throw new IllegalStateException("could not generate mimic info from " + cls);
                    val faces = new ArrayList<>(Arrays.asList(cls.getInterfaces()));
                    faces.add(0, cls);
                    final FunctorInfo functor = FunctorInfo.of(info.propertyInfo.size());
                    final Function<Method, Class> findDeclared = Sneaky.function(x -> {
                        for (Class<?> c : faces) {
                            for (Method m : c.getDeclaredMethods()) {
                                if (m.equals(x))
                                    return c;
                            }
                        }
                        return null;
                    });
                    final AsmCreator ctor;

                    DynamicType.Builder<?> eager = new ByteBuddy()
                        .subclass(Base.class)
                        .implement(cls)
                        .name(typeName);
                    //Map.Entry<String, Tuple3<Method, Method, PropertyInfo>>
                    for (val entry : info.propertyInfo.entrySet()) {
                        val prop = entry.getKey();
                        val typo = entry.getValue().v3.type;
                        eager = eager.defineField(prop, typo, Modifier.PRIVATE);
                        Tuple2<Function<Object, Object>, BiConsumer<Object, Object>> fn = tuple(null, null);
                        //getter
                        {
                            val m = entry.getValue().v1;
                            if (m != null) {
                                {
                                    fn = fn.map1($ -> (Function<Object, Object>) Util.Functor.getter(
                                        findDeclared.apply(m),
                                        typo,
                                        m.getName()));
                                }
                                eager = eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                    .intercept(FieldAccessor.ofField(prop));
                            }
                        }
                        //setter
                        {
                            val m = entry.getValue().v2;
                            if (m != null) {
                                {
                                    fn = fn.map2($ -> (BiConsumer<Object, Object>) Util.Functor.setter(
                                        findDeclared.apply(m),
                                        typo,
                                        m.getName(),
                                        m.getReturnType() != Void.TYPE
                                    ));
                                    functor.put(prop, fn);
                                }
                                eager = m.getReturnType() == Void.TYPE ?
                                    eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                        .withParameters(Arrays.asList(m.getParameterTypes()))
                                        .intercept(MethodCall
                                            .invoke(Base.SET)
                                            .with(prop).withArgument(0)
                                            .andThen(FieldAccessor.ofField(prop).setsArgumentAt(0)))
                                    :
                                    eager.defineMethod(m.getName(), m.getReturnType(), Modifier.PUBLIC)
                                        .withParameters(Arrays.asList(m.getParameterTypes()))
                                        .intercept(
                                            MethodCall
                                                .invoke(Base.SET)
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
                    val prop = info.getPropertyInfo();
                    {
                        val defaultValues = info.defaultValues;

                        // val functor = functorBuilder(cls);//build functor
                        val ctorRef = eager.make()
                            .load(cls.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                            .getLoaded()
                            .getConstructor(
                                Map.class,
                                PropertiesInfo.class,
                                FunctorInfo.class,
                                String.class,
                                int.class,
                                Validator.class);
                        val builder = info.mapBuilder;
                        val con = info.concurrentMode;
                        val valid = info.validation;
                        ctor = (m) -> {
                            try {
                                val x = builder.get();
                                if (m != null && !m.isEmpty()) {
                                    x.putAll(m);
                                }
                                if (defaultValues != null && !defaultValues.isEmpty()) {
                                    defaultValues.forEach(x::putIfAbsent);
                                }
                                val t = (T) ctorRef.newInstance(x, prop, functor, typeName, con, valid);
                                val b = (Base) t;
                                if (m != null && !m.isEmpty())
                                    b.initial();
                                return t;
                            } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                                log.error("fail to build Mimic '{}' instance", cls, e);
                                throw new RuntimeException(e);
                            }
                        };
                    }
                    return tuple(ctor, prop);
                } catch (Exception e) {
                    log.error("fail to build Mimic '{}' factory", cls, e);
                    throw e;

                }
            }

            LoadingCache<Class, Factory> cache = Caffeine.newBuilder()
                .softValues()
                .maximumSize(cacheSize.get())
                .build(internal.AsmFactory::build);

            static Factory build(Class<? extends Mimic> cls) {
                if (!Mimic.class.isAssignableFrom(cls)) {
                    throw new IllegalStateException(cls + " is not a Mimic");
                }
                final Tuple2<AsmCreator, PropertiesInfo> t = buildInfo(cls);
                return new ByteBuddyFactory(t.v2, t.v1, cls);
            }
        }


        static final AtomicReference<Function<Class, Factory>> factory = new AtomicReference<>();


        @SuppressWarnings({"rawtypes"})
        static Mimic instance(Class type, Map<String, Object> map) {
            return Objects.requireNonNull(factory.get(), "not configurer Mimic Factory mode ").apply(type).build(map);
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
    @SuppressWarnings({"rawtypes", "unchecked", "unused", "UnusedReturnValue"})
    interface Dao<T extends Mimic> {

        //region Units
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
        String TypeInstantAsTimestamp = "InstantAsTimestamp";
        String TypeBigIntIdentity = "BigIntIdentity";
        String TypeInstantAsTimestampDefaultNow = "InstantAsTimestampDefaultNow";
        Function<DataType, DataType> Identity = x -> x.identity(true);
        Function<DataType, DataType> NotNull = x -> x.nullable(false);
        Function<DataType, DataType> DefaultNull = x -> x.nullable(true).default_(null);
        Function<DataType, DataType> DefaultNow = x -> x.isTimestamp() ? x.default_(DSL.now()) : x;
        String ProcIdentity = "Identity";
        String ProcNotNull = "NotNull";
        String ProcDefaultNull = "DefaultNull";
        String ProcDefaultNow = "DefaultNow";
        //endregion

        //region Definitions

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
            Class<?> typeHolder() default Dao.class;

            /**
             * which public static field in {@link As#typeHolder()} is the {@link DataType} of current field.
             * <p> see {@link Dao#BigIntIdentity} as Example.
             */
            String typeProperty() default "";

            /**
             * define a class holds the DataType Processor;
             * <p>see {@link Dao#Identity} as Example
             */
            Class<?> procHolder() default Dao.class;

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
         * this method used to fetch internal Configuration
         * <p><b>NOTE:</b> DO NOT OVERRIDE
         */
        @ApiStatus.AvailableSince("1.0.5")
        Configuration configuration();

        /**
         * this method returns all fields in database order
         * <p><b>NOTE:</b> MAYBE OVERRIDE
         */
        List<Field<?>> allFields();

        /**
         * default method to find all into a Seq
         */
        @ApiStatus.AvailableSince("1.0.7")
        default Stream<T> queryAll() {
            return ctx()
                .select(allFields())
                .from(table())
                .fetchStream()
                .map(x -> instance(x.intoMap()));
        }

        /**
         * default method to find with conditionOperator into a Stream
         */
        @ApiStatus.AvailableSince("1.0.7")
        default Stream<T> queryConditional(@NotNull Function<SelectJoinStep<?>, ResultQuery<?>> conditionOperator) {
            return conditionOperator.apply(
                    ctx()
                        .select(allFields())
                        .from(table())
                )
                .fetchStream()
                .map(x -> instance(x.intoMap()));
        }

        /**
         * insert value into
         */
        @ApiStatus.AvailableSince("1.0.7")
        default int inertInto(T value) {
            return ctx().insertInto(table())
                .set(toDatabase(value.underlyingMap()))
                .execute();
        }

        /**
         * update value with condition
         */
        @ApiStatus.AvailableSince("1.0.7")
        default int updateWith(T value, Condition condition) {
            val changes = value.underlyingChangedProperties();
            val map = seq(value.underlyingMap())
                .filter(x -> changes.contains(x.v1))
                .toMap(Tuple2::v1, Tuple2::v2);
            return ctx().update(table())
                .set(toDatabase(map))
                .where(condition)
                .execute();
        }


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
        //endregion

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
            private internal() {
                throw new IllegalAccessError();
            }

            interface DynamicFactory {
                final class Factory implements DaoFactory {
                    final Table<Record> table;
                    final Map<String, Field> fields;
                    final List<Field<?>> all;
                    final Class type;

                    final Class entity;
                    final Map<String, String> fieldToProperty;

                    Factory(Table<Record> table, Map<String, Field> fields, List<Field<?>> all, Class type, Class entity) {
                        this.table = table;
                        this.fields = fields;
                        this.type = type;
                        this.entity = entity;
                        this.fieldToProperty = seq(fields)
                            .map(x -> x.map2(Field::getName))
                            .map(Tuple2::swap).toMap(Tuple2::v1, Tuple2::v2);
                        this.all = all;
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
                                    return type.getCanonicalName() + "$Proxy<" + table.getName() + ">" + fields;
                                case "hashCode":
                                    return table.hashCode();
                                case "equals":
                                    return table.equals(args[0]);
                                case "table": //special method
                                    return table;
                                case "ctx": //special method
                                    return config.dsl();
                                case "configuration": //special method
                                    return config;
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
                                            return Util.fetchMethod(m.getDeclaringClass(), m)
                                                .bindTo(result[0])
                                                .invokeWithArguments(args);
                                        } catch (ReflectiveOperationException e) {
                                            throw new IllegalStateException("Cannot invoke default method", e);
                                        }
                                    } else if (m.getParameterCount() == 0 && method.equals("allFields")) {
                                        return all;
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
                    val info = DaoFactory.repositoryInfoCache.get(type);
                    if (info == null || info.fields == null)
                        throw new IllegalStateException("could not generate repository info for " + type);
                    return new Factory(info.table, info.fields, info.all, info.dao, info.entity);
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
                    final static Method FIELD_METHOD;
                    final List<Field<?>> all;

                    static {
                        try {
                            FIELD_METHOD = Base.class.getDeclaredMethod("getField", String.class);
                        } catch (NoSuchMethodException e) {
                            throw new IllegalStateException(e);
                        }
                    }

                    static final List<String> baseMethods = Arrays.asList("table",
                        "ctx",
                        "instance",
                        "toDatabase",
                        "toEntity",
                        "configuration",
                        "allFields"
                    );

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

                        @Override
                        public String toString() {
                            return type.getCanonicalName() + "$ASM<" + table.toString() + ">" + fields;
                        }

                        @Override
                        public Configuration configuration() {
                            return config;
                        }

                        @Override
                        public List<Field<?>> allFields() {
                            return all;
                        }

                        @Override
                        public int hashCode() {
                            return table.hashCode() * 31 + fields.hashCode();
                        }

                        @Override
                        public boolean equals(Object obj) {
                            if (obj instanceof Base) {
                                return table.equals(((Base<?>) obj).table()) && allFields().equals(((Base<?>) obj).allFields());
                            }
                            return false;
                        }
                    }

                    @SneakyThrows
                    private Function<Configuration, Dao> build() {
                        try {
                            val typeName = type.getCanonicalName() + "$ASM";
                            final List<Class> faces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
                            faces.add(0, type);
                            val face = (List<Class<?>>) (List) faces;
                            DynamicType.Builder<?> builder = new ByteBuddy()
                                .subclass(Base.class)
                                .implement(type)
                                .name(typeName);
                            for (Method m : type.getMethods()) {
                                if (Modifier.isStatic(m.getModifiers()) || m.isDefault()) {// fix skip default methods
                                    continue;
                                }
                                if (baseMethods.contains(m.getName())) { //else allFields hit this
                                    builder = builder.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                                        .withParameters(Arrays.asList(m.getParameterTypes()))
                                        .intercept(SuperMethodCall.INSTANCE);
                                } else {
                                    builder = builder.defineMethod(m.getName(), m.getReturnType(), Visibility.PUBLIC)
                                        .withParameters(Arrays.asList(m.getParameterTypes()))
                                        .intercept(MethodCall
                                            .invoke(FIELD_METHOD)
                                            .with(m.getName())
                                            .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));
                                }
                            }
                            builder = builder.defineMethod("toString", String.class, Visibility.PUBLIC)
                                .intercept(SuperMethodCall.INSTANCE);
                            builder = builder.defineMethod("hashCode", int.class, Visibility.PUBLIC)
                                .intercept(SuperMethodCall.INSTANCE);
                            builder = builder.defineMethod("equals", boolean.class, Visibility.PUBLIC)
                                .withParameters(Object.class)
                                .intercept(SuperMethodCall.INSTANCE);
                            val ctor = builder.make().load(type.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                                .getLoaded()
                                .getConstructor(Factory.class, Configuration.class);
                            return (c) -> {
                                try {
                                    return (Dao) ctor.newInstance(this, c);
                                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                                    log.error("fail to create DAO instance {}", type, e);
                                    throw new IllegalStateException(e);
                                }
                            };
                        } catch (SecurityException | NoSuchMethodException e) {
                            log.error("fail to create DAO '{}' factory", type, e);
                            throw e;
                        }
                    }

                    public Factory(Table<Record> table, Map<String, Field> fields, List<Field<?>> all, Class type, Class entity) {
                        this.table = table;
                        this.fields = fields;
                        this.type = type;
                        this.entity = entity;
                        this.fieldToProperty = fields == null ? null : seq(fields)
                            .map(x -> x.map2(Field::getName))
                            .map(Tuple2::swap).toMap(Tuple2::v1, Tuple2::v2);
                        this.ctor = build();
                        this.all = all;
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
                    val info = DaoFactory.repositoryInfoCache.get(type);
                    if (info == null) throw new IllegalStateException("could not generate repository info for " + type);
                    return new Factory(info.table, info.fields, info.all, info.dao, info.entity);
                }
            }

            interface DaoFactory {
                Map<String, Object> toProperty(Map<String, Object> dm);

                Map<String, Object> toField(Map<String, Object> dm);

                Dao build(Configuration config);

                LoadingCache<Tuple2<Class, Class>, RepoInfo> repositoryInfoCache = Caffeine.newBuilder()
                    .softValues()
                    .maximumSize(cacheSize.get())
                    .build(DaoFactory::buildInfo);

                @AllArgsConstructor(staticName = "of")
                final class RepoInfo {
                    final Table<Record> table;
                    final Map<String, Field> fields;
                    final List<Field<?>> all;
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
                    final List<Class<?>> faces = new ArrayList<>(Arrays.asList(repo.getInterfaces()));
                    faces.add(0, repo);
                    val ann = entity.getAnnotationsByType(Entity.class);
                    if (ann.length == 0) {
                        throw new IllegalStateException("there have no @Entity on Mimic ");
                    }
                    var tableName = ann[0].value();
                    if (tableName.isEmpty()) {
                        tableName = Util.Case.classToTable.apply(entity.getSimpleName());
                    }
                    val table = DSL.table(DSL.name(tableName));
                    val tuples = Seq.of(repo.getMethods())
                        .filter(x ->
                            !Modifier.isStatic(x.getModifiers()) &&
                                !x.isDefault() &&
                                info.propertyInfo.containsKey(x.getName()))
                        .map(f -> buildField(table, f, info.propertyInfo.get(f.getName()).v3.type, faces)).toList();
                    val fields = seq(tuples).toMap(Tuple2::v1, Tuple2::v2);
                    List<Field<?>> all = (List<Field<?>>) (List) seq(tuples).map(x -> x.v2).toList();
                    return RepoInfo.of(table, fields, all, repo, entity);
                }

                @SneakyThrows
                static Tuple2<String, Field> buildField(Table<Record> table, Method m, Class<?> type, List<Class<?>> faces) {
                    try {
                        val field = m.getName();
                        var name = Util.Case.propertyToTable.apply(field);
                        DataType<?> dataType;
                        //guess
                        try {
                            dataType = DefaultDataType.getDataType(lastConfig.get() == null ? SQLDialect.DEFAULT : lastConfig.get().dialect(), type, null);
                        } catch (Exception e) {
                            dataType = null;
                        }
                        {
                            val ann = Util.collectAnnotations(m, As.class, faces);
                            if (!ann.isEmpty()) {
                                val an = ann.get(0);
                                if (!an.value().isEmpty()) {
                                    name = an.value();
                                }
                                if (an.typeHolder() != Void.TYPE && !an.typeProperty().isEmpty()) {
                                    val dt = Util.fetchStaticFieldValue(an.typeHolder(), an.typeProperty());
                                    if (!(dt instanceof DataType)) {
                                        throw new IllegalStateException("defined DataType with @As invalid");
                                    }
                                    dataType = (DataType<?>) dt;
                                }
                                if (an.procHolder() != Void.TYPE && !an.procProperty().isEmpty()) {
                                    val dt = Util.fetchStaticFieldValue(an.procHolder(), an.procProperty());
                                    if (!(dt instanceof Function)) {
                                        throw new IllegalStateException("defined DataType Proc with @As invalid");
                                    }
                                    dataType = ((Function<DataType, DataType>) dt).apply(dataType);
                                }
                            }
                        }
                        if (dataType == null)
                            throw new IllegalStateException("type " + type.getCanonicalName() + " can't convert to DataType!");
                        return tuple(field, DSL.field(DSL.name(table.getName(), name), dataType));
                    } catch (Exception e) {
                        log.error("fail to generate DAO '{}' information", type, e);
                        throw e;
                    }
                }

            }

            final static AtomicReference<Function<Tuple2<Class, Class>, DaoFactory>> cache = new AtomicReference<>();

            static DaoFactory factory(Tuple2<Class<Mimic>, Class<Dao>> type) {
                return Objects.requireNonNull(cache.get(), "not configurer DAO factory mode ").apply((Tuple2<Class, Class>) (Tuple2) type);
            }

            final static AtomicReference<Configuration> lastConfig = new AtomicReference<>();

            @SuppressWarnings("unchecked")
            static <T extends Mimic, D extends Dao<T>> D createRepo(Class<T> type, Class<D> repo, Configuration config) {
                setConfiguration(config);
                //noinspection RedundantCast
                return (D) factory(tuple((Class<Mimic>) type, (Class<Dao>) (Class) repo))
                    .build(config == null ? lastConfig.get() : config);
            }

            static void setConfiguration(Configuration config) {
                if (config != null && isNotInTransaction(config)) {
                    if (log.isTraceEnabled()) log.trace("update global Configuration to {}", config);
                    internal.lastConfig.set(config);
                }
            }

            static boolean isNotInTransaction(Configuration config) {
                try (val conn = config.dsl().parsingConnection()) {
                    val status = conn.getAutoCommit();
                    if (log.isTraceEnabled()) log.trace("current Configuration autocommit is {}", status);
                    return status;
                } catch (SQLException e) {
                    return false;
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
         * <p>this method will check the configuration status:
         * <p>if found connection with AutoCommit true ( means that it's a transaction configuration ), will set it as Global Configuration, else won't.
         *
         * @param entity the Mimic type
         * @param dao    the Dao type
         * @param config the jooq configuration, if already set once or set with {@link Dao#setConfiguration}, it could be null.
         *               <b>Note:</b> everytime pass a configuration will override global configuration in Dao. but won't affect with Dao instance.
         * @return a Dao Instance
         */
        static <T extends Mimic, D extends Dao<T>> D newInstance(@NotNull Class<T> entity, @NotNull Class<D> dao, Configuration config) {
            return internal.createRepo(entity, dao, config);
        }
    }

    @ApiStatus.AvailableSince("1.1.2")
    interface Util {
        /**
         * Lambda runtime wrapping for Getters And Setters
         */
        interface Functor {
            @SneakyThrows
            static <T> CallSite generateCallSite(
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
                    return LambdaMetafactory.metafactory(
                        lookup,
                        invokeName,
                        MethodType.methodType(lambdaType),
                        samMethodType,
                        lookup.findVirtual(targetClass, methodName, virtualMethodType),
                        instanceMethodType);
                } catch (Throwable ex) {
                    log.error("error to generate call site from {} named as {}", targetClass, methodName, ex);
                    throw ex;
                }
            }

            @SneakyThrows
            @SuppressWarnings("unchecked")
            static <O, V> Function<O, V> getter(
                final @NotNull Class<O> target,
                final @NotNull Class<V> valueType,
                final @NotNull String getterName
            ) {
                val site = generateCallSite(
                    Function.class,
                    "apply",
                    MethodType.methodType(Object.class, Object.class),
                    target,
                    getterName,
                    MethodType.methodType(valueType),
                    MethodType.methodType(valueType, target)
                );
                try {
                    return (Function<O, V>) site.getTarget().invokeExact();
                } catch (Throwable ex) {
                    log.error("error to generate getter lambda from {} named as {}", target, getterName, ex);
                    throw ex;
                }

            }

            @SneakyThrows
            @SuppressWarnings("unchecked")
            static <O, V> BiConsumer<O, V> setter(
                final @NotNull Class<O> target,
                final @NotNull Class<V> valueType,
                final @NotNull String setterName,
                final boolean chain
            ) {
                val site = chain ?
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
                );
                try {
                    if (chain) {
                        val fn = (BiFunction<O, V, O>) site.getTarget().invokeExact();
                        return fn::apply;
                    }
                    return (BiConsumer<O, V>) site.getTarget().invokeExact();
                } catch (Throwable ex) {
                    log.error("error to generate BeanSetter Functor from {} named as {} ", target, setterName, ex);
                    throw ex;
                }
            }
        }

        /**
         * The major version of current JVM, eg: 8 for 1.8.x, 17 for jdk 17.x.x
         */
        static int jvmMajorVersion() {
            var v = System.getProperty("java.version");
            if (v.startsWith("1.")) {
                return Integer.parseInt(v.substring(2, 3));
            } else return Integer.parseInt(v.substring(0, v.indexOf(".")));
        }

        int jvmMajorVersion = jvmMajorVersion();

        /**
         * set AccessibleObject to accessible
         */
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
            if (!accessible.isAccessible())
                accessible.setAccessible(true);
            return accessible;
        }

        /**
         * MethodHandles Lookup Constructor
         */
        AtomicReference<Constructor<MethodHandles.Lookup>> constructor = new AtomicReference<>();

        @SuppressWarnings("JavaReflectionMemberAccess")
        @SneakyThrows
        static MethodHandle fetchMethod(Class<?> declaringClass, Method m) {
            if (constructor.get() == null) {
                constructor.set(
                    accessible(
                        jvmMajorVersion < 14 ? //checked in OpenJDK repository changed in
                            // https://github.com/openjdk/jdk16/releases/tag/jdk-14-ga
                            MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class)
                            : MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Class.class, int.class)
                    )
                );
            }
            return (jvmMajorVersion < 14 ?
                constructor.get()
                    .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                : constructor.get()
                .newInstance(declaringClass, null, MethodHandles.Lookup.PRIVATE))
                .unreflectSpecial(m, declaringClass);
        }

        @SneakyThrows
        static Object fetchStaticFieldValue(Class<?> cls, String field) {
            val mField = cls.getField(field);
            if (!Modifier.isStatic(mField.getModifiers()))
                throw new IllegalStateException("not static field '" + field + "' on '" + cls + "'");
            return accessible(mField).get(null);
        }

        static <T extends Annotation> List<T> collectAnnotations(Method m, Class<T> an, List<Class<?>> faces) {
            val collected = new ArrayList<T>();
            for (Class<?> cls : faces) {
                try {
                    val fn = cls.getDeclaredMethod(m.getName(), m.getParameterTypes());
                    collected.addAll(Arrays.asList(fn.getAnnotationsByType(an)));
                } catch (NoSuchMethodException ignored) {

                }
            }
            return collected;
        }

        Predicate<Method> isPublicNoneDefault = x -> !x.isDefault() &&
            !Modifier.isStatic(x.getModifiers()) &&
            Modifier.isPublic(x.getModifiers());
        Predicate<Method> isBeanGetter = x -> (x.getParameterCount() == 0 &&
            (x.getName().startsWith("get") && x.getReturnType() != Void.TYPE ||
                (x.getReturnType() == boolean.class && x.getName().startsWith("is")))
        );
        Predicate<Method> isBeanSetter = x -> (x.getParameterCount() == 1 &&
            x.getName().startsWith("set") &&
            (x.getReturnType() == Void.TYPE || x.getReturnType() == x.getDeclaringClass()));

        Predicate<Method> isFluentSetter = x -> (
            x.getParameterCount() == 1 &&
                (x.getReturnType() == Void.TYPE || x.getReturnType() == x.getDeclaringClass())
        );
        Predicate<Method> isFluentGetter = x -> (x.getParameterCount() == 0 && x.getReturnType() != Void.TYPE);
        Predicate<Method> fluentGetter = isPublicNoneDefault.and(isFluentGetter);
        Predicate<Method> fluentSetter = isPublicNoneDefault.and(isFluentSetter);
        Predicate<Method> beanSetter = isPublicNoneDefault.and(isBeanSetter);
        Predicate<Method> beanGetter = isPublicNoneDefault.and(isBeanGetter);
        Predicate<Method> mixGetter = isPublicNoneDefault.and(isFluentGetter.or(isBeanGetter));
        Predicate<Method> mixSetter = isPublicNoneDefault.and(isFluentSetter.or(isBeanSetter));
        Function<Method, String> fluentGetterName = Method::getName;
        Function<Method, String> fluentSetterName = Method::getName;
        Function<Method, String> beanGetterName = x -> Case.pascalToCamel(x.getName().startsWith("is") ? x.getName().substring(2) : x.getName().substring(3));
        Function<Method, String> beanSetterName = x -> Case.pascalToCamel(x.getName().substring(3));
        Function<Method, String> mixSetterName = x -> x.getName().startsWith("set") ? Case.pascalToCamel(x.getName().substring(3)) : x.getName();
        Function<Method, String> mixGetterName = x -> x.getName().startsWith("is") || x.getName().startsWith("get") ? beanGetterName.apply(x) : x.getName();

        static String commonPropertyNameExtract(String x) {
            return x.startsWith("is") ? Case.pascalToCamel(x.substring(2)) :
                x.startsWith("get") || x.startsWith("set") ? Case.pascalToCamel(x.substring(3)) : x;
        }

        interface Case {
            int CASE_MASK = 0x20;

            static boolean isLower(char c) {
                return (c >= 'a') && (c <= 'z');
            }

            static boolean isUpper(char c) {
                return (c >= 'A') && (c <= 'Z');
            }

            static char toUpper(char c) {
                return isLower(c) ? (char) (c ^ CASE_MASK) : c;
            }

            static char toLower(char c) {
                return isUpper(c) ? (char) (c ^ CASE_MASK) : c;
            }

            static boolean isLowerUnderscore(String str, char divider) {
                return str.chars().allMatch(x -> x == divider || !isUpper((char) x));
            }

            static boolean isUpperDivide(String str, char divider) {
                return str.chars().allMatch(x -> x == divider || !isLower((char) x));
            }

            static boolean isPascal(String str) {
                return isUpper(str.charAt(0)) && str.chars().noneMatch(x -> x == '-' || x == '_');
            }

            static boolean isCamel(String str) {
                return isLower(str.charAt(0)) && str.chars().noneMatch(x -> x == '-' || x == '_');
            }

            static String camelToLowerDivide(String str, char divider) {
                val sb = new StringBuilder();
                for (char c : str.toCharArray()) {
                    if (isUpper(c)) {
                        sb.append(divider).append(toLower(c));
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }

            static String camelToUpperDivide(String str, char divider) {
                val sb = new StringBuilder();
                for (char c : str.toCharArray()) {
                    if (isUpper(c)) {
                        sb.append(divider).append(c);
                    } else {
                        sb.append(toUpper(c));
                    }
                }
                return sb.toString();
            }

            static String camelToPascal(String str) {
                return toUpper(str.charAt(0)) + str.substring(1);
            }

            static String pascalToCamel(String str) {
                return toLower(str.charAt(0)) + str.substring(1);
            }

            static String pascalToUpperDivide(String str, char divider) {
                val sb = new StringBuilder();
                for (char c : str.toCharArray()) {
                    if (isUpper(c)) {
                        if (sb.length() > 2)
                            sb.append(divider);
                        sb.append(c);
                    } else {
                        sb.append(toUpper(c));
                    }
                }
                return sb.toString();
            }

            static String pascalToLowerDivide(String str, char divider) {
                val sb = new StringBuilder();
                for (char c : str.toCharArray()) {
                    if (isUpper(c)) {
                        if (sb.length() > 2)
                            sb.append(divider);
                        sb.append(toLower(c));
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }

            Function<String, String> classToTable = s -> pascalToLowerDivide(s, '_');
            Function<String, String> propertyToTable = s -> camelToLowerDivide(s, '_');
        }

    }
}