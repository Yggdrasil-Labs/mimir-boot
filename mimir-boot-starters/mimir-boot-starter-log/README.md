# Mimir Boot Starter Log

基于 Logback 的企业级日志启动器，提供统一的日志配置和最佳实践。

## 用途

提供开箱即用的日志解决方案：
- ✅ 控制台彩色输出（开发环境）
- ✅ 文件输出，自动滚动和压缩
- ✅ 错误日志单独文件存储
- ✅ 支持多环境配置（dev/test/prod）
- ✅ 异步写入，提升性能
- ✅ **敏感信息自动脱敏**（密码、账号、身份证号等）✨
- ✅ **TraceId & SpanId 支持**（与 Micrometer Tracing 无缝集成）🔍
- ✅ **访问日志**（access.log）：记录每个请求的 IP、URI、耗时、状态等信息，慢接口自动 WARN 📊

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-log</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**不需要排除任何依赖**，直接引入即可使用！

### 使用示例

#### 方式1：传统方式

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class HelloController {
    private static final Logger log = LoggerFactory.getLogger(HelloController.class);
    
    @GetMapping("/hello")
    public String hello() {
        log.info("Hello World");
        return "Hello World";
    }
}
```

#### 方式2：使用 Lombok（推荐）

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class HelloController {
    
    @GetMapping("/hello")
    public String hello() {
        log.info("Hello World");  // log 对象由 Lombok 自动生成
        return "Hello World";
    }
}
```

**提示**：项目需要引入 Lombok 依赖：
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

#### 方式3：与 Micrometer Tracing 集成（推荐）✨

**引入依赖**：

```xml
<!-- Micrometer Tracing -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<!-- 或 OpenTelemetry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**使用方式**：

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class TraceController {
    
    @GetMapping("/api/user/{id}")
    public String getUser(@PathVariable String id) {
        // 直接使用，TraceId 和 SpanId 由 Micrometer Tracing 自动注入
        log.info("查询用户: id={}", id);
        
        processRequest();
        
        log.info("请求处理完成");
        return "success";
    }
    
    private void processRequest() {
        log.debug("处理业务逻辑");
    }
}
```

**日志输出示例**：
```
2024-01-01 10:00:00.123 [http-nio-8080-exec-1] INFO  [a1b2c3d4e5f6] [c7d8e9f0] com.example.TraceController - 查询用户: id=123
                                                               ^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^
                                                                TraceId        SpanId
```

**所有日志都包含相同的 TraceId**，方便追踪整个请求链路！

#### Lombok 日志注解说明

Lombok 提供了多种日志注解：

| 注解 | 生成的 Logger 类型 | 说明 |
|------|-------------------|------|
| `@Slf4j` | `org.slf4j.Logger` | **推荐**，SLF4J |
| `@Log4j` | `org.apache.log4j.Logger` | Log4j 1.x |
| `@Log4j2` | `org.apache.logging.log4j.Logger` | Log4j 2.x |
| `@Log` | `java.util.logging.Logger` | Java Util Logging |

**我们的 Starter 使用 Logback + SLF4J，所以推荐使用 `@Slf4j`**。

生成的代码示例：

```java
@Slf4j
public class YourClass {
    // 等价于：
    // private static final Logger log = LoggerFactory.getLogger(YourClass.class);
    
    public void method() {
        log.info("Hello");  // log 对象自动生成
        log.debug("Debug");
        log.error("Error", e);
    }
}
```

## 配置参数

### 日志文件路径

**配置项**：`mimir.boot.log.path` 或环境变量 `LOG_PATH`

**默认值**：`logs`（项目运行目录下的 logs 文件夹）

**示例**：
```yaml
# application.yml
mimir:
  boot:
    log:
      path: /var/log/myapp
```

或者使用环境变量：
```bash
export LOG_PATH=/var/log/myapp
```

### 启用/禁用日志

**配置项**：`mimir.boot.log.enabled`

