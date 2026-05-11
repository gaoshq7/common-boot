package cn.gsq.common.interceptor;

import cn.hutool.extra.servlet.ServletUtil;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Project : galaxy
 * Class : cn.gsq.common.interceptor.BaseCallbackController
 *
 * @author : gsq
 * @date : 2021-09-10 14:35
 * @note : It's not technology, it's art !
 **/
public abstract class BaseCallbackController {

    /**
     * 重置信息
     */
    public void resetInfo() {
    }

    /**
     * 全局获取请求对象
     *
     * @throws IllegalStateException 当前线程不在 HTTP 请求上下文中时抛出
     */
    public static ServletRequestAttributes getRequestAttributes() {
        ServletRequestAttributes servletRequestAttributes = tryGetRequestAttributes();
        if (servletRequestAttributes == null) {
            throw new IllegalStateException("当前线程不在 HTTP 请求上下文中（常见于 @Async / 定时任务 / MQ 消费线程），无法获取请求属性");
        }
        return servletRequestAttributes;
    }

    /**
     * 获取 ServletRequestAttributes（不会抛异常，不在请求上下文中时返回 null）
     */
    public static ServletRequestAttributes tryGetRequestAttributes() {
        try {
            RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
            if (attributes instanceof ServletRequestAttributes) {
                return (ServletRequestAttributes) attributes;
            }
            return null;
        } catch (IllegalStateException e) {
            // 当前线程不在请求上下文中（如异步线程、定时任务）
            return null;
        }
    }

    /**
     * 获取客户端的 ip 地址（优先代理头，可被伪造）
     */
    public static String getClientIP() {
        ServletRequestAttributes servletRequest = tryGetRequestAttributes();
        if (servletRequest == null) {
            return null;
        }
        HttpServletRequest request = servletRequest.getRequest();
        if (request == null) {
            return null;
        }
        return ServletUtil.getClientIP(request);
    }

    /**
     * 提取 request 中的所有 header 信息。
     * <p>同名 header 的值用逗号拼接（RFC 2616 标准）。
     *
     * @param request HTTP 请求
     */
    public static Map<String, String> getHeaderMapValues(HttpServletRequest request) {
        Enumeration<String> enumeration = request.getHeaderNames();
        Map<String, String> headerMapValues = new HashMap<>(20);
        if (enumeration != null) {
            while (enumeration.hasMoreElements()) {
                String name = enumeration.nextElement();
                Enumeration<String> values = request.getHeaders(name);
                if (values != null && values.hasMoreElements()) {
                    String first = values.nextElement();
                    if (values.hasMoreElements()) {
                        StringBuilder sb = new StringBuilder(first);
                        while (values.hasMoreElements()) {
                            sb.append(", ").append(values.nextElement());
                        }
                        headerMapValues.put(name, sb.toString());
                    } else {
                        headerMapValues.put(name, first);
                    }
                }
            }
        }
        return headerMapValues;
    }

    /**
     * 获取 request
     *
     * @throws IllegalStateException 当前线程不在 HTTP 请求上下文中时抛出
     */
    public HttpServletRequest getRequest() {
        return getRequestAttributes().getRequest();
    }

    /**
     * 获取 response（可能返回 null，如拦截器 preHandle 阶段）
     */
    public HttpServletResponse getResponse() {
        return getRequestAttributes().getResponse();
    }

    /**
     * 获取 session（不存在时自动创建）
     */
    public HttpSession getSession() {
        return getSession(true);
    }

    /**
     * 获取 session
     *
     * @param create 不存在时是否自动创建
     */
    public HttpSession getSession(boolean create) {
        return getRequestAttributes().getRequest().getSession(create);
    }

    /**
     * 获取 ServletContext
     */
    public ServletContext getServletContext() {
        return getRequest().getServletContext();
    }

    /**
     * 获取 request 属性
     *
     * @param name 属性名
     */
    public Object getAttribute(String name) {
        Objects.requireNonNull(name, "attribute name must not be null");
        return getRequestAttributes().getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * 添加 request 属性
     *
     * @param name   属性名
     * @param object 属性值
     */
    public void setAttribute(String name, Object object) {
        Objects.requireNonNull(name, "attribute name must not be null");
        getRequestAttributes().setAttribute(name, object, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * 获取 session 字符串（不存在时返回 null）
     *
     * @param name session key
     */
    public String getSessionAttribute(String name) {
        Object value = getSessionAttributeObj(name);
        return value == null ? null : value.toString();
    }

    /**
     * 获取 session 中对象
     *
     * @param name session key
     */
    public Object getSessionAttributeObj(String name) {
        Objects.requireNonNull(name, "session attribute name must not be null");
        return getRequestAttributes().getAttribute(name, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * 移除 session 值
     *
     * @param name session key
     */
    public void removeSessionAttribute(String name) {
        Objects.requireNonNull(name, "session attribute name must not be null");
        getRequestAttributes().removeAttribute(name, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * 设置 session 值
     *
     * @param name   session key
     * @param object session value
     */
    public void setSessionAttribute(String name, Object object) {
        Objects.requireNonNull(name, "session attribute name must not be null");
        getRequestAttributes().setAttribute(name, object, RequestAttributes.SCOPE_SESSION);
    }

}
