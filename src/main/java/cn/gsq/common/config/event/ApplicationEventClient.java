package cn.gsq.common.config.event;

import org.springframework.context.ApplicationEvent;

/**
 * Project : galaxy
 * Class : cn.gsq.common.config.event.ApplicationEventClient
 *
 * @author : gsq
 * @date : 2024-04-30 11:27
 * @note : It's not technology, it's art !
 **/
public interface ApplicationEventClient {

    void onApplicationEvent(ApplicationEvent event);

}
