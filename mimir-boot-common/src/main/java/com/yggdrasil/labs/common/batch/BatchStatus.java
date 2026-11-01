package com.yggdrasil.labs.common.batch;

/**
 * 批量结果状态，由逐项结果派生
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public enum BatchStatus {

    /** 无条目 */
    EMPTY,

    /** 全部成功 */
    ALL_SUCCESS,

    /** 部分成功 */
    PARTIAL_SUCCESS,

    /** 全部失败 */
    ALL_FAILED
}
