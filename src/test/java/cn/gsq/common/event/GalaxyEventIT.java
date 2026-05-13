package cn.gsq.common.event;

import cn.gsq.common.EventHandleSelector;
import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.common.event.testfixture.CounterRegistry;
import cn.gsq.common.event.testfixture.OrderHandlers;
import cn.gsq.common.event.testfixture.OrderPaidEvent;
import cn.gsq.common.event.testfixture.OrderShippedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自定义事件推送 + 接收的集成测试。
 * <p>
 * 启动一个最小 Spring 上下文（只装 {@link GalaxySpringUtil}），通过类静态块把测试 fixture 包注入
 * {@code GalaxyApplicationBuilder.EVENT_HANDLE_PATHS}，模拟下游项目用 {@code addEventHandlePaths(...)}
 * 注册 handler 的效果。
 */
@SpringBootTest(classes = {GalaxySpringUtil.class})
@DisplayName("自定义事件 - 推送 & 接收")
class GalaxyEventIT {

    private static final String FIXTURE_PKG = "cn.gsq.common.event.testfixture";

    /**
     * 在 Spring 启动前完成两件事：
     * 1. 把 fixture 包注入 EVENT_HANDLE_PATHS（生产路径里这是 builder.addEventHandlePaths 做的事）
     * 2. reset 一次 EventHandleSelector，避免其它 test class 的 init 状态影响本次扫描
     */
    static {
        try {
            Field f = Class.forName("cn.gsq.common.GalaxyApplicationBuilder")
                    .getDeclaredField("EVENT_HANDLE_PATHS");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String> paths = (List<String>) f.get(null);
            if (!paths.contains(FIXTURE_PKG)) {
                paths.add(FIXTURE_PKG);
            }
            // 清掉可能的前一轮加载状态，保证本测试启动时一定会扫描 fixture 包
            EventHandleSelector.reset();
        } catch (Exception e) {
            throw new IllegalStateException("无法注入测试用 handler 包路径", e);
        }
    }

    @BeforeEach
    void resetCounters() {
        CounterRegistry.reset();
    }

    @Test
    @DisplayName("publishEvent: handler module 与事件 module 匹配时触发")
    void publishEvent_should_invoke_matching_module_handler() {
        GalaxySpringUtil.publishEvent(new OrderPaidEvent("order", "ORDER-001"));

        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_ORDER)).isEqualTo(1);
        assertThat(CounterRegistry.last(OrderHandlers.KEY_PAID_STRICT_ORDER)).isEqualTo("ORDER-001");
    }

    @Test
    @DisplayName("publishEvent: 事件 module 不匹配时，对应 handler 不触发")
    void publishEvent_should_skip_unmatched_module_handler() {
        GalaxySpringUtil.publishEvent(new OrderPaidEvent("order", "ORDER-002"));

        // module="billing" 的 handler 不应触发
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_BILLING)).isZero();
    }

    @Test
    @DisplayName("@EventHandleMethod 空 module → 同类型事件全部接收")
    void empty_module_handler_should_receive_all_modules() {
        GalaxySpringUtil.publishEvent(new OrderPaidEvent("order", "A"));
        GalaxySpringUtil.publishEvent(new OrderPaidEvent("billing", "B"));
        GalaxySpringUtil.publishEvent(new OrderPaidEvent("anything", "C"));

        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_ANY)).isEqualTo(3);
    }

    @Test
    @DisplayName("同事件多个匹配 handler 全部触发（精确 + 全收）")
    void multiple_matching_handlers_should_all_fire() {
        GalaxySpringUtil.publishEvent(new OrderPaidEvent("order", "ORDER-X"));

        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_ORDER)).isEqualTo(1);
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_ANY)).isEqualTo(1);
        // 不匹配 module 的 handler 不触发
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_BILLING)).isZero();
    }

    @Test
    @DisplayName("事件类型按 Class 精确匹配，不同事件类型互不影响")
    void event_types_should_be_isolated() {
        GalaxySpringUtil.publishEvent(new OrderShippedEvent("order", "SHP-001"));

        assertThat(CounterRegistry.count(OrderHandlers.KEY_SHIPPED)).isEqualTo(1);
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_ORDER)).isZero();
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_ANY)).isZero();
    }

    @Test
    @DisplayName("tryPublishEvent: context 就绪时返回 true，且 handler 被触发")
    void tryPublishEvent_when_context_ready_should_return_true() {
        boolean ok = GalaxySpringUtil.tryPublishEvent(new OrderPaidEvent("order", "ORDER-Y"));

        assertThat(ok).isTrue();
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_ORDER)).isEqualTo(1);
    }

    @Test
    @DisplayName("handler 被自动注册为容器单例，bean name = lowerFirst(SimpleName)")
    void handler_should_be_registered_as_singleton_bean_with_lowerFirst_name() {
        // OrderHandlers → "orderHandlers"
        assertThat(GalaxySpringUtil.containsBean("orderHandlers")).isTrue();
        assertThat(GalaxySpringUtil.findBean("orderHandlers")).isPresent();
    }

    @Test
    @DisplayName("多次发布同一事件，handler 调用次数累加")
    void repeated_publish_should_accumulate_invocations() {
        for (int i = 0; i < 5; i++) {
            GalaxySpringUtil.publishEvent(new OrderPaidEvent("order", "ORDER-" + i));
        }

        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_STRICT_ORDER)).isEqualTo(5);
        assertThat(CounterRegistry.count(OrderHandlers.KEY_PAID_ANY)).isEqualTo(5);
        assertThat(CounterRegistry.last(OrderHandlers.KEY_PAID_STRICT_ORDER)).isEqualTo("ORDER-4");
    }
}
