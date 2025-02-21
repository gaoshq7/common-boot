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
     * @Description : 全局获取请求对象
     * @Param : []
     * @Return : org.springframework.web.context.request.ServletRequestAttributes
     * @Author : gsq
     * @Date : 14:12
     * @note : An art cell ! 
    **/
    public static ServletRequestAttributes getRequestAttributes() {
        ServletRequestAttributes servletRequestAttributes = tryGetRequestAttributes();
        Objects.requireNonNull(servletRequestAttributes);
        return servletRequestAttributes;
    }

    /**
     * @Description : 获取ServletRequestAttributes
     * @Param : []
     * @Return : org.springframework.web.context.request.ServletRequestAttributes
     * @Author : gsq
     * @Date : 14:52
     * @note : An art cell ! 
    **/
    public static ServletRequestAttributes tryGetRequestAttributes() {
        RequestAttributes attributes = null;
        try {
            attributes = RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException e) {
            // TODO: handle exception
        }
        if (attributes == null) {
            return null;
        }
        if (attributes instanceof ServletRequestAttributes) {
            return (ServletRequestAttributes) attributes;
        }
        return null;
    }

    /**
     * @Description : 获取客户端的ip地址
     * @Param : []
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 17:01
     * @note : An art cell ! 
    **/
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
     * @Description : 提取request中的所有harder信息
     * @Param : [request]
     * @Return : java.util.Map<java.lang.String,java.lang.String>
     * @Author : gsq
     * @Date : 14:11
     * @note : An art cell ! 
    **/
    public static Map<String, String> getHeaderMapValues(HttpServletRequest request) {
        Enumeration<String> enumeration = request.getHeaderNames();
        Map<String, String> headerMapValues = new HashMap<>(20);
        if (enumeration != null) {
            for (; enumeration.hasMoreElements(); ) {
                String name = enumeration.nextElement();
                headerMapValues.put(name, request.getHeader(name));
            }
        }
        return headerMapValues;
    }

    /**
     * @Description : 获取request
     * @Param : []
     * @Return : javax.servlet.http.HttpServletRequest
     * @Author : gsq
     * @Date : 14:11
     * @note : An art cell ! 
    **/
    public HttpServletRequest getRequest() {
        HttpServletRequest request = getRequestAttributes().getRequest();
        Objects.requireNonNull(request, "request null");
        return request;
    }

    /**
     * @Description : 获取response
     * @Param : []
     * @Return : javax.servlet.http.HttpServletResponse
     * @Author : gsq
     * @Date : 16:58
     * @note : An art cell ! 
    **/
    public HttpServletResponse getResponse() {
        HttpServletResponse response = getRequestAttributes().getResponse();
        Objects.requireNonNull(response, "response null");
        return response;
    }

    /**
     * @Description : 获取session
     * @Param : []
     * @Return : javax.servlet.http.HttpSession
     * @Author : gsq
     * @Date : 16:58
     * @note : An art cell ! 
    **/
    public HttpSession getSession() {
        HttpSession session = getRequestAttributes().getRequest().getSession();
        if (session == null) {
            session = BaseInterceptor.getSession();
        }
        Objects.requireNonNull(session, "session null");
        return session;
    }

    /**
     * @Description : 获取ServletContext
     * @Param : []
     * @Return : javax.servlet.ServletContext
     * @Author : gsq
     * @Date : 16:59
     * @note : An art cell ! 
    **/
    public ServletContext getServletContext() {
        return getRequest().getServletContext();
    }

    /**
     * @Description : 获取request属性
     * @Param : [name]
     * @Return : java.lang.Object
     * @Author : gsq
     * @Date : 16:59
     * @note : An art cell ! 
    **/
    public Object getAttribute(String name) {
        return getRequestAttributes().getAttribute(name, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * @Description : 添加request属性
     * @Param : [name, object]
     * @Return : void
     * @Author : gsq
     * @Date : 17:00
     * @note : An art cell ! 
    **/
    public void setAttribute(String name, Object object) {
        getRequestAttributes().setAttribute(name, object, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * @Description : 获取session字符串
     * @Param : [name]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 16:59
     * @note : An art cell ! 
    **/
    public String getSessionAttribute(String name) {
        return Objects.toString(getSessionAttributeObj(name), "");
    }

    /**
     * @Description : 获取session中对象
     * @Param : [name]
     * @Return : java.lang.Object
     * @Author : gsq
     * @Date : 17:00
     * @note : An art cell ! 
    **/
    public Object getSessionAttributeObj(String name) {
        return getRequestAttributes().getAttribute(name, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * @Description : 移除session值
     * @Param : [name]
     * @Return : void
     * @Author : gsq
     * @Date : 17:01
     * @note : An art cell ! 
    **/
    public void removeSessionAttribute(String name) {
        getRequestAttributes().removeAttribute(name, RequestAttributes.SCOPE_SESSION);
    }

    /**
     * @Description : 设置session字符串
     * @Param : [name, object]
     * @Return : void
     * @Author : gsq
     * @Date : 17:01
     * @note : An art cell ! 
    **/
    public void setSessionAttribute(String name, Object object) {
        getRequestAttributes().setAttribute(name, object, RequestAttributes.SCOPE_SESSION);
    }

}
