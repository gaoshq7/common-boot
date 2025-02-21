package cn.gsq.common.config.event;

import cn.hutool.core.date.DateUtil;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

/**
 * Project : galaxy
 * Class : cn.gsq.common.config.event.GlobalArgUpdateEvent
 *
 * @author : gsq
 * @date : 2024-05-08 11:24
 * @note : It's not technology, it's art !
 **/
public class GalaxyGeneralEvent extends ApplicationEvent {

    @Getter
    private final String date = DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss");

    @Getter
    private final String module;  // 模块名称

    @Getter
    private final Object source;     // 事件内容

    /**
     * @Description : 事件构造器
     * @Param : [module, source]
     * @Return :
     * @Author : gsq
     * @Date : 11:42
     * @note : An art cell !
    **/
    public GalaxyGeneralEvent(String module, Object source) {
        super(source);
        this.module = module;
        this.source = source;
    }

}