**默认值**：`true`

**示例**：
```yaml
mimir:
  boot:
    log:
      enabled: false  # 禁用日志文件输出
```

### 日志级别

**配置项**：`mimir.boot.log.level`

**默认值**：`INFO`

**可选项**：`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`

**示例**：
```yaml
mimir:
  boot:
    log:
      level: DEBUG
```

### 应用名称

**配置项**：`spring.application.name`

**默认值**：`application`

**说明**：影响日志文件名，如 `myapp.log`、`myapp-error.log`

**示例**：
```yaml
spring:
  application:
    name: myapp
```

### 访问日志配置

**说明**：自动记录每个 HTTP 请求的访问信息到独立的 `access.log` 文件

**配置项**：`mimir.boot.log.access`

**默认值**：
- `enabled=true` - 启用访问日志
- `slowThresholdMs=1000` - 慢接口阈值（毫秒）

**示例**：
```yaml
mimir:
  boot:
    log:
      access:
        enabled: true           # 启用访问日志
        slowThresholdMs: 2000   # 慢接口阈值为 2000 毫秒（2 秒）
```

**访问日志输出示例**：

**成功请求（INFO 级别）**：
```
2024-01-01 10:00:00.123 [http-nio-8080-exec-1] INFO  [a1b2c3d4e5f6] [c7d8e9f0] - IP=[192.168.1.100], Method=[GET], URI=[/api/user/123], Status=[200], Duration=[45ms], UserAgent=[Mozilla/5.0...]
```

**慢接口（WARN 级别）**：
```
2024-01-01 10:05:00.456 [http-nio-8080-exec-2] WARN  [b2c3d4e5f6a7] [d8e9f0a1] - IP=[192.168.1.101], Method=[POST], URI=[/api/export/report], Status=[200], Duration=[2150ms], UserAgent=[Apache-HttpClient/4.5] [慢接口]
```

**客户端错误（4xx - WARN 级别）**：
```
2024-01-01 10:10:00.789 [http-nio-8080-exec-3] WARN  [c3d4e5f6a7b8] [e9f0a1b2] - IP=[192.168.1.102], Method=[GET], URI=[/api/user/999], Status=[404], Duration=[12ms], UserAgent=[Mozilla/5.0...]
```

**服务器错误（5xx - ERROR 级别）**：
```
2024-01-01 10:15:00.012 [http-nio-8080-exec-4] ERROR [d4e5f6a7b8c9] [f0a1b2c3] - IP=[192.168.1.103], Method=[POST], URI=[/api/process], Status=[500], Duration=[230ms], UserAgent=[Mozilla/5.0...]
```

**特点**：
- 自动记录请求的 IP、HTTP 方法、URI、状态码、耗时、User-Agent
- 支持获取真实 IP（自动处理反向代理场景）
- **智能日志级别**（最佳实践）：
  - 2xx/3xx 成功/重定向：INFO（慢接口为 WARN）
  - 4xx 客户端错误：WARN（如 400、401、403、404、429）
  - 5xx 服务器错误：ERROR（如 500、502、503）
- 独立的日志文件，与业务日志分离，方便分析
- 异步写入，不影响业务性能
- **所有环境都会生成**：dev、test、prod 都会自动记录访问日志

### 敏感信息脱敏

**说明**：选择性启用脱敏功能，支持预置规则和自定义规则

**示例1：使用预置规则**

```yaml
mimir:
  boot:
    log:
      mask:
        enabledPatterns:                   # 启用指定的脱敏规则
          - password                        # 密码
          - token                           # Token
          - phone_number                    # 纯手机号
        replacement: "******"              # 替换字符
```

**示例2：预置规则 + 自定义规则**

```yaml
mimir:
  boot:
    log:
      mask:
        enabledPatterns:
          - password
          - phone
        customPatterns:                     # 自定义正则表达式
          - "(?i)(custom_field)\\s*[=:]\\s*['\"]?[^'\"\\s]+"
        replacement: "***"
```

