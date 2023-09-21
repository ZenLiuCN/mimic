package cn.zenliu.java.mimic;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.jooq.lambda.Seq.seq;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @since 2023-09-19
 */
@SuppressWarnings("rawtypes")
public final class daos {
    private daos() {
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
                var m = new HashMap<String, Object>();
                dm.forEach((k, v) -> m.put(fieldToProperty.get(k) != null ? fieldToProperty.get(k) : k, v));
                return m;
            }

            public Map<String, Object> toField(Map<String, Object> dm) {
                if (dm == null) return null;
                var m = new HashMap<String, Object>();
                dm.forEach((k, v) -> m.put(fields.get(k) != null ? fields.get(k).getName() : k, v));
                return m;
            }

            public Mimic.Dao build(Configuration config) {
                final Object[] result = new Object[1];
                result[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (p, m, args) -> {
                    var method = m.getName();
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
                return (Mimic.Dao) result[0];
            }
        }

        LoadingCache<Tuple2<Class, Class>, DaoFactory> cache = Caffeine.newBuilder()
            .softValues()
            .maximumSize(Optional.ofNullable(System.getProperty("mimic.cache")).map(Integer::parseInt).orElse(1024))
            .build(DynamicFactory::factory);

        static DaoFactory factory(Tuple2<Class, Class> type) {
            var info = DaoFactory.repositoryInfoCache.get(type);
            if (info == null || info.fields == null)
                throw new IllegalStateException("could not generate repository info for " + type);
            return new DynamicFactory.Factory(info.table, info.fields, info.all, info.dao, info.entity);
        }
    }

    interface AsmFactory {
        final class Factory implements DaoFactory {
            final Table<Record> table;
            final Map<String, Field> fields;
            final Class type;
            final Class entity;
            final Map<String, String> fieldToProperty;
            final Function<Configuration, Mimic.Dao> ctor;
            final static Method FIELD_METHOD;
            final List<Field<?>> all;

