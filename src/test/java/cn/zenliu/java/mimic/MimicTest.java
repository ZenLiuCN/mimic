package cn.zenliu.java.mimic;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.val;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
    }

    public interface FluentDao extends Mimic.Dao<Fluent> {
        static DataType<Long> identity = SQLDataType.BIGINT.identity(true);

        @As(typeHolder = FluentDao.class, typeProperty = "identity")
        Field<Long> id();

        @As(typeHolder = SQLDataType.class, typeProperty = "BIGINT")
        Field<Long> identity();

        @As(typeHolder = SQLDataType.class, typeProperty = "VARCHAR")
        Field<String> idOfUser();


        default int insert(Fluent i) {
            return ctx().insertInto(table()).set(toDatabase(i.underlyingMap())).execute();
        }

        default Fluent fetchById(long id) {
            return instance(ctx().selectFrom(table()).where(id().eq(id)).fetchOne().intoMap());
        }

        default Fluent update(Fluent i) {
            if (i.id() == 0) {
                throw new IllegalStateException("can't update entity have no id");
            }
            val und = i.underlyingMap();
            val changes = i.underlyingChangedProperties();
            if (changes == null || changes.isEmpty()) throw new IllegalStateException("nothing to update entity");
            val m = new HashMap<String, Object>();
            for (String property : changes) {
                m.put(property, und.get(property));
            }
            if (ctx().update(table()).set(toDatabase(m)).where(id().eq(i.id())).execute() < 1) {
                throw new IllegalStateException("update failure");
            }
            return i;
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

        default int insert(Flue i) {
            return ctx().insertInto(table()).set(toDatabase(i.underlyingMap())).execute();
        }

        default Flue fetchById(long id) {
            return instance(ctx().selectFrom(table()).where(id().eq(id)).fetchOne().intoMap());
        }

        default void deleteAll() {
            ctx().delete(table()).execute();
        }
    }

    static class MimicTestWithOutDao {
        @Test
        void proxyMimic() {
            {
                val i = Mimic.newInstance(Fluent.class, null);
                i.id(12L);
                System.out.println(i.underlyingMap());
                i.underlyingMap().put("id", 12);
                assertEquals(12L, i.id());
                i.identity(11L);
                System.out.println(i.underlyingMap());
                assertEquals(12L, i.id());
                assertThrows(IllegalStateException.class, i::validate);
                assertNotNull(i.underlyingChangedProperties());
            }
            {
                val i = Mimic.newInstance(Flue.class, null);
                i.id(12L);
                System.out.println(i.underlyingMap());
                assertEquals(12L, i.id());
                assertThrows(IllegalStateException.class, () -> i.identity(null));
                i.identity(11L);
                i.user(BigDecimal.TEN);
                assertThrows(IllegalStateException.class, i::validate);
                i.identity(10L);
                i.user(BigDecimal.valueOf(9));
                System.out.println(i.underlyingMap());
                assertEquals(12L, i.id());
                assertEquals(10L, i.identity());
                assertEquals(BigDecimal.valueOf(9), i.user());
                assertThrows(IllegalStateException.class, i::validate);
                assertNotNull(i.underlyingChangedProperties());
                System.out.println(i);
            }
        }


        @Test
        void asmMimic() {
            Mimic.ByteASM.enable();
            {
                val i = Mimic.newInstance(Fluent.class, null);
                i.id(12L);
                System.out.println(i.underlyingMap());
                assertEquals(12L, i.id());
                i.identity(11L);
                System.out.println(i.underlyingMap());
                assertEquals(12L, i.id());
                assertThrows(IllegalStateException.class, i::validate);
                assertNotNull(i.underlyingChangedProperties());
            }
            {
                val i = Mimic.newInstance(Flue.class, null);
                i.id(12L);
                System.out.println(i.underlyingMap());
                assertEquals(12L, i.id());
                assertThrows(IllegalStateException.class, () -> i.identity(null));
                i.identity(11L);
                i.user(BigDecimal.TEN);
                assertThrows(IllegalStateException.class, i::validate);
                i.identity(10L);
                i.user(BigDecimal.valueOf(9));
                System.out.println(i.underlyingMap() + ":" + i);
                assertEquals(12L, i.id());
                assertEquals(10L, i.identity());
                assertEquals(BigDecimal.valueOf(9), i.user());
                assertThrows(IllegalStateException.class, i::validate);
                assertNotNull(i.underlyingChangedProperties());
            }
        }
    }

    static class MimicTestWithDao {
        @Test
        void proxyDaoAllFields() {
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao1 = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                val daoOrdered = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                assertEquals("identityidid_of_user", seq(dao1.allFields()).map(Field::getName).toString(""));
                assertEquals("ididentityid_of_useruser", seq(daoOrdered.allFields()).map(Field::getName).toString(""));
            }

        }

        @Test
        void proxyDaoConfiguration() {
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao1 = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                val daoOrdered = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                assertEquals(cfg, dao1.configuration());
                assertEquals(cfg, daoOrdered.configuration());
            }

        }

        @Test
        void asmDaoConfiguration() {
            Mimic.Dao.ByteASM.enable();
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao1 = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                val daoOrdered = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                assertEquals(cfg, dao1.configuration());
                assertEquals(cfg, daoOrdered.configuration());
            }

        }

        @Test
        void asmDaoAllFields() {
            Mimic.Dao.ByteASM.enable();
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao1 = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                val daoOrdered = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                assertEquals("identityidid_of_user", seq(dao1.allFields()).map(Field::getName).toString(""));
                assertEquals("ididentityid_of_useruser", seq(daoOrdered.allFields()).map(Field::getName).toString(""));
            }
        }

        @Test
        void proxyDao() {
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                System.out.println(dao);
                System.out.println(dao.DDL());
                val i = Mimic.newInstance(Fluent.class, null);
                i.id(12L);
                i.identity(12L);
                i.idOfUser(24L);
                assertEquals(seq(Arrays.asList("id", "identity", "idOfUser")).sorted().toString(""), seq(i.underlyingChangedProperties()).sorted().toString(""));
                dao.insert(i);
                val i2 = dao.fetchById(12L);
                System.out.println(i2);
                i2.identity(8L);
                assertEquals("identity", seq(i2.underlyingChangedProperties()).sorted().toString(""));
                System.out.println(dao.update(i2));
                val r = dao.fetchById(12L);
                assertEquals(12L, r.id());
                assertEquals(8L, r.identity());
                assertEquals(24L, r.idOfUser());
            }
            {
                val dao = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                System.out.println(dao);
                System.out.println(dao.DDL());
                val i = Mimic.newInstance(Flue.class, null);
                i.id(12L);
                i.identity(12L);
                i.idOfUser(24L);
                dao.insert(i);
                val r = dao.fetchById(12L);
                assertEquals(12L, r.id());
                assertEquals(12L, r.identity());
                assertEquals(24L, r.idOfUser());
            }
        }

        @Test
        void asmProxyDao() {
            Mimic.ByteASM.enable();
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                System.out.println(dao);
                System.out.println(dao.DDL());
                dao.deleteAll();
                val i = Mimic.newInstance(Fluent.class, null);
                i.id(12L);
                i.identity(12L);
                i.idOfUser(24L);
                assertEquals(seq(Arrays.asList("id", "identity", "idOfUser")).sorted().toString(""), seq(i.underlyingChangedProperties()).sorted().toString(""));
                dao.insert(i);
                val i2 = dao.fetchById(12L);
                System.out.println(i2);
                i2.identity(8L);
                assertEquals("identity", seq(i2.underlyingChangedProperties()).sorted().toString(""));
                System.out.println(dao.update(i2));
                val r = dao.fetchById(12L);
                assertEquals(12L, r.id());
                assertEquals(8L, r.identity());
                assertEquals(24L, r.idOfUser());
            }
            {
                val dao = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                System.out.println(dao);
                System.out.println(dao.DDL());
                dao.deleteAll();
                val i = Mimic.newInstance(Flue.class, null);
                i.id(12L);
                i.identity(12L);
                i.idOfUser(24L);
                dao.insert(i);
                val r = dao.fetchById(12L);
                assertEquals(12L, r.id());
                assertEquals(12L, r.identity());
                assertEquals(24L, r.idOfUser());
            }
        }

        @Test
        void asmAsmDao() {
            Mimic.ByteASM.enable();
            Mimic.Dao.ByteASM.enable();
            val cfg = new DefaultConfiguration();
            cfg.setSQLDialect(SQLDialect.H2);
            val hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:test");
            cfg.setDataSource(new HikariDataSource(hc));
            {
                val dao = Mimic.Dao.newInstance(Fluent.class, FluentDao.class, cfg);
                System.out.println(dao);
                System.out.println(dao.DDL());
                dao.deleteAll();
                val i = Mimic.newInstance(Fluent.class, null);
                i.id(12L);
                i.identity(12L);
                i.idOfUser(24L);
                assertEquals(seq(Arrays.asList("id", "identity", "idOfUser")).sorted().toString(""), seq(i.underlyingChangedProperties()).sorted().toString(""));
                dao.insert(i);
                val i2 = dao.fetchById(12L);
                System.out.println(i2);
                i2.identity(8L);
                assertEquals("identity", seq(i2.underlyingChangedProperties()).sorted().toString(""));
                System.out.println(dao.update(i2));
                val r = dao.fetchById(12L);
                assertEquals(12L, r.id());
                assertEquals(8L, r.identity());
                assertEquals(24L, r.idOfUser());
            }
            {
                val dao = Mimic.Dao.newInstance(Flue.class, FlueDao.class, cfg);
                System.out.println(dao);
                System.out.println(dao.DDL());
                dao.deleteAll();
                val i = Mimic.newInstance(Flue.class, null);
                i.id(12L);
                i.identity(12L);
                i.idOfUser(24L);
                dao.insert(i);
                val r = dao.fetchById(12L);
                assertEquals(12L, r.id());
                assertEquals(12L, r.identity());
                assertEquals(24L, r.idOfUser());
            }
        }
    }


}