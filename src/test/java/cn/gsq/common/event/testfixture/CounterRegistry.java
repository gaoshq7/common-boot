package cn.gsq.common.event.testfixture;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事件处理器调用次数 / 最后一次 payload 的简单收集器。
 * 测试间通过 {@link #reset()} 清理。
 */
public final class CounterRegistry {

    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Object> LAST_PAYLOAD = new ConcurrentHashMap<>();

    private CounterRegistry() {}

    public static void inc(String key, Object payload) {
        COUNTS.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (payload != null) {
            LAST_PAYLOAD.put(key, payload);
        }
    }

    public static int count(String key) {
        AtomicInteger c = COUNTS.get(key);
        return c == null ? 0 : c.get();
    }

    public static Object last(String key) {
        return LAST_PAYLOAD.get(key);
    }

    public static void reset() {
        COUNTS.clear();
        LAST_PAYLOAD.clear();
    }
}
