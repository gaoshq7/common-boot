package cn.gsq.common;

import cn.gsq.common.config.servlet.LogHook;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

/**
 * Project : galaxy
 * Class : cn.gsq.common.DefaultSystemLog
 *
 * @author : gsq
 * @date : 2021-09-10 14:38
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class DefaultSystemLog {

    @Setter
    @Getter
    private static volatile LogHook hook;   // 日志钩子

    /**
     * @Description : 构造器私有化
     * @Param : []
     * @Return :
     * @Author : gsq
     * @Date : 13:35
     * @note : An art cell !
    **/
    private DefaultSystemLog() {}

    /**
     * @Description : 获取系统日志对象
     * @Param : []
     * @Return : org.slf4j.Logger
     * @Author : gsq
     * @Date : 13:36
     * @note : An art cell !
    **/
    public static Logger getLog() {
        return log;
    }

}
