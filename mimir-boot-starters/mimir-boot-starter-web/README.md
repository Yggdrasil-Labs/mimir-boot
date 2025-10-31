# Mimir Boot Starter Web

Web 层增强启动器，提供统一的 Web 层配置和最佳实践。

## 概述

Mimir Boot Starter Web 提供了开箱即用的 Web 层增强功能：

- ✅ **CORS 跨域配置**：统一配置跨域资源共享策略
- ✅ **Jackson 序列化配置**：统一日期时间格式、空值处理等
- ✅ **Trace 拦截器**：自动生成或获取 traceId，设置到 MDC 和响应头
- ✅ **Web 拦截器**：自动提取客户端 IP、清理请求上下文
- ✅ **响应体增强器**：自动为 `R` 响应对象填充 traceId
- ✅ **安全配置**：请求大小限制、XSS 防护等
- ✅ **可配置开关**：支持通过配置文件启用/禁用各项功能

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-web</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**注意**：此模块已依赖 `mimir-boot-starter-exception` 和 `mimir-boot-common`，会自动引入异常处理和统一响应格式。

### 使用示例

引入依赖后，无需额外配置即可使用。所有功能默认启用，可按需调整配置。

```java
import com.yggdrasil.labs.common.response.R;

@RestController
public class UserController {
    
    @GetMapping("/api/user/{id}")
    public R<UserVO> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        // traceId 会自动填充到响应对象中
        return R.success(UserVO.from(user));
    }
}
```

**响应示例**：
```json
{
  "code": "00000",
  "message": "成功",
  "data": {
    "id": 123,
    "username": "test"
  },
  "traceId": "a1b2c3d4e5f6"  // 自动填充
}
```

## 配置参数

### 启用/禁用 Web 增强

**配置项**：`mimir.boot.web.enabled`

**默认值**：`true`

**示例**：
```yaml
mimir:
  boot:
    web:
      enabled: true  # 启用 Web 增强（默认值）
```

### CORS 跨域配置

**配置项**：`mimir.boot.web.cors`

**默认值**：
- `enabled: true` - 启用 CORS
- `allowedOrigins: ["*"]` - 允许所有源
- `allowedMethods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]` - 允许所有常用方法
- `allowedHeaders: ["*"]` - 允许所有请求头
- `allowCredentials: true` - 允许携带凭证
- `maxAge: 3600s` - 预检请求有效期 1 小时

**示例**：
```yaml
mimir:
  boot:
    web:
      cors:
        enabled: true
        allowedOrigins:
          - "https://example.com"
          - "https://api.example.com"
        allowedMethods:
          - GET
          - POST
          - PUT
          - DELETE
        allowedHeaders:
          - Content-Type
          - Authorization
        allowCredentials: true
        maxAge: PT1H  # 1 小时（ISO-8601 格式）
        exposedHeaders:
          - X-Trace-Id
```

**禁用 CORS**：
```yaml
mimir:
  boot:
    web:
      cors:
        enabled: false
```

### Jackson 序列化配置

**配置项**：`mimir.boot.web.serialization`

**默认值**：
- `dateTimeFormat: "yyyy-MM-dd HH:mm:ss"` - 日期时间格式
- `dateFormat: "yyyy-MM-dd"` - 日期格式
- `timeFormat: "HH:mm:ss"` - 时间格式
- `timeZone: "Asia/Shanghai"` - 时区
- `writeNulls: false` - 不写入 null 值
- `prettyPrint: false` - 不美化输出
- `ignoreUnknownProperties: true` - 忽略未知属性

**示例**：
```yaml
mimir:
  boot:
    web:
      serialization:
        dateTimeFormat: "yyyy-MM-dd HH:mm:ss"
        dateFormat: "yyyy-MM-dd"
        timeFormat: "HH:mm:ss"
        timeZone: "Asia/Shanghai"
        writeNulls: false  # false 表示不输出 null 值
        prettyPrint: false  # false 表示不格式化 JSON
        ignoreUnknownProperties: true  # 忽略 JSON 中未知的属性
```

**日期时间序列化示例**：
```java
public class UserVO {
    private LocalDateTime createTime;
    private LocalDate birthDate;
    private LocalTime workTime;
}
```

**序列化结果**：
```json
{
  "createTime": "2024-01-01 12:00:00",
  "birthDate": "1990-01-01",
  "workTime": "09:00:00"
}
```

### 安全配置

**配置项**：`mimir.boot.web.security`

