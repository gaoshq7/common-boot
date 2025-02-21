package cn.gsq.common;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.gsq.common.config.event.GalaxyGeneralEvent;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Project : galaxy
 * Class : cn.gsq.common.EventHandleSelector
 *
 * @author : gsq
 * @date : 2024-05-08 16:58
 * @note : It's not technology, it's art !
 **/
@Slf4j
public class EventHandleSelector {

    private volatile static boolean init = false;   // 是否已经预加载过

    private static final HashSet<String> PACKAGE_NAME_LIST = new HashSet<>();   // 加载过的包路径集合

    private static final Map<Class<?>, Set<Kit>> EVENT_METHODS_MAP = new HashMap<>();  // 事件和处理函数的对应集合

    /**
     * @Description : 全事件处理函数
     * @Param : [event]
     * @Return : void
     * @Author : gsq
     * @Date : 09:59
     * @note : ⚠️ 该函数全事件触发 !
    **/
    public static void handleEvent(ApplicationEvent event) {
        // 加载事件处理器（不会重复加载）
        if (!init) {
            for (String path : GalaxyApplicationBuilder.EVENT_HANDLE_PATHS) {
                load(path);
            }
            init = true;
        }
        // 过滤掉不关注的事件
        Class<?> eventClass = event.getClass();
        Set<Kit> kits = EVENT_METHODS_MAP.get(eventClass);
        if (CollUtil.isEmpty(kits)) {
            return;
        }
        // 执行事件处理函数
        for (Kit kit : kits) {
            kit.Handle(event);
        }
    }

    /**
     * @Description : 加载事件处理器
     * @Param : [packageName]
     * @Return : void
     * @Author : gsq
     * @Date : 18:00
     * @note : ⚠️ 处理器将注册到spring beans容器中 !
    **/
    private static void load(String packageName) {
        if (PACKAGE_NAME_LIST.contains(packageName) || StrUtil.isBlank(packageName)) {
            return;
        }
        Set<Class<?>> templates = ClassUtil.scanPackageByAnnotation(packageName, EventHandleClass.class);
        if (CollUtil.isEmpty(templates)) {
            return;
        }
        for (Class<?> template : templates) {
            // 注册到bean容器中
            Object singleton = GalaxySpringUtil.registerSingleton(template);
            // 过滤含有@EventHandleMethod的函数
            Method[] methods = ArrayUtil.filter(
                    template.getDeclaredMethods(),
                    method -> method.getAnnotation(EventHandleMethod.class) != null
            );
            for (Method method : methods) {
                try {
                    Parameter[] params = method.getParameters();
                    Class<?> type = params[0].getType();
                    EVENT_METHODS_MAP.computeIfAbsent(type, k -> new HashSet<>());
                    EVENT_METHODS_MAP.get(type).add(new Kit(singleton, method));
                } catch (Exception e) {
                    log.error("事件函数{}加载失败：{}", method.getName(), e.getMessage(), e);
                }

            }
        }
        // 记录处理过的类路径
        PACKAGE_NAME_LIST.add(packageName);
    }

    private static final class Kit {

        private final Object source;    // 执行主体（已存在于bean环境中）

        private final Method method;    // 执行函数

        /**
         * @Description : 构造器
         * @Param : [source, method]
         * @Return :
         * @Author : gsq
         * @Date : 10:28
         * @note : An art cell !
        **/
        private Kit(Object source, Method method) {
            Parameter[] params = method.getParameters();
            // 参数只有一个且为"ApplicationEvent"子类
            if (params.length == 1 && ApplicationEvent.class.isAssignableFrom(params[0].getType())) {
                this.source = source;
                this.method = method;
                this.method.setAccessible(true);
            } else {
                throw new RuntimeException("处理函数不符合规范...");
            }
        }
        
        /**
         * @Description : 事件处理
         * @Param : [event]
         * @Return : void
         * @Author : gsq
         * @Date : 12:43
         * @note : An art cell ! 
        **/
        private void Handle(ApplicationEvent event) {
            try {
                if (GalaxyGeneralEvent.class.isAssignableFrom(event.getClass())) {
                    // "GalaxyGeneralEvent"事件根据module推送（不写则全部推送）
                    EventHandleMethod annotation = this.method.getAnnotation(EventHandleMethod.class);
                    String module = annotation.module();
                    if (StrUtil.isBlank(module) || ((GalaxyGeneralEvent) event).getModule().equals(module)) {
                        this.method.invoke(this.source, event);
                    }
                } else {
                    // 非"GalaxyGeneralEvent"事件直接推送
                    this.method.invoke(this.source, event);
                }
            } catch (InvocationTargetException | IllegalAccessException e) {
                log.error("事件处理器执行失败：{}", e.getMessage(), e);
            }
        }

    }

}
