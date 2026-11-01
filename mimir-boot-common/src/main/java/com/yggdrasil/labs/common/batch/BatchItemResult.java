package com.yggdrasil.labs.common.batch;

import java.io.Serial;
import java.io.Serializable;

import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.ErrorDefinition;

/**
 * 批量处理中单个输入的结果
 *
 * <p>至少提供一种关联：非 null 的 index 必须非负；index 为 null 时 inputId 必须为非空白文本。成功项可携带 data，code 与 message 为
 * null；失败项只保存 code/message 文本，data 为 null。不可变，成功标记由 code 派生，不单独存储。
 *
 * <p>仅承诺作为服务端输出模型，不承诺跨服务 JSON 反序列化；调用方需按 JSON 字段自建接收 DTO。
 *
 * @param <T> 成功数据类型
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class BatchItemResult<T extends Serializable> implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 输入序号 */
    private final Integer index;

    /** 业务输入标识 */
    private final String inputId;

    /** 成功数据 */
    private final T data;

    /** 失败错误码 */
    private final String code;

    /** 失败错误信息 */
    private final String message;

    // 字段与构造参数一一对应，全部校验在此执行：工厂创建必经校验；若 Jackson 经此构造创建对象同样经过校验（跨服务反序列化不在承诺范围内）
    private BatchItemResult(Integer index, String inputId, T data, String code, String message) {
        if (index != null) {
            if (index < 0) {
                throw new IllegalArgumentException("index 不能为负数: " + index);
            }
        } else if (inputId == null || inputId.isBlank()) {
            throw new IllegalArgumentException("未提供 index 时 inputId 必须为非空白文本");
        }
        if (code == null) {
            if (message != null) {
                throw new IllegalArgumentException("成功项不能携带 message");
            }
        } else {
            if (code.isBlank() || ErrorCode.SUCCESS.getCode().equals(code)) {
                throw new IllegalArgumentException("失败项 code 不能为空白或成功码: " + code);
            }
            if (data != null) {
                throw new IllegalArgumentException("失败项不能携带成功数据");
            }
        }
        this.index = index;
        this.inputId = inputId;
        this.data = data;
        this.code = code;
        this.message = message;
    }

    /**
     * 创建成功项
     *
     * @param index 输入序号，可为 null
     * @param inputId 业务输入标识，index 为 null 时必须非空白
     * @param data 成功数据，可为 null
     * @param <T> 成功数据类型
     * @return 成功项
     */
    public static <T extends Serializable> BatchItemResult<T> success(
            Integer index, String inputId, T data) {
        return new BatchItemResult<>(index, inputId, data, null, null);
    }

    /**
     * 按错误定义创建失败项，使用默认消息
     *
     * @param index 输入序号，可为 null
     * @param inputId 业务输入标识，index 为 null 时必须非空白
     * @param error 错误定义，code 不能为空白或成功码
     * @param <T> 成功数据类型
     * @return 失败项
     */
    public static <T extends Serializable> BatchItemResult<T> failure(
            Integer index, String inputId, ErrorDefinition error) {
        return failure(index, inputId, error, null);
    }

    /**
     * 按错误定义创建失败项
     *
     * @param index 输入序号，可为 null
     * @param inputId 业务输入标识，index 为 null 时必须非空白
     * @param error 错误定义，code 不能为空白或成功码
     * @param message 覆盖消息，为 null 时使用默认消息（默认消息也允许为 null）
     * @param <T> 成功数据类型
     * @return 失败项
     */
    public static <T extends Serializable> BatchItemResult<T> failure(
            Integer index, String inputId, ErrorDefinition error, String message) {
        if (error == null) {
            throw new IllegalArgumentException("error 不能为 null");
        }
        String code = error.getCode();
        // 构造器以 code == null 判定成功项，null code 须在此拦截，文案与构造器一致
        if (code == null) {
            throw new IllegalArgumentException("失败项 code 不能为空白或成功码: null");
        }
        return new BatchItemResult<>(
                index, inputId, null, code, message != null ? message : error.getMessage());
    }

    public Integer getIndex() {
        return index;
    }

    public String getInputId() {
        return inputId;
    }

    /** 是否成功，由 code 是否为 null 派生 */
    public boolean isSuccess() {
        return code == null;
    }

    public T getData() {
        return data;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
