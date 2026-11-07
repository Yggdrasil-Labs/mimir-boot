package com.yggdrasil.labs.nacos.config;

import java.util.concurrent.atomic.AtomicInteger;

/** 两种刷新入口共用的配置绑定目标。 */
public final class NacosRefreshSettingsSupport {

    private NacosRefreshSettingsSupport() {}

    public static class Settings {
        private final AtomicInteger initialized;
        private final AtomicInteger destroyed;
        private String secret;
        private int timeout = 30;

        Settings(AtomicInteger initialized, AtomicInteger destroyed) {
            // 计数仍属于各测试上下文；真实推送线程和断言线程通过原子计数观察生命周期。
            this.initialized = initialized;
            this.destroyed = destroyed;
        }

        public void initialize() {
            initialized.incrementAndGet();
        }

        public void destroy() {
            destroyed.incrementAndGet();
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
    }
}
