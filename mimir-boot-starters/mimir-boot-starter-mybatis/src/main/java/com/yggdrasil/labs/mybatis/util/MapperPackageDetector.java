package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.mybatis.config.MybatisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Mapper 包自动检测工具类。
 * 用于自动检测 classpath 中所有以 ".mapper" 结尾的包，主要用于检测 processor 生成的 mapper。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class MapperPackageDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapperPackageDetector.class);

    private MapperPackageDetector() {
        // 工具类，禁止实例化
    }

    /**
     * 自动检测 classpath 中所有以 ".mapper" 结尾的包。
     * 主要用于检测 processor 生成的 mapper，因为 processor 默认生成的 mapper 包路径
     * 是 "实体类包名.mapper"，所以通过检测所有 ".mapper" 结尾的包可以自动发现这些 mapper。
     * 注意：此方法只检测包路径，不加载类，性能较好。
     * 
     * 优化：如果检测到的包已经在默认包（com.yggdrasil.labs.**.mapper）的覆盖范围内，
     * 则不会重复添加，因为默认包的通配符已经可以匹配到这些包。
     * 只添加不在默认包覆盖范围内的包（如其他组织或项目的 mapper 包）。
     *
     * @return 检测到的 mapper 包集合（使用通配符模式，如 "com.example.**.mapper"）
     */
    public static Set<String> detectMapperPackages() {
        Set<String> mapperPackages = new LinkedHashSet<>();

        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    MybatisConstants.MAPPER_SCAN_PATTERN;
            Resource[] resources = resolver.getResources(packageSearchPath);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("开始自动检测 Mapper 包，扫描路径: {}", packageSearchPath);
            }

            Set<String> detectedPackages = new LinkedHashSet<>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }

                String packageName = extractPackageFromResource(resource);
                if (StringUtils.hasText(packageName) && packageName.endsWith(MybatisConstants.MAPPER_PACKAGE_SUFFIX)) {
                    detectedPackages.add(packageName);
                }
            }

            // 将检测到的包转换为通配符模式，以便扫描子包
            for (String pkg : detectedPackages) {
                // 如果包路径不是以默认包前缀开头，则添加到扫描列表
                // 因为默认包 com.yggdrasil.labs.**.mapper 已经可以匹配所有 com.yggdrasil.labs 下的 .mapper 包
                // 注意：使用 DEFAULT_PACKAGE_PREFIX + "." 确保是子包，避免误判（如 com.yggdrasil.labsx.mapper）
                if (!pkg.startsWith(MybatisConstants.DEFAULT_PACKAGE_PREFIX + ".")) {
                    mapperPackages.add(pkg + MybatisConstants.PACKAGE_WILDCARD_SUFFIX);
                }
            }

            if (!mapperPackages.isEmpty() && LOGGER.isDebugEnabled()) {
                LOGGER.debug("自动检测到 {} 个 Mapper 包: {}", mapperPackages.size(), mapperPackages);
            }
        } catch (IOException e) {
            LOGGER.warn("自动检测 Mapper 包时发生 IO 异常，将跳过自动检测。用户可通过配置 {} 手动指定",
                    MybatisConstants.CONFIG_MAPPER_PACKAGES, e);
        } catch (Exception e) {
            LOGGER.warn("自动检测 Mapper 包时发生未知异常，将跳过自动检测。用户可通过配置 {} 手动指定",
                    MybatisConstants.CONFIG_MAPPER_PACKAGES, e);
        }

        return mapperPackages;
    }

    /**
     * 从资源中提取包名。
     *
     * @param resource 资源对象
     * @return 包名，如果无法提取则返回 null
     */
    private static String extractPackageFromResource(Resource resource) {
        try {
            String url = resource.getURL().toString();
            return extractPackageFromUrl(url);
        } catch (IOException e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("无法读取资源 URL: {}", resource, e);
            }
            return null;
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("处理资源时发生异常: {}", resource, e);
            }
            return null;
        }
    }

    /**
     * 从资源 URL 中提取包名。
     * 支持多种 URL 格式：
     * - file:/path/to/target/classes/com/example/mapper/UserMapper.class
     * - jar:file:/path/to/app.jar!/com/example/mapper/UserMapper.class
     *
     * @param url 资源 URL
     * @return 包名，如果无法提取则返回 null
     */
    private static String extractPackageFromUrl(String url) {
        if (!url.contains(MybatisConstants.MAPPER_PACKAGE_SEPARATOR)) {
            return null;
        }

        try {
            int mapperIndex = url.indexOf(MybatisConstants.MAPPER_PACKAGE_SEPARATOR);
            if (mapperIndex <= 0) {
                return null;
            }

            String beforeMapper = extractPathBeforeMapper(url, mapperIndex);
            if (beforeMapper == null) {
                return null;
            }

            int startIndex = findPackageStartIndex(beforeMapper);
            if (startIndex < 0 || startIndex >= mapperIndex) {
                return null;
            }

            String packagePath = beforeMapper.substring(startIndex, mapperIndex)
                    .replace('/', '.');
            if (StringUtils.hasText(packagePath)) {
                return packagePath + MybatisConstants.MAPPER_PACKAGE_SUFFIX;
            }
        } catch (StringIndexOutOfBoundsException e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("解析 URL 时发生字符串索引越界: {}", url, e);
            }
        } catch (Exception e) {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("解析 URL 时发生异常: {}", url, e);
            }
        }

        return null;
    }

    /**
     * 从 URL 中提取 "/mapper/" 之前的部分，处理 jar 包格式。
     *
     * @param url         完整 URL
     * @param mapperIndex "/mapper/" 在 URL 中的索引位置
     * @return 提取的路径，如果无法提取则返回 null
     */
    private static String extractPathBeforeMapper(String url, int mapperIndex) {
        String beforeMapper = url.substring(0, mapperIndex);

        // 处理 jar 包中的类文件
        if (beforeMapper.contains(MybatisConstants.JAR_SEPARATOR)) {
            int jarIndex = beforeMapper.lastIndexOf(MybatisConstants.JAR_SEPARATOR);
            if (jarIndex >= 0 && jarIndex + MybatisConstants.JAR_SEPARATOR.length() < beforeMapper.length()) {
                return beforeMapper.substring(jarIndex + MybatisConstants.JAR_SEPARATOR.length());
            }
            return null;
        }

        return beforeMapper;
    }

    /**
     * 查找包路径的起始索引位置。
     *
     * @param path "/mapper/" 之前的路径
     * @return 包路径的起始索引，如果无法确定则返回 0
     */
    private static int findPackageStartIndex(String path) {
        // 查找 classes 目录
        int classesIndex = path.indexOf(MybatisConstants.CLASSES_DIR);
        if (classesIndex != -1) {
            return classesIndex + MybatisConstants.CLASSES_DIR.length();
        }

        // 如果是 jar 包，可能没有 classes 目录，尝试从最后一个 / 开始
        if (path.contains("/")) {
            int lastSlash = path.lastIndexOf("/");
            if (lastSlash != -1 && lastSlash + 1 < path.length()) {
                return lastSlash + 1;
            }
        }

        return 0;
    }
}

