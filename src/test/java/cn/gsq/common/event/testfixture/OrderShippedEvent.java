package cn.gsq.common.event.testfixture;

import cn.gsq.common.config.event.GalaxyGeneralEvent;

/**
 * 测试用：订单已发货事件（与 {@link OrderPaidEvent} 类型隔离，用于验证 handler 按事件类型精确匹配）。
 */
public class OrderShippedEvent extends GalaxyGeneralEvent {

    public OrderShippedEvent(String module, Object source) {
        super(module, source);
    }
}