**默认值**：
- `enabled: true` - 启用安全增强
- `maxRequestSize: 10` - 最大请求大小 10MB
- `maxFileSize: 10` - 单个文件最大大小 10MB
- `xssProtectionEnabled: true` - 启用 XSS 防护

**示例**：
```yaml
mimir:
  boot:
    web:
      security:
        enabled: true
        maxRequestSize: 50  # 最大请求大小 50MB
        maxFileSize: 20  # 单个文件最大大小 20MB
        xssProtectionEnabled: true
```

**注意**：请求大小限制需要配合 Spring Boot 配置使用：
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 50MB
```

### 响应增强配置

**配置项**：`mimir.boot.web.response`

**默认值**：
- `enabled: true` - 启用响应增强
- `autoFillTraceId: true` - 自动填充 traceId

**示例**：
```yaml
mimir:
  boot:
    web:
      response:
        enabled: true
        autoFillTraceId: true  # 自动为 R 响应对象填充 traceId
```

**禁用响应增强**：
```yaml
mimir:
  boot:
    web:
      response:
        enabled: false  # 禁用响应增强
```

## 核心功能

### 1. Trace 拦截器

**功能**：
- 自动生成或从请求头获取 traceId
- 将 traceId 设置到 MDC 和响应头 `X-Trace-Id`
- 支持与 Micrometer Tracing 集成（自动禁用）

**使用方式**：
```java
// 自动处理，无需手动编码
// 1. 从请求头 X-Trace-Id 获取（如果存在）
// 2. 从 MDC 获取（可能已被其他组件设置，如 Micrometer Tracing）
// 3. 生成新的 UUID（去除连字符）
```

**请求头示例**：
```http
GET /api/user/123 HTTP/1.1
X-Trace-Id: a1b2c3d4e5f6
```

**响应头示例**：
```http
HTTP/1.1 200 OK
X-Trace-Id: a1b2c3d4e5f6
```

**与 Micrometer Tracing 集成**：
- 如果检测到 classpath 中存在 `io.micrometer.tracing.Tracer`，Trace 拦截器会自动禁用
- 由 Micrometer Tracing 或 `starter-trace` 模块接管 Trace 逻辑

### 2. Web 拦截器

**功能**：
- 自动提取客户端真实 IP（支持反向代理场景）
- 将 IP 设置到 MDC
- 请求处理完成后清理 MDC（防止内存泄漏）

**IP 提取优先级**：
1. `X-Forwarded-For` 请求头
2. `X-Real-IP` 请求头
3. `Proxy-Client-IP` 请求头
4. `WL-Proxy-Client-IP` 请求头
5. `getRemoteAddr()`

**使用方式**：
```java
// 在日志中使用 IP（自动设置到 MDC）
log.info("用户登录：IP={}", org.slf4j.MDC.get("ip"));
```

### 3. 响应体增强器

**功能**：
- 自动为 `R` 响应对象填充 traceId
- 仅处理返回类型为 `R` 的接口
- 仅处理 `@RestController` 注解的类

**使用示例**：
```java
@RestController
public class UserController {
    
    @GetMapping("/api/user/{id}")
    public R<UserVO> getUser(@PathVariable Long id) {
        // traceId 会自动填充到响应对象中
        return R.success(UserVO.from(user));
    }
}
```

**响应示例**：
```json
{
  "code": "00000",
  "message": "成功",
  "data": {...},
  "traceId": "a1b2c3d4e5f6"  // 自动填充
}
```

**跳过填充**：
```java
// 如果响应已包含 traceId，会跳过填充
R<UserVO> response = R.success(UserVO.from(user));
response.setTraceId("custom-trace-id");  // 手动设置
return response;  // 不会覆盖已有的 traceId
```

### 4. CORS 跨域配置

**功能**：
- 统一配置跨域资源共享策略
- 支持通过配置文件自定义跨域规则
- 提供合理的默认配置（允许所有源、所有方法）

**默认配置**：
```yaml
mimir:
  boot:
    web:
      cors:
        enabled: true
        allowedOrigins: ["*"]  # 允许所有源
        allowedMethods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]
        allowedHeaders: ["*"]  # 允许所有请求头
        allowCredentials: true
        maxAge: PT1H  # 1 小时
```

**生产环境配置示例**：
```yaml
mimir:
  boot:
    web:
      cors:
        enabled: true
        allowedOrigins:
          - "https://www.example.com"
          - "https://admin.example.com"
        allowedMethods:
          - GET
          - POST
          - PUT
          - DELETE
        allowedHeaders:
          - Content-Type
          - Authorization
          - X-Requested-With
        allowCredentials: true
        maxAge: PT1H
        exposedHeaders:
          - X-Trace-Id
