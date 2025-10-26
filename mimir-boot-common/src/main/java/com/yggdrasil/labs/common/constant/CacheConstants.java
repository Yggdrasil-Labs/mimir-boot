package com.yggdrasil.labs.common.constant;

/**
 * 缓存常量
 * 
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class CacheConstants {
    
    /**
     * 默认缓存名称
     */
    public static final String DEFAULT_CACHE_NAME = "default";
    
    /**
     * 用户缓存名称
     */
    public static final String USER_CACHE_NAME = "user";
    
    /**
     * 角色缓存名称
     */
    public static final String ROLE_CACHE_NAME = "role";
    
    /**
     * 权限缓存名称
     */
    public static final String PERMISSION_CACHE_NAME = "permission";
    
    /**
     * 字典缓存名称
     */
    public static final String DICT_CACHE_NAME = "dict";
    
    /**
     * 配置缓存名称
     */
    public static final String CONFIG_CACHE_NAME = "config";
    
    /**
     * 默认缓存过期时间（秒）
     */
    public static final long DEFAULT_EXPIRE_TIME = 3600L;
    
    /**
     * 短期缓存过期时间（秒）
     */
    public static final long SHORT_EXPIRE_TIME = 300L;
    
    /**
     * 中期缓存过期时间（秒）
     */
    public static final long MEDIUM_EXPIRE_TIME = 1800L;
    
    /**
     * 长期缓存过期时间（秒）
     */
    public static final long LONG_EXPIRE_TIME = 7200L;
    
    /**
     * 用户缓存过期时间（秒）
     */
    public static final long USER_EXPIRE_TIME = 1800L;
    
    /**
     * 角色缓存过期时间（秒）
     */
    public static final long ROLE_EXPIRE_TIME = 3600L;
    
    /**
     * 权限缓存过期时间（秒）
     */
    public static final long PERMISSION_EXPIRE_TIME = 3600L;
    
    /**
     * 字典缓存过期时间（秒）
     */
    public static final long DICT_EXPIRE_TIME = 7200L;
    
    /**
     * 配置缓存过期时间（秒）
     */
    public static final long CONFIG_EXPIRE_TIME = 3600L;
    
    /**
     * 缓存键前缀
     */
    public static final String CACHE_KEY_PREFIX = "mimir:";
    
    /**
     * 用户缓存键前缀
     */
    public static final String USER_CACHE_KEY_PREFIX = CACHE_KEY_PREFIX + "user:";
    
    /**
     * 角色缓存键前缀
     */
    public static final String ROLE_CACHE_KEY_PREFIX = CACHE_KEY_PREFIX + "role:";
    
    /**
     * 权限缓存键前缀
     */
    public static final String PERMISSION_CACHE_KEY_PREFIX = CACHE_KEY_PREFIX + "permission:";
    
    /**
     * 字典缓存键前缀
     */
    public static final String DICT_CACHE_KEY_PREFIX = CACHE_KEY_PREFIX + "dict:";
    
    /**
     * 配置缓存键前缀
     */
    public static final String CONFIG_CACHE_KEY_PREFIX = CACHE_KEY_PREFIX + "config:";
    
    /**
     * 私有构造方法，防止实例化
     */
    private CacheConstants() {
    }
}
