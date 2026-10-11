package com.yggdrasil.labs.nacos.configdata;

/** Nacos 配置资源的稳定归属，不包含认证凭据或刷新中的资源对象引用。 */
public record NacosConfigDataResourceKey(
        String serverAddr, String namespace, String group, String dataId) {

    public NacosConfigDataResourceKey {
        serverAddr = normalize(serverAddr);
        namespace = normalize(namespace);
        group = normalize(group);
        dataId = normalize(dataId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