**可用的预置规则**：

| 规则名称 | 说明 | 示例 |
|---------|------|------|
| `password` | 密码字段 | password=123456 |
| `token` | Token字段 | token=abc123 |
| `secret` | 密钥字段 | secret=xxx |
| `api_key` | API Key | apikey=xxx |
| `account` | 账号字段 | accountId=xxx |
| `id_card` | 证件字段 | idcard=xxx |
| `phone` | 手机号字段 | phone=13812345678 |
| `bank_card` | 银行卡字段 | bankcard=xxx |
| `email` | 邮箱字段 | email=xxx@example.com |
| `name` | 姓名字段 | name=张三 |
| `phone_number` | 纯手机号 | 13812345678 |
| `id_card_number` | 纯身份证号 | 110101199001011234 |
| `bank_card_number` | 纯银行卡号 | 6222021234567890 |
| `email_address` | 纯邮箱地址 | user@example.com |

**编程式扩展**：

```java
import com.yggdrasil.labs.log.converter.SensitiveDataConverter;

// 添加自定义规则
SensitiveDataConverter.addCustomPattern("(?i)(sensitive\\s+data)\\s*[=:]\\s*[^\\s]+");

// 获取所有可用的预置规则
List<String> allPatterns = SensitiveDataConverter.getAllPresetPatternNames();

// 清空自定义规则
SensitiveDataConverter.clearCustomPatterns();
```

### 日志格式说明

**默认日志格式**：

```
2024-01-01 10:00:00.123 [http-nio-8080-exec-1] INFO  [a1b2c3d4e5f6] [c7d8e9f0] com.example.Service - 查询用户
├────────┬─────────────────┬─────┬──────────┬──────────┬────────────────┐
│  时间  │     线程名       │级别 │ TraceId  │ SpanId   │   日志内容     │
│        │                 │     │          │          │               │
│        │                 │     │          │          │               │
```

**TraceId 和 SpanId**：
- 由 Micrometer Tracing 自动注入到 MDC
- 日志格式中通过 `%X{traceId}` 和 `%X{spanId}` 输出
- 所有日志自动包含，无需手动配置

### MDC 扩展支持

**说明**：如需在日志中显示额外信息，可以通过 MDC 手动设置

**可用的工具类**：
- `MdcUtil.setUserId(String)` - 设置用户ID
- `MdcUtil.setRequestId(String)` - 设置请求ID
- `MdcUtil.setOperation(String)` - 设置操作名称
- `MdcUtil.setIp(String)` - 设置IP地址
- `MdcUtil.setTenantId(String)` - 设置租户ID
- `MdcUtil.put(String, String)` - 自定义字段

**使用示例**：

```java
import com.yggdrasil.labs.log.util.MdcUtil;

@Slf4j
@RestController
public class UserController {
    
    @GetMapping("/api/user/{id}")
    public User getUser(@PathVariable String id) {
        // TraceId 和 SpanId 由 Micrometer Tracing 自动注入
        log.info("查询用户: id={}", id);
        
        User user = userService.getById(id);
        
        // 业务代码中手动设置额外信息
        MdcUtil.setUserId(user.getId());
        MdcUtil.setOperation("getUserById");
        
        log.info("用户查询成功: id={}, name={}", id, user.getName());
        
        return user;
    }
}
```

**扩展方向**：
- 可以通过拦截器自动提取 HTTP 请求信息（IP、路径、方法等）
- 可以通过过滤器统一处理 MDC 的清除
- 可以根据业务需求自定义额外的 MDC 字段

---

## 默认行为

### 日志输出

1. **控制台输出**：
   - 彩色日志格式
   - 所有环境都会输出到控制台

2. **文件输出**：
   - 位置：`./logs/`（项目运行目录下）
   - 通用日志：`application.log`
   - 错误日志：`application-error.log`
   - 访问日志：`application-access.log`（记录所有 HTTP 请求）

