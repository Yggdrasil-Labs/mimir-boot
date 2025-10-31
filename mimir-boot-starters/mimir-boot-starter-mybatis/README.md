# Mimir Boot Starter MyBatis

基于 MyBatis-Plus 的企业级持久层启动器，提供开箱即用的增强功能和最佳实践。

## 用途

提供完整的 MyBatis-Plus 集成方案：

- ✅ **自动配置拦截器**（分页、乐观锁、JSON SQL日志）
- ✅ **审计字段自动填充**（createdBy、createdTime、updatedBy、updatedTime）
- ✅ **字段加解密支持**（String、Integer、Long 类型）
- ✅ **SQL 日志结构化输出**（JSON 格式，支持敏感信息脱敏）
- ✅ **分页工具类**（与通用分页模型无缝转换）

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-mybatis</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**注意**：需要同时配置数据源（如 HikariCP、Druid 等）。

### 基础配置

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/dbname?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: password

# MyBatis-Plus 配置（可选）
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  type-aliases-package: com.example.entity
  configuration:
    map-underscore-to-camel-case: true
```

## 核心功能

### 1. 自动配置拦截器

Starter 会自动配置以下拦截器：

#### 分页拦截器

自动注册分页拦截器，支持分页查询：

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 分页查询会自动拦截
    IPage<User> selectUserPage(Page<User> page, String name);
}
```

#### 乐观锁拦截器

自动启用乐观锁功能，需要在实体类字段上添加 `@Version` 注解：

```java
import com.baomidou.mybatisplus.annotation.Version;

public class User {
    @Version
    private Integer version;  // 乐观锁字段
    // ...
}
```

### 2. 审计字段自动填充

#### 启用审计功能

审计功能默认启用，自动填充以下字段：
- `createdBy` - 创建人
- `createdTime` - 创建时间
- `updatedBy` - 更新人
- `updatedTime` - 更新时间

#### 实体类示例

```java
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_user")
public class User {
    private Long id;
    private String username;
    
    // 审计字段
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    
    // getters and setters...
}
```

#### 自定义审计提供器

默认使用 "system" 作为审计主体，可通过实现 `AuditorProvider` 接口自定义：

```java
import com.yggdrasil.labs.mybatis.audit.AuditorProvider;
import org.springframework.stereotype.Component;

@Component
public class UserAuditorProvider implements AuditorProvider {
    @Override
    public String currentAuditor() {
        // 从 Spring Security 或其他上下文获取当前用户
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
    }
}
```

### 3. 字段加解密

#### 配置密钥

**生产环境必须配置密钥**，开发测试环境可自动生成（不推荐用于生产）：

```yaml
mimir:
  mybatis:
    crypto-key: BASE64_ENCODED_KEY_HERE  # Base64 编码的密钥
```

#### 使用加密 TypeHandler

在实体类字段上使用对应的 TypeHandler：

```java
import com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler;
import org.apache.ibatis.type.TypeHandler;

@TableName("user")
public class User {
    @TableId
    private Long id;
    
    // String 类型加密
    @TableField(typeHandler = StringCryptoTypeHandler.class)
    private String phoneNumber;
    
    // Long 类型加密
    @TableField(typeHandler = LongCryptoTypeHandler.class)
    private Long accountId;
    
    // Integer 类型加密
    @TableField(typeHandler = IntegerCryptoTypeHandler.class)
    private Integer secretCode;
}
```

#### 自定义密钥提供器

```java
import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import org.springframework.stereotype.Component;

@Component
public class CustomCryptoKeyProvider implements CryptoKeyProvider {
    @Override
    public String getKey() {
        // 从配置文件、环境变量或密钥管理服务获取
        return System.getenv("MYBATIS_CRYPTO_KEY");
    }
}
```

**安全提示**：
- 生产环境请使用密钥管理服务（如 Vault、KMS）动态获取密钥
- 当前实现使用 AES/ECB 模式，仅用于演示；生产环境建议使用 AES-GCM 等更安全的模式

### 4. JSON SQL 日志

#### 启用 JSON SQL 日志

开发/测试环境默认启用，生产环境需要显式配置：

