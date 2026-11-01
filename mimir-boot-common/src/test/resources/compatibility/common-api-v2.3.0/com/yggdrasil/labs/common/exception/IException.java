// 兼容性 fixture：按 v2.3.0 标签 mimir-boot-common 公共签名去 Lombok 后的最小历史源码，仅用于编译旧消费者。
package com.yggdrasil.labs.common.exception;

public interface IException {

    String getCode();

    String getMessage();
}
