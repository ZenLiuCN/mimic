package cn.zenliu.java.mimic;

import java.lang.annotation.*;

/**
 * define a Mimic type use JavaBean GetterSetter protocol
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
public @interface JavaBean {
    /**
     * Allow Fluent Mode
     */
    boolean value() default false;
}