```

### 5. Jackson 序列化配置

**功能**：
- 统一日期时间格式
- 配置空值处理策略
- 配置序列化特性

**日期时间格式**：
```java
// 默认格式：yyyy-MM-dd HH:mm:ss
LocalDateTime dateTime = LocalDateTime.now();
// 序列化结果：2024-01-01 12:00:00
```

**空值处理**：
```yaml
mimir:
  boot:
    web:
      serialization:
        writeNulls: false  # false 表示不输出 null 值
```

**示例**：
```java
public class UserVO {
    private String name = "test";
    private String email = null;  // null 值
}
```

**序列化结果**（`writeNulls: false`）：
```json
{
  "name": "test"
  // email 字段不输出
}
```

**序列化结果**（`writeNulls: true`）：
```json
{
  "name": "test",
  "email": null
}
```

## 拦截器执行顺序

```
请求
  ↓
TraceInterceptor（优先级高）
  - 设置 traceId 到 MDC 和响应头
  ↓
WebInterceptor
  - 设置 IP 到 MDC
  ↓
Controller
  ↓
响应
  ↓
ResponseBodyEnhancer
  - 为 R 响应对象填充 traceId
  ↓
WebInterceptor（afterCompletion）
  - 清理 MDC
  ↓
返回客户端
```

## 与 Micrometer Tracing 集成

### 自动集成

如果项目中引入了 Micrometer Tracing：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

Starter Web 会自动检测并禁用内置的 Trace 拦截器，由 Micrometer Tracing 接管 Trace 逻辑。

### 手动集成

如果需要手动控制 Trace 逻辑，可以：

1. **禁用响应增强的自动填充 traceId**：
```yaml
mimir:
  boot:
    web:
      response:
        autoFillTraceId: false
```

2. **自定义 TraceInterceptor**：
```java
@Bean
public TraceInterceptor customTraceInterceptor() {
    return new CustomTraceInterceptor();
}
```

## 最佳实践

### 1. TraceId 使用

```java
// ✅ 使用统一的 traceId（自动生成或从请求头获取）
@GetMapping("/api/user/{id}")
public R<UserVO> getUser(@PathVariable Long id) {
    // traceId 已自动设置到 MDC，可在日志中使用
    log.info("查询用户：id={}", id);
    return R.success(UserVO.from(user));
}

// ✅ 在日志中自动包含 traceId（通过 MDC）
log.info("用户查询成功：id={}", id);
// 输出：2024-01-01 10:00:00.123 [http-nio-8080-exec-1] INFO  [a1b2c3d4e5f6] - 用户查询成功：id=123
```

### 2. CORS 配置

```yaml
# ✅ 生产环境：明确指定允许的源
mimir:
  boot:
    web:
      cors:
        allowedOrigins:
          - "https://www.example.com"
          - "https://admin.example.com"

# ❌ 生产环境：不要使用 "*"（安全风险）
# allowedOrigins: ["*"]
```

### 3. 日期时间格式

```yaml
# ✅ 统一使用标准格式
mimir:
  boot:
    web:
      serialization:
        dateTimeFormat: "yyyy-MM-dd HH:mm:ss"
        dateFormat: "yyyy-MM-dd"
        timeFormat: "HH:mm:ss"
        timeZone: "Asia/Shanghai"
```

### 4. 空值处理

```yaml
# ✅ 不输出 null 值（减少响应体积）
mimir:
  boot:
    web:
      serialization:
        writeNulls: false
```

### 5. 响应增强

```java
// ✅ 让响应增强器自动填充 traceId
@GetMapping("/api/user/{id}")
public R<UserVO> getUser(@PathVariable Long id) {
    // 无需手动设置 traceId
    return R.success(UserVO.from(user));
}

// ❌ 不需要手动设置 traceId（除非有特殊需求）
R<UserVO> response = R.success(UserVO.from(user));
response.setTraceId("custom-trace-id");  // 通常不需要
return response;
```

## 技术栈

- **Spring Boot**: 3.3.13+
- **Spring Web MVC**: 拦截器、配置类
- **Jackson**: JSON 序列化
- **Java**: 17+
- **Mimir Boot Starter Exception**: 异常处理
- **Mimir Boot Common**: 统一响应格式

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs

