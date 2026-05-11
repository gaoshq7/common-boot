package cn.gsq.common.controller.multipart;

import ch.qos.logback.core.util.FileSize;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.UnicodeUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Project : galaxy
 * Class : cn.gsq.common.controller.multipart.MultipartFileBuilder
 *
 * @author : gsq
 * @date : 2021-09-10 15:00
 * @note : It's not technology, it's art !
 **/
public class MultipartFileBuilder {

    /**
     * 默认单文件大小限制：10MB
     */
    private static final long DEFAULT_MAX_SIZE = 10 * 1024 * 1024;

    private MultipartHttpServletRequest multipartHttpServletRequest;
    /**
     * 限制上传文件的大小（默认 10MB）
     */
    private long maxSize = DEFAULT_MAX_SIZE;
    /**
     * 字段名称（使用 LinkedHashSet 保证 saves() 返回路径顺序与 addFieldName 调用顺序一致）
     */
    private Set<String> fieldNames = new LinkedHashSet<>();
    /**
     * 多文件上传
     */
    private boolean multiple;
    /**
     * 文件名后缀
     */
    private String[] fileExt;
    /**
     * 文件类型
     *
     * @see FileUtil#getMimeType(String)
     */
    private String contentTypePrefix;
    /**
     * 文件流类型
     *
     * @see FileTypeUtil#getType(InputStream)
     */
    private String[] inputStreamType;
    /**
     * 保存路径
     */
    private String savePath;
    /**
     * 使用原文件名
     */
    private boolean useOriginalFilename;

    /**
     * 文件上传大小限制
     *
     * @param maxSize 字节大小（0 表示不限制）
     * @return this
     */
    public MultipartFileBuilder setMaxSize(long maxSize) {
        this.maxSize = maxSize;
        return this;
    }

    /**
     * 文件上传大小限制
     *
     * @param maxSize 字符串
     * @return this
     */
    public MultipartFileBuilder setMaxSize(String maxSize) {
        this.maxSize = FileSize.valueOf(maxSize).getSize();
        return this;
    }

    /**
     * 是否使用原文件名保存
     *
     * @param useOriginalFilename true 是
     * @return this
     */
    public MultipartFileBuilder setUseOriginalFilename(boolean useOriginalFilename) {
        this.useOriginalFilename = useOriginalFilename;
        return this;
    }

    /**
     * 需要接受的文件字段
     *
     * @param fieldName 参数名
     * @return this
     */
    public MultipartFileBuilder addFieldName(String fieldName) {
        this.fieldNames.add(fieldName);
        return this;
    }

    /**
     * 清空数据并重新赋值
     *
     * @param fieldName 参数名
     * @return this
     */
    public MultipartFileBuilder resetFieldName(String fieldName) {
        this.fieldNames.clear();
        this.fieldNames.add(fieldName);
        return this;
    }

    /**
     * 是否为多文件上传
     *
     * @param multiple true
     * @return this
     */
    public MultipartFileBuilder setMultiple(boolean multiple) {
        this.multiple = multiple;
        return this;
    }

    /**
     * 限制文件后缀名
     *
     * @param fileExt 后缀
     * @return this
     */
    public MultipartFileBuilder setFileExt(String... fileExt) {
        this.fileExt = fileExt;
        return this;
    }

    /**
     * 限制文件流类型
     *
     * @param inputStreamType type
     * @return this
     * @see FileTypeUtil#getType(InputStream)
     */
    public MultipartFileBuilder setInputStreamType(String... inputStreamType) {
        this.inputStreamType = inputStreamType;
        return this;
    }

    /**
     * 使用  获取到类型
     *
     * @param contentTypePrefix 前缀
     * @return this
     * @see FileUtil#getMimeType(String)
     */
    public MultipartFileBuilder setContentTypePrefix(String contentTypePrefix) {
        this.contentTypePrefix = contentTypePrefix;
        return this;
    }

    /**
     * 文件保存的路径
     *
     * @param savePath 路径
     * @return this
     */
    public MultipartFileBuilder setSavePath(String savePath) {
        this.savePath = savePath;
        return this;
    }

    private void checkSaveOne() {
        if (this.fieldNames.size() != 1) {
            throw new IllegalArgumentException("fieldNames size:" + this.fieldNames.size() + "  use saves");
        }
        if (this.multiple) {
            throw new IllegalArgumentException("multiple use saves");
        }
    }

    /**
     * 接收单文件上传
     *
     * @return 本地路径
     * @throws IOException IO
     */
    public String save() throws IOException {
        checkSaveOne();
        String[] paths = saves();
        return paths[0];
    }

    /**
     * 保存多个文件
     *
     * @return 本地路径数组
     * @throws IOException IO
     */
    public String[] saves() throws IOException {
        if (fieldNames.isEmpty()) {
            throw new IllegalArgumentException("fieldNames:empty");
        }
        List<String> pathList = new ArrayList<>();
        for (String fieldName : fieldNames) {
            if (this.multiple) {
                List<MultipartFile> multipartFiles = multipartHttpServletRequest.getFiles(fieldName);
                for (MultipartFile multipartFile : multipartFiles) {
                    if (multipartFile != null && !multipartFile.isEmpty()) {
                        pathList.add(saveAndName(multipartFile)[0]);
                    }
                }
            } else {
                MultipartFile multipartFile = multipartHttpServletRequest.getFile(fieldName);
                if (multipartFile == null || multipartFile.isEmpty()) {
                    throw new IllegalArgumentException("fieldName:" + fieldName + " 没有对应的文件");
                }
                pathList.add(saveAndName(multipartFile)[0]);
            }
        }
        return pathList.toArray(new String[0]);
    }

