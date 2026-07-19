package com.yggdrasil.labs.nacos.decrypt;

import com.yggdrasil.labs.nacos.config.NacosEncryptProperties;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置解密处理器
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>自动检测配置值中的 ENC(encrypted_value) 格式</li>
 * <li>提取加密内容并解密</li>
 * <li>替换为解密后的明文值</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class ConfigDecryptProcessor {

    private static final Logger log = LoggerFactory.getLogger(ConfigDecryptProcessor.class);
    private static final String DECRYPTED_PROPERTIES_PREFIX = "decryptedProperties:";

    private final NacosEncryptProperties properties;
    private final Pattern encryptMarkerPattern;
    private final Pattern encryptPattern;

    public ConfigDecryptProcessor(NacosEncryptProperties properties) {
        this.properties = properties;
        // 匹配 ENC(encrypted_value) 格式，支持大小写
        String prefix = properties.getPrefix();
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Nacos 加密前缀不能为空");
        }
        this.encryptMarkerPattern = Pattern.compile("(?i)" + Pattern.quote(prefix) + "\\(");
        this.encryptPattern = Pattern.compile(
                "(?i)" + Pattern.quote(prefix) + "\\(" + "([^)]+)" + "\\)",
                Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * 处理环境配置，解密所有加密的配置值
     *
     * @param environment Spring 环境配置
     */
    public void process(ConfigurableEnvironment environment) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            removeDecryptedPropertySources(environment);
            log.debug("Nacos 配置加密脱敏功能已禁用");
            return;
        }

        boolean containsEncryptedProperty = containsEncryptedProperty(environment);
        if (!containsEncryptedProperty) {
            removeDecryptedPropertySources(environment);
            return;
        }

        String key = properties.getKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("检测到加密配置，但未配置加密密钥");
        }
        try {
            ConfigCryptoUtils.validateKey(key);
            ConfigCryptoUtils.validateConfiguredAlgorithm(properties.getAlgorithm());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Nacos 加密配置校验失败", e);
        }

        // 获取所有原始属性源并处理
        List<PropertySource<?>> propertySources = new ArrayList<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            // 跳过已经解密的属性源
            if (!isDecryptedPropertySource(propertySource)) {
                propertySources.add(propertySource);
            }
        }

        List<DecryptedPropertySource> decryptedPropertySources = new ArrayList<>();
        int decryptedCount = 0;
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                Map<String, Object> decryptedProperties = new HashMap<>();
                processPropertySource(
                        enumerablePropertySource,
                        decryptedProperties,
                        key
                );
                if (!decryptedProperties.isEmpty()) {
                    decryptedPropertySources.add(new DecryptedPropertySource(
                            propertySource.getName(),
                            decryptedProperties
                    ));
                    decryptedCount += decryptedProperties.size();
                }
            }
        }

        replaceDecryptedPropertySources(environment, decryptedPropertySources);
        if (decryptedCount > 0) {
            log.info("成功解密 {} 个配置项", decryptedCount);
        }
    }

    private void replaceDecryptedPropertySources(
            ConfigurableEnvironment environment,
            List<DecryptedPropertySource> decryptedPropertySources) {
        removeDecryptedPropertySources(environment);
        for (DecryptedPropertySource decryptedPropertySource : decryptedPropertySources) {
            environment.getPropertySources().addBefore(
                    decryptedPropertySource.sourceName(),
                    new MapPropertySource(
                            DECRYPTED_PROPERTIES_PREFIX + decryptedPropertySource.sourceName(),
                            decryptedPropertySource.properties()
                    )
            );
        }
    }

    private void removeDecryptedPropertySources(ConfigurableEnvironment environment) {
        List<String> propertySourceNames = new ArrayList<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (isDecryptedPropertySource(propertySource)) {
                propertySourceNames.add(propertySource.getName());
            }
        }
        propertySourceNames.forEach(environment.getPropertySources()::remove);
    }

    private boolean isDecryptedPropertySource(PropertySource<?> propertySource) {
        return propertySource.getName().startsWith(DECRYPTED_PROPERTIES_PREFIX);
    }

    private boolean containsEncryptedProperty(ConfigurableEnvironment environment) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource) {
                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    Object propertyValue = enumerablePropertySource.getProperty(propertyName);
                    if (propertyValue instanceof String value && encryptMarkerPattern.matcher(value).find()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 处理单个属性源
     *
     * @param propertySource      属性源
     * @param decryptedProperties 解密后的属性集合
     * @param key                 加密密钥
     */
    private void processPropertySource(
            EnumerablePropertySource<?> propertySource,
            Map<String, Object> decryptedProperties,
            String key) {

        String[] propertyNames = propertySource.getPropertyNames();

        for (String propertyName : propertyNames) {
            Object propertyValue = propertySource.getProperty(propertyName);
            if (propertyValue instanceof String encryptedValue) {
                String decryptedValue = decryptValue(propertyName, encryptedValue, key);
                if (!encryptedValue.equals(decryptedValue)) {
                    // 只有解密成功时才添加到集合中
                    decryptedProperties.put(propertyName, decryptedValue);
                    log.debug("配置项解密成功: {}", propertyName);
                }
            }
        }
    }

    /**
     * 解密配置值
     *
     * @param propertyName 配置属性名
     * @param value     配置值
     * @param key       加密密钥
     * @return 解密后的值，如果不是加密格式则返回原值
     */
    private String decryptValue(String propertyName, String value, String key) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (!encryptMarkerPattern.matcher(value).find()) {
            // 不包含 ENC() 格式，直接返回
            return value;
        }

        try {
            Matcher matcher = encryptPattern.matcher(value);
            StringBuffer decryptedValue = new StringBuffer();
            boolean found = false;
            while (matcher.find()) {
                found = true;
                String decrypted = ConfigCryptoUtils.decrypt(matcher.group(1), key);
                matcher.appendReplacement(decryptedValue, Matcher.quoteReplacement(decrypted));
            }
            if (!found) {
                throw new IllegalArgumentException("加密配置格式不合法");
            }
            matcher.appendTail(decryptedValue);
            return decryptedValue.toString();
        } catch (Exception e) {
            log.error("解密 Nacos 配置项失败: {}", propertyName);
            throw new IllegalStateException("无法解密 Nacos 配置项: " + propertyName, e);
        }
    }

    private record DecryptedPropertySource(String sourceName, Map<String, Object> properties) {
    }
}
