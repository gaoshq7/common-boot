package cn.gsq.common;

public final class CommonPropertiesFinal {
    /**
     * 程序启动banner 文字信息
     */
    public static final String BANNER_MSG = "banner.msg";
    /**
     * 当前程序定义一个id
     */
    public static final String APPLICATION_ID = "spring.application.name";
    /**
     * 请求的相关配置
     */
    private static final String REQUEST = "request";
    /**
     * 拦截器中记录超时请求时长
     */
    public static final String REQUEST_TIME_OUT = REQUEST + ".timeout";
    /**
     * 是否记录日志  默认记录  true false
     */
    public static final String REQUEST_LOG = REQUEST + ".log";
    /**
     * 是否复制ServletInputStream  默认不复制  true false
     */
    public static final String REQUEST_COPY_INPUT_STREAM = REQUEST + ".copyInputStream";
    /**
     * 参数xss 提前过滤
     */
    public static final String REQUEST_PARAMETER_XSS = REQUEST + ".parameterXss";
    /**
     * 参数去掉前后空格
     */
    public static final String REQUEST_PARAMETER_TRIM_ALL = REQUEST + ".trimAll";
    /**
     * 自定义外部代理中已经获取到的ip header 信息名称（比如nginx 中代理）
     */
    public static final String IP_DEFAULT_HEADER_NAME = "ip.defaultHeaderName";
    /**
     * 拦截器配置
     */
    private static final String INTERCEPTOR = "interceptor";
    /**
     * 加载指定包名下的拦截器
     */
    public static final String INTERCEPTOR_INIT_PACKAGE_NAME = INTERCEPTOR + ".initPackageName";
    /**
     * 拦截器静态资源url路径
     */
    public static final String INTERCEPTOR_RESOURCE_HANDLER = INTERCEPTOR + ".resourceHandler";
    /**
     * 拦截器静态资源文件路径
     */
    public static final String INTERCEPTOR_RESOURCE_LOCATION = INTERCEPTOR + ".resourceLocation";
    /**
     * 预加载
     */
    private static final String PRELOAD = "preload";
    /**
     * 预加载指定包下面的class
     */
    public static final String PRELOAD_PACKAGE_NAME = PRELOAD + ".packageName";
    /**
    *  文件后缀名
    */
    public static final String[] IMAGE_EXTENSION = new String[]{"gif", "jpg", "png"};
    /**
     * 扫描classpath根目录获取资源加载路径
     */
    public static final String SCAN_ROOT_PACKAGE = "cn.galaxy.loader";
}
