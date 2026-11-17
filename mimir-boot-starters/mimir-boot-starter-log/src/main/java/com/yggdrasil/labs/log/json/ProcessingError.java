package com.yggdrasil.labs.log.json;

/** 字段处理失败的固定原因码；不存储原值或异常消息。 */
record ProcessingError(String field, String provider, String reason) {}
