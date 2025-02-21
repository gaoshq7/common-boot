package cn.gsq.common.controller.multipart;

import cn.hutool.core.util.StrUtil;
import cn.hutool.system.UserInfo;

/**
 * Project : galaxy
 * Class : cn.gsq.common.controller.multipart.MultipartFileConfig
 *
 * @author : gsq
 * @date : 2021-09-10 15:00
 * @note : It's not technology, it's art !
 **/
public class MultipartFileConfig {

    private static String fileTempPath;
    private static final UserInfo USER_INFO = new UserInfo();

    /**
     * 设置文件上传保存路径
     *
     * @param fileTempPath path
     */
    public static void setFileTempPath(String fileTempPath) {
        MultipartFileConfig.fileTempPath = fileTempPath;
    }

    public static String getFileTempPath() {
        if (StrUtil.isBlank(fileTempPath)) {
            fileTempPath = USER_INFO.getTempDir();
        }
        return fileTempPath;
    }

}
