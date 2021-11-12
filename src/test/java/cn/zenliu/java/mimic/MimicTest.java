package cn.zenliu.java.mimic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.val;
import lombok.var;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jooq.lambda.Seq.seq;
import static org.junit.jupiter.api.Assertions.*;

class MimicTest {
    @Mimic.Dao.Entity
    public interface Fluent extends Mimic {
        long id();

        void id(long val);

        @Validation(property = "notNull")
        Long identity();

        Fluent identity(Long val);

        @AsString
        Long idOfUser();

        Fluent idOfUser(Long val);

        @Override
        default void validate() throws IllegalStateException {
            if (identity() > 10) {
                throw new IllegalStateException("identity must less than 10");
            }
        }
    }

    @Mimic.Dao.Entity
    public interface Flue extends Fluent {

        @AsString
        @Override
        Long identity();

        BigDecimal user();

        Flue user(BigDecimal val);

        @Override
        default void validate() throws IllegalStateException {
            if (user().compareTo(BigDecimal.TEN) < 0) {
                throw new IllegalStateException("user must bigger than 10");
            }
            Fluent.super.validate();
        }

        default BigDecimal halfOrDefault() {
            if (user() == null) return BigDecimal.ZERO;
            return user().divide(BigDecimal.valueOf(2), RoundingMode.HALF_DOWN);
        }
    }

    public interface FluentDao extends Mimic.Dao<Fluent> {
        static DataType<Long> identity = SQLDataType.BIGINT.identity(true);

        @As(typeHolder = FluentDao.class, typeProperty = "identity")
        Field<Long> id();

        @As(typeHolder = SQLDataType.class, typeProperty = "BIGINT")
        Field<Long> identity();

        @As(typeHolder = SQLDataType.class, typeProperty = "VARCHAR")
        Field<String> idOfUser();


        default Fluent fetchById(long id) {
            return instance(ctx().selectFrom(table()).where(id().eq(id)).fetchOne().intoMap());
        }


        default void deleteAll() {
            ctx().delete(table()).execute();
        }
    }

    public interface FlueDao extends Mimic.Dao<Flue> {
        Field<BigDecimal> user();

        @As(typeHolder = FluentDao.class, typeProperty = "identity")
        Field<Long> id();

        @As(typeHolder = SQLDataType.class, typeProperty = "VARCHAR")
        Field<String> identity();

        @As(typeHolder = SQLDataType.class, typeProperty = "VARCHAR")
        Field<String> idOfUser();

        @Override
        default List<Field<?>> allFields() {
            return Arrays.asList(id(), identity(), idOfUser(), user());
        }

        default Flue fetchById(long id) {
            return instance(ctx().selectFrom(table()).where(id().eq(id)).fetchOne().intoMap());
        }

        default void deleteAll() {
            ctx().delete(table()).execute();
        }
    }

    static final DefaultConfiguration cfg;

    static {
        cfg = new DefaultConfiguration();
        cfg.setSQLDialect(SQLDialect.H2);
        val hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:test");
        cfg.setDataSource(new HikariDataSource(hc));
    }

    static final Supplier<FluentDao> fluentDao = () -> Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
    static final Supplier<FlueDao> flueDao = () -> Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
    static final Supplier<Fluent> fluent = () -> Mimic.newInstance(Fluent.class, null);
    static final Supplier<Flue> flue = () -> Mimic.newInstance(Flue.class, null);

    @Test
    void mimic() {
        final Consumer<Fluent> fluentValidate = i -> {
            i.id(12L);
            System.out.println(i.underlyingMap());
            i.underlyingMap().put("id", 12);
            assertEquals(12L, i.id());
            i.identity(11L);
            System.out.println(i.underlyingMap());
            assertEquals(12L, i.id());
            assertThrows(IllegalStateException.class, i::validate);
            assertNotNull(i.underlyingChangedProperties());
        };
        final Consumer<Flue> flueValidate = i -> {
            i.id(12L);
            System.out.println(i.underlyingMap());
            assertEquals(12L, i.id());
            assertThrows(IllegalStateException.class, () -> i.identity(null));
            i.identity(11L);
            assertEquals(BigDecimal.ZERO, i.halfOrDefault());
            i.user(BigDecimal.TEN);
            assertThrows(IllegalStateException.class, i::validate);
            i.identity(10L);
            i.user(BigDecimal.valueOf(8));
            System.out.println(i.underlyingMap());
            assertEquals(12L, i.id());
            assertEquals(10L, i.identity());
            assertEquals(BigDecimal.valueOf(8), i.user());
            assertThrows(IllegalStateException.class, i::validate);
            assertNotNull(i.underlyingChangedProperties());
            assertEquals(BigDecimal.valueOf(4), i.halfOrDefault());
            System.out.println(i);
        };
        var F = fluent.get();
        var f = flue.get();
        fluentValidate.accept(F);
        flueValidate.accept(f);
        Mimic.ByteASM.enable();
        F = fluent.get();
        f = flue.get();
        fluentValidate.accept(F);
        flueValidate.accept(f);
    }

