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


import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jooq.Record;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.lambda.tuple.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.jooq.lambda.Seq.seq;

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


    /**
     * use Byte ASM Mode for Mimic
     */
    interface ByteASM {

        static void enable() {
            mimics.factory.set(mimics.AsmFactory.cache::get);
        }
    }

    /**
     * use JDK Dynamic Proxy Mode for Mimic
     */
    interface DynamicProxy {
        static void enable() {
            mimics.factory.set(mimics.ProxyFactory.cache::get);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Mimic> T newInstance(Class<T> type, Map<String, Object> data) {
        return (T) mimics.instance(type, data);
    }

    /**
     * <p> Dao is a Jooq repository interface for {@link Mimic}.
     * <p> <h3>Introduce</h3>
     * <p> Dao extends with Jooq Dynamic api to create Repository interface for a {@link Mimic}.
     * <p> <b>Note:</b>
     * <p>  Dao can not be inherited. that means a Dao must directly extended {@link Dao}.
     * <p>  {@link Mimic} used by Dao must directly annotate with {@link Entity}.
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
         *
         * @deprecated use {@link #stream(Function)} instead
         */
        @ApiStatus.AvailableSince("1.0.7")
        @Deprecated
        default Stream<T> queryConditional(@NotNull Function<SelectJoinStep<?>, ResultQuery<?>> conditionOperator) {
            return stream(conditionOperator);
        }

        /**
         * Query many by condition
         *
         * @param conditionOperator condition
         * @return stream
         */
        @ApiStatus.AvailableSince("1.2.0")
        default Stream<T> stream(@NotNull Function<SelectJoinStep<?>, ResultQuery<?>> conditionOperator) {
            return conditionOperator.apply(
                    ctx()
                        .select(allFields())
                        .from(table())
                )
                .fetchStream()
                .map(x -> instance(x.intoMap()));
        }

        /**
         * Query many by condition, with select of fields
         *
         * @param fields            to query
         * @param conditionOperator condition
         * @param dto               output Data Transport Object (which must a Mimic interface)
         * @return stream
         */
        @ApiStatus.AvailableSince("1.2.0")
        default <O extends Mimic> Stream<O> stream(@NotNull List<Field<?>> fields, @NotNull Function<SelectJoinStep<?>, ResultQuery<?>> conditionOperator, @NotNull Class<O> dto) {
            return conditionOperator.apply(
                    ctx()
                        .select(fields)
                        .from(table())
                )
                .fetchStream()
                .map(x -> Mimic.newInstance(dto, x.intoMap()));
        }

        /**
         * Query many by condition, with select of fields
         *
         * @param fields            to query
         * @param conditionOperator condition
         * @param dto               output Data Transport Object (which must a Pojo class or interface)
         * @return stream
         */
        @ApiStatus.AvailableSince("1.2.0")
        default <O> Stream<O> streamPojo(@NotNull List<Field<?>> fields, @NotNull Function<SelectJoinStep<?>, ResultQuery<?>> conditionOperator, @NotNull Class<O> dto) {
            if (Mimic.class.isAssignableFrom(dto))
                return (Stream<O>) stream(fields, conditionOperator, (Class<Mimic>) dto);
            return conditionOperator.apply(
                    ctx()
                        .select(fields)
                        .from(table())
                )
                .fetchStream()
                .map(x -> x.into(dto));
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
            var changes = value.underlyingChangedProperties();
            var map = seq(value.underlyingMap())
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
                        .columns(allFields().toArray(Field[]::new))
                )
                    .execute();
        }

        /**
         * this method used to add constants for {@link Dao#DDL}
         */
        default CreateTableElementListStep setConstants(CreateTableElementListStep columns) {
            return columns;
        }
        //endregion


        /**
         * set global JOOQ configuration to avoid pass configuration to {@link Dao#newInstance} every time.
         *
         * @param config the jooq configuration
         */
        static void setConfiguration(Configuration config) {
            daos.setConfiguration(config);
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
            return daos.createRepo(entity, dao, config);
        }


        /**
         * use Byte ASM Mode for DAO
         */
        interface ByteASM {
            static void enable() {
                daos.cache.set(daos.AsmFactory.cache::get);
            }
        }

        /**
         * use JDK Dynamic Proxy Mode for DAO
         */
        interface DynamicProxy {
            static void enable() {
                daos.cache.set(daos.DynamicFactory.cache::get);
            }
        }
    }

}