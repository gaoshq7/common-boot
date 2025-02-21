package cn.gsq.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Project : galaxy
 * Class : cn.gsq.common.LogLevel
 *
 * @author : gsq
 * @date : 2024-04-30 13:25
 * @note : It's not technology, it's art !
 **/
@Slf4j
@Getter
@AllArgsConstructor
public enum LogLevel {

    INFO("信息"),

    ERROR("异常");

    private final String name;  // 类型

}
