package cn.gsq.common;

import java.util.List;

/**
 * Project : galaxy
 * Class : cn.gsq.common.AbstractInformationLoader
 *
 * @author : gsq
 * @date : 2024-05-08 14:35
 * @note : It's not technology, it's art !
 **/
public abstract class AbstractInformationLoader {

    /**
     * @Description : 是否启用改资源配置
     * @Param : []
     * @Return : boolean
     * @Author : gsq
     * @Date : 15:14
     * @note : An art cell !
    **/
    public boolean isEnable() {
        return true;
    }

    /**
     * @Description : Beans扫描路径提供函数
     * @Param : []
     * @Return : java.util.List<java.lang.String>
     * @Author : gsq
     * @Date : 14:45
     * @note : ⚠️ 路径下所有的class将会按照spring boot的规则进行扫描 !
    **/
    public List<String> springBeansSupply() {
        return null;
    }

    /**
     * @Description : Env属性扫描路径提供函数
     * @Param : []
     * @Return : java.util.List<java.lang.String>
     * @Author : gsq
     * @Date : 14:49
     * @note : ⚠️ 路径下所有的class将会按照galaxy注解规则进行扫描 !
    **/
    public List<String> envArgsSupply() {
        return null;
    }

    /**
     * @Description : 环境初始化函数扫描路径提供函数
     * @Param : []
     * @Return : java.util.List<java.lang.String>
     * @Author : gsq
     * @Date : 14:58
     * @note : ⚠️ 路径下所有的class将会按照规则在ApplicationReadyEvent事件发生时触发 !
    **/
    public List<String> initMethodsSupply() {
        return null;
    }

    /**
     * @Description : 事件处理函数扫描路径提供函数
     * @Param : []
     * @Return : java.util.List<java.lang.String>
     * @Author : gsq
     * @Date : 16:52
     * @note : An art cell !
    **/
    public List<String> eventHandleSupply() {
        return null;
    }

}
