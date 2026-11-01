package com.yggdrasil.labs.common.exception;

/** 异常命名工厂共用的错误定义校验，仅包内使用 */
final class ErrorDefinitions {

    private ErrorDefinitions() {}

    /** 校验错误定义并读取一次 code */
    static String requireCode(ErrorDefinition error) {
        if (error == null) {
            throw new IllegalArgumentException("error 不能为 null");
        }
        String code = error.getCode();
        if (code == null || code.isBlank() || ErrorCode.SUCCESS.getCode().equals(code)) {
            throw new IllegalArgumentException("error.code 不能为空白或成功码: " + code);
        }
        return code;
    }

    /** 覆盖消息非 null 时原样采用，否则回退默认消息（允许为 null） */
    static String resolveMessage(ErrorDefinition error, String message) {
        return message != null ? message : error.getMessage();
    }
}
