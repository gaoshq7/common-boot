package cn.gsq.common.controller;

import cn.gsq.common.controller.multipart.MultipartFileBuilder;
import cn.gsq.common.interceptor.BaseCallbackController;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.HttpUtil;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

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
     * 拦截器注入
     */
    @Override
    public void resetInfo() {

    }

    /**
     * @Description : 获取客户端IP地址
     * @Param : []
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 16:47
     * @note : An art cell !
    **/
    protected String getIp() {
        return ServletUtil.getClientIP(getRequest());
    }

    /**
     * @Description : 获取指定的header值
     * @Param : [name]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 16:51
     * @note : An art cell !
    **/
    protected String getHeader(String name) {
        return getRequest().getHeader(name);
    }

    /**
     * @Description : 根据名称获取cookie的值
     * @Param : [name]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 16:52
     * @note : An art cell !
    **/
    protected String getCookieValue(String name) {
        Cookie cookie = ServletUtil.getCookie(getRequest(), name);
        if (cookie == null) {
            return "";
        }
        return cookie.getValue();
    }

    /**
     * @Description : 根据名称获取参数值
     * @Param : [name]
     * @Return : java.lang.String
     * @Author : gsq
     * @Date : 16:52
     * @note : An art cell !
    **/
    protected String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * @Description : 获取数组参数
     * @Param : [name]
     * @Return : java.lang.String[]
     * @Author : gsq
     * @Date : 16:53
     * @note : An art cell !
    **/
    protected String[] getParameters(String name) {
        return getRequest().getParameterValues(name);
    }

   /**
    * @Description : 获取参数值缺省为def
    * @Param : [name, def]
    * @Return : java.lang.String
    * @Author : gsq
    * @Date : 16:54
    * @note : An art cell !
   **/
    protected String getParameter(String name, String def) {
        String value = getRequest().getParameter(name);
        return value == null ? def : value;
    }

    /**
     * @Description : 获取int类型参数值缺省为def
     * @Param : [name, def]
     * @Return : int
     * @Author : gsq
     * @Date : 16:54
     * @note : An art cell !
    **/
    protected int getParameterInt(String name, int def) {
        return Convert.toInt(getParameter(name), def);
    }

    /**
     * @Description : 获取Long类型参数值缺省为def
     * @Param : [name, def]
     * @Return : long
     * @Author : gsq
     * @Date : 16:56
     * @note : An art cell !
    **/
    protected long getParameterLong(String name, long def) {
        String value = getParameter(name);
        return Convert.toLong(value, def);
    }

    /**
     * @Description : 获取来源的url参数
     * @Param : []
     * @Return : java.util.Map<java.lang.String,java.lang.String>
     * @Author : gsq
     * @Date : 16:56
     * @note : An art cell !
    **/
    protected Map<String, String> getRefererParameter() {
        String referer = getHeader(HttpHeaders.REFERER);
        return HttpUtil.decodeParamMap(referer, CharsetUtil.charset(CharsetUtil.UTF_8));
    }

    /**
     * @Description : 获取表单数据到实体中
     * @Param : [tClass]
     * @Return : T
     * @Author : gsq
     * @Date : 16:57
     * @note : An art cell !
    **/
    protected <T> T getObject(Class<T> tClass) {
        return ServletUtil.toBean(getRequest(), tClass, true);
    }

    /**
     * @Description : 获取请求头中的所有信息
     * @Param : []
     * @Return : java.util.Map<java.lang.String,java.lang.String>
     * @Author : gsq
     * @Date : 14:10
     * @note : An art cell !
    **/
    protected Map<String, String> getHeaders() {
        return getHeaderMapValues(getRequest());
    }

    /**
     * @Description : 获取所有参数
     * @Param : []
     * @Return : java.util.Map<java.lang.String,java.lang.String[]>
     * @Author : gsq
     * @Date : 16:57
     * @note : An art cell !
    **/
    protected Map<String, String[]> getParametersMap() {
        return getRequest().getParameterMap();
    }

    /*---------- 文件上传相关函数 ----------*/

    /**
     * cache
     */
    private static final ThreadLocal<MultipartHttpServletRequest> THREAD_LOCAL_MULTIPART_HTTP_SERVLET_REQUEST = new ThreadLocal<>();

    /**
     * @Description : 释放资源
     * @Param : []
     * @Return : void
     * @Author : gsq
     * @Date : 17:07
     * @note : An art cell !
    **/
    public static void clearResources() {
        THREAD_LOCAL_MULTIPART_HTTP_SERVLET_REQUEST.remove();
    }

    /**
     * @Description : 根据当前线程获取对应的MultipartHttpServletRequest
     * @Param : []
     * @Return : org.springframework.web.multipart.MultipartHttpServletRequest
     * @Author : gsq
     * @Date : 17:05
     * @note : An art cell !
    **/
    protected MultipartHttpServletRequest getMultiRequest() {
        HttpServletRequest request = getRequest();
        if (request instanceof MultipartHttpServletRequest) {
            return (MultipartHttpServletRequest) request;
        }
        if (ServletFileUpload.isMultipartContent(request)) {
            MultipartHttpServletRequest multipartHttpServletRequest = THREAD_LOCAL_MULTIPART_HTTP_SERVLET_REQUEST.get();
            if (multipartHttpServletRequest != null) {
                return multipartHttpServletRequest;
            }
            multipartHttpServletRequest = new StandardMultipartHttpServletRequest(request);
            THREAD_LOCAL_MULTIPART_HTTP_SERVLET_REQUEST.set(multipartHttpServletRequest);
            return multipartHttpServletRequest;
        }
        throw new IllegalArgumentException("此次访问没有对应的MultipartHttpServletRequest ...");
    }

    /**
     * @Description : 判断是否存在文件
     * @Param : []
     * @Return : boolean
     * @Author : gsq
     * @Date : 17:06
     * @note : An art cell !
    **/
    protected boolean hasFile() {
        Map<String, MultipartFile> fileMap = getMultiRequest().getFileMap();
        return fileMap != null && fileMap.size() > 0;
    }

    /**
     * @Description : 创建多文件上传对象
     * @Param : []
     * @Return : cn.gsq.common.controller.multipart.MultipartFileBuilder
     * @Author : gsq
     * @Date : 17:06
     * @note : An art cell !
    **/
    protected MultipartFileBuilder createMultipart() {
        return new MultipartFileBuilder(getMultiRequest());
    }

}
