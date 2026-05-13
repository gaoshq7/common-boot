package cn.gsq.common.event.testfixture;

import cn.gsq.common.config.event.GalaxyGeneralEvent;

/**
 * 测试用：订单已支付事件。
 */
public class OrderPaidEvent extends GalaxyGeneralEvent {

    public OrderPaidEvent(String module, Object source) {
        super(module, source);
    }
}
