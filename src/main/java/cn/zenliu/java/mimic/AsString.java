package cn.zenliu.java.mimic;

import java.lang.annotation.*;
import java.math.BigDecimal;

/**
 * define a property is converted to string, should not use with {@link Array}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@Documented
public @interface AsString {
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
