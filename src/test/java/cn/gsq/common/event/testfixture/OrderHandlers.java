package cn.gsq.common.event.testfixture;

import cn.gsq.common.EventHandleClass;
import cn.gsq.common.EventHandleMethod;

/**
 * 测试用事件处理类。
 * <p>覆盖几种典型组合：
 * <ul>
 *   <li>{@link #onOrderPaidStrict} - 只接收 module="order" 的 {@link OrderPaidEvent}</li>
 *   <li>{@link #onOrderPaidBilling} - 只接收 module="billing" 的 {@link OrderPaidEvent}</li>
 *   <li>{@link #onAnyOrderPaid} - module 留空，接收所有 {@link OrderPaidEvent}</li>
 *   <li>{@link #onOrderShipped} - 验证不同事件类型互相隔离</li>
 * </ul>
 */
@EventHandleClass
public class OrderHandlers {

    public static final String KEY_PAID_STRICT_ORDER = "paid-strict-order";
    public static final String KEY_PAID_STRICT_BILLING = "paid-strict-billing";
    public static final String KEY_PAID_ANY = "paid-any";
    public static final String KEY_SHIPPED = "shipped";

    @EventHandleMethod(module = "order")
    private void onOrderPaidStrict(OrderPaidEvent event) {
        CounterRegistry.inc(KEY_PAID_STRICT_ORDER, event.getSource());
    }

    @EventHandleMethod(module = "billing")
    private void onOrderPaidBilling(OrderPaidEvent event) {
        CounterRegistry.inc(KEY_PAID_STRICT_BILLING, event.getSource());
    }

    @EventHandleMethod
    private void onAnyOrderPaid(OrderPaidEvent event) {
        CounterRegistry.inc(KEY_PAID_ANY, event.getSource());
    }

    @EventHandleMethod(module = "order")
    private void onOrderShipped(OrderShippedEvent event) {
        CounterRegistry.inc(KEY_SHIPPED, event.getSource());
    }
}
