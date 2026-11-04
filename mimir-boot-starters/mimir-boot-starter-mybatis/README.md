# Mimir Boot Starter MyBatis

基于 MyBatis-Plus 的企业级持久层启动器，提供开箱即用的增强功能和最佳实践。

## 用途

提供完整的 MyBatis-Plus 集成方案：

- ✅ **自动配置拦截器**（分页、乐观锁；JSON SQL 日志按环境和配置启用）
- ✅ **审计字段自动填充**（createBy、createTime、updateBy、updateTime）
- ✅ **字段加解密支持**（String、Integer、Long 类型）
- ✅ **SQL 日志结构化输出**（JSON 格式，支持敏感信息脱敏）
- ✅ **独立慢 SQL 日志**（阈值可配置，默认不输出 SQL 原值）
- ✅ **分页工具类**（与通用分页模型无缝转换）

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-mybatis</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</dependency>
```

**注意**：

- 需要同时配置数据源（如 HikariCP、Druid 等）
- 从 MyBatis-Plus 3.5.9 开始，分页插件需要单独引入 `mybatis-plus-jsqlparser` 依赖，本 Starter 已自动包含

### 与 Processor 配合使用

如果使用了 `mimir-boot-starter-mybatis-processor` 自动生成 Mapper，Starter 会自动检测并扫描生成的 Mapper，无需额外配置：

- ✅ **自动检测**：自动扫描所有以 `.mapper` 结尾的包（processor 默认生成的包路径）
- ✅ **无需配置**：即使 Mapper 不在 `com.yggdrasil.labs` 包下，也会被自动扫描到
- ✅ **支持自定义**：如果 processor 使用了自定义的 `mapperPackage`，可以通过配置 `mimir.boot.mybatis.mapper-packages` 手动指定

**示例**：

```java
// 实体类在 com.example.entity 包下
package com.example.entity;

@AutoMybatis  // processor 会生成 com.example.entity.mapper.UserMapper
@TableName("sys_user")
public class User {
    // ...
}
```

生成的 `com.example.entity.mapper.UserMapper` 会被自动扫描到，无需额外配置。

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

### 重要提示：避免 Mapper 重复注册

**问题**：如果 Mapper 接口上同时使用了 `@Mapper` 注解，并且 Spring 的组件扫描也扫描到了该包，可能会导致 Mapper 被重复注册，出现警告：

```
Skipping MapperFactoryBean with name 'xxxMapper' and 'xxx.xxx.mapper.XxxMapper' mapperInterface. Bean already defined with the same name!
```

**解决方案**：

1. **推荐方式**：在 Mapper 接口上使用 `@Mapper` 注解，Starter 会自动扫描并注册（推荐）

   ```java
   @Mapper
   public interface UserMapper extends BaseMapper<User> {
       // ...
   }
   ```

2. **如果使用 `@MapperScan`**：移除 Mapper 接口上的 `@Mapper` 注解，统一使用 `@MapperScan` 扫描

   ```java
   @SpringBootApplication
   @MapperScan("com.example.mapper")
   public class Application {
       // ...
   }
   ```

3. **确保组件扫描不包含 Mapper 包**：如果 Mapper 接口上没有 `@Mapper` 注解，确保 Spring 的组件扫描不会扫描到 Mapper 接口所在的包

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

审计功能默认启用。实体类需要为以下字段声明对应的 `@TableField(fill = ...)`，处理器才会自动填充：

- `createBy` - 创建人
- `createTime` - 创建时间
- `updateBy` - 更新人
- `updateTime` - 更新时间

#### 实体类示例

```java
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("sys_user")
public class User {
    private Long id;
    private String username;
    
    // 审计字段
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
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

#### 启用加密功能

**加密功能默认关闭**，需要显式启用：

```yaml
mimir:
  boot:
    mybatis:
      crypto-enabled: true  # 启用加密功能（默认 false）
      crypto-key: ${MYBATIS_CRYPTO_KEY}  # Base64 编码的 128/192/256 位稳定 AES 密钥（启用时必填）
```

> ⚠️ **注意**：如果不启用加密功能，即使字段上配置了 `typeHandler = StringCryptoTypeHandler.class`，也不会生效。

#### 使用加密 TypeHandler

启用加密只注册供显式映射使用的 Handler，普通 `String`、`Long`、`Integer` 字段仍使用原有类型处理器。在需要加密的实体字段上指定对应 Handler，并启用 `autoResultMap` 以便查询结果也使用该 Handler：

```java
import com.yggdrasil.labs.mybatis.typehandler.StringCryptoTypeHandler;

@TableName(value = "user", autoResultMap = true)
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

密文列应使用可容纳完整密文的字符类型，包括加密的 `Long`、`Integer` 字段。自定义 XML、结果映射及手工构造的参数需要显式配置对应 `typeHandler`；实体注解不会自动覆盖所有自定义 SQL。

升级前请检查是否曾依赖旧版意外的全局类型注册保存密文。此类列需要补充显式字段映射或单独迁移；普通字段不会自动识别和解密既有密文。

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

- 启用字段加密时，必须配置 `crypto-key` 或提供返回稳定密钥的 `CryptoKeyProvider`；Starter 不会生成临时密钥。
- 生产环境请使用密钥管理服务（如 Vault、KMS）动态获取密钥
- 当前实现使用带随机 IV 的 AES-GCM，密文包含认证标签；请妥善轮换和保管稳定密钥
- `crypto-context` 是应用级稳定标识，用于 v2 密文的 AAD；它不提供字段或记录级完整性。
- `crypto-v2-write-enabled` 默认关闭。迁移时先配置相同 context 让所有实例具备 v2 读取能力并继续写 v1，完成列长度预检和全量升级后再统一开启；写入 v2 后只能回退到支持 v2 且使用相同 context 的版本。

### 4. JSON SQL 日志

#### 启用 JSON SQL 日志

未显式配置时，`dev`、`local`、`development`、`test` profile 默认启用；其他环境需要显式配置：

```yaml
mimir:
  boot:
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

对于参数 `Map`（包括多个 `@Param` 参数），`password`、`passwd`、`pwd`、`token`、`access_token`、`refresh_token`、`id_token`、`secret`、`client_secret`、`authorization` 和 `api_key` 会自动替换为 `****`。参数名不区分大小写，并兼容 `_`、`-` 分隔；其他敏感字段仍应使用 `@SensitiveField` 显式标注。

**支持的脱敏策略**：

| 策略 | 说明 | 示例 |
|------|------|------|
| `ALL` | 全部脱敏 | `13812345678` → `****` |
| `PHONE` | 保留前3位后4位 | `13812345678` → `138****5678` |
| `ID_CARD` | 保留前6位后4位 | `110101199001011234` → `110101****1234` |
| `BANK_CARD` | 保留前4位后4位 | `6222021234567890` → `6222****7890` |
| `EMAIL` | 邮箱脱敏 | `user@example.com` → `u****@example.com` |
| `CUSTOM` | 自定义替换字符 | 使用 `@SensitiveField` 的 `replacement` 属性指定 |

#### 慢 SQL 日志

慢 SQL 与上述 `SQL.JSON` 分别配置。默认对受支持的语句调用计时，耗时严格大于 1000 毫秒时向 `SQL.SLOW` 记录一条 WARN，不要求开启 JSON SQL 日志或 Mapper DEBUG 日志：

```yaml
mimir:
  boot:
    mybatis:
      slow-sql:
        enabled: true
        threshold-ms: 1000
```

`enabled` 默认 `true`；`threshold-ms` 默认 `1000`，合法范围为 1 至 9223372036854 毫秒，关闭记录时也会校验。配置在启动时绑定，不承诺动态刷新。将 `SQL.SLOW` 的 WARN 级别关闭也会跳过计时及模板处理。可以提供自己的 `SlowSqlInterceptor` Bean 替换默认实例；若使用子类，须按 MyBatis 插件约定在子类重新声明 `@Intercepts`，覆盖 query、update 和 queryCursor 三种签名。

日志消息为单行 JSON，含 `statementId`、`operation`、`elapsedMs`、`thresholdMs`、`timingScope`、`outcome`、`errorType`、`sql` 和 `sqlStatus`。`ERROR` 是执行结果字段，日志级别仍为 WARN。失败时只记录异常类名，不记录异常消息、堆栈、参数或原始 SQL；常规日志故障不会改变 SQL 的返回值或异常。不要在动态构造的 `statementId` 中放入秘密。

`sql` 是安全结构模板，并非可执行 SQL：表名、列名、字面量和参数替换为 `?`；函数名通常也被替换，例如 `COUNT(*)`，但固定白名单中的 `LEFT` 和 `RIGHT` 即使作为函数名也会保留。`IS NULL` 的 `NULL` 会被替换。模板器只接受有限的单条 DML 文法，别名须显式使用 `AS`，嵌套深度最多 32 层。不支持的语法或原始 SQL 超过 65536 个字符时，`sql` 为 `<omitted>` 并由 `sqlStatus` 说明原因。省略的情况包括 `WITH` 开头的 CTE、`t.*`、带引号的标识符、`CAST`、窗口函数、PostgreSQL `::`、`||` 和 JSON 运算符、可执行注释及无法明确判断的方言引号或注释。模板生成失败不会回退到原文，也不影响 SQL 本身执行。应根据 `statementId` 定位 Mapper；该保护只适用于 `SQL.SLOW`，不会改变 `SQL.JSON` 的既有脱敏范围。

计时覆盖 `StatementHandler` 的 query、update 和 cursor 创建；query 包含结果映射与嵌套调用，cursor 不包含后续遍历。分页 count 和主查询、`selectKey` 与外层更新分别计时，嵌套耗时可能重叠，不能相加为数据库独占时间。缓存命中未执行语句时不记录；连接获取、prepare、batch 添加和 flush 不在本能力覆盖范围。

框架默认分页插件使用当前工厂配置创建 COUNT 语句，避免 MyBatis-Plus 3.5.17 的静态 COUNT 缓存在多个工厂之间复用配置。应用自行提供 `MybatisPlusInterceptor` 或分页插件时，需自行处理该上游缓存限制；否则相同 Mapper 语句 ID 的 COUNT 可能沿用先前工厂的拦截器或阈值。

框架只为 **MyBatis-Plus 自动创建的 `SqlSessionFactory`** 自动接入一次慢 SQL 拦截器，包括使用路由 DataSource 的单工厂场景。应用声明任何自建 `SqlSessionFactory` 后，MyBatis-Plus 不再创建默认工厂；自建工厂未安装拦截器时不会产生慢 SQL 记录。如果工厂已经注入完整 `Interceptor[]` 并调用 `setPlugins`，慢 SQL 拦截器已包含在内，不应重复添加：

```java
import javax.sql.DataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;

@Bean
SqlSessionFactory sqlSessionFactory(DataSource dataSource, Interceptor[] interceptors)
        throws Exception {
    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    // 完整数组同时保留 MybatisPlusInterceptor 等其他插件
    factory.setPlugins(interceptors);
    return factory.getObject();
}
```

`setPlugins` 会替换原插件数组，不能只传慢 SQL 拦截器而丢掉分页等插件。也可在工厂构建前使用 MyBatis-Plus 3.5.17 的 `MybatisSqlSessionFactoryBean.addPlugins(...)` 追加；或在工厂构建后、任何 SQL 执行之前使用 `Configuration.addInterceptor(...)` 追加。后者不允许与 SQL 运行并发修改插件链。上述方式均需先检查是否已注册，避免重复记录；其他 MyBatis-Plus 版本需自行复核 `addPlugins` 行为。

选择 Mimir Log 容器预设时 `SQL.SLOW` 输出到标准输出，服务器预设输出到 `sql.log`；未使用预设时由应用日志配置决定。同步输出或异步队列满时可能阻塞，配置为非阻塞时可能丢弃；产生日志事件不等于采集或持久化成功。

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

#### 转换为 COLA 分页

COLA DTO 是可选依赖，使用转换工具的项目须显式引入 `com.alibaba.cola:cola-component-dto`（版本可由 Mimir BOM 管理）：

```xml
<dependency>
    <groupId>com.alibaba.cola</groupId>
    <artifactId>cola-component-dto</artifactId>
</dependency>
```

未导入 Mimir BOM 的项目需要自行指定兼容版本。

```java
import com.alibaba.cola.dto.PageResponse;
import com.yggdrasil.labs.mybatis.page.IPageColaConverters;

PageResponse<User> sameItems = IPageColaConverters.toCola(page);
PageResponse<UserDto> mapped = IPageColaConverters.toCola(page, UserDto::from);
```

两个重载复制记录列表并保留分页元数据；映射时 null 元素原位保留，映射异常原样传播。`total` 必须非负，`current` 和 `size` 必须为正，三者均不能超过 `Integer.MAX_VALUE`。转换不查询数据库，也不推断总数。Common 模块的 `ColaPageConverters` 用于 `PageResult`，此处工具用于 `IPage`。

#### 不重复执行 COUNT

如果业务已经单独查到总数，可关闭分页自动 COUNT，并在转换前设置真实总数：

```java
long total = userMapper.selectCount(null);
Page<User> page = new Page<>(1, 20, false);
userMapper.selectPage(page, null);
page.setTotal(total);
PageResponse<User> result = IPageColaConverters.toCola(page);
```

如果只需要前 N 条，可使用 `new Page<>(1, n, false)` 查询并直接返回 `page.getRecords()`。此时没有查询总数，不能将默认的 `total=0` 当作真实总数返回。

## 配置参数

### MyBatis 配置

所有配置项前缀为 `mimir.boot.mybatis`：

```yaml
mimir:
  boot:
    mybatis:
      # Mapper 扫描包（多个用逗号分隔）；默认包始终包含
      mapper-packages:
        - com.example.mapper
        - com.example.other.mapper

      # 是否启用 JSON SQL 日志；未配置时仅 dev/local/development/test profile 默认启用
      enable-json-sql-log: true

      # 是否启用字段加解密功能（默认 false）
      crypto-enabled: true
      # 加解密密钥（Base64 编码的 128/192/256 位 AES 密钥），启用加密时必填
      crypto-key: ${MYBATIS_CRYPTO_KEY}
      # 应用级 v2 密文 context；启用 v2 写入时必须配置，且所有实例使用同一值
      crypto-context: ${MYBATIS_CRYPTO_CONTEXT}
      # 全量升级和列长度预检完成后才开启，默认 false
      crypto-v2-write-enabled: false
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
    # 日志实现由 starter 统一设置为 SLF4J，无需配置 log-impl
  
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
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
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
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
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

**症状**：插入/更新记录时，`createBy`、`updateTime` 等字段为 null

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
3. 检查环境配置（未配置时仅 `dev`、`local`、`development`、`test` profile 默认启用）

```yaml
logging:
  level:
    SQL.JSON: INFO  # 确保日志级别足够
```

## 技术栈

- **MyBatis-Plus**
- **MyBatis**
- **Spring Boot**: 3.3.13+
- **Java**: 17+

## 📚 相关文档

- [Mimir Boot 项目主页](../../README.md) - 项目总体说明
- [Mimir Boot Common](../../mimir-boot-common/README.md) - 公共组件说明（包含分页模型）
- [Mimir Boot Starter MyBatis Processor](../mimir-boot-starter-mybatis-processor/README.md) - MyBatis 编译期处理器（配合使用）

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs
