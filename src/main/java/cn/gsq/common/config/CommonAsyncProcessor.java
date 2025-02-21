package cn.gsq.common.config;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Project : galaxy
 * Class : cn.gsq.sdp.config.SdpAsyncProcessor
 *
 * @author : gsq
 * @date : 2021-07-23 14:05
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class CommonAsyncProcessor {

    @Getter
    private final ExecutorService service;

    /**
     * @Description : 构造函数
     * @Param : [service]
     * @Return :
     * @Author : gsq
     * @Date : 2:51 下午
     * @note : An art cell !
    **/
    public CommonAsyncProcessor(ExecutorService service) {
        this.service = service;
    }

    /**
     * @Description : 提交一个异步线程
     * @Param : [task]
     * @Return : java.util.concurrent.Future<?>
     * @Author : gsq
     * @Date : 2:53 下午
     * @note : An art cell !
    **/
    protected Future<?> submit(Runnable task){
        return service.submit(task);
    }

    /**
     * @Description : 提交一个带返回值的异步线程
     * @Param : [task, result]
     * @Return : java.util.concurrent.Future<T>
     * @Author : gsq
     * @Date : 2:54 下午
     * @note : ⚠️ 返回值需要自行建立守护线程监控 !
    **/
    protected <T> Future<T> submit(Runnable task, T result){
        return service.submit(task, result);
    }

    /**
     * @Description : 提交一个带返回值的异步线程
     * @Param : [task]
     * @Return : java.util.concurrent.Future<T>
     * @Author : gsq
     * @Date : 2:55 下午
     * @note : ⚠️ 返回值需要自行建立守护线程监控 !
    **/
    protected <T> Future<T> submit(Callable<T> task){
        return service.submit(task);
    }

    /**
     * @Description : 同步阻塞获取线程返回值
     * @Param : [processor]
     * @Return : T
     * @Author : gsq
     * @Date : 2:56 下午
     * @note : ⚠️ 此函数为异步提交任务, 同步获取返回值函数 !
    **/
    @SneakyThrows
    public <T> T submitTask(SyncProcessor<T> processor){
        Future<T> future = submit(processor::actuator);
        return future.get();
    }

    /**
     * @Description : 简单异步任务
     * @Param : [processor]
     * @Return : void
     * @Author : gsq
     * @Date : 3:03 下午
     * @note : An art cell !
    **/
    public void submitTask(SimpleProcessor processor){
        submit(processor::actuator);
    }

    /**
     * @Description : 异步任务提交
     * @Param : [processor]
     * @Return : void
     * @Author : gsq
     * @Date : 3:03 下午
     * @note : ⚠️ 可自定义返回值回调函数、错误处理函数 !
    **/
    public <T> void submitTask(AsyncProcessor<T> processor){
        T instance = processor.declaration();
        CompletableFuture
                .supplyAsync(processor::actuator, service)
                .whenComplete((result, error) -> {
                    if(ObjectUtil.isNotNull(result)) {
                        processor.callback(result);
                    }
                })
                .exceptionally(error -> {
                    log.error("线程 {} 异常：{}", Thread.currentThread().getName(), error.getMessage(), error);
                    processor.error(instance, error);
                    return instance;
                });
    }

    /**
     * @Description : 异步任务提交，异常延迟处理
     * @Param : [processor]
     * @Return : void
     * @Author : gsq
     * @Date : 17:48
     * @note : An art cell !
    **/
    public void submitTask(ExceptionProcessor processor) {
        CompletableFuture
                .supplyAsync(processor::adorn, service)
                .exceptionally(error -> {
                    processor.error(error);
                    return null;
                });
    }

    public interface SimpleProcessor {

        void actuator();   // 简单提交线程

    }

    public interface SyncProcessor<T> {

        T actuator();   // 同步阻塞获取结果

    }

    public interface AsyncProcessor<T> {

        T declaration();    // 声明

        T actuator();   // 异步执行方法

        void callback(T t);    // 异步回调函数

        void error(T t, Throwable e);    // 程序异常处理

    }

    public interface ExceptionProcessor {

        void actuator();    // 执行器

        void error(Throwable e);    // 程序异常处理

        default String adorn() {
            actuator();
            return "";
        }

    }

}
