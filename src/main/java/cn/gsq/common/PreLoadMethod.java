package cn.gsq.common;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.PreLoadMethod
 *
 * @author : gsq
 * @date : 2024-04-29 17:54
 * @note : It's not technology, it's art !
 **/
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreLoadMethod {

    int value() default 0;

}
