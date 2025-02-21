package cn.gsq.common.interceptor;

import cn.gsq.common.CommonPropertiesFinal;
import cn.gsq.common.DefaultSystemLog;
import cn.gsq.common.GalaxyApplicationBuilder;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.interceptor.InterceptorControl
 *
 * @author : gsq
 * @date : 2021-09-10 15:08
 * @note : It's not technology, it's art !
 **/
@Configuration
//@EnableWebMvc
public class InterceptorControl implements WebMvcConfigurer {

    @Value("${" + CommonPropertiesFinal.INTERCEPTOR_INIT_PACKAGE_NAME + ":}")
    private String loadPath;
    /**
     * 加载成功
     */
    private static final List<Class> LOAD_OK = new ArrayList<>();
    private InterceptorRegistry registry;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        this.registry = registry;
        //  加载application 注入
        Set<Class<?>> def = loadApplicationInterceptor();
        // 用户添加的
        if (StrUtil.isNotEmpty(loadPath)) {
            String[] paths = StrUtil.splitToArray(loadPath, StrUtil.COMMA);
            Collection<Class<?>> newClassSet = CollUtil.union(def, new ArrayList<>());
            for (String item : paths) {
                Set<Class<?>> classSet = ClassUtil.scanPackageByAnnotation(item, InterceptorPattens.class);
                // 合并
                newClassSet = CollUtil.union(newClassSet, classSet);
            }
            loadClass(newClassSet);
        } else if (def != null) {
            loadClass(def);
        }
    }

    private Set<Class<?>> loadApplicationInterceptor() {
        Set<Class<? extends BaseInterceptor>> interceptorClass = GalaxyApplicationBuilder
                .getActiveApplication(GalaxyApplicationBuilder::getInterceptorClass);
        if (interceptorClass == null) {
            return null;
        }
        Class<?>[] cls = interceptorClass.toArray(new Class[0]);
        return new HashSet<>(Arrays.asList(cls));
    }

    private void loadClass(Collection<Class<?>> set) {
        if (null == set) {
            return;
        }
        List<Map.Entry<Class, Integer>> newList = splitClass(set);
        if (newList != null) {
            for (Map.Entry<Class, Integer> entry : newList) {
                loadInterceptor(entry.getKey(), registry);
            }
        }
    }

    /**
     * 排序class
     *
     * @param list list
     * @return 排序后的
     */
    private static List<Map.Entry<Class, Integer>> splitClass(Collection<Class<?>> list) {
        HashMap<Class, Integer> sortMap = new HashMap<>(10);
        for (Class item : list) {
            boolean isAbstract = Modifier.isAbstract(item.getModifiers());
            if (isAbstract) {
                continue;
            }
            if (!HandlerInterceptor.class.isAssignableFrom(item)) {
                DefaultSystemLog.getLog().error("加载拦截器异常: {} 没有实现 {}", item, HandlerInterceptor.class);
                continue;
            }
            InterceptorPattens interceptorPattens = (InterceptorPattens) item.getAnnotation(InterceptorPattens.class);
            sortMap.put(item, interceptorPattens.sort());
        }
        List<Map.Entry<Class, Integer>> newList = null;
        if (sortMap.size() >= 1) {
            newList = new ArrayList<>(sortMap.entrySet());
            newList.sort(Comparator.comparing(Map.Entry::getValue));
        }
        return newList;
    }

    private void loadInterceptor(Class<?> itemCls, InterceptorRegistry registry) {
        InterceptorPattens interceptorPattens = itemCls.getAnnotation(InterceptorPattens.class);
        Object handlerInterceptor = Singleton.get(itemCls);
        String[] patterns = interceptorPattens.value();
        // 注册
        InterceptorRegistration registration = registry.addInterceptor((HandlerInterceptor) handlerInterceptor);
        registration.addPathPatterns(patterns);
        // 排除
        String[] exclude = interceptorPattens.exclude();
        if (exclude.length > 0) {
            registration.excludePathPatterns(exclude);
        }
        LOAD_OK.add(itemCls);
        DefaultSystemLog.getLog().debug("加载拦截器：{} {} {} {}", itemCls, Arrays.toString(patterns), Arrays.toString(exclude), interceptorPattens.sort());
    }

}
