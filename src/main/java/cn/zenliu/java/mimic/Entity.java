package cn.zenliu.java.mimic;

import java.lang.annotation.*;

/**
 * define a Mimic is Entity,which can build a Repository;
 * <p>this must <b>directly</b> annotate on a Mimic type;
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Entity {
    String value() default "";
}
