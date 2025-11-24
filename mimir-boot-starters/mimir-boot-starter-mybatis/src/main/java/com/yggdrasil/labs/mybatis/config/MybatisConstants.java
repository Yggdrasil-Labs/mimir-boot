package com.yggdrasil.labs.mybatis.config;

/**
 * MyBatis 相关常量。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class MybatisConstants {

    private MybatisConstants() {
        // 工具类，禁止实例化
    }

    /** Mapper 包路径分隔符 */
    public static final String MAPPER_PACKAGE_SEPARATOR = "/mapper/";

    /** Mapper 包后缀 */
    public static final String MAPPER_PACKAGE_SUFFIX = ".mapper";

    /** 包路径通配符后缀 */
    public static final String PACKAGE_WILDCARD_SUFFIX = ".**";

    /** 默认包前缀 */
    public static final String DEFAULT_PACKAGE_PREFIX = "com.yggdrasil.labs";

    /** Classes 目录名 */
    public static final String CLASSES_DIR = "/classes/";

    /** Jar 包分隔符 */
    public static final String JAR_SEPARATOR = "!/";

    /** 分页拦截器类名 */
    public static final String PAGINATION_INTERCEPTOR_CLASS_NAME =
            "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor";

    /** 开发环境名称 */
    public static final String PROFILE_DEV = "dev";

    /** 测试环境名称 */
    public static final String PROFILE_TEST = "test";

    /** Mapper 扫描路径模式 */
    public static final String MAPPER_SCAN_PATTERN = "**/mapper/*.class";

    /** 配置属性前缀 */
    public static final String CONFIG_PREFIX = "mimir.boot.mybatis";

    /** Mapper 包配置属性名 */
    public static final String CONFIG_MAPPER_PACKAGES = CONFIG_PREFIX + ".mapper-packages";
}

