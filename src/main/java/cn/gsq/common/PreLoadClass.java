package cn.gsq.common;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.PreLoadClass
 *
 * @author : gsq
 * @date : 2024-04-30 14:45
 * @note : It's not technology, it's art !
 **/
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface PreLoadClass {

    int value() default 0;

}
