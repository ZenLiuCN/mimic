package cn.zenliu.java.mimic;

import java.lang.annotation.*;

/**
 * <p>used to define Validation on Getter;
 * <p>this used to Validate on Set and on validate method;
 * <p>if current property is Annotated with {@link AsString},the Validation only happened when Setter is called.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@Documented
public @interface Validation {
    /**
     * the holder of Validation Property;
     */
    Class<?> value() default Mimic.Validate.class;

    /**
     * the static property name of Validate BiConsumer< PropertyName,Value >
     */
    String property() default "noop";

}
