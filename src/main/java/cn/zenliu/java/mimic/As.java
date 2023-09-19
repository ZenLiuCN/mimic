package cn.zenliu.java.mimic;

import org.jooq.DataType;

import java.lang.annotation.*;

/**
 * define a Field detail
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@Documented
public @interface As {
    /**
     * define field name,default is Property name convert to underscore case.
     */
    String value() default "";

    /**
     * which class hold current field DataType
     */
    Class<?> typeHolder() default Mimic.Dao.class;

    /**
     * which public static field in {@link As#typeHolder()} is the {@link DataType} of current field.
     * <p> see {@link Mimic.Dao#BigIntIdentity} as Example.
     */
    String typeProperty() default "";

    /**
     * define a class holds the DataType Processor;
     * <p>see {@link Mimic.Dao#Identity} as Example
     */
    Class<?> procHolder() default Mimic.Dao.class;

    /**
     * define a static property holds the DataType Processor on {@link As#procHolder()}
     * <p> the property must is type Function< DataType , DataType >
     */
    String procProperty() default "";
}
