package com.yggdrasil.labs.log.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;

/** 虚拟线程模式下默认执行器未自动接入 MDC 传播的启动提示（内部实现） */
class MdcVirtualThreadNotice implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(MdcVirtualThreadNotice.class);

    @Override
    public void afterSingletonsInstantiated() {
        LOGGER.info("当前为虚拟线程模式，默认执行器未自动传播 MDC；如需传播请使用 MdcTasks.wrap 或 MdcTasks.compose 显式接入");
    }
}
