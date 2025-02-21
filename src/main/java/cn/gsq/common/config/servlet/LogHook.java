package cn.gsq.common.config.servlet;

import cn.gsq.common.LogLevel;

/**
 * Project : galaxy
 * Class : cn.gsq.common.config.servlet.ServletLogBack
 *
 * @author : gsq
 * @date : 2024-04-30 11:53
 * @note : It's not technology, it's art !
 **/
public interface LogHook {

    /**
     * @Description : servlet请求日志
     * @Param : [level, msg]
     * @Return : void
     * @Author : gsq
     * @Date : 13:47
     * @note : An art cell !
    **/
    void servletLog(LogLevel level, String msg);

}
