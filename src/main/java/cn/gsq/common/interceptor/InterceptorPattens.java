package cn.gsq.common.interceptor;

import java.lang.annotation.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.interceptor.InterceptorPattens
 *
 * @author : gsq
 * @date : 2021-09-10 15:10
 * @note : It's not technology, it's art !
 **/
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface InterceptorPattens {

    /**
     * 拦截目录
     *
     * @return 数组
     */
    String[] value() default {"/**"};

    /**
     * 排除目录
     *
     * @return 数组
     */
    String[] exclude() default {};

    /**
     * 拦截器排序
     *
     * @return 值越小 先执行
     */
    int sort() default 0;

}