3. **环境差异**：
   - 开发环境：DEBUG 级别，详细日志
   - 测试环境：INFO 级别
   - 生产环境：WARN 级别，减少日志

### 日志滚动策略

- **按日期**：每天生成新日志文件
- **按大小**：单个文件超过 100MB 自动滚动
- **总容量**：
  - 通用日志：不超过 10GB
  - 访问日志：不超过 10GB  
  - 错误日志：不超过 5GB
- **保留天数**：
  - 通用日志：30 天
  - 访问日志：30 天
  - 错误日志：60 天

### 日志格式

```
2024-01-01 12:00:00.123 [http-nio-8080-exec-1] INFO  [a1b2c3d4e5f6] [c7d8e9f0] com.example.HelloController - Hello World
├────────┬────────────────┬─────┬───────────┬──────────┬────────────────────────────────┐
│  日期  │     线程名      │级别 │  TraceId  │  SpanId  │         日志内容               │
```

**说明**：
- TraceId 和 SpanId 由 Micrometer Tracing 自动注入
- 在日志中通过 `[traceId]` 和 `[spanId]` 显示
- 如果未集成 Micrometer，这两个字段显示为空

## 异常处理

### 问题1：日志文件未生成

**症状**：
- 控制台有日志，但 `logs` 目录下没有文件

**可能原因**：
1. 目录权限不足
2. 配置被覆盖
3. 日志被禁用

**解决方案**：

1. **检查目录权限**：
```bash
# Linux/Mac
ls -la ./logs/
mkdir -p logs && chmod 755 logs

# Windows
# 确保有写入权限
```

2. **检查配置**：
```yaml
# 确保没有禁用日志
mimir:
  boot:
    log:
      enabled: true  # 必须是 true
```

3. **查看启动日志**：
```java
// 在启动时添加日志检查
@PostConstruct
public void checkLog() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.getLoggerList().forEach(logger -> {
        logger.getAllAppenders().forEach((name, appender) -> {
            System.out.println("Appender: " + name + ", " + appender);
        });
    });
}
```

### 问题2：日志级别不生效

**症状**：
- 配置了 `DEBUG` 级别，但看不到 DEBUG 日志

**可能原因**：
1. 环境配置覆盖
2. 日志框架选择错误

**解决方案**：

1. **检查环境配置**：
```yaml
# application-dev.yml
logging:
  level:
    root: DEBUG  # 确保级别设置正确
```

2. **检查使用正确的日志框架**：
```java
import org.slf4j.Logger;  // ✅ 正确
import org.slf4j.LoggerFactory;  // ✅ 正确

// 不要使用
import java.util.logging.Logger;  // ❌ 错误
import org.apache.log4j.Logger;  // ❌ 错误
```

### 问题3：日志文件过大

**症状**：
- 日志文件快速增长，磁盘空间不足

**解决方案**：

1. **调整日志级别**：
```yaml
logging:
  level:
    root: WARN  # 减少日志输出
```

2. **减少日志文件大小**：
在 `logback-spring.xml` 中修改：
```xml
<!-- 从 100MB 改为 50MB -->
<maxFileSize>50MB</maxFileSize>
```

3. **减少保留天数**：
```xml
<!-- 从 30 天改为 7 天 -->
<maxHistory>7</maxHistory>
```

### 问题4：控制台日志过多

**症状**：
- 控制台日志刷屏，影响开发

**解决方案**：

1. **调整日志级别**：
```yaml
# application-dev.yml
logging:
  level:
    root: INFO  # 从 DEBUG 改为 INFO
```

2. **关闭控制台输出**（仅测试用）：
```xml
<!-- 在 logback-spring.xml 中注释掉 CONSOLE appender -->
<!-- <appender-ref ref="CONSOLE"/> -->
```

## 冲突处理

### 场景1：项目中已使用 Log4j2

