package cn.gsq.common.interceptor;

import cn.gsq.common.DefaultSystemLog;
import cn.gsq.common.controller.AbstractController;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Project : galaxy
 * Class : cn.gsq.common.interceptor.BaseInterceptor
 *
 * @author : gsq
 * @date : 2021-09-10 14:33
 * @note : ⚠️ http请求执行顺序：preHandle（没有捕捉到异常可中断后面操作）-> controller -> postHandle（系统异常该函数不会执行）-> afterCompletion !
 *         ⚠️ 当系统遇到异常则会将请求重定向到/error路径上 !
 **/
public abstract class BaseInterceptor extends HandlerInterceptorAdapter {

    private static final ThreadLocal<BaseCallbackController> currentController = new ThreadLocal<>();

    /**
     * @Description : 清空当前线程的 controller 资源
     * @Return : void
     * @Author : gsq
     * @Date : 2024/5/23 13:25
     * @Note : An art cell !
     **/
    protected void clearResources() {
        currentController.remove();
    }

    /**
     * @Description : 获取http协议会话
     * @Return : javax.servlet.http.HttpSession
     * @Author : gsq
     * @Date : 2024/5/23 13:26
     * @Note : An art cell !
     **/
    public static HttpSession getSession() {
        return BaseCallbackController.getRequestAttributes().getRequest().getSession();
    }

    /**
     * @Description : http协议请求开始拦截函数
     * @param javax.servlet.http.HttpServletRequest request : http请求
     * @param javax.servlet.http.HttpServletResponse response : http响应
     * @param java.lang.Object handler : 处理当前请求的 controller
     * @Return : boolean
     * @Author : gsq
     * @Date : 2024/5/23 13:27
     * @Note : ⚠️ 子类不需要覆盖该函数 !
     **/
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 重定向系统异常
        if (request.getRequestURI().equals("/error")) {
            response.setContentType("application/json;charset=utf-8");
            Object o = error(response);     // 根据response自定义异常返回信息
            if (o == null) {
                o = ResponseEntity.status(response.getStatus()).contentType(MediaType.APPLICATION_JSON)
                        .body(MapUtil.builder().put("code", response.getStatus()).put("msg", "系统异常").build());
            }
            response.getOutputStream().write(JSONUtil.toJsonStr(o).getBytes());
            return false;
        }
        // 默认网页图标
        if (request.getRequestURI().equals("/favicon.ico")) {
            response.getOutputStream().write(ResourceUtil.readBytes("favicon.ico"));
            return false;
        }
        HandlerMethod handlerMethod = handler instanceof HandlerMethod ? (HandlerMethod) handler : null;
        if (handlerMethod == null) {
            return true;
        }
        Object object = handlerMethod.getBean();
        Class<?> controlClass = object.getClass();
        // 如果是 BaseCallbackController 的子类则加入到当前请求线程的缓存中
        if (BaseCallbackController.class.isAssignableFrom(controlClass)) {
            currentController.set((BaseCallbackController) object);
        }
        return preHandle(request, response, handlerMethod);
    }

    /**
     * @Description : http协议请求完成拦截函数
     * @param javax.servlet.http.HttpServletRequest request : http请求
     * @param javax.servlet.http.HttpServletResponse response : http响应
     * @param java.lang.Object handler : 处理当前请求的 controller
     * @param org.springframework.web.servlet.ModelAndView modelAndView : 请求模型
     * @Return : void
     * @Author : gsq
     * @Date : 2024/5/23 13:58
     * @Note : ⚠️ 在 controller 中遇到系统异常（500）该函数不会执行 !
     **/
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (response.getStatus() >= HttpStatus.BAD_REQUEST.value()) {
            DefaultSystemLog.getLog().error("http请求错误: {}, 错误码：{}", request.getRequestURL().toString(), response.getStatus());
        }
    }

    /**
     * @Description : http协议请求完成异常拦截函数
     * @param javax.servlet.http.HttpServletRequest request : http请求
     * @param javax.servlet.http.HttpServletResponse response : http响应
     * @param java.lang.Object handler : 处理当前请求的 controller
     * @param java.lang.Exception ex : http请求异常实例
     * @Return : void
     * @Author : gsq
     * @Date : 2024/5/23 14:20
     * @Note : An art cell !
     **/
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (ex instanceof ClientAbortException) {
            DefaultSystemLog.getLog().warn("http客户端意外终止了链接: {}", request.getRequestURL().toString());
        } else if (ex != null) {
            DefaultSystemLog.getLog().error("http请求异常: {}", request.getRequestURL().toString());
        }
        // 释放线程缓存资源
        AbstractController.clearResources();
        clearResources();
    }

    /**
     * @Description : 回调 controller 中的 resetInfo 函数
     * @Return : void
     * @Author : gsq
     * @Date : 2024/5/23 13:53
     * @Note : An art cell !
     **/
    protected void reload() {
        BaseCallbackController baseCallbackController = currentController.get();
        if (baseCallbackController != null) {
            baseCallbackController.resetInfo();
        }
    }

    /**
     * @Description : 系统异常时返回体
     * @param javax.servlet.http.HttpServletResponse response : 异常的响应信息
     * @Return : T
     * @Author : gsq
     * @Date : 2024/5/23 17:14
     * @Note : An art cell !
     **/
    protected Object error(HttpServletResponse response) {
        return null;
    }

    /**
     * @Description : 拦截函数
     * @param javax.servlet.http.HttpServletRequest request : http请求
     * @param javax.servlet.http.HttpServletResponse response : http响应
     * @param org.springframework.web.method.HandlerMethod handlerMethod : 处理当前请求的 controller
     * @Return : boolean
     * @Author : gsq
     * @Date : 2024/5/23 14:22
     * @Note : An art cell !
     **/
    protected abstract boolean preHandle(HttpServletRequest request, HttpServletResponse response, HandlerMethod handlerMethod) throws Exception;

}
