package cn.gsq.common.controller;

import cn.gsq.common.controller.multipart.MultipartFileBuilder;
import cn.gsq.common.interceptor.BaseCallbackController;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.HttpUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Project : galaxy
 * Class : cn.gsq.common.controller.AbstractController
 *
 * @author : gsq
 * @date : 2021-09-10 14:40
 * @note : It's not technology, it's art !
 **/
public abstract class AbstractController extends BaseCallbackController {

    /**
     * MultipartHttpServletRequest 缓存 key（使用 request attribute 替代 ThreadLocal）
     */
    private static final String MULTIPART_REQUEST_ATTR = AbstractController.class.getName() + ".MULTIPART_REQUEST";

    /**
     * 获取客户端IP地址（优先读取 X-Forwarded-For 等代理头）。
     * <p>注意：代理头可被客户端伪造，仅在已部署反向代理（且代理已剥离伪造头）时使用。
     * 若需要不可伪造的 IP，请使用 {@link #getRemoteIp()}。
     */
    protected String getIp() {
        return ServletUtil.getClientIP(getRequest());
    }

    /**
     * 获取客户端真实网络地址（TCP 层，不可伪造）。
     */
    protected String getRemoteIp() {
        return getRequest().getRemoteAddr();
    }

    /**
     * 判断是否为 Ajax 请求。
     * <p>同时检查 X-Requested-With 头和 Accept 头，兼容 Fetch API / Axios / jQuery。
     */
    protected boolean isAjax() {
        String requestedWith = getRequest().getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return true;
        }
        String accept = getRequest().getHeader("Accept");
        return accept != null && accept.contains("application/json");
    }

    /**
     * 判断当前请求是否为 GET
     */
    protected boolean isGet() {
        return "GET".equalsIgnoreCase(getRequest().getMethod());
    }

    /**
     * 判断当前请求是否为 POST
     */
    protected boolean isPost() {
        return "POST".equalsIgnoreCase(getRequest().getMethod());
    }

    /**
     * 判断当前请求是否为指定的 HTTP 方法
     *
     * @param method HTTP 方法名（如 "PUT"、"DELETE"、"PATCH"）
     */
    protected boolean isMethod(String method) {
        return method != null && method.equalsIgnoreCase(getRequest().getMethod());
    }

    /**
     * 获取 User-Agent（不存在时返回 null）
     */
    protected String getUserAgent() {
        return getRequest().getHeader("User-Agent");
    }

    /**
     * 获取 User-Agent（不存在时返回 def）
     *
     * @param def 默认值
     */
    protected String getUserAgent(String def) {
        String value = getRequest().getHeader("User-Agent");
        return value == null ? def : value;
    }

    /**
     * 获取指定的 header 值（不存在时返回 null）
     *
     * @param name header 名称
     */
    protected String getHeader(String name) {
        Objects.requireNonNull(name, "header name must not be null");
        return getRequest().getHeader(name);
    }

    /**
     * 获取指定的 header 值（不存在时返回 def）
     *
     * @param name header 名称
     * @param def  默认值
     */
    protected String getHeader(String name, String def) {
        Objects.requireNonNull(name, "header name must not be null");
        String value = getRequest().getHeader(name);
        return value == null ? def : value;
    }

    /**
     * 根据名称获取 cookie 的值（不存在时返回 null）
     *
     * @param name cookie 名称
     */
    protected String getCookieValue(String name) {
        Objects.requireNonNull(name, "cookie name must not be null");
        Cookie cookie = ServletUtil.getCookie(getRequest(), name);
        return cookie == null ? null : cookie.getValue();
    }

    /**
     * 根据名称获取 cookie 的值（不存在时返回 def）
     *
     * @param name cookie 名称
     * @param def  默认值
     */
    protected String getCookieValue(String name, String def) {
        Objects.requireNonNull(name, "cookie name must not be null");
        Cookie cookie = ServletUtil.getCookie(getRequest(), name);
        return cookie == null ? def : cookie.getValue();
    }

    /**
     * 根据名称获取参数值（不存在时返回 null）
     *
     * @param name 参数名称
     */
    protected String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * 获取数组参数（参数不存在时返回 null）。
     *
     * @param name 参数名称
     */
    protected String[] getParameters(String name) {
        Objects.requireNonNull(name, "parameter name must not be null");
        return getRequest().getParameterValues(name);
    }

    /**
     * 获取参数值，缺省为 def（参数不存在时返回 def，空串视为合法值原样返回）
     *
     * @param name 参数名称
     * @param def  默认值
     */
    protected String getParameter(String name, String def) {
        Objects.requireNonNull(name, "parameter name must not be null");
        String value = getRequest().getParameter(name);
        return value == null ? def : value;
    }

    /**
     * 获取 int 类型参数值缺省为 def。
     * <p>参数不存在或值为空串时返回 def；非法值（如 "abc"）时抛异常。
     *
     * @param name 参数名称
     * @param def  默认值
     */
    protected int getParameterInt(String name, int def) {
        String value = getRequest().getParameter(name);
        if (value == null || value.isEmpty()) {
            return def;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的整数: " + value);
        }
    }

    /**
     * 获取 Long 类型参数值缺省为 def。
     * <p>参数不存在或值为空串时返回 def；非法值（如 "abc"）时抛异常。
     *
     * @param name 参数名称
     * @param def  默认值
     */
    protected long getParameterLong(String name, long def) {
        String value = getRequest().getParameter(name);
        if (value == null || value.isEmpty()) {
            return def;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的长整数: " + value);
        }
    }

    /**
     * 获取 boolean 类型参数值缺省为 def。
     * <p>支持的值为：true/false/1/0/yes/no/on/off（不区分大小写）。
     * <p>参数不存在或值为空串时返回 def；非法值（如 "abc"）时抛异常。
     *
     * @param name 参数名称
     * @param def  默认值
     */
    protected boolean getParameterBool(String name, boolean def) {
        String value = getRequest().getParameter(name);
        if (value == null || value.isEmpty()) {
            return def;
        }
        Boolean result = Convert.toBool(value);
        if (result == null) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的布尔值: " + value);
        }
        return result;
    }

    /**
     * 获取来源的 url 参数。
     * <p>注意：Referer 可被客户端伪造，不可作为安全决策依据。
     */
    protected Map<String, String> getRefererParameter() {
        String referer = getHeader(HttpHeaders.REFERER);
        if (StrUtil.isBlank(referer)) {
            return Collections.emptyMap();
        }
        return HttpUtil.decodeParamMap(referer, CharsetUtil.charset(CharsetUtil.UTF_8));
    }

    /**
     * 获取完整的请求 URL（含 scheme、host、port、path）。
     * <p>注意：在反向代理（Nginx/SLB）后面，此方法返回的是内部地址
     * （如 http://127.0.0.1:8080/xxx），而非外部公网地址。
     */
    protected String getRequestUrl() {
        return getRequest().getRequestURL().toString();
    }

    /**
     * 获取请求 URI（不含 scheme、host、port，含 path）
     */
    protected String getRequestUri() {
        return getRequest().getRequestURI();
    }

    /**
     * 获取表单数据到实体中（类型转换失败时抛出异常）。
     *
     * @param tClass 目标类型
     */
    protected <T> T getObject(Class<T> tClass) {
        return getObject(tClass, false);
    }

    /**
     * 获取表单数据到实体中。
     *
     * @param tClass      目标类型
     * @param ignoreError 是否忽略转换错误（true 时类型不匹配字段保持默认值，可能导致数据丢失）
     */
    protected <T> T getObject(Class<T> tClass, boolean ignoreError) {
        Objects.requireNonNull(tClass, "tClass must not be null");
        return ServletUtil.toBean(getRequest(), tClass, ignoreError);
    }

    /**
     * 获取请求头中的所有信息
     */
    protected Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(new HashMap<>(getHeaderMapValues(getRequest())));
    }

    /**
     * 获取所有参数
     */
    protected Map<String, String[]> getParametersMap() {
        return Collections.unmodifiableMap(new HashMap<>(getRequest().getParameterMap()));
    }

    /*---------- 文件上传相关函数 ----------*/

    /**
     * 释放资源（保留此方法以兼容现有拦截器，内部已使用 request attribute 替代 ThreadLocal）。
     */
    public static void clearResources() {
        // 已由 request attribute 替代 ThreadLocal，无需手动清理
    }

    /**
     * 获取对应的 MultipartHttpServletRequest
     */
    protected MultipartHttpServletRequest getMultiRequest() {
        HttpServletRequest request = getRequest();
        if (request instanceof MultipartHttpServletRequest) {
            return (MultipartHttpServletRequest) request;
        }
        if (isMultipartRequest(request)) {
            MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request.getAttribute(MULTIPART_REQUEST_ATTR);
            if (multipartHttpServletRequest != null) {
                return multipartHttpServletRequest;
            }
            multipartHttpServletRequest = new StandardMultipartHttpServletRequest(request);
            request.setAttribute(MULTIPART_REQUEST_ATTR, multipartHttpServletRequest);
            return multipartHttpServletRequest;
        }
        throw new IllegalArgumentException("此次访问没有对应的MultipartHttpServletRequest ...");
    }

    /**
     * 判断是否为 multipart 请求
     */
    private static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 判断是否存在文件（非 multipart 请求返回 false，格式错误的 multipart 请求也返回 false）
     */
    protected boolean hasFile() {
        HttpServletRequest request = getRequest();
        if (!isMultipartRequest(request)) {
            return false;
        }
        try {
            Map<String, MultipartFile> fileMap = getMultiRequest().getFileMap();
            return fileMap != null && !fileMap.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建多文件上传对象
     */
    protected MultipartFileBuilder createMultipart() {
        return new MultipartFileBuilder(getMultiRequest());
    }

}
