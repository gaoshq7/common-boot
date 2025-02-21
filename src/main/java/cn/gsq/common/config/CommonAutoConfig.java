package cn.gsq.common.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Configuration
public class CommonAutoConfig {

    /**
     * @Description : 获取线程处理器
     * @Param : []
     * @Return : cn.gsq.sdp.config.SdpAsyncProcessor
     * @Author : gsq
     * @Date : 9:35 上午
     * @note : An art cell !
     **/
    @Bean
    @ConditionalOnMissingBean(CommonAsyncProcessor.class)
    public CommonAsyncProcessor buildConsumerQueueThreadPool(){
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("galaxy-common-thread-%d")
                .build();
        // 不限制线程数量；60秒空闲自动释放
        ExecutorService pool = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        return new CommonAsyncProcessor(pool);
    }
}
