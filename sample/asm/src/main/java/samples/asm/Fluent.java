package samples.asm;

import cn.zenliu.java.mimic.Mimic;

import java.math.BigDecimal;
import java.util.Map;

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

}
