package cn.gsq.common.config.event;

/**
 * Project : galaxy
 * Class : cn.gsq.common.config.event.ApplicationEventLoad
 *
 * @author : gsq
 * @date : 2024-04-30 11:04
 * @note : It's not technology, it's art !
 **/
public interface ApplicationEventLoad {

    /**
     * @Description : 上下文初始化完成事件出发器
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @Date : 11:06
     * @note : ⚠️ WebApplicationContext初始化完成时该函数触发 !
    **/
    void applicationLoad();

}
