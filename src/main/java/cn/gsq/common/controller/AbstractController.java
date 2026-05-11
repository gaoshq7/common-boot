package cn.gsq.common.controller;

import cn.gsq.common.DefaultSystemLog;
import cn.gsq.common.controller.multipart.MultipartFileBuilder;
import cn.gsq.common.interceptor.BaseCallbackController;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    /*---------- 客户端信息 ----------*/

    /**
     * 获取客户端 IP 地址（优先读取 X-Forwarded-For 等代理头）。
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
     * 判断当前请求的 TCP 层客户端 IP 是否为本机回环地址（127.0.0.0/8、::1）。
     * <p>用 {@link #getRemoteIp()} 而非 {@link #getIp()}——后者基于可伪造的代理头，不可用于安全决策。
     */
    protected boolean isLocalIp() {
        return isLocalIp(getRemoteIp());
    }

    /**
     * 判断指定 IP 是否为本机回环地址。
     *
     * @param ip 待检查的 IP
     */
    protected static boolean isLocalIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        return ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    /**
     * 判断当前请求的 TCP 层客户端 IP 是否为内网地址（含本机回环、10/8、172.16/12、192.168/16、169.254/16）。
     * <p>常用于限制管理后台接口仅内网可访问。<b>不要</b>用 {@link #getIp()} 做这种判断，因为它依赖可伪造的代理头。
     */
    protected boolean isInternalIp() {
        return isInternalIp(getRemoteIp());
    }

    /**
     * 判断指定 IP 是否为内网地址（仅支持 IPv4 私有/链路本地段；本机回环也算）。
     *
     * @param ip 待检查的 IP
     */
    protected static boolean isInternalIp(String ip) {
        if (StrUtil.isBlank(ip)) {
            return false;
        }
        if (isLocalIp(ip)) {
            return true;
        }
        try {
            return NetUtil.isInnerIP(ip);
        } catch (Exception e) {
            return false;
        }
    }

    /*---------- 请求方法判定 ----------*/

    /**
     * 获取当前请求的 HTTP 方法（如 "GET"、"POST"、"DELETE"）。
     */
    protected String getRequestMethod() {
        return getRequest().getMethod();
    }

    /**
     * 判断当前请求是否为 GET。
     */
    protected boolean isGet() {
        return "GET".equalsIgnoreCase(getRequest().getMethod());
    }

    /**
     * 判断当前请求是否为 POST。
     */
    protected boolean isPost() {
        return "POST".equalsIgnoreCase(getRequest().getMethod());
    }

    /**
     * 判断当前请求是否为指定的 HTTP 方法。
     *
     * @param method HTTP 方法名（如 "PUT"、"DELETE"、"PATCH"）
     */
    protected boolean isMethod(String method) {
        return method != null && method.equalsIgnoreCase(getRequest().getMethod());
    }

    /**
     * 限制当前请求只能使用指定的 HTTP 方法之一，否则抛 {@link IllegalStateException}。
     * <p>更"Spring-y" 的做法是用 {@code @RequestMapping(method = ...)} 限定；
     * 本方法用于同一 mapping 内手动分流的场景。
     *
     * @param allowed 允许的 HTTP 方法名（不区分大小写）
     */
    protected void requireMethod(String... allowed) {
        Objects.requireNonNull(allowed, "allowed must not be null");
        String actual = getRequest().getMethod();
        for (String m : allowed) {
            if (m != null && m.equalsIgnoreCase(actual)) {
                return;
            }
        }
        throw new IllegalStateException("不支持的请求方法: " + actual + "，允许: " + Arrays.toString(allowed));
    }

    /**
     * 判断是否为 Ajax 请求（仅依据 X-Requested-With: XMLHttpRequest）。
     * <p>⚠️ 不要用 {@code Accept: application/json} 判断 ajax——大量非浏览器客户端
     * （Postman/curl/REST 工具）也会带此头。判断"请求体是 JSON"或"客户端期望 JSON"请改用 {@link #isJson()}。
     */
    protected boolean isAjax() {
        return "XMLHttpRequest".equalsIgnoreCase(getRequest().getHeader("X-Requested-With"));
    }

    /*---------- User-Agent ----------*/

    /**
     * 获取 User-Agent（不存在时返回 null）。
     */
    protected String getUserAgent() {
        return getRequest().getHeader("User-Agent");
    }

    /**
     * 解析当前请求的 User-Agent。
     * <p>无 UA 头时返回的 {@link UserAgent} 各字段为 null/空，调用方需判空使用。
     */
    protected UserAgent parseUserAgent() {
        return UserAgentUtil.parse(getUserAgent());
    }

    /**
     * 判断当前请求是否来自移动端（基于 UA 启发式判断，不一定 100% 准确）。
     */
    protected boolean isMobile() {
        UserAgent ua = UserAgentUtil.parse(getUserAgent());
        return ua != null && ua.isMobile();
    }

    /*---------- Header ----------*/

    /**
     * 获取指定的 header 值（不存在时返回 null）。
     *
     * @param name header 名称
     */
    protected String getHeader(String name) {
        Objects.requireNonNull(name, "header name must not be null");
        return getRequest().getHeader(name);
    }

    /**
     * 获取指定的 header 值（不存在时返回 def）。
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
     * 获取请求头中的所有信息（不可修改副本）。
     */
    protected Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(getHeaderMapValues(getRequest()));
    }

    /*---------- Cookie ----------*/

    /**
     * 根据名称获取 cookie 的值（不存在时返回 null）。
     *
     * @param name cookie 名称
     */
    protected String getCookieValue(String name) {
        Objects.requireNonNull(name, "cookie name must not be null");
        Cookie cookie = ServletUtil.getCookie(getRequest(), name);
        return cookie == null ? null : cookie.getValue();
    }

    /**
     * 根据名称获取 cookie 的值（不存在时返回 def）。
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
     * 写入会话级 Cookie（关闭浏览器即失效，默认 path=/、HttpOnly=true、Secure=false）。
     *
     * @param name  cookie 名
     * @param value cookie 值
     */
    protected void setCookie(String name, String value) {
        setCookie(name, value, -1);
    }

    /**
     * 写入指定生存秒数的 Cookie（默认 path=/、HttpOnly=true、Secure=false）。
     *
     * @param name   cookie 名
     * @param value  cookie 值
     * @param maxAge 生存秒数：-1=会话级，0=立即过期（等同删除），&gt;0=N 秒后过期
     */
    protected void setCookie(String name, String value, int maxAge) {
        setCookie(name, value, maxAge, "/", true, false);
    }

    /**
     * 写入 Cookie（完全参数）。
     * <p>建议生产环境 HTTPS 下显式设 secure=true，避免 cookie 在明文连接中被嗅探。
     *
     * @param name     cookie 名
     * @param value    cookie 值
     * @param maxAge   生存秒数
     * @param path     作用路径（一般 "/"）
     * @param httpOnly true 时 JS 无法读取，推荐 true 以减灾 XSS
     * @param secure   true 时仅 HTTPS 下回传
     */
    protected void setCookie(String name, String value, int maxAge, String path, boolean httpOnly, boolean secure) {
        Objects.requireNonNull(name, "cookie name must not be null");
        Cookie cookie = new Cookie(name, value);
        cookie.setMaxAge(maxAge);
        if (path != null) {
            cookie.setPath(path);
        }
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(secure);
        requireResponse().addCookie(cookie);
    }

    /**
     * 删除 Cookie（默认 path=/）。
     * <p>⚠️ 删除 Cookie 时浏览器要求 name+path 与写入时一致，否则不会被删。
     * 若写入时用了非根路径，请改用 {@link #removeCookie(String, String)}。
     *
     * @param name cookie 名
     */
    protected void removeCookie(String name) {
        removeCookie(name, "/");
    }

    /**
     * 删除指定 path 下的 Cookie。
     *
     * @param name cookie 名
     * @param path 写入时使用的 path
     */
    protected void removeCookie(String name, String path) {
        Objects.requireNonNull(name, "cookie name must not be null");
        Cookie cookie = new Cookie(name, "");
        cookie.setMaxAge(0);
        if (path != null) {
            cookie.setPath(path);
        }
        requireResponse().addCookie(cookie);
    }

    /*---------- 参数获取 ----------*/

    /**
     * 根据名称获取参数值（不存在时返回 null）。
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
     * 获取参数值，缺省为 def（参数不存在时返回 def，空串视为合法值原样返回）。
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
     * <p>参数不存在或值为空串时返回 def；非法值（如 "abc"）时抛 {@link IllegalArgumentException}。
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
     * 获取 long 类型参数值缺省为 def。
     * <p>参数不存在或值为空串时返回 def；非法值（如 "abc"）时抛 {@link IllegalArgumentException}。
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
     * <p>参数不存在或值为空串时返回 def；非法值（如 "abc"）时抛 {@link IllegalArgumentException}。
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
     * 获取必填字符串参数（缺失或空白时抛 {@link IllegalArgumentException}）。
     *
     * @param name 参数名称
     */
    protected String getParameterRequired(String name) {
        Objects.requireNonNull(name, "parameter name must not be null");
        String value = getRequest().getParameter(name);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("参数 " + name + " 不能为空");
        }
        return value;
    }

    /**
     * 获取必填 int 参数（缺失/空白/非法整数时抛 {@link IllegalArgumentException}）。
     */
    protected int getParameterIntRequired(String name) {
        String value = getParameterRequired(name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的整数: " + value);
        }
    }

    /**
     * 获取必填 long 参数（缺失/空白/非法长整数时抛 {@link IllegalArgumentException}）。
     */
    protected long getParameterLongRequired(String name) {
        String value = getParameterRequired(name);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的长整数: " + value);
        }
    }

    /**
     * 获取 {@link LocalDate} 参数（默认 pattern=yyyy-MM-dd；缺失/空白返回 def；非法格式抛 {@link IllegalArgumentException}）。
     *
     * @param name 参数名称
     * @param def  默认值
     */
    protected LocalDate getParameterDate(String name, LocalDate def) {
        return getParameterDate(name, "yyyy-MM-dd", def);
    }

    /**
     * 获取 {@link LocalDate} 参数（自定义 pattern）。
     */
    protected LocalDate getParameterDate(String name, String pattern, LocalDate def) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        String value = getRequest().getParameter(name);
        if (StrUtil.isBlank(value)) {
            return def;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的日期(" + pattern + "): " + value);
        }
    }

    /**
     * 获取 {@link LocalDateTime} 参数(默认 pattern=yyyy-MM-dd HH:mm:ss)。
     */
    protected LocalDateTime getParameterDateTime(String name, LocalDateTime def) {
        return getParameterDateTime(name, "yyyy-MM-dd HH:mm:ss", def);
    }

    /**
     * 获取 {@link LocalDateTime} 参数（自定义 pattern）。
     */
    protected LocalDateTime getParameterDateTime(String name, String pattern, LocalDateTime def) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        String value = getRequest().getParameter(name);
        if (StrUtil.isBlank(value)) {
            return def;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的日期时间(" + pattern + "): " + value);
        }
    }

    /**
     * 获取枚举参数（按 {@link Enum#name()} 不区分大小写匹配；不匹配抛 {@link IllegalArgumentException}）。
     *
     * @param name      参数名称
     * @param enumClass 枚举类型
     * @param def       默认值
     */
    protected <E extends Enum<E>> E getParameterEnum(String name, Class<E> enumClass, E def) {
        Objects.requireNonNull(enumClass, "enumClass must not be null");
        String value = getRequest().getParameter(name);
        if (StrUtil.isBlank(value)) {
            return def;
        }
        for (E e : enumClass.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("参数 " + name + " 不是有效的 " + enumClass.getSimpleName() + ": " + value);
    }

    /**
     * 获取 {@link BigDecimal} 参数（保留原始精度；非法格式抛 {@link IllegalArgumentException}）。
     */
    protected BigDecimal getParameterDecimal(String name, BigDecimal def) {
        String value = getRequest().getParameter(name);
        if (value == null || value.isEmpty()) {
            return def;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 " + name + " 不是有效的数字: " + value);
        }
    }

    /**
     * 获取所有参数。
     * <p>返回不可修改的 map 副本，且值数组也是克隆副本——外部修改不会影响 servlet 容器内部状态。
     */
    protected Map<String, String[]> getParametersMap() {
        Map<String, String[]> source = getRequest().getParameterMap();
        Map<String, String[]> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, String[]> entry : source.entrySet()) {
            String[] values = entry.getValue();
            copy.put(entry.getKey(), values == null ? null : values.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    /*---------- 请求体读取 ----------*/

    /**
     * 读取请求体原始字符串。
     * <p><b>⚠️ 请求体输入流仅能消费一次</b>：若已被 {@code @RequestBody} / filter / 拦截器 / 其他读取消耗过，
     * 再调本方法会抛 {@link IORuntimeException}。常见踩坑：filter 中打日志读了 body，controller 再调本方法即失败。
     * <p>本方法对 multipart 请求不适用——表单字段会被 servlet 容器从流中读出，body 通常变空。
     *
     * @throws IORuntimeException body 已被消费或 IO 错误
     */
    protected String getBody() {
        return ServletUtil.getBody(getRequest());
    }

    /**
     * 读取请求体并解析为指定 bean（基于 hutool {@link JSONUtil#toBean(String, Class)}）。
     * <p>body 为空时返回 null。
     *
     * @param cls 目标类型
     */
    protected <T> T getBodyJson(Class<T> cls) {
        Objects.requireNonNull(cls, "cls must not be null");
        String body = getBody();
        if (StrUtil.isBlank(body)) {
            return null;
        }
        return JSONUtil.toBean(body, cls);
    }

    /**
     * 读取请求体并解析为 {@link JSONObject}（要求 body 是 JSON 对象，非数组）。
     * <p>body 为空时返回 null；body 为 JSON 数组或非法 JSON 时抛 hutool 异常。
     */
    protected JSONObject getBodyJson() {
        String body = getBody();
        if (StrUtil.isBlank(body)) {
            return null;
        }
        return JSONUtil.parseObj(body);
    }

    /*---------- 表单转 Bean ----------*/

    /**
     * 将请求参数（query + form）按字段名自动绑定到 bean。
     *
     * <p><b>⚠️ 安全警告 — Mass Assignment 风险</b>：本方法基于字段名自动绑定，<b>不做白名单</b>。
     * 客户端可传入任意参数覆盖 bean 字段：若 bean 含敏感字段（isAdmin / role / balance），
     * 攻击者能通过额外参数提权或篡改数据。<b>禁止</b>直接绑定持久层 entity，必须使用仅含必要字段的 DTO。
     *
     * <p><b>能力局限</b>：底层 hutool {@code ServletUtil.toBean} 对嵌套对象、List 字段、复杂泛型支持有限。
     * 复杂场景请改用 Spring MVC 的 {@code @RequestBody} + Jackson。
     *
     * <p>类型转换失败时抛 {@link IllegalArgumentException}。
     *
     * @param tClass 目标 DTO 类型
     */
    protected <T> T getObject(Class<T> tClass) {
        return getObject(tClass, false);
    }

    /**
     * 同 {@link #getObject(Class)}，可选择忽略类型转换错误。
     *
     * <p>同样存在 Mass Assignment 风险，详见 {@link #getObject(Class)} javadoc。
     *
     * @param tClass      目标类型
     * @param ignoreError true 时类型不匹配字段保持默认值（⚠️ 数据可能静默丢失）
     */
    protected <T> T getObject(Class<T> tClass, boolean ignoreError) {
        Objects.requireNonNull(tClass, "tClass must not be null");
        return ServletUtil.toBean(getRequest(), tClass, ignoreError);
    }

    /*---------- URL 信息 ----------*/

    /**
     * 获取完整的请求 URL（含 scheme、host、port、path）。
     * <p>注意：在反向代理（Nginx/SLB）后面，此方法返回的是内部地址（如 http://127.0.0.1:8080/xxx），
     * 而非外部公网地址。
     */
    protected String getRequestUrl() {
        return getRequest().getRequestURL().toString();
    }

    /**
     * 获取请求 URI（不含 scheme、host、port，含 path）。
     */
    protected String getRequestUri() {
        return getRequest().getRequestURI();
    }

    /**
     * 获取 servlet context path（部署上下文路径，如 "/app"；根部署时为 ""）。
     */
    protected String getContextPath() {
        return getRequest().getContextPath();
    }

    /**
     * 获取 query string（URL 中 "?" 之后部分，不含 "?"；无 query 时返回 null）。
     */
    protected String getQueryString() {
        return getRequest().getQueryString();
    }

    /**
     * 获取应用基础 URL，形如 "scheme://host:port/contextPath"，不含末尾斜杠。
     * <p>注意：反向代理（Nginx/SLB）后面返回的是内部地址。若需要外部 URL，
     * 业务方应改读 X-Forwarded-Host / X-Forwarded-Proto 并自行拼接。
     */
    protected String getBaseUrl() {
        HttpServletRequest request = getRequest();
        StringBuilder sb = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(request.getScheme()) && port == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && port == 443);
        if (!defaultPort) {
            sb.append(':').append(port);
        }
        return sb.append(request.getContextPath()).toString();
    }

    /**
     * 获取 Referer 头（不存在返回 null）。
     * <p>Referer 可被客户端伪造，不可作为安全决策依据。
     */
    protected String getReferer() {
        return getRequest().getHeader(HttpHeaders.REFERER);
    }

    /**
     * 获取 Origin 头（不存在返回 null）。CORS 场景常用。
     */
    protected String getOrigin() {
        return getRequest().getHeader(HttpHeaders.ORIGIN);
    }

    /*---------- Content-Type 判定 ----------*/

    /**
     * 获取请求的 Content-Type（不存在返回 null）。
     */
    protected String getContentType() {
        return getRequest().getContentType();
    }

    /**
     * 判断当前请求 Content-Type 是否以指定前缀开头（不区分大小写）。
     *
     * @param prefix 前缀，如 "application/json"
     */
    protected boolean isContentType(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        String contentType = getRequest().getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(prefix.toLowerCase());
    }

    /**
     * 判断当前请求是否为 JSON（Content-Type 以 application/json 开头）。
     * <p>比 {@link #isAjax()} 更可靠的"客户端期望/提交 JSON"判定。
     */
    protected boolean isJson() {
        return isContentType("application/json");
    }

    /**
     * 判断当前请求是否为表单提交（Content-Type=application/x-www-form-urlencoded）。
     */
    protected boolean isFormUrlEncoded() {
        return isContentType("application/x-www-form-urlencoded");
    }

    /**
     * 判断当前请求是否为 multipart（文件上传）。
     */
    protected boolean isMultipart() {
        return isMultipartRequest(getRequest());
    }

    /*---------- 响应输出 ----------*/

    /**
     * 直接向响应写入 JSON（Content-Type=application/json;charset=UTF-8）。
     *
     * <p><b>⚠️ 调用约束</b>：本方法直接操作 response writer，调用后 Controller 方法<b>必须返回 void</b>
     * （或类型为 {@code ResponseEntity} 但返回 {@code null}），否则 Spring 会对返回值二次序列化，
     * 导致响应体重复或损坏。
     *
     * <p>⚠️ data 会被 JSONUtil 序列化；若 data 本身已是 JSON 字符串会被再次 escape，请改用 {@link #writeText(String)}。
     *
     * @param data 任意对象（含 null，会输出字面量 "null"）
     */
    protected void writeJson(Object data) {
        write("application/json;charset=UTF-8", JSONUtil.toJsonStr(data));
    }

    /**
     * 直接向响应写入纯文本（Content-Type=text/plain;charset=UTF-8）。
     * <p>同 {@link #writeJson(Object)}：调用后方法必须返回 void。
     *
     * @param text 文本内容（null 视为空串）
     */
    protected void writeText(String text) {
        write("text/plain;charset=UTF-8", text == null ? "" : text);
    }

    /**
     * 直接向响应写入 HTML（Content-Type=text/html;charset=UTF-8）。
     * <p>⚠️ 调用方需自行做 HTML 转义防 XSS；切勿把未净化的用户输入拼进来。
     * <p>同 {@link #writeJson(Object)}：调用后方法必须返回 void。
     *
     * @param html HTML 内容（null 视为空串）
     */
    protected void writeHtml(String html) {
        write("text/html;charset=UTF-8", html == null ? "" : html);
    }

    /**
     * 通用响应写入：设置 Content-Type 并把文本写到 response writer。
     *
     * @param contentType 含 charset 的 Content-Type
     * @param content     文本内容
     */
    private void write(String contentType, String content) {
        HttpServletResponse response = requireResponse();
        response.setContentType(contentType);
        try {
            response.getWriter().write(content);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    /**
     * 发送 302 重定向。
     * <p>同 {@link #writeJson(Object)}：调用后方法必须返回 void，否则 Spring 会在重定向之后再写一次响应。
     *
     * @param url 跳转地址（相对/绝对均可）
     */
    protected void redirect(String url) {
        Objects.requireNonNull(url, "redirect url must not be null");
        try {
            requireResponse().sendRedirect(url);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    /**
     * 设置响应状态码（不立即发送响应体，后续仍可写入响应内容）。
     *
     * @param code HTTP 状态码
     */
    protected void status(int code) {
        requireResponse().setStatus(code);
    }

    /**
     * 发送错误响应（立即提交，跳转到 servlet 容器的 error page）。
     * <p>同 {@link #writeJson(Object)}：调用后方法必须返回 void。
     *
     * @param code    HTTP 状态码（如 400、404、500）
     * @param message 错误说明
     */
    protected void sendError(int code, String message) {
        try {
            requireResponse().sendError(code, message);
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    /**
     * 设置标准的"禁止缓存"响应头（Cache-Control、Pragma、Expires），适用于敏感页/动态数据。
     */
    protected void setNoCache() {
        HttpServletResponse response = requireResponse();
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }

    /**
     * 获取当前请求的 response，不存在（preHandle 或非 web 线程）时抛 {@link IllegalStateException}。
     */
    private HttpServletResponse requireResponse() {
        HttpServletResponse response = getResponse();
        if (response == null) {
            throw new IllegalStateException("当前请求上下文不存在 response（preHandle 阶段或非 web 请求线程）");
        }
        return response;
    }

    /*---------- 文件下载 ----------*/

    /**
     * 下载文件（保留原文件名）。
     * <p>同 {@link #writeJson(Object)}：调用后方法必须返回 void。
     *
     * @param file 要下载的文件
     */
    protected void download(File file) {
        Objects.requireNonNull(file, "file must not be null");
        download(file, file.getName());
    }

    /**
     * 下载文件（指定下载文件名，自动处理中文 RFC 5987 编码）。
     *
     * @param file     要下载的文件
     * @param filename 浏览器下载时显示的文件名（支持中文）
     */
    protected void download(File file, String filename) {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在或不是普通文件: " + file.getAbsolutePath());
        }
        try (InputStream in = new FileInputStream(file)) {
            download(in, filename, file.length());
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    /**
     * 流式下载（适合大文件，避免全部读入内存）。
     * <p>调用方提供的 {@code in} 在方法结束时会被关闭。
     *
     * @param in            数据源
     * @param filename      浏览器下载时显示的文件名
     * @param contentLength 总字节数；&lt; 0 表示未知（不设 Content-Length，浏览器无进度条）
     */
    protected void download(InputStream in, String filename, long contentLength) {
        Objects.requireNonNull(in, "in must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
        HttpServletResponse response = requireResponse();
        response.setContentType("application/octet-stream");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename));
        if (contentLength >= 0) {
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
        }
        try (InputStream input = in) {
            IoUtil.copy(input, response.getOutputStream());
        } catch (IOException e) {
            throw new IORuntimeException(e);
        }
    }

    /**
     * 构造 Content-Disposition: attachment 头。
     * <p>同时输出 ASCII 兜底（{@code filename="..."}）和 RFC 5987 现代编码（{@code filename*=UTF-8''...}），
     * 中文文件名在主流浏览器中正常显示。
     */
    private static String buildContentDisposition(String filename) {
        String encoded;
        try {
            encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 是 JVM 必备字符集，永远不会到达这里
            throw new IllegalStateException(e);
        }
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    /*---------- 文件上传 ----------*/

    /**
     * 获取对应的 {@link MultipartHttpServletRequest}。非 multipart 请求时抛 {@link IllegalStateException}。
     * <p>建议先用 {@link #hasFile()} 检测或在 controller 中限定 {@code consumes = "multipart/form-data"}。
     *
     * @throws IllegalStateException 当前请求不是 multipart
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
        throw new IllegalStateException("此次访问不是 multipart 请求，无法获取 MultipartHttpServletRequest");
    }

    /**
     * 判断指定 request 是否为 multipart 请求。
     */
    private static boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * 判断是否存在文件（非 multipart 请求返回 false，格式错误的 multipart 请求也返回 false）。
     * <p>注意：进入此方法的 multipart 分支会触发 Spring 实际解析请求体（生成临时文件），并非"零成本探测"。
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
            DefaultSystemLog.getLog().warn("hasFile() 解析 multipart 失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 创建多文件上传对象。
     */
    protected MultipartFileBuilder createMultipart() {
        return new MultipartFileBuilder(getMultiRequest());
    }

}