```yaml
mimir:
  mybatis:
    enable-json-sql-log: true  # 启用 JSON 格式 SQL 日志
```

#### 日志输出示例

```json
{"sql":"SELECT id,username FROM user WHERE id = ?","params":{"id":123}}
```

#### 敏感信息脱敏

使用 `@SensitiveField` 注解标记敏感字段，日志输出时自动脱敏：

```java
import com.yggdrasil.labs.mybatis.annotation.SensitiveField;

public class UserQuery {
    private Long id;
    
    @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
    private String phone;
    
    @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
    private String idCard;
    
    @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
    private String email;
}
```

**支持的脱敏策略**：

| 策略 | 说明 | 示例 |
|------|------|------|
| `ALL` | 全部脱敏 | `13812345678` → `******` |
| `PHONE` | 保留前3位后4位 | `13812345678` → `138****5678` |
| `ID_CARD` | 保留前6位后4位 | `110101199001011234` → `110101********1234` |
| `BANK_CARD` | 保留前4位后4位 | `6222021234567890` → `6222****7890` |
| `EMAIL` | 邮箱脱敏 | `user@example.com` → `u****@example.com` |
| `CUSTOM` | 自定义替换字符 | 使用 `replacement` 属性指定 |

### 5. 分页工具类

提供便捷的分页转换工具：

```java
import com.yggdrasil.labs.mybatis.page.PageConverters;
import com.yggdrasil.labs.common.page.PageRequest;
import com.yggdrasil.labs.common.page.PageResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@RestController
public class UserController {
    
    @Autowired
    private UserMapper userMapper;
    
    @GetMapping("/users")
    public PageResult<User> listUsers(PageRequest pageRequest) {
        // 将通用分页请求转换为 MyBatis-Plus Page
        Page<User> page = PageConverters.toMybatisPage(pageRequest);
        
        // 执行分页查询
        IPage<User> result = userMapper.selectUserPage(page, null);
        
        // 转换为通用分页结果
        return PageConverters.toPageResult(result);
    }
}
```

## 配置参数

### MyBatis 配置

所有配置项前缀为 `mimir.mybatis`：

```yaml
mimir:
  mybatis:
    # Mapper 扫描包（多个用逗号分隔）
    mapper-packages:
      - com.example.mapper
      - com.example.other.mapper
    
    # 是否启用控制台 SQL 日志（开发/测试环境默认 true）
    enable-sql-stdout: true
    
    # 是否启用 JSON SQL 日志（开发/测试环境默认 true）
    enable-json-sql-log: true
    
    # 加解密密钥（Base64 编码），生产环境必须配置
    crypto-key: YOUR_BASE64_ENCODED_KEY
```

### MyBatis-Plus 标准配置

```yaml
mybatis-plus:
  # Mapper XML 文件位置
  mapper-locations: classpath*:mapper/**/*Mapper.xml
  
  # 实体类包路径（别名）
  type-aliases-package: com.example.entity
  
  # MyBatis 配置
  configuration:
    # 驼峰命名转换
    map-underscore-to-camel-case: true
    # 日志实现（可选，已通过 starter 自动配置）
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  
  # 全局配置
  global-config:
    # 数据库类型
    db-config:
      db-type: mysql
      # 逻辑删除字段
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

## 使用示例

### 完整的 CRUD 示例

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.ibatis.annotations.Mapper;

// 1. 实体类
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    
    // 审计字段（自动填充）
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
}

// 2. Mapper 接口
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后即可使用 CRUD 方法
    // selectById, insert, updateById, deleteById 等
}

// 3. Service 接口
public interface UserService extends IService<User> {
    // 可以添加自定义业务方法
    User findByUsername(String username);
}

// 4. Service 实现
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Override
    public User findByUsername(String username) {
        return lambdaQuery()
            .eq(User::getUsername, username)
            .one();
    }
}

// 5. Controller
@RestController
@RequestMapping("/api/users")
public class UserController {
    @Autowired
    private UserService userService;
    
    @PostMapping
    public User createUser(@RequestBody User user) {
        // 审计字段会自动填充
        userService.save(user);
        return user;
    }
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);
    }
    
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        // 审计字段会自动更新
        userService.updateById(user);
        return user;
    }
}
```