**症状**：
- 引入后出现 `SLF4J: Class path contains multiple SLF4J bindings`

**解决方案**：

1. **移除 Log4j2 依赖**（推荐）：
```xml
<!-- 移除这些依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-log4j2</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

2. **或者**，将 Log4j2 迁移到 Logback：
```xml
<!-- 添加桥接器 -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>log4j-over-slf4j</artifactId>
</dependency>
```

### 场景2：Apache Commons Logging 冲突

**症状**：
- 日志输出混乱，出现 `commons-logging` 相关异常

**解决方案**：

```xml
<!-- 在项目 pom.xml 中添加排除 -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-core</artifactId>
    <exclusions>
        <exclusion>
            <groupId>commons-logging</groupId>
            <artifactId>commons-logging</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**或者**，Starter 已经自动处理，无需额外配置。

### 场景3：用户有自定义 logback.xml

**症状**：
- 用户的配置覆盖了 Starter 的配置

**解决方案**：

1. **删除用户的自定义配置**（推荐）：
```bash
rm src/main/resources/logback.xml
rm src/main/resources/logback-spring.xml
```

2. **或者**，修改 Spring 配置的优先级：
```yaml
# application.yml
logging:
  config: classpath:logback-spring.xml  # 明确指定使用我们的配置
```

### 场景4：第三方库使用了其他日志框架

**症状**：
- 某些第三方库的日志无法输出

**解决方案**：

Starter 已经包含了桥接器，自动将其他日志框架桥接到 SLF4J：
- `jcl-over-slf4j` - Apache Commons Logging
- `jul-to-slf4j` - Java Util Logging
- `log4j-over-slf4j` - Log4j

如果仍有问题，检查依赖树：
```bash
mvn dependency:tree | grep log
```

## 最佳实践

### 1. 日志命名

#### 方式1：使用 Lombok（最推荐）✨

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j  // ✅ 最简单
public class YourClass {
    // log 对象自动生成
    public void method() {
        log.info("Hello");
    }
}
```

#### 方式2：传统方式

```java
// ✅ 推荐
private static final Logger log = LoggerFactory.getLogger(YourClass.class);

// ❌ 不推荐
private static final Logger LOGGER = LoggerFactory.getLogger(YourClass.class);
```

### 2. 日志级别使用

```java
// TRACE - 最详细的跟踪信息
log.trace("详细调试信息");

// DEBUG - 开发调试
log.debug("调试：用户ID={}", userId);

// INFO - 重要业务流程
log.info("用户登录成功：username={}", username);

// WARN - 警告，不影响业务
log.warn("用户连续失败登录5次：username={}", username);

// ERROR - 错误，需关注
log.error("用户登录失败：username={}", username, e);
```

### 3. 敏感信息处理

#### 推荐：启用脱敏功能

**配置启用脱敏**：
```yaml
mimir:
  boot:
    log:
      mask:
        enabledPatterns:     # 启用的脱敏规则
          - password
          - token
          - phone_number
```

**使用效果**：
```java
log.info("用户登录：password=123456");
// 自动输出：用户登录：password=******

log.info("手机号：13812345678");
// 自动输出：手机号：******

log.info("token=abc123xyz");
// 自动输出：token=******
```

#### 手动避免

```java
// ❌ 不要这样做
log.info("用户密码：{}", password);
log.info("银行账号：{}", bankAccount);

// ✅ 应该这样做
log.info("用户登录成功：userId={}", userId);
```

### 4. 性能优化

```java
// ❌ 性能问题：即使日志关闭也会执行字符串拼接
log.debug("User: " + user);

// ✅ 推荐：使用占位符
log.debug("User: {}", user);

// ✅ 需要复杂计算时使用 lambda
log.debug("Complex: {}", () -> expensiveOperation());
```

## 技术栈

- **Logback**: 1.5.20
- **SLF4J**: 2.0.16
- **Spring Boot**: 3.3.13+
- **Java**: 17+

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs
