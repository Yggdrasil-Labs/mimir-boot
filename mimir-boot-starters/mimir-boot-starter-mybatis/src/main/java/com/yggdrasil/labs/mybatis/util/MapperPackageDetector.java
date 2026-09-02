package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.mybatis.config.MybatisConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.net.URL;
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
     * @return 检测到的 mapper 包集合（使用通配符模式，如 "com.example.mapper.**"）
     */
    public static Set<String> detectMapperPackages() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    MybatisConstants.MAPPER_SCAN_PATTERN;
            Resource[] resources = resolver.getResources(packageSearchPath);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("开始自动检测 Mapper 包，扫描路径: {}", packageSearchPath);
            }
            return detectMapperPackages(resources);
        } catch (IOException e) {
            LOGGER.warn("自动检测 Mapper 包时发生 IO 异常，将跳过自动检测。用户可通过配置 {} 手动指定",
                    MybatisConstants.CONFIG_MAPPER_PACKAGES, e);
        } catch (Exception e) {
            LOGGER.warn("自动检测 Mapper 包时发生未知异常，将跳过自动检测。用户可通过配置 {} 手动指定",
                    MybatisConstants.CONFIG_MAPPER_PACKAGES, e);
        }

        return new LinkedHashSet<>();
    }

    /**
     * 基于给定资源执行包发现，供包内测试构造可控的坏资源和正常资源组合。
     *
     * @param resources 待解析的 classpath 资源
     * @return 检测到的外部 Mapper 包通配符集合
     */
    static Set<String> detectMapperPackages(Resource... resources) {
        if (resources == null) {
            return new LinkedHashSet<>();
        }

        Set<String> detectedPackages = new LinkedHashSet<>();
        for (Resource resource : resources) {
            addDetectedPackage(detectedPackages, resource);
        }

        Set<String> mapperPackages = toExternalMapperPackages(detectedPackages);
        if (!mapperPackages.isEmpty() && LOGGER.isDebugEnabled()) {
            LOGGER.debug("自动检测到 {} 个 Mapper 包: {}", mapperPackages.size(), mapperPackages);
        }
        return mapperPackages;
    }

    private static void addDetectedPackage(Set<String> detectedPackages, Resource resource) {
        String packageName = detectMapperPackage(resource);
        if (isMapperPackage(packageName)) {
            detectedPackages.add(packageName);
        }
    }

    private static String detectMapperPackage(Resource resource) {
        if (resource == null) {
            LOGGER.warn("跳过无法处理的 Mapper 资源 resource=null, reason=resource is null");
            return null;
        }

        String resourceDescription = describeResource(resource);
        try {
            if (resource.isReadable()) {
                return extractPackageFromResource(resource);
            }
            LOGGER.warn("跳过不可读的 Mapper 资源 resource={}, reason=resource is not readable",
                    resourceDescription);
        } catch (Exception e) {
            LOGGER.warn("跳过无法处理的 Mapper 资源 resource={}, reason={}",
                    resourceDescription, messageOf(e), e);
        }
        return null;
    }

    private static boolean isMapperPackage(String packageName) {
        return StringUtils.hasText(packageName)
                && packageName.endsWith(MybatisConstants.MAPPER_PACKAGE_SUFFIX);
    }

    private static Set<String> toExternalMapperPackages(Set<String> detectedPackages) {
        Set<String> mapperPackages = new LinkedHashSet<>();
        for (String pkg : detectedPackages) {
            if (!pkg.startsWith(MybatisConstants.DEFAULT_PACKAGE_PREFIX + ".")) {
                mapperPackages.add(pkg + MybatisConstants.PACKAGE_WILDCARD_SUFFIX);
            }
        }
        return mapperPackages;
    }

    /**
     * 从资源中提取包名。
     *
     * @param resource 资源对象
     * @return 包名，如果无法提取则返回 null
     */
    @SuppressWarnings("java:S2583") // 自定义 Resource 可能返回 null；IOException 降级契约仍需保留
    private static String extractPackageFromResource(Resource resource) {
        String resourceDescription = describeResource(resource);
        try {
            URL resourceUrl = resource.getURL();
            if (resourceUrl == null) {
                LOGGER.warn("跳过无法解析的 Mapper 资源 resource={}, reason=resource URL is null",
                        resourceDescription);
                return null;
            }

            String url = resourceUrl.toExternalForm();
            String packageName = extractPackageFromUrl(url);
            if (!StringUtils.hasText(packageName)) {
                LOGGER.warn("跳过无法解析的 Mapper 资源 resource={}, reason=缺少合法 !/、classes 根目录或 Mapper 包路径",
                        resourceDescription);
            }
            return packageName;
        } catch (IOException e) {
            LOGGER.warn("跳过无法读取的 Mapper 资源 resource={}, reason={}",
                    resourceDescription, messageOf(e), e);
            return null;
        } catch (Exception e) {
            LOGGER.warn("跳过无法处理的 Mapper 资源 resource={}, reason={}",
                    resourceDescription, messageOf(e), e);
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
        if (!StringUtils.hasText(url)) {
            return null;
        }

        String normalizedUrl = url.replace('\\', '/');
        int mapperIndex = normalizedUrl.lastIndexOf(MybatisConstants.MAPPER_PACKAGE_SEPARATOR);
        if (mapperIndex <= 0) {
            return null;
        }

        String pathBeforeMapper = extractPathBeforeMapper(normalizedUrl, mapperIndex);
        if (pathBeforeMapper == null) {
            return null;
        }

        String packagePath = extractClassesRelativePath(pathBeforeMapper);
        if (packagePath == null && normalizedUrl.contains(MybatisConstants.JAR_SEPARATOR)) {
            packagePath = pathBeforeMapper;
        }
        if (!StringUtils.hasText(packagePath)) {
            return null;
        }
        return packagePath.replace('/', '.') + MybatisConstants.MAPPER_PACKAGE_SUFFIX;
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
        if (beforeMapper.endsWith("!")) {
            return "";
        }
        if (beforeMapper.contains(MybatisConstants.JAR_SEPARATOR)) {
            int jarIndex = beforeMapper.lastIndexOf(MybatisConstants.JAR_SEPARATOR);
            return beforeMapper.substring(jarIndex + MybatisConstants.JAR_SEPARATOR.length());
        }
        return beforeMapper;
    }

    private static String extractClassesRelativePath(String pathBeforeMapper) {
        int classesIndex = pathBeforeMapper.lastIndexOf(MybatisConstants.CLASSES_DIR);
        if (classesIndex < 0) {
            return null;
        }
        return pathBeforeMapper.substring(classesIndex + MybatisConstants.CLASSES_DIR.length());
    }

    private static String describeResource(Resource resource) {
        try {
            return resource.getDescription();
        } catch (Exception e) {
            return resource.getClass().getName();
        }
    }

    private static String messageOf(Exception exception) {
        return StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
    }

}