### 条件构造器示例

```java
@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;
    
    public List<User> findActiveUsers() {
        return userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, "ACTIVE")
                .orderByDesc(User::getCreatedTime)
        );
    }
    
    public void deleteInactiveUsers() {
        userMapper.delete(
            new LambdaQueryWrapper<User>()
                .eq(User::getStatus, "INACTIVE")
                .lt(User::getUpdatedTime, LocalDateTime.now().minusMonths(6))
        );
    }
}
```

## 最佳实践

### 1. 实体类设计

```java
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 业务字段
    private String username;
    private String email;
    
    // 审计字段（推荐统一命名）
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    
    // 逻辑删除字段（可选）
    @TableLogic
    private Integer deleted;
    
    // 乐观锁字段（可选）
    @Version
    private Integer version;
}
```

### 2. 敏感字段处理

```java
public class User {
    // 加密存储手机号
    @TableField(typeHandler = StringCryptoTypeHandler.class)
    private String phoneNumber;
    
    // 加密存储身份证号
    @TableField(typeHandler = StringCryptoTypeHandler.class)
    private String idCard;
}
```

### 3. 日志脱敏

```java
public class UserQuery {
    @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
    private String phone;
    
    @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
    private String idCard;
}
```

### 4. 分页查询

```java
@GetMapping("/users")
public PageResult<User> listUsers(
    @RequestParam(defaultValue = "1") Long pageIndex,
    @RequestParam(defaultValue = "10") Long pageSize,
    @RequestParam(required = false) String keyword) {
    
    PageRequest pageRequest = new PageRequest(pageIndex, pageSize);
    Page<User> page = PageConverters.toMybatisPage(pageRequest);
    
    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
        wrapper.like(User::getUsername, keyword);
    }
    
    IPage<User> result = userMapper.selectPage(page, wrapper);
    return PageConverters.toPageResult(result);
}
```

### 5. 自定义拦截器

```java
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisConfig {
    
    @Bean
    public InnerInterceptor customInterceptor() {
        return new CustomInterceptor();
    }
}
```

## 常见问题

### 问题1：审计字段未自动填充

**症状**：插入/更新记录时，`createdBy`、`updatedTime` 等字段为 null

**解决方案**：
1. 确认实体类字段名与 `AuditMetaObjectHandler` 中定义的字段名一致
2. 检查是否注册了自定义 `MetaObjectHandler`（会覆盖默认配置）

```java
// 如果实体类字段名不同，需要自定义 MetaObjectHandler
@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        strictInsertFill(metaObject, "creator", String.class, "system");
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

### 问题2：分页查询不生效

**症状**：使用 `Page` 对象查询，但没有进行分页

**解决方案**：
1. 确认已引入 `mimir-boot-starter-mybatis` 依赖
2. 确认使用了正确的 `Page` 类：

```java
// ✅ 正确
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
Page<User> page = new Page<>(1, 10);

// ❌ 错误
import com.github.pagehelper.Page;  // 这是 PageHelper 的类
```

### 问题3：加密字段查询异常

**症状**：查询加密字段时抛出解密异常

**解决方案**：
1. 确认使用了对应的 TypeHandler
2. 检查密钥配置是否正确
3. 确认数据库中的值是加密后的 Base64 字符串

```java
// 确认 TypeHandler 配置正确
@TableField(typeHandler = StringCryptoTypeHandler.class)
private String encryptedField;
```

### 问题4：JSON SQL 日志未输出

**症状**：配置了 `enable-json-sql-log: true`，但没有 JSON 日志

**解决方案**：
1. 检查日志级别配置（JSON 日志使用 INFO 级别）
2. 确认日志记录器名称：`SQL.JSON`
3. 检查环境配置（开发/测试环境默认启用）

```yaml
logging:
  level:
    SQL.JSON: INFO  # 确保日志级别足够
```

## 技术栈

- **MyBatis-Plus**: 3.5.14+
- **MyBatis**: 3.5.19+
- **Spring Boot**: 3.3.13+
- **Java**: 17+

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs

