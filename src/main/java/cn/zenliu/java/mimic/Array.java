package cn.zenliu.java.mimic;

import java.lang.annotation.*;

/**
 * define a property is Collection of Mimic;
 * <p>Current support 1. Array 2. List 3. Set;
 * <p>should not use with {@link Array}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@Documented
public @interface Array {
    /**
     * mark the value type
     */
    Class<? extends Mimic> value();
}
