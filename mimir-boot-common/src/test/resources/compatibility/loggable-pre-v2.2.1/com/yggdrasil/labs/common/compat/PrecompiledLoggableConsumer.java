package com.yggdrasil.labs.common.compat;

import com.yggdrasil.labs.common.annotation.Loggable;

@Loggable(module = "legacy", type = "READ", description = "legacy consumer")
public class PrecompiledLoggableConsumer {

    public String module() {
        return getClass().getAnnotation(Loggable.class).module();
    }
}
