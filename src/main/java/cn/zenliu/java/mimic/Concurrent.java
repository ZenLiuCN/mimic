package cn.zenliu.java.mimic;

import java.lang.annotation.*;

/**
 * mark the mimic use Concurrent protection.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
public @interface Concurrent {
    /**
     * use lock not concurrent hashmap: not suggested.
     */
    boolean value() default false;
}
