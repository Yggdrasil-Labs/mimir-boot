package com.yggdrasil.labs.log.json;

import org.slf4j.event.KeyValuePair;

/** 将内部快照挂载到事件；其他编码器只能看到无敏感内容的固定占位符。 */
final class FieldSnapshotKeyValuePair extends KeyValuePair {

    final FieldSnapshot snapshot;

    FieldSnapshotKeyValuePair(FieldSnapshot snapshot) {
        super(LogJsonKeys.RESERVED_KEY, "[mimir-fields]");
        this.snapshot = snapshot;
    }
}
