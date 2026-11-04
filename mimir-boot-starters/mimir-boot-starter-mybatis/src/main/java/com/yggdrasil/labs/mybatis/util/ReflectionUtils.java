package com.yggdrasil.labs.mybatis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.config.MybatisConstants;

/**
 * 反射工具类。 用于通过反射创建对象，主要用于兼容不同版本的依赖。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class ReflectionUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionUtils.class);

    private ReflectionUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 尝试创建分页拦截器。 为了兼容不同版本的 MyBatis-Plus，通过反射可选加载分页拦截器。
     *
     * @return 分页拦截器实例，如果无法创建则返回 null
     */
    public static InnerInterceptor createPaginationInnerInterceptor() {
        return createPaginationInnerInterceptor(MybatisConstants.PAGINATION_INTERCEPTOR_CLASS_NAME);
    }

    /** 创建框架默认分页拦截器；可选分页依赖缺失时仍保持反射加载边界。 */
    public static InnerInterceptor createConfigurationScopedPaginationInnerInterceptor() {
        return createPaginationInnerInterceptor(
                "com.yggdrasil.labs.mybatis.page.ConfigurationScopedPaginationInnerInterceptor");
    }

    private static InnerInterceptor createPaginationInnerInterceptor(String className) {
        try {
            // 先检查可选的上游父类，防止加载扩展类时因缺少父类产生 NoClassDefFoundError。
            Class.forName(MybatisConstants.PAGINATION_INTERCEPTOR_CLASS_NAME);
            Class<?> clazz = Class.forName(className);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            return (InnerInterceptor) instance;
        } catch (ClassNotFoundException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("未找到分页拦截器类，可能使用了不包含分页功能的 MyBatis-Plus 版本");
            }
            return null;
        } catch (NoSuchMethodException e) {
            LOGGER.warn("分页拦截器类缺少无参构造方法", e);
            return null;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("创建分页拦截器实例失败", e);
            return null;
        } catch (ClassCastException e) {
            LOGGER.warn("分页拦截器类无法转换为 InnerInterceptor", e);
            return null;
        }
    }
}
