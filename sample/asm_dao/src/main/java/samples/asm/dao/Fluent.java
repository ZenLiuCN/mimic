package samples.asm.dao;

import cn.zenliu.java.mimic.Mimic;
import lombok.val;
import org.jooq.Configuration;
import org.jooq.Field;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.util.*;

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

    static Fluent of(Map<String, Object> value) {
        return Mimic.newInstance(Fluent.class, value);
    }

    @Dao.Entity
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

        static Flue of(Map<String, Object> value) {
            return Mimic.newInstance(Flue.class, value);
        }
    }

    public interface FluentDao extends Dao<Fluent> {
        static FluentDao of(Configuration cfg) {
            return Dao.newInstance(Fluent.class, FluentDao.class, cfg);
        }

        @As(typeHolder = Dao.class, typeProperty = "BigIntIdentity")
        Field<Long> id();

        @As(typeHolder = SQLDataType.class, typeProperty = "BIGINT")
        Field<Long> identity();

        @As(typeHolder = SQLDataType.class, typeProperty = "VARCHAR")
        Field<String> idOfUser();

        @Override
        default List<Field<?>> allFields() {
            return Arrays.asList(id(), identity(), idOfUser());
        }

        default int insert(Fluent i) {
            return ctx().insertInto(table()).set(toDatabase(i.underlyingMap())).execute();
        }

        default Fluent fetchById(long id) {
            return instance(ctx().selectFrom(table()).where(id().eq(id)).fetchOne().intoMap());
        }

        default Fluent fetchByIdentity(long identity) {
            return instance(ctx().selectFrom(table()).where(identity().eq(identity)).fetchOne().intoMap());
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

    public interface FlueDao extends Dao<Flue> {
        Field<BigDecimal> user();

        @As(typeHolder = Dao.class, typeProperty = "BigIntIdentity")
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

        default Optional<Flue> fetchById(long id) {
            return ctx().selectFrom(table()).where(id().eq(id)).fetchOptional().map(x -> instance(x.intoMap()));
        }

        default void deleteAll() {
            ctx().delete(table()).execute();
        }

        static FlueDao of(Configuration cfg) {
            return Dao.newInstance(Flue.class, FlueDao.class, cfg);
        }
    }

}