    static final List<String> name1 = Stream.of("identity", "id", "id_of_user").sorted().collect(Collectors.toList());
    static final List<String> name2 = Stream.of("identity", "id", "id_of_user", "user").sorted().collect(Collectors.toList());
    static final List<String> property1 = Stream.of("identity", "id", "idOfUser").sorted().collect(Collectors.toList());
    static final List<String> property2 = Stream.of("identity", "id", "idOfUser", "user").sorted().collect(Collectors.toList());

    @Test
    void testDao() {
        final Consumer<FluentDao> fluentValidate = dao -> {
            System.out.println(dao);
            System.out.println(dao.DDL());
            val id = System.currentTimeMillis();
            val i = dao.instance(null);
            i.id(id);
            i.identity(12L);
            i.idOfUser(24L);
            assertEquals(property1, seq(i.underlyingChangedProperties()).sorted().toList());
            dao.inertInto(i);
            val i2 = dao.fetchById(id);
            System.out.println(i2);
            i2.identity(8L);
            assertEquals("identity", seq(i2.underlyingChangedProperties()).sorted().toString(""));
            System.out.println(dao.updateWith(i2, dao.id().eq(id)));
            var r = dao.fetchById(id);
            assertEquals(id, r.id());
            assertEquals(8L, r.identity());
            assertEquals(24L, r.idOfUser());
            r = dao.queryConditional(s -> s.where(dao.id().eq(id)).orderBy(dao.identity()).limit(1))
                .findFirst().orElseThrow(IllegalStateException::new);
            assertEquals(id, r.id());
            assertEquals(8L, r.identity());
            assertEquals(24L, r.idOfUser());
        };
        final Consumer<FlueDao> flueValidate = dao -> {
            System.out.println(dao);
            System.out.println(dao.DDL());
            val id = System.currentTimeMillis();
            val i = dao.instance(null);
            i.id(id);
            i.identity(12L);
            i.idOfUser(24L);
            i.user(BigDecimal.TEN);
            assertEquals(property2, seq(i.underlyingChangedProperties()).sorted().toList());
            dao.inertInto(i);
            val i2 = dao.fetchById(id);
            System.out.println(i2);
            i2.identity(8L);
            assertEquals("identity", seq(i2.underlyingChangedProperties()).sorted().toString(""));
            System.out.println(dao.updateWith(i2, dao.id().eq(id)));
            var r = dao.fetchById(id);
            assertEquals(id, r.id());
            assertEquals(8L, r.identity());
            assertEquals(24L, r.idOfUser());
            assertEquals(BigDecimal.TEN, r.user());
            r = dao.queryConditional(s -> s.where(dao.id().eq(id)).orderBy(dao.identity()).limit(1))
                .findFirst().orElseThrow(IllegalStateException::new);
            assertEquals(id, r.id());
            assertEquals(8L, r.identity());
            assertEquals(24L, r.idOfUser());
            assertEquals(BigDecimal.TEN, r.user());
        };
        var fluent = fluentDao.get();
        var flue = flueDao.get();
        assertEquals(name1, seq(fluent.allFields()).map(Field::getName).sorted().toList());
        assertEquals(name2, seq(flue.allFields()).map(Field::getName).sorted().toList());
        assertEquals(cfg, fluent.configuration());
        assertEquals(cfg, flue.configuration());
        fluentValidate.accept(fluent);
        flueValidate.accept(flue);
        Mimic.Dao.ByteASM.enable();
        fluent = fluentDao.get();
        flue = flueDao.get();
        assertEquals(name1, seq(fluent.allFields()).map(Field::getName).sorted().toList());
        assertEquals(name2, seq(flue.allFields()).map(Field::getName).sorted().toList());
        assertEquals(cfg, fluent.configuration());
        assertEquals(cfg, flue.configuration());
        fluentValidate.accept(fluent);
        flueValidate.accept(flue);


    }

}