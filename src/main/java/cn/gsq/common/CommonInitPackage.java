package cn.gsq.common;

import cn.gsq.common.config.GalaxySpringUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.CommonInitPackage
 *
 * @author : gsq
 * @date : 2024-04-30 14:34
 * @note : It's not technology, it's art !
 **/
@Configuration
public class CommonInitPackage {

    private volatile static boolean init = false;   // 是否已经预加载过

    private static final HashSet<Method> METHOD_LIST = new HashSet<>(); // 加载过的函数集合

    private static final HashSet<String> PACKAGE_NAME_LIST = new HashSet<>();   // 加载过的包路径集合

    /**
     * @Description : 该加载函数由 ApplicationReadyEvent 事件触发
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @Date : 14:46
     * @note : An art cell !
    **/
    public static void init() {
        if (init) {
            DefaultSystemLog.getLog().warn("系统已经完成了初始化...");
            return;
        }
        for (String item : GalaxyApplicationBuilder.PRE_CLASS_PATHS) {
            if(StrUtil.isNotBlank(item)) {
                DefaultSystemLog.getLog().debug("系统将要加载{}路径下@PreLoadClass的类文件", item);
                load(item);
            }
        }
        init = true;
    }

    /**
     * @Description : 加载包中函数
     * @Param : [packageName]
     * @Return : void
     * @Author : gsq
     * @Date : 14:53
     * @note : An art cell !
    **/
    private static void load(String packageName) {
        if (PACKAGE_NAME_LIST.contains(packageName)) {
            DefaultSystemLog.getLog().warn("{}包已经被初始化过了...", packageName);
            return;
        }
        //扫描
        Set<Class<?>> set = ClassUtil.scanPackageByAnnotation(packageName, PreLoadClass.class);
        if (set == null || set.size() <= 0) {
            return;
        }
        // 排序调用
        List<Map.Entry<Class<?>, Integer>> newList = splitClass(set);
        if (newList != null) {
            for (Map.Entry<Class<?>, Integer> item : newList) {
                loadClass(item.getKey());
            }
        }
        PACKAGE_NAME_LIST.add(packageName);
    }

    /**
     * @Description : 根据函数顺序执行预加载函数
     * @Param : [classT]
     * @Return : void
     * @Author : gsq
     * @Date : 15:07
     * @note : An art cell !
    **/
    private static void loadClass(Class<?> classT) {
        // 注入到Spring bean容器中
        Object singleton = GalaxySpringUtil.registerSingleton(classT);
        // 筛选函数
        Method[] methods = classT.getDeclaredMethods();
        HashMap<Method, Integer> sortMap = new HashMap<>();
        for (Method method : methods) {
            PreLoadMethod preLoadMethod = method.getAnnotation(PreLoadMethod.class);
            if (preLoadMethod == null) {
                continue;
            }
            Type type = method.getGenericReturnType();
            Type[] parameters = method.getParameterTypes();
            if (parameters.length <= 0 && Void.TYPE.equals(type)) {
                sortMap.put(method, preLoadMethod.value());
            } else {
                DefaultSystemLog.getLog().error("函数加载类{}的{}函数不符合规范：无参数、无返回值!", classT.getName(), method.getName());
            }
        }
        // 根据顺序执行函数
        if (sortMap.size() > 0) {
            List<Map.Entry<Method, Integer>> newList = new ArrayList<>(sortMap.entrySet());
            newList.sort(Map.Entry.comparingByValue());
            for (Map.Entry<Method, Integer> item : newList) {
                Method method = item.getKey();
                if (METHOD_LIST.contains(method)) {
                    DefaultSystemLog.getLog().warn("{}的{}函数已经执行过了...", classT.getName(), method.getName());
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(singleton);
                    METHOD_LIST.add(method);
                } catch (Exception e) {
                    DefaultSystemLog.getLog().error("函数预加载类{}的{}函数错误:", classT.getName(), method.getName(), e);
                }
            }
        }
    }

    /**
     * @Description : 根据序号排序
     * @Param : [list]
     * @Return : java.util.List<java.util.Map.Entry<java.lang.Class<?>,java.lang.Integer>>
     * @Author : gsq
     * @Date : 14:54
     * @note : An art cell !
     **/
    private static List<Map.Entry<Class<?>, Integer>> splitClass(Set<Class<?>> list) {
        HashMap<Class<?>, Integer> sortMap = new HashMap<>(10);
        for (Class<?> item : list) {
            PreLoadClass preLoadClass = item.getAnnotation(PreLoadClass.class);
            sortMap.put(item, preLoadClass.value());
        }
        List<Map.Entry<Class<?>, Integer>> newList = null;
        if (sortMap.size() > 0) {
            newList = new ArrayList<>(sortMap.entrySet());
            newList.sort(Map.Entry.comparingByValue());
        }
        return newList;
    }

}
