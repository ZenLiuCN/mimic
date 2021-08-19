package cn.zenliu.java.mimic;

import jdk.nashorn.internal.ir.annotations.Immutable;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.val;
import lombok.var;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.function.Function4;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import org.jooq.lambda.tuple.Tuple3;
import sun.reflect.CallerSensitive;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.lang.annotation.*;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Mimic is a Protocol to use Map as Underlying storage for a interface.which must satisfied: <br/>
 * <p> a: Must a Interface.
 * <p> b: Must annotated with {@link Configuring.Mimicked}, or inherited from a Interface is annotated with {@link Configuring.Mimicked}.
 * <p> c: Optional to extends from {@link Mimic}, which supported with underlying methods. User can just directly define one or more underlying methods with same signature of each as default methods.
 * <p> d: {@link Mimic#underlyingInstance()} is required when there have one or more Setter match Chain protocol (which return self when called).
 * <p><b>Features:</b>
 * <p> 1. all field should naming match JavaBean or Fluent definition. default use JavaBean protocol, which can use {@link Configuring.Mimicked#fluent()} to switch into Fluent mode.
 * <p> 2. operation methods should defined as default methods.
 * <p> 3. use {@link Configuring} annotations to configure the Mimic.
 * <p> 4. use {@link Converting} annotations to define value convert.
 * <p> 5. use {@link Containing} annotations to define container with Mimic type.
 * <p> 6. use {@link Validating} annotations to define value validate, which will effect on setter is called or {@link Mimic#validate()} is called.
 * <p><b>NOTE:</b> carefully to use a Mimic type nesting in fields, this may cause a loop dependency or slowdown the efficiency.
 *
 * @param <T> type of final interface, which will be the returning value of a Chain Setter.
 * @author Zen.Liu
 * @apiNote
 * @since 2021-08-12
 */


@SuppressWarnings("unused")
public interface Mimic<T> {
    /**
     * all underlying method names
     */
    @ApiStatus.Internal
    List<String> underlyingNames = Arrays.asList(
        "underlyingMap",
        "underlyingNaming",
        "signature",
        "underlyingType",
        "underlyingInstance"
    );

    /**
     * <b>DO NOT OVERRIDE</b>: it will implemented by Factory.<br/>
     * fetch the storage map: Key is Field Number.
     *
     * @return map
     */
    default Map<Integer, Object> underlyingMap() {
        throw new NotImplementedException();
    }

    /**
     * <b>DO NOT OVERRIDE</b>: it will implemented by Factory.<br/>
     * fetch the field name list, which indexed as the field number
     *
     * @return List
     */
    default @Immutable
    List<String> underlyingNaming() {
        throw new NotImplementedException();
    }

    /**
     * <b>DO NOT OVERRIDE</b>: it will implemented by Factory.<br/>
     * fetch the instance signature. current is full class name.
     *
     * @return String
     */
    default @NotNull String signature() {
        throw new NotImplementedException();
    }

    /**
     * <b>DO NOT OVERRIDE</b>: it will implemented by Factory.<br/>
     * fetch the instance interface type.
     *
     * @return Class
     */
    default Class<T> underlyingType() {
        throw new NotImplementedException();
    }

    /**
     * <b>DO NOT OVERRIDE</b>: it will implemented by Factory.<br/>
     * fetch the instance. use for internal purpose.
     *
     * @return the Instance it self.
     */
    default T underlyingInstance() {
        throw new NotImplementedException();
    }

    /**
     * could be overridden. this method will trigger value validate before call the real definition.<br/>
     * fetch the instance signature. current is full class name.
     *
     * @return Self
     * @see Validating.Validator
     */
    default T validate() throws IllegalArgumentException {
        return underlyingInstance();
    }

    /**
     * element use to define the convert behavior.
     */
    interface Converting {
        String NULL = "";

        @FunctionalInterface
        interface Serialize<T> {
            String proc(@Nullable T object);
        }

        @FunctionalInterface
        interface Deserialize<T> {
            @Nullable T proc(String data);
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        @Target(ElementType.METHOD)
        @interface Converted {
            Class<?> value();

            String serialize();

            String deserialize();
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Repeatable(Converter.List.class)
        @Target(ElementType.TYPE)
        @Documented
        @interface Converter {
            Class<?> value();

            Class<?> holder();

            String serialize() default "serialize";

            String deserialize() default "deserialize";

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.TYPE)
            @Documented
            @interface List {
                Converter[] value();
            }
        }

        @ApiStatus.Internal
        @SuppressWarnings("unchecked")
        @SneakyThrows
        static Tuple2<Class<?>, Tuple2<Deserialize<Object>, Serialize<Object>>> extract(Converter ann) {
            var f = ann.holder().getField(ann.serialize());
            if (!Modifier.isStatic(f.getModifiers()) || !Serialize.class.isAssignableFrom(f.getType()))
                throw new IllegalStateException(ann.holder().getName() + "#" + ann.serialize() + " not valid Serialize type!");
            val serialize = (Serialize<Object>) f.get(ann.holder());
            f = ann.holder().getField(ann.deserialize());
            if (!Modifier.isStatic(f.getModifiers()) || !Deserialize.class.isAssignableFrom(f.getType()))
                throw new IllegalStateException(ann.holder().getName() + "#" + ann.deserialize() + " not valid Deserialize type!");
            val deserialize = (Deserialize<Object>) f.get(ann.holder());
            return tuple(ann.value(), tuple(deserialize, serialize));
        }

        @ApiStatus.Internal
        @SuppressWarnings("unchecked")
        @SneakyThrows
        static Tuple2<Deserialize<Object>, Serialize<Object>> extract(Converted ann) {
            var f = ann.value().getField(ann.serialize());
            if (f.getModifiers() != Modifier.STATIC || !Serialize.class.isAssignableFrom(f.getType()))
                throw new IllegalStateException(ann.value().getName() + "#" + ann.serialize() + " not valid Serialize type!");
            val serialize = (Serialize<Object>) f.get(ann.value());
            f = ann.value().getField(ann.deserialize());
            if (f.getModifiers() != Modifier.STATIC || !Deserialize.class.isAssignableFrom(f.getType()))
                throw new IllegalStateException(ann.value().getName() + "#" + ann.deserialize() + " not valid Deserialize type!");
            val deserialize = (Deserialize<Object>) f.get(ann.value());
            return tuple(deserialize, serialize);
        }
    }

    /**
     * element use to define the validation behavior.
     */
    interface Validating {
        @FunctionalInterface
        interface Validator<T> {
            /**
             * @param object the parameter
             * @return true if valid, or else false
             */
            boolean valid(@Nullable T object);

            default Validator<T> then(Validator<T> next) {
                return o -> valid(o) && next.valid(o);
            }

            default void validate(String entity, String field, String message, @Nullable T object) throws IllegalArgumentException {
                if (!valid(object)) throw new IllegalArgumentException(entity + "." + field + " " + message);
            }

            interface EntityFieldValidator<T> {
                void valid(String entity, String field, T value);

                @SuppressWarnings("unchecked")
                default Factory.Validator closure(String entity, String field) {
                    return o -> valid(entity, field, (T) o);
                }
            }

            default EntityFieldValidator<T> withMessage(String message) {
                return (entity, field, obj) -> validate(entity, field, message, obj);
            }
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Repeatable(Validate.List.class)
        @Documented
        @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
        @interface Validate {
            Class<?> value();

            String field() default "validate";

            String message() default "not match requirement";

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @Documented
            @interface List {
                Validate[] value();
            }
        }

        @ApiStatus.Internal
        @SneakyThrows
        static Validator.EntityFieldValidator<?> extract(Validate annotation) {
            val f = annotation.value().getField(annotation.field());
            if (!Modifier.isStatic(f.getModifiers()) || !Validator.class.isAssignableFrom(f.getType()))
                throw new IllegalStateException(annotation.value().getName() + "#" + annotation.field() + " not valid Validator type!");
            return ((Validator<?>) f.get(annotation.value())).withMessage(annotation.message());
        }
    }

    /**
     * element use to define the container behavior
     */
    interface Containing {
        /**
         * support for Collection value, Map value, Location in Tuple
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Repeatable(Container.List.class)
        @Documented
        @Target({ElementType.METHOD})
        @Inherited
        @interface Container {
            int inTuple() default -1;

            Class<? extends Mimic<?>> value();


            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @Documented
            @Inherited
            @interface List {
                Container[] value();
            }

        }

        @ApiStatus.Internal
        static Tuple dynamicConstruct(Object[] o) {
            switch (o.length) {
                case 0:
                    return tuple();
                case 1:
                    return tuple(o[0]);
                case 2:
                    return tuple(
                        o[0],
                        o[1]);
                case 3:
                    return tuple(o[0],
                        o[1],
                        o[2]);
                case 4:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3]);
                case 5:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4]);
                case 6:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5]);
                case 7:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6]);
                case 8:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7]);
                case 9:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8]);
                case 10:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9]);
                case 11:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9],
                        o[10]);
                case 12:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9],
                        o[10],
                        o[11]);
                case 13:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9],
                        o[10],
                        o[11],
                        o[12]);
                case 14:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9],
                        o[10],
                        o[11],
                        o[12],
                        o[13]);
                case 15:
                    return tuple(o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9],
                        o[10],
                        o[11],
                        o[12],
                        o[13],
                        o[14]);
                case 16:
                    return tuple(
                        o[0],
                        o[1],
                        o[2],
                        o[3],
                        o[4],
                        o[5],
                        o[6],
                        o[7],
                        o[8],
                        o[9],
                        o[10],
                        o[11],
                        o[12],
                        o[13],
                        o[14],
                        o[15]);
                default:
                    throw new IllegalStateException("tuple degree should never more than 16!");

            }
        }

        @SuppressWarnings("unchecked")
        @ApiStatus.Internal
        //(getter,setter)
        static @Nullable Factory.Processor[] extract(Method method, Factory.Strategy.FactorySupplier supplier) {
            val getter = method.getParameterCount() == 0;
            final Class<?> type = getter ? method.getReturnType() : method.getParameterTypes()[0];
            val anon = Arrays.asList(method.getAnnotationsByType(Container.class));
            if (anon.isEmpty()) return null;
            if (Collection.class.isAssignableFrom(type) && anon.size() == 1) {
                val fn = supplier.apply(anon.get(0).value());
                assert fn != null;
                if (List.class.isAssignableFrom(type)) {
                    return new Factory.Processor[]{
                        x -> x == null ? null : seq((Collection<Map<Integer, Object>>) x).map(fn::innerBuild).toList(),
                        x -> x == null ? null : seq((Collection<Mimic<?>>) x).map(Mimic::underlyingMap).toList()
                    };
                } else if (Set.class.isAssignableFrom(type)) {
                    return new Factory.Processor[]{
                        x -> x == null ? null : seq((Set<Map<Integer, Object>>) x).map(fn::innerBuild).toSet(),
                        x -> x == null ? null : seq((Collection<Mimic<?>>) x).map(Mimic::underlyingMap).toSet()
                    };
                } else throw new IllegalStateException("unsupported container type: " + type);
            } else if (Map.class.isAssignableFrom(type) && anon.size() == 1) {
                val fn = supplier.apply(anon.get(0).value());
                assert fn != null;
                return new Factory.Processor[]{
                    x -> x == null ? null : seq((Map<Object, Map<Integer, Object>>) x)
                        .map(e -> e.map2(fn::innerBuild))
                        .toMap(Tuple2::v1, Tuple2::v2),
                    x -> x == null ? null : seq((Map<Object, Mimic<?>>) x)
                        .map(e -> e.map2(Mimic::underlyingMap))
                        .toMap(Tuple2::v1, Tuple2::v2)
                };
            } else if (Tuple.class.isAssignableFrom(type)) {
                val fns = seq(anon)
                    .map(c -> tuple(
                        Objects.requireNonNull(supplier.apply(c.value()), "mimic factory not generated for " + c),
                        c.inTuple()))
                    .toMap(Tuple2::v2, Tuple2::v1);
                final Function<Object[], Tuple> pop = x -> {
                    for (int i = 0; i < x.length; i++) {
                        val fn = fns.get(i);
                        if (fn != null) {
                            x[i] = fn.innerBuild((Map<Integer, Object>) x[i]);
                        }
                    }
                    return dynamicConstruct(x);
                };
                final Function<Tuple, Object> push = x -> {
                    val ar = x.toArray();
                    for (int i = 0; i < ar.length; i++) {
                        if (fns.containsKey(i)) {
                            ar[i] = ((Mimic<?>) ar[i]).underlyingMap();
                        }
                    }
                    return ar;
                };
                return new Factory.Processor[]{
                    x -> x == null ? null : pop.apply((Object[]) x),
                    x -> x == null ? null : push.apply((Tuple) x)
                };
            } else throw new IllegalStateException("unknown container with annotation:" + anon + " =>" + type);
        }
    }

    /**
     * element use to configure the behavior of Mimic
     */
    interface Configuring {
        @ToString
        final class ClassObj<T> {
            public boolean isAssignableFrom(Class<?> returnType) {
                return returnType == type || seq(interfaces)
                    // T return is as the interface it self in reflection.
                    .anyMatch(x -> x.type != Mimic.class && x.type.isAssignableFrom(returnType));
            }

            @ToString
            static final class ClassElement<T> {
                public boolean isInterface() {
                    return type.isInterface();
                }

                public String getName() {
                    return type.getName();
                }

                public int getModifiers() {
                    return type.getModifiers();
                }


                public String getSimpleName() {
                    return type.getSimpleName();
                }

                public String getTypeName() {
                    return type.getTypeName();
                }

                public String getCanonicalName() {
                    return type.getCanonicalName();
                }

                @CallerSensitive
                public Method[] getMethods() throws SecurityException {
                    return type.getMethods();
                }

                final Class<T> type;
                final int level;

                ClassElement(Class<T> type, int level) {
                    this.type = type;
                    this.level = level;
                }
            }

            public String getName() {
                return type.getName();
            }

            public String getSimpleName() {
                return type.getSimpleName();
            }

            public String getTypeName() {
                return type.getTypeName();
            }

            public String getCanonicalName() {
                return type.getCanonicalName();
            }

            public List<Method> getMethods() {
                return Seq.of(type.getMethods())
                    .concat(Seq
                        .seq(interfaces)
                        .flatMap(x -> Seq.of(x.getMethods()))
                    ).distinct()
                    .toList();
            }

            /**
             * all declared methods which is not default and must public
             *
             * @param filter filter
             */
            public List<Method> getDeclareMethods(Predicate<Method> filter) {
                return Seq.of(type.getMethods())
                    .concat(Seq
                        .seq(interfaces)
                        .flatMap(x -> Seq.of(x.getMethods()))
                    ).distinct()
                    .filter(x -> !x.isDefault() && Modifier.isPublic(x.getModifiers()))
                    .filter(filter)
                    .toList();
            }

            /**
             * direct annotations means annotation which are directly annotated on the type or on supper interfaces.
             */
            public <A extends Annotation> List<A> directAnnotatedOf(Class<A> type) {
                val lst = new ArrayList<>(Arrays.asList(this.type.getAnnotationsByType(type)));
                interfaces.forEach(i -> lst.addAll(Arrays.asList(i.type.getAnnotationsByType(type))));
                return lst;

            }

            /**
             * annotation includes those is annotated on annotation( only one level supported).
             *
             * @param type target annotation type
             * @param <A>  annotation type
             */
            public <A extends Annotation> List<A> annotatedOf(Class<A> type) {
                val lst = directAnnotatedOf(type);
                Seq.seq(interfaces)
                    .flatMap(x -> Seq.of(x.type.getAnnotations()))
                    .concat(Seq.of(this.type.getAnnotations()))
                    .flatMap(ann -> Seq.of(ann.annotationType().getAnnotationsByType(type)))
                    .forEach(lst::add);
                return lst;
            }

            final Class<T> type;
            final List<ClassElement<?>> interfaces;

            public ClassObj(Class<T> type) {
                this.type = type;
                this.interfaces = deflate(type);
            }

            static List<ClassElement<?>> deflate(Class<?> type) {
                val classes = new ArrayList<ClassElement<?>>();
                var level = 0;
                val targets = new ArrayList<>(Arrays.asList(type.getInterfaces()));
                while (!targets.isEmpty()) {
                    val next = new ArrayList<Class<?>>();
                    for (Class<?> target : targets) {
                        classes.add(new ClassElement<>(target, level));
                        next.addAll(Arrays.asList(target.getInterfaces()));
                    }
                    targets.clear();
                    targets.addAll(next);
                    level++;
                }
                return classes;
            }


        }


        /**
         * Mark the type use fluent getter|setter strategy.
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Documented
        @Target(ElementType.TYPE)
        @Inherited
        @interface Mimicked {
            boolean fluent() default false;

            boolean concurrent() default false;

            /**
             * optional self defined map factory
             */
            Class<?> mapFactory() default Void.class;

            /**
             * the static factory field<br/>
             * <pre>
             *     must a  {@link java.util.function.Supplier}&lt;Map&lt;String,Object&gt;&gt;or  {@link java.util.function.IntFunction}&lt;Map&lt;String,Object&gt;&gt;
             * </pre>
             */
            String field() default "";
        }
    }

    /**
     * a factory to build new instance.
     *
     * @param <T> type
     */
    interface Factory<T> {
        /**
         * @param map a FieldNumber => Value Map as Underlying storage.
         * @return a Instance
         */
        T innerBuild(@NotNull Map<Integer, Object> map);

        /**
         * build a New Instance ,with Data (FieldName=>Value), or just empty|null
         *
         * @param map initial data map (this map must store the underlying data type exactly)
         * @return instance
         */
        T build(@Nullable Map<String, Object> map);

        /**
         * Factory building strategy
         */
        interface Strategy {
         /*   final class IntMap implements Map<Integer, Object> {
                final int size;
                final Object[] container;

                IntMap(int size) {
                    this.size = size;
                    this.container = new Object[size];
                }

                @Override
                public int size() {
                    return size;
                }

                @Override
                public boolean isEmpty() {
                    return false;
                }

                @Override
                public boolean containsKey(Object key) {
                    if (key instanceof Integer) {
                        return (Integer) key <= size - 1;
                    }
                    return false;
                }

                @Override
                public boolean containsValue(Object value) {
                    return false;
                }

                @Override
                public Object get(Object key) {
                    if (containsKey(key)) {
                        return container[(Integer) key];
                    }
                    return null;
                }

                @Nullable
                @Override
                public Object put(Integer key, Object value) {
                    if (containsKey(key)) {
                        val old = container[key];
                        container[key] = value;
                        return old;
                    }
                    return null;
                }

                @Override
                public Object remove(Object key) {
                    if (containsKey(key)) {
                        val old = container[(Integer) key];
                        container[(Integer) key] = null;
                        return old;
                    }
                    return null;
                }

                @Override
                public void putAll(@NotNull Map<? extends Integer, ?> m) {
                    m.forEach(this::put);
                }

                @Override
                public void clear() {
                    Arrays.fill(container, null);
                }

                @NotNull
                @Override
                public Set<Integer> keySet() {
                    val set = new HashSet<Integer>();
                    for (int i = 0; i < container.length; i++) {
                        set.add(i);
                    }
                    return set;
                }

                @NotNull
                @Override
                public Collection<Object> values() {
                    return Arrays.asList(container);
                }

                @NotNull
                @Override
                public Set<Entry<Integer, Object>> entrySet() {
                    val set = new HashSet<Entry<Integer, Object>>();
                    for (int i = 0; i < container.length; i++) {
                        set.add(new AbstractMap.SimpleEntry<>(i, container[i]));
                    }
                    return set;
                }
            }*/

            final class Fields {
                final String names;
                final int[] index;
                private transient List<String> list;

                Fields(String names, int[] index) {
                    this.names = names;
                    Arrays.sort(index);
                    this.index = index;
                }

                Fields(Set<String> names) {
                    val b = new StringBuilder();
                    val max = names.size() - 1;
                    val i = new int[max];
                    var p = -1;
                    for (String name : names) {
                        b.append(name);
                        p++;
                        if (p == max) {
                            break;
                        }
                        i[p] = b.length();
                    }
                    this.names = b.toString();
                    this.index = i;
                    //save memory
                    //this.list = Collections.unmodifiableList(new ArrayList<>(names));
                }

                boolean isEmpty() {
                    return names.isEmpty();
                }

                int max() {
                    return index.length;
                }

                List<String> toList() {
                    if (list == null) {
                        synchronized (names) {
                            list = new ArrayList<>();
                            var last = 0;
                            for (int i : index) {
                                list.add(names.substring(last, i));
                                last = i;
                            }
                            list.add(names.substring(last));
                            list = Collections.unmodifiableList(list);
                        }
                    }
                    return list;
                }

                public int indexOf(String n) {
                    return names.indexOf(n);
                }

                public String of(int n) {
                    val x = index.length;
                    if (n < 0 || n > x) return null;
                    if (n == x) return names.substring(index[x - 1]);
                    val idx0 = n == 0 ? 0 : index[n - 1];
                    val idx1 = index[n];
                    return names.substring(idx0, idx1);
                }

                public static void main(String[] args) {
//                    val f = new Fields(new HashSet<>(Arrays.asList("a", "ab", "abc", "abcd", "abcde", "abcdef", "abcdefg", "abcdefgh")));
                    val f = new Fields("aababcdefghabcabcdeabcdefabcdefgabcd", new int[]{1, 3, 11, 14, 19, 25, 32});
                    System.out.println(f.names);
                    System.out.println(Arrays.toString(f.index));
                    System.out.println(f.toList());
                    for (int i = 0; i <= f.max(); i++) {
                        System.out.println(i + ":" + f.of(i));
                    }
                }
            }

            interface FactorySupplier extends Function<Class<?>, Factory<?>> {
            }

            interface ConverterSupplier extends Function<Class<?>, Tuple2<Converting.Deserialize<Object>, Converting.Serialize<Object>>> {
            }

            static <T extends AccessibleObject> T accessible(T accessible) {
                if (accessible == null) {
                    return null;
                }
                if (accessible instanceof Member) {
                    val member = (Member) accessible;
                    if (Modifier.isPublic(member.getModifiers()) &&
                        Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                        return accessible;
                    }
                }
                if (!accessible.isAccessible()) {
                    accessible.setAccessible(true);
                }
                return accessible;
            }

            static Map<Class<?>, Tuple2<Converting.Deserialize<Object>, Converting.Serialize<Object>>>
            converters(Configuring.ClassObj<?> type) {
                return Seq
                    .seq(type.directAnnotatedOf(Converting.Converter.class))//direct annotation
                    .map(Converting::extract)
                    .toMap(Tuple2::v1, Tuple2::v2);
            }


            //(field =>(getter,setter) (type converting)
            @SuppressWarnings("unchecked")
            static Map<String, Processor[]> processors(
                List<Method> methods,
                Function<Method, String> fieldName,
                ConverterSupplier converterSupplier,
                FactorySupplier factorySupplier
            ) {
                val mx = seq(methods)
                    .map(m -> {
                        final String f = fieldName.apply(m);
                        final boolean isGetter = m.getParameterCount() == 0;
                        final Class<?> typing = isGetter ? m.getReturnType() : m.getParameterTypes()[0];
                        //simple mimic type
                        if (Mimic.class.isAssignableFrom(typing)) {
                            val fn = factorySupplier.apply(typing);
                            return Tuple.tuple(f, isGetter,
                                new Processor[]{
                                    //for getter
                                    x -> x == null ? null : fn.innerBuild((Map<Integer, Object>) x),
                                    x -> x == null ? null : ((Mimic<?>) x).underlyingMap()
                                    //for setter
                                }
                            );
                        }
                        //is a Converter type
                        val cc = converterSupplier.apply(typing);
                        if (cc != null) {
                            return Tuple.tuple(f, isGetter,
                                new Processor[]{
                                    //getter
                                    x -> cc.v1.proc(x == null ? Converting.NULL : (String) x),
                                    cc.v2::proc
                                    //for setter

                                });
                        }
                        //is a Container
                        final Processor[] p = Containing.extract(m, factorySupplier);
                        if (p != null) return Tuple.tuple(f, isGetter, p);
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .groupBy(t -> t.v1);
                return seq(mx)
                    .map(t -> {
                        Tuple3<String, Boolean, Processor[]> tx = null;
                        for (Tuple3<String, Boolean, Processor[]> tu : t.v2) {
                            if (!tu.v2 && tu.v3 != null && tu.v3.length == 2) return tuple(t.v1, tu.v3);
                            tx = tu;
                        }
                        return tx != null ? tuple(t.v1, tx.v3) : null;
                    })
                    .filter(Objects::nonNull)
                    .toMap(Tuple2::v1, Tuple2::v2);
            }

            boolean isGetter(Method method, Configuring.ClassObj<?> type);

            boolean isSetter(Method method, Configuring.ClassObj<?> type);

            default String fieldName(Method method) {
                val n = method.getName();
                val f = n.startsWith("get") || n.startsWith("set") ? n.substring(3)
                    : n.startsWith("is") ? n.substring(2) : n;
                val chars = f.toCharArray();
                chars[0] = Character.toLowerCase(chars[0]);//may not just ASCII
                return new String(chars);
            }

            //(fields(index as number),Validation(may null),Map of getter and setters)
            default Tuple3<Set<String>, Validation, Map<String, Tuple2<Getter, Setter>>> build(Configuring.ClassObj<?> type, FactorySupplier factorySupplier) {
                val entity = type.getName();
                val converter = converters(type);
                val gs = type.getDeclareMethods(m -> isSetter(m, type) || isGetter(m, type));
                val chained = seq(gs).filter(m -> isSetter(m, type))
                    .map(m -> {
                        final String f = fieldName(m);
                        if (type.isAssignableFrom(m.getReturnType())) {
                            return f;
                        }
                        return null;
                    }).filter(Objects::nonNull).toList();
                val conv = processors(gs, this::fieldName, converter::get, factorySupplier);
                val validators = seq(gs)
                    .map(m -> {
                        final String f = fieldName(m);
                        final boolean getter = m.getParameterCount() == 0;
                        Validator v = null;
                        {//directly validator
                            val anon = m.getAnnotationsByType(Validating.Validate.class);
                            for (Validating.Validate validate : anon) {
                                val x = Validating.extract(validate);
                                if (x != null) {
                                    val vx = x.closure(entity, f);
                                    v = v == null ? vx : v.then(vx);
                                }
                            }
                        }
                        {//indirectly validator
                            val anon = m.getAnnotations();
                            for (Annotation an : anon) {
                                for (Validating.Validate validate : an.annotationType().getAnnotationsByType(Validating.Validate.class)) {
                                    val x = Validating.extract(validate);
                                    if (x != null) {
                                        val vx = x.closure(entity, f);
                                        v = v == null ? vx : v.then(vx);
                                    }
                                }
                            }
                        }
                        return tuple(f, getter, v);
                    })
                    .groupBy(Tuple3::v1);
                val validator = seq(validators)
                    .map(t -> {
                        Tuple3<String, Boolean, Validator> tx = null;
                        for (Tuple3<String, Boolean, Validator> tu : t.v2) {
                            if (!tu.v2 && tu.v3 != null) return tuple(t.v1, tu.v3);
                            tx = tu;
                        }
                        return tx == null ? null : tx.v3 == null ? null : tuple(t.v1, tx.v3);
                    })
                    .filter(Objects::nonNull)
                    .toMap(Tuple2::v1, Tuple2::v2);
                val fields = seq(gs).filter(m -> isGetter(m, type)).map(this::fieldName)
                    .sorted()
                    .toSet();//TODO: none checked set
                final Validation[] vali = new Validation[]{null};
                val getset = seq(fields)
                    .zipWithIndex()
                    .map(t -> {
                        val f = t.v1;
                        val con = conv.get(f);
                        val val = validator.get(f);
                        val chain = chained.contains(f);
                        val n = (int) (long) t.v2;
                        Setter set;
                        Getter get;
                        if (con != null && val != null) {
                            val setp = con[1];
                            val getp = con[0];
                            set = chain ?
                                (proxy, map, value) -> {
                                    val.valid(value);
                                    map.put(n, setp.process(value));
                                    return proxy;
                                }
                                :
                                (proxy, map, value) -> {
                                    val.valid(value);
                                    map.put(n, setp.process(value));
                                    return null;
                                };
                            get = (map) -> getp.process(map.get(n));
                            vali[0] = vali[0] == null ? m -> val.valid(getp.process(m.get(n))) : vali[0].then(m -> val.valid(getp.process(m.get(n))));
                        } else if (con != null) { //convert only
                            val setp = con[1];
                            val getp = con[0];
                            set = chain ?
                                (proxy, map, value) -> {
                                    map.put(n, setp.process(value));
                                    return proxy;
                                }
                                :
                                (proxy, map, value) -> {
                                    map.put(n, setp.process(value));
                                    return null;
                                };
                            get = (map) -> getp.process(map.get(n));
                        } else if (val != null) {//validator only
                            set = chain ?
                                (proxy, map, value) -> {
                                    val.valid(value);
                                    map.put(n, value);
                                    return proxy;
                                }
                                :
                                (proxy, map, value) -> {
                                    val.valid(value);
                                    map.put(n, value);
                                    return null;
                                };
                            get = (map) -> map.get(n);
                            vali[0] = vali[0] == null ? m -> val.valid(m.get(n)) : vali[0].then(m -> val.valid(m.get(n)));
                        } else {
                            set = chain ?
                                (proxy, map, value) -> {

                                    map.put(n, value);
                                    return proxy;
                                }
                                :
                                (proxy, map, value) -> {
                                    map.put(n, value);
                                    return null;
                                };
                            get = (map) -> map.get(n);
                        }
                        return tuple(t.v1, tuple(get, set));
                    }).toMap(Tuple2::v1, Tuple2::v2);
                return tuple(fields, vali[0], getset);
            }

        }

        interface JavaBeanStrategy extends Strategy {
            Strategy INSTANCE = new JavaBeanStrategy() {
            };

            @Override
            default boolean isGetter(Method method, Configuring.ClassObj<?> type) {
                val n = method.getName();
                if (underlyingNames.contains(n)) return false;
                return
                    method.getParameterCount() == 0
                        &&
                        ((n.startsWith("get")
                            && method.getReturnType() != void.class)
                            || (n.startsWith("is")
                            && method.getReturnType() == boolean.class));
            }

            @Override
            default boolean isSetter(Method method, Configuring.ClassObj<?> type) {
                val n = method.getName();
                if (underlyingNames.contains(n)) return false;
                return method.getParameterCount() == 1
                    && n.startsWith("set")
                    && (method.getReturnType() == void.class || type.isAssignableFrom(method.getReturnType()))
                    ;
            }
        }

        interface FluentStrategy extends Strategy {
            Strategy INSTANCE = new FluentStrategy() {
            };

            @Override
            default boolean isGetter(Method method, Configuring.ClassObj<?> type) {
                if (underlyingNames.contains(method.getName())) return false;
                return method.getParameterCount() == 0
                    && method.getReturnType() != void.class
                    && !type.isAssignableFrom(method.getReturnType())
                    ;
            }

            @Override
            default boolean isSetter(Method method, Configuring.ClassObj<?> type) {
                if (underlyingNames.contains(method.getName())) return false;
                return method.getParameterCount() == 1
                    && (method.getReturnType() == void.class || method.getReturnType() == type.type || type.isAssignableFrom(method.getReturnType()))
                    ;
            }
        }

        interface Getter {
            Object get(Map<Integer, Object> data);
        }

        interface Setter {
            Object set(Object proxy, Map<Integer, Object> data, Object value);
        }

        interface Validator {
            void valid(Object data);

            default Validator then(Validator v) {
                return o -> {
                    valid(o);
                    v.valid(o);
                };
            }
        }

        interface Validation {
            void valid(Map<Integer, Object> data) throws IllegalArgumentException;

            default Validation then(Validation v) {
                return o -> {
                    valid(o);
                    v.valid(o);
                };
            }
        }

        interface Processor {
            Object process(Object data);
        }

        @ApiStatus.Internal
        final class FactoryImpl<T> implements Factory<T> {
            static final Map<String, Function4<Class<?>, Map<Integer, Object>, Object, Object[], Object>> common = new HashMap<>();

            static {
                //@formatter:off
                common.put("hashCode",(type,map,proxy,args)->map.hashCode()+type.hashCode());
                common.put("toString",(type,map,proxy,args)->type.getSimpleName()+"@"+Objects.hashCode(proxy)+"@"+map);
                common.put("equals",(type,map,proxy,args)->type.isAssignableFrom(args[0].getClass()) && ((Mimic<?>)args[0]).underlyingMap().equals(map) );
                //@formatter:on
            }

            final Strategy strategy;
            final Supplier<Map<Integer, Object>> mapSupplier;
            final Strategy.Fields fields;
            final Validation validation;
            final Map<String, Tuple2<Getter, Setter>> methods;
            final HashMap<String, String> names;
            final Class<T> type;

            @SneakyThrows
            FactoryImpl(Class<T> type, Function<Class<?>, Factory<?>> cache) {
                this.type = type;
                val classes = new Configuring.ClassObj<>(type);
                val mimics = classes.annotatedOf(Configuring.Mimicked.class);
                if (mimics.isEmpty())
                    throw new IllegalArgumentException("the type [" + type + "] not annotated with Mimicked");
                val mimicked = mimics.get(0);//first appearance
                this.strategy = mimicked.fluent() ? FluentStrategy.INSTANCE : JavaBeanStrategy.INSTANCE;

                val v =
                    strategy.build(classes, cache::apply);
                fields = new Strategy.Fields(v.v1);
                if (fields.isEmpty())
                    throw new IllegalStateException("the type [" + type + "] found no fields exists!");
                validation = v.v2;
                methods = v.v3;
                if (mimicked.mapFactory() != Void.class) {
                    val f = mimicked.mapFactory().getField(mimicked.field());
                    if (Supplier.class.isAssignableFrom(f.getType())) {
                        @SuppressWarnings("unchecked") val x = (Supplier<Map<Integer, Object>>) f.get(mimicked.mapFactory());
                        this.mapSupplier = x;
                    } else if (IntFunction.class.isAssignableFrom(f.getType())) {
                        @SuppressWarnings("unchecked") val x = (IntFunction<Map<Integer, Object>>) f.get(mimicked.mapFactory());
                        this.mapSupplier = () -> x.apply(fields.max());
                    } else throw new IllegalStateException("Mimicked defined invalid map factory!");
                } else
                    this.mapSupplier = mimicked.concurrent() ? () -> new ConcurrentHashMap<>(fields.max() + 1) : () -> new HashMap<>(fields.max() + 1);
                this.names = new HashMap<String, String>((fields.max() + 1) * 2);
            }

            private Constructor<MethodHandles.Lookup> constructor;

            private Object invokeDefault(Method method, Object[] obj, Object[] args) {
                try {
                    if (constructor == null)
                        constructor = Strategy.accessible(MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class));
                    Class<?> declaringClass = method.getDeclaringClass();
                    return constructor
                        .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                        .unreflectSpecial(method, declaringClass)
                        .bindTo(obj[0])
                        .invokeWithArguments(args);
                } catch (Throwable e) {
                    throw new IllegalStateException("Cannot invoke default method", e);
                }
            }


            @SuppressWarnings("unchecked")
            public T innerBuild(@NotNull Map<Integer, Object> map) {
                val underlying = map;
                val obj = new Object[1];

                obj[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, ((proxy, method, args) -> {
                    val name = names.computeIfAbsent(method.getName(), (k) -> strategy.fieldName(method));
                    val length = (args == null ? 0 : args.length);
                    if (common.containsKey(name) && length == 0) {
                        return common.get(name).apply(type, underlying, proxy, args);
                    } else if (name.equals("equals") && length == 1) {
                        return common.get("equals").apply(type, underlying, proxy, args);
                    } else if (method.isDefault() && length == 0 && name.equals("validate")) {
                        if (validation != null) validation.valid(underlying);
                        invokeDefault(method, obj, args);
                        return null;
                    } else if (length == 0 && name.equals("underlyingMap")) {
                        return underlying;
                    } else if (length == 0 && name.equals("underlyingNaming")) {
                        return Collections.unmodifiableList(fields.toList());
                    } else if (length == 0 && name.equals("underlyingType")) {
                        return type;
                    } else if (length == 0 && name.equals("underlyingInstance")) {
                        return proxy;
                    } else if (length == 0 && name.equals("signature")) {
                        return type.getName();
                    } else if (method.isDefault()) {
                        return invokeDefault(method, obj, args);
                    } else if (length <= 1) {
                        val f = strategy.fieldName(method);
                        val getter = length == 0;
                        val t = methods.get(f);
                        if (t != null && getter && t.v1 != null) {
                            return t.v1.get(underlying);
                        } else if (t != null && !getter && t.v2 != null) {
                            return t.v2.set(proxy, underlying, args[0]);
                        } else {
                            throw new IllegalStateException("Cannot invoke unknown method of " + method);
                        }
                    } else {
                        throw new IllegalStateException("Cannot invoke unknown method of " + method);
                    }
                }));
                return (T) obj[0];
            }

            public T build(@Nullable Map<String, Object> map) {
                if (map == null) return innerBuild(mapSupplier.get());
                val m = mapSupplier.get();
                map.forEach((k, v) -> {
                    val i = fields.indexOf(k);
                    if (i < 0) {
                        throw new IllegalStateException("field" + k + " not exists ");
                    }
                    m.put(i, v);
                });
                return innerBuild(m);
            }
        }

        /**
         * @param type        The mimic
         * @param cacheGetter the cache which should support as a LoadingCache: if some value not exists, then generate it.
         * @param <T>         type
         * @return a Factory, should keep it in a cache.
         */
        static <T> Factory<T> factory(Class<T> type, Function<Class<?>, Factory<?>> cacheGetter) {
            return new FactoryImpl<>(type, cacheGetter);
        }

    }

}
