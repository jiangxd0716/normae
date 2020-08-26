package com.galaxyt.normae.sms.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.galaxyt.normae.core.exception.GlobalException;
import com.galaxyt.normae.core.exception.GlobalExceptionCode;
import lombok.extern.slf4j.Slf4j;

/**
 * 验证码类型
 * 仅作为短信验证码校验使用及传参使用
 * 其作为 com.galaxyt.normae.sms.enums.MessageType 的子集存在
 * @author zhouqi
 * @date 2020/8/24 17:24
 * @version v1.0.0
 * @Description
 *
 */
@Slf4j
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum CaptchaType {

    LOGIN(1, "登录"),
    REGISTER(2, "注册"),
    RESET(3, "重置"),

            ;

    /**
     * 代码
     */
    private final int code;

    /**
     * 内容
     */
    private final String msg;


    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    CaptchaType(int code, String msg) {
        this.code = code;
        this.msg = msg;

    }

    /**
     * 根据代码获取枚举
     * 不要尝试缓存全部的枚举，该方法用到的频率不会太高，且枚举很少，不会造成资源浪费
     *
     * 使用 @JsonCreator 让 jackson 解析 json 的时候能匹配到该枚举
     * 参考：https://segmentfault.com/q/1010000020636087
     * @param code
     * @return
     */
    @JsonCreator
    public static CaptchaType getByCode(int code) {

        CaptchaType[] values = CaptchaType.values();

        for (CaptchaType value : values) {
            if (value.getCode() == code) {
                return value;
            }
        }

        throw new GlobalException(GlobalExceptionCode.NOT_FOUND_ENUM, String.format("未找到 [%s] 对应的短信验证码类型!", code));
    }
}