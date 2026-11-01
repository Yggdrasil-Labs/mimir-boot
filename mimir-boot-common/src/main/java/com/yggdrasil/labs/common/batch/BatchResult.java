package com.yggdrasil.labs.common.batch;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 完整批量结果
 *
 * <p>按调用方提供的顺序保存逐项结果的不可修改快照；计数与状态在读取时由条目派生，不单独存储。模型不接收原始输入总数，不校验条目是否完整、
 * 序号是否连续或唯一，完整性由业务执行方保证。只承载结果，不执行任务、不决定事务或重试。
 *
 * <p>仅承诺作为服务端输出模型（含 {@code R<BatchResult<T>>}），不承诺跨服务 JSON 反序列化；调用方需按 JSON 字段自建接收 DTO。
 *
 * @param <T> 成功数据类型
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class BatchResult<T extends Serializable> implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 逐项结果不可修改快照 */
    private final List<BatchItemResult<T>> items;

    private BatchResult(List<BatchItemResult<T>> items) {
        if (items == null) {
            throw new IllegalArgumentException("items 不能为 null");
        }
        List<BatchItemResult<T>> snapshot = new ArrayList<>(items.size());
        for (BatchItemResult<T> item : items) {
            if (item == null) {
                throw new IllegalArgumentException("items 不能包含 null 条目");
            }
            snapshot.add(item);
        }
        this.items = Collections.unmodifiableList(snapshot);
    }

    /**
     * 由已完成的逐项结果创建批量结果
     *
     * @param items 逐项结果，不能为 null 或包含 null 条目
     * @param <T> 成功数据类型
     * @return 批量结果
     */
    public static <T extends Serializable> BatchResult<T> of(List<BatchItemResult<T>> items) {
        return new BatchResult<>(items);
    }

    public int getTotalCount() {
        return items.size();
    }

    public int getSuccessCount() {
        int count = 0;
        for (BatchItemResult<T> item : items) {
            if (item.isSuccess()) {
                count++;
            }
        }
        return count;
    }

    public int getFailureCount() {
        return getTotalCount() - getSuccessCount();
    }

    public BatchStatus getStatus() {
        if (items.isEmpty()) {
            return BatchStatus.EMPTY;
        }
        int success = getSuccessCount();
        if (success == items.size()) {
            return BatchStatus.ALL_SUCCESS;
        }
        return success == 0 ? BatchStatus.ALL_FAILED : BatchStatus.PARTIAL_SUCCESS;
    }

    /** 逐项结果，返回列表不可修改 */
    public List<BatchItemResult<T>> getItems() {
        return items;
    }
}