            static {
                try {
                    FIELD_METHOD = AsmFactory.Factory.Base.class.getDeclaredMethod("getField", String.class);
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

            public abstract class Base<T extends Mimic> implements Mimic.Dao<T> {
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

                @SuppressWarnings("unchecked")
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
                    if (obj instanceof AsmFactory.Factory.Base) {
                        return table.equals(((AsmFactory.Factory.Base<?>) obj).table()) && allFields().equals(((AsmFactory.Factory.Base<?>) obj).allFields());
                    }
                    return false;
                }
            }

            @SneakyThrows
            private Function<Configuration, Mimic.Dao> build() {
                try {
                    var typeName = type.getCanonicalName() + "$ASM";
                    final List<Class> faces = new ArrayList<>(Arrays.asList(type.getInterfaces()));
                    faces.add(0, type);
                    //var face = (List<Class<?>>) (List) faces;
                    DynamicType.Builder<?> builder = new ByteBuddy()
                        .subclass(AsmFactory.Factory.Base.class)
                        .implement(type)
                        .name(typeName);
                    for (var m : type.getMethods()) {
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
                    var ctor = builder.make().load(type.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                        .getLoaded()
                        .getConstructor(AsmFactory.Factory.class, Configuration.class);
                    return (c) -> {
                        try {
                            return (Mimic.Dao) ctor.newInstance(this, c);
                        } catch (InstantiationException | IllegalAccessException |
                                 InvocationTargetException e) {
                            Mimic.log.error("fail to create DAO instance {}", type, e);
                            throw new IllegalStateException(e);
                        }
                    };
                } catch (SecurityException | NoSuchMethodException e) {
                    Mimic.log.error("fail to create DAO '{}' factory", type, e);
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
                var m = new HashMap<String, Object>();
                dm.forEach((k, v) -> m.put(fieldToProperty.get(k) != null ? fieldToProperty.get(k) : k, v));
                return m;
            }

            public Map<String, Object> toField(Map<String, Object> dm) {
                if (dm == null) return null;
                var m = new HashMap<String, Object>();
                dm.forEach((k, v) -> m.put(fields.get(k) != null ? fields.get(k).getName() : k, v));
                return m;
            }

            @Override
            public Mimic.Dao build(Configuration config) {
                return ctor.apply(config);
            }
        }

        LoadingCache<Tuple2<Class, Class>, DaoFactory> cache = Caffeine.newBuilder()
            .softValues()
            .maximumSize(Optional.ofNullable(System.getProperty("mimic.cache")).map(Integer::parseInt).orElse(1024))
            .build(AsmFactory::factory);

        static DaoFactory factory(Tuple2<Class, Class> type) {
            var info = DaoFactory.repositoryInfoCache.get(type);
            if (info == null) throw new IllegalStateException("could not generate repository info for " + type);
            return new AsmFactory.Factory(info.table, info.fields, info.all, info.dao, info.entity);
        }
    }

    interface DaoFactory {
        Map<String, Object> toProperty(Map<String, Object> dm);

        Map<String, Object> toField(Map<String, Object> dm);

        Mimic.Dao build(Configuration config);

        LoadingCache<Tuple2<Class, Class>, DaoFactory.RepoInfo> repositoryInfoCache = Caffeine.newBuilder()
            .softValues()
            .maximumSize(Optional.ofNullable(System.getProperty("mimic.cache")).map(Integer::parseInt).orElse(1024))
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
        @SuppressWarnings("unchecked")
        static DaoFactory.RepoInfo buildInfo(Tuple2<Class, Class> type) {
            var info = mimics.Factory.infoCache.get(type.v1);
            if (info == null) {
                throw new IllegalStateException("not found Mimic Factory");
            }
            final Class<?> entity = type.v1;
            final Class<?> repo = type.v2;
            final List<Class<?>> faces = new ArrayList<>(Arrays.asList(repo.getInterfaces()));
            faces.add(0, repo);
            var ann = entity.getAnnotationsByType(Mimic.Dao.Entity.class);
            if (ann.length == 0) {
                throw new IllegalStateException("there have no @Entity on Mimic ");
            }
            var tableName = ann[0].value();
            if (tableName.isEmpty()) {
                tableName = Util.Case.classToTable.apply(entity.getSimpleName());
            }
            var table = DSL.table(DSL.name(tableName));
            var tuples = Seq.of(repo.getMethods())
                .filter(x ->
                    !Modifier.isStatic(x.getModifiers()) &&
                    !x.isDefault() &&
                    info.propertyInfo.containsKey(x.getName()))
                .map(f -> buildField(table, f, info.propertyInfo.get(f.getName()).v3.type, faces)).toList();
            var fields = seq(tuples).toMap(Tuple2::v1, Tuple2::v2);
            List<Field<?>> all = (List<Field<?>>) (List) seq(tuples).map(x -> x.v2).toList();
            return DaoFactory.RepoInfo.of(table, fields, all, repo, entity);
        }

        @SneakyThrows
        static Tuple2<String, Field> buildField(Table<Record> table, Method m, Class<?> type, List<Class<?>> faces) {
            try {
                var field = m.getName();
                var name = Util.Case.propertyToTable.apply(field);
                DataType<?> dataType;
                //guess
                try {
                    dataType = DefaultDataType.getDataType(lastConfig.get() == null ? SQLDialect.DEFAULT : lastConfig.get().dialect(), type, null);
                } catch (Exception e) {
                    dataType = null;
                }
                {
                    var ann = Util.collectAnnotations(m, Mimic.Dao.As.class, faces);
                    if (!ann.isEmpty()) {
                        var an = ann.get(0);
                        if (!an.value().isEmpty()) {
                            name = an.value();
                        }
                        if (an.typeHolder() != Void.TYPE && !an.typeProperty().isEmpty()) {
                            var dt = Util.fetchStaticFieldValue(an.typeHolder(), an.typeProperty());
                            if (!(dt instanceof DataType)) {
                                throw new IllegalStateException("defined DataType with @As invalid");
                            }
                            dataType = (DataType<?>) dt;
                        }
                        if (an.procHolder() != Void.TYPE && !an.procProperty().isEmpty()) {
                            var dt = Util.fetchStaticFieldValue(an.procHolder(), an.procProperty());
                            if (!(dt instanceof Function)) {
                                throw new IllegalStateException("defined DataType Proc with @As invalid");
                            }
                            dataType = ((Function<DataType, DataType>) dt).apply(dataType);
                        }
                    }
                }
                if (dataType == null)
                    throw new IllegalStateException("type " + type.getCanonicalName() + " can't convert to DataType!");
                var jf=DSL.field(DSL.name(table.getName(), name), dataType);
                return tuple(field, jf);
            } catch (Exception e) {
                Mimic.log.error("fail to generate DAO '{}' information", type, e);
                throw e;
            }
        }

    }

    final static AtomicReference<Function<Tuple2<Class, Class>, DaoFactory>> cache = new AtomicReference<>();

    static DaoFactory factory(Tuple2<Class<Mimic>, Class<Mimic.Dao>> type) {
        return Objects.requireNonNull(cache.get(), "not configurer DAO factory mode ").apply((Tuple2<Class, Class>) (Tuple2) type);
    }

    final static AtomicReference<Configuration> lastConfig = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    static <T extends Mimic, D extends Mimic.Dao<T>> D createRepo(Class<T> type, Class<D> repo, Configuration config) {
        setConfiguration(config);
        //noinspection RedundantCast
        return (D) factory(tuple((Class<Mimic>) type, (Class<Mimic.Dao>) (Class) repo))
            .build(config == null ? lastConfig.get() : config);
    }

    static void setConfiguration(Configuration config) {
        if (config != null && isNotInTransaction(config)) {
            if (Mimic.log.isTraceEnabled()) Mimic.log.trace("update global Configuration to {}", config);
            daos.lastConfig.set(config);
        }
    }

    static boolean isNotInTransaction(Configuration config) {
        try (var conn = config.dsl().parsingConnection()) {
            var status = conn.getAutoCommit();
            if (Mimic.log.isTraceEnabled()) Mimic.log.trace("current Configuration autocommit is {}", status);
            return status;
        } catch (SQLException e) {
            return false;
        }
    }

}
