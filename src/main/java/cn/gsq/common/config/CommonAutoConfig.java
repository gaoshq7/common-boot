package cn.gsq.common.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Configuration
public class CommonAutoConfig {

    /**
     * 默认线程池配置：
     * - 核心线程：max(8, CPU * 2)，常驻；
     * - 最大线程：200，防止突发流量打爆 JVM；
     * - 队列容量：1024，先入队列、再扩线程；
     * - 空闲存活：60s；
     * - 拒绝策略：CallerRunsPolicy，过载时由调用线程兜底执行，提供天然背压。
     *
     * 不满足时声明自己的 CommonAsyncProcessor Bean 即可（@ConditionalOnMissingBean 会让位）。
     */
    @Bean
    @ConditionalOnMissingBean(CommonAsyncProcessor.class)
    public CommonAsyncProcessor buildConsumerQueueThreadPool() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("galaxy-common-thread-%d")
                .setDaemon(true)
                .build();
        int core = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService pool = new ThreadPoolExecutor(
                core, 200, 60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return new CommonAsyncProcessor(pool);
    }

}