    /**
     * 上传文件，并且返回原文件名
     *
     * @return 数组
     * @throws IOException IO
     */
    public String[] saveAndName() throws IOException {
        checkSaveOne();
        List<String[]> list = saveAndNames();
        return list.get(0);
    }

    /**
     * 上传文件，并且返回原文件名
     *
     * @return 集合
     * @throws IOException IO
     */
    public List<String[]> saveAndNames() throws IOException {
        if (fieldNames.isEmpty()) {
            throw new IllegalArgumentException("fieldNames:empty");
        }
        List<String[]> list = new ArrayList<>();
        for (String fieldName : fieldNames) {
            if (this.multiple) {
                List<MultipartFile> multipartFiles = multipartHttpServletRequest.getFiles(fieldName);
                for (MultipartFile multipartFile : multipartFiles) {
                    if (multipartFile != null && !multipartFile.isEmpty()) {
                        list.add(saveAndName(multipartFile));
                    }
                }
            } else {
                MultipartFile multipartFile = multipartHttpServletRequest.getFile(fieldName);
                if (multipartFile == null || multipartFile.isEmpty()) {
                    throw new IllegalArgumentException("fieldName:" + fieldName + " 没有对应的文件");
                }
                list.add(saveAndName(multipartFile));
            }
        }
        return list;
    }

    /**
     * 保存文件并验证类型
     *
     * @param multiFile file
     * @return 本地路径和原文件名
     * @throws IOException IO
     */
    private String[] saveAndName(MultipartFile multiFile) throws IOException {
        if (multiFile == null || multiFile.isEmpty()) {
            throw new IllegalArgumentException("multipartFile:文件对象为空");
        }
        String fileName = multiFile.getOriginalFilename();
        if (StrUtil.isEmpty(fileName)) {
            throw new IllegalArgumentException("fileName:不能获取到文件名");
        }
        // 防止路径遍历攻击
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("fileName:非法文件名:" + fileName);
        }
        long fileSize = multiFile.getSize();
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize:文件内容为空");
        }
        // 文件名后缀（空数组与 null 等价，均视为不限制）
        if (this.fileExt != null && this.fileExt.length > 0) {
            String checkName = FileUtil.extName(fileName);
            boolean find = false;
            for (String ext : this.fileExt) {
                find = StrUtil.equalsIgnoreCase(checkName, ext);
                if (find) {
                    break;
                }
            }
            if (!find) {
                throw new IllegalArgumentException("fileExt:类型错误:" + checkName);
            }
        }
        // 文件大小（0 表示不限制）
        if (maxSize > 0 && fileSize > maxSize) {
            throw new IllegalArgumentException("maxSize:too big:" + fileSize + ">" + maxSize);
        }
        // 文件流类型（通过文件内容判断真实类型，防篡改扩展名；空数组与 null 等价，均视为不限制）
        if (this.inputStreamType != null && this.inputStreamType.length > 0) {
            try (InputStream inputStream = multiFile.getInputStream()) {
                String fileType = FileTypeUtil.getType(inputStream);
                if (!ArrayUtil.containsIgnoreCase(this.inputStreamType, fileType)) {
                    throw new IllegalArgumentException("inputStreamType:类型错误:" + fileType);
                }
            }
        }
        // 保存路径
        String localPath;
        if (this.savePath != null) {
            localPath = this.savePath;
        } else {
            localPath = MultipartFileConfig.getFileTempPath();
        }
        // 保存的文件名
        String filePath;
        if (useOriginalFilename) {
            filePath = FileUtil.normalize(String.format("%s/%s", localPath, fileName));
            if (FileUtil.exist(filePath)) {
                throw new IllegalArgumentException("fileName:文件已存在:" + filePath);
            }
        } else {
            // 防止中文乱码
            String saveFileName = UnicodeUtil.toUnicode(fileName);
            saveFileName = saveFileName.replace(StrUtil.BACKSLASH, "_");
            // 生成唯一id
            filePath = FileUtil.normalize(String.format("%s/%s_%s", localPath, IdUtil.objectId(), saveFileName));
        }
        FileUtil.writeFromStream(multiFile.getInputStream(), filePath);
        // 文件contentType（优先基于文件内容，其次基于扩展名）
        if (this.contentTypePrefix != null) {
            String contentType = null;
            try {
                contentType = Files.probeContentType(Paths.get(filePath));
            } catch (Exception ignored) {
                // 系统不支持 probeContentType 时回退到扩展名判断
            }
            if (contentType == null) {
                contentType = FileUtil.getMimeType(filePath);
            }
            if (contentType == null) {
                deleteFileQuietly(filePath);
                throw new IllegalArgumentException("contentTypePrefix:获取文件类型失败");
            }
            if (!contentType.startsWith(contentTypePrefix)) {
                deleteFileQuietly(filePath);
                throw new IllegalArgumentException("contentTypePrefix:文件类型不正确:" + contentType);
            }
        }
        return new String[]{filePath, fileName};
    }

    /**
     * 静默删除文件（删除失败不抛异常）
     */
    private static void deleteFileQuietly(String filePath) {
        try {
            FileUtil.del(filePath);
        } catch (Exception ignored) {
        }
    }

    public MultipartFileBuilder(MultipartHttpServletRequest multipartHttpServletRequest) {
        Objects.requireNonNull(multipartHttpServletRequest);
        this.multipartHttpServletRequest = multipartHttpServletRequest;
    }

}
