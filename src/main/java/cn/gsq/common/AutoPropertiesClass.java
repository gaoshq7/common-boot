package cn.gsq.common;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.AutoPropertiesClass
 *
 * @author : gsq
 * @date : 2024-04-29 17:53
 * @note : It's not technology, it's art !
 **/
@Documented
@Target(ElementType.TYPE)
@Inherited
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoPropertiesClass {
}
