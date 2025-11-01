package com.yggdrasil.labs.nacos.decrypt;

import com.yggdrasil.labs.nacos.config.NacosEncryptProperties;
import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
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

    private final NacosEncryptProperties properties;
    private final Pattern encryptPattern;

    public ConfigDecryptProcessor(NacosEncryptProperties properties) {
        this.properties = properties;
        // 匹配 ENC(encrypted_value) 格式，支持大小写
        String prefix = properties.getPrefix();
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
            log.debug("Nacos 配置加密脱敏功能已禁用");
            return;
        }

        String key = properties.getKey();
        if (key == null || key.isEmpty()) {
            log.warn("未配置加密密钥 (mimir.nacos.encrypt.key)，跳过配置解密");
            return;
        }

        String algorithm = properties.getAlgorithm();
        log.debug("开始处理配置解密，算法: {}, 前缀: {}", algorithm, properties.getPrefix());

        // 检查是否已经处理过，避免重复处理
        boolean alreadyProcessed = false;
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if ("decryptedProperties".equals(propertySource.getName())) {
                alreadyProcessed = true;
                break;
            }
        }

        if (alreadyProcessed) {
            log.debug("配置已经解密过，跳过重复处理");
            return;
        }

        // 获取所有属性源并处理
        List<PropertySource<?>> propertySources = new ArrayList<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            // 跳过已经解密的属性源
            if (!"decryptedProperties".equals(propertySource.getName())) {
                propertySources.add(propertySource);
            }
        }

        // 从后往前处理，确保优先级正确
        Collections.reverse(propertySources);

        Map<String, Object> decryptedProperties = new HashMap<>();
        for (PropertySource<?> propertySource : propertySources) {
            if (propertySource instanceof EnumerablePropertySource) {
                processPropertySource(
                        (EnumerablePropertySource<?>) propertySource,
                        decryptedProperties,
                        key,
                        algorithm
                );
            }
        }

        // 将解密后的属性添加到环境配置中（最高优先级）
        if (!decryptedProperties.isEmpty()) {
            PropertySource<Map<String, Object>> decryptedPropertySource =
                    new PropertySource<>("decryptedProperties", decryptedProperties) {
                        @Override
                        public Object getProperty(String name) {
                            return source.get(name);
                        }
                    };
            environment.getPropertySources().addFirst(decryptedPropertySource);
            log.info("成功解密 {} 个配置项", decryptedProperties.size());
        }
    }

    /**
     * 处理单个属性源
     *
     * @param propertySource      属性源
     * @param decryptedProperties 解密后的属性集合
     * @param key                 加密密钥
     * @param algorithm           加密算法
     */
    private void processPropertySource(
            EnumerablePropertySource<?> propertySource,
            Map<String, Object> decryptedProperties,
            String key,
            String algorithm) {

        String[] propertyNames = propertySource.getPropertyNames();

        for (String propertyName : propertyNames) {
            Object propertyValue = propertySource.getProperty(propertyName);
            if (propertyValue instanceof String encryptedValue) {
                String decryptedValue = decryptValue(encryptedValue, key, algorithm);
                if (!encryptedValue.equals(decryptedValue)) {
                    // 只有解密成功时才添加到集合中
                    decryptedProperties.put(propertyName, decryptedValue);
                    log.debug("解密配置: {} = {} -> {}", propertyName, encryptedValue, decryptedValue);
                }
            }
        }
    }

    /**
     * 解密配置值
     *
     * @param value     配置值
     * @param key       加密密钥
     * @param algorithm 加密算法
     * @return 解密后的值，如果不是加密格式则返回原值
     */
    private String decryptValue(String value, String key, String algorithm) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        Matcher matcher = encryptPattern.matcher(value);
        if (!matcher.find()) {
            // 不包含 ENC() 格式，直接返回
            return value;
        }

        try {
            // 提取加密内容
            String encryptedContent = matcher.group(1);
            // 解密
            String decrypted = ConfigCryptoUtils.decrypt(encryptedContent, key, algorithm);
            // 替换整个 ENC(...) 为解密后的值
            return matcher.replaceAll(Matcher.quoteReplacement(decrypted));
        } catch (Exception e) {
            log.error("解密配置值失败: {}", value, e);
            // 解密失败时返回原值，避免应用启动失败
            return value;
        }
    }
}
