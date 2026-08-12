# Mimir Boot Starter Web

Web 层增强启动器，提供统一的 Web 层配置和最佳实践。

## 概述

Mimir Boot Starter Web 提供了开箱即用的 Web 层增强功能：

- ✅ **CORS 跨域配置**：统一配置跨域资源共享策略
- ✅ **Jackson 序列化配置**：统一日期时间格式、空值处理等
- ✅ **Trace 拦截器**：处理受限格式的 traceId/requestId，写入 MDC，并将 traceId 写入响应头
- ✅ **Web 拦截器**：记录容器提供的直连 IP，并只恢复自己写入的 MDC 键
- ✅ **响应体增强器**：自动为 `R` 响应对象填充 traceId
- ✅ **上传限制迁移**：使用 Spring Boot multipart 配置管理请求和文件大小
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

- `enabled: false` - 默认关闭 CORS，需显式启用
- `allowedOrigins: []` - 默认空 Origin 白名单
- `allowedMethods: [GET, POST, PUT, DELETE, PATCH, OPTIONS]` - 允许所有常用方法
- `allowedHeaders: ["*"]` - 允许所有请求头
- `allowCredentials: false` - 默认不允许携带凭证
- `maxAge: 3600s` - 预检请求有效期 1 小时

启用 CORS 时必须配置具体 Origin 白名单。若 `allowCredentials: true`，不能使用 `"*"` Origin；应用会在启动时拒绝该不安全组合。

**升级迁移**：旧版本依赖默认 CORS 的应用，需要显式设置 `enabled: true` 和具体 `allowedOrigins`。若浏览器请求依赖 Cookie、会话或客户端证书，再显式设置 `allowCredentials: true`；`"*"` 与凭证组合不再受支持。

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

### 请求大小限制迁移

`mimir.boot.web.security` 已弃用，并将在下一个主版本移除。为保持 2.x 配置绑定和 Java API 兼容，`WebProperties.Security` 与 `getSecurity()` 暂时保留，但仍是 no-op：配置它不会产生 XSS 防护或请求大小限制。请使用 Spring Boot 的 multipart 配置管理上传大小限制：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 50MB
```

XSS 防护应由应用根据渲染入口实施：服务端模板和 API 输出需要进行上下文相关的输出编码；浏览器页面应配置 Content Security Policy（CSP）。

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
- 从 `X-Request-Id` 获取合法 requestId，缺失或非法时生成新的 32 位十六进制值
- 仅在请求范围内修改 traceId/requestId，完成后恢复进入前状态
- 仅接受最长 64 位、以字母或数字开头的 ASCII `[A-Za-z0-9._-]`；无效请求头会生成新的 traceId

**使用方式**：

```java
// 自动处理，无需手动编码
// 1. 请求头 X-Trace-Id 合法时直接使用
// 2. 请求头存在但非法时生成新的 UUID（去除连字符），不复用 MDC
// 3. 请求头缺失时使用 MDC 中的合法 traceId
// 4. 请求头与 MDC 均无可用值时生成新的 UUID
```

**请求头示例**：

```http
GET /api/user/123 HTTP/1.1
X-Trace-Id: a1b2c3d4e5f6
X-Request-Id: request-123
```

**响应头示例**：

```http
HTTP/1.1 200 OK
X-Trace-Id: a1b2c3d4e5f6
```

Micrometer Tracing 在 classpath 中不会禁用默认拦截器；应用如需替换行为，可声明自己的 `TraceInterceptor` Bean。

### 2. Web 拦截器

**功能**：

- 默认只使用 `request.getRemoteAddr()`，并将该 IP 设置到 MDC
- 请求完成后仅恢复此前的 `ip` 值；`traceId` 和业务自定义 MDC 键由各自所有者保留

**可信代理边界**：

Starter 不会自行信任任意 `X-Forwarded-For`、`X-Real-IP` 或其他转发头。只有网络入口和 Servlet 容器已经被配置为仅信任受控反向代理，并将可信转发信息安全改写到 `remoteAddr` 时，默认行为才会记录原始客户端地址。

Tomcat 部署在受控反向代理后时，可按实际网段精确配置，例如：

```yaml
server:
  tomcat:
    remoteip:
      remote-ip-header: x-forwarded-for
      protocol-header: x-forwarded-proto
      internal-proxies: "10\\.42\\.0\\.\\d{1,3}|192\\.0\\.2\\.10"
```

`internal-proxies` 只能列出实际受控的代理网段或地址，绝不能配置为 `.*`；同时必须阻止客户端绕过反向代理直接访问应用端口。否则客户端可伪造转发头并影响审计 IP。

若需要 Spring MVC 处理协议、主机或客户端地址等转发信息，只有在边界反向代理已经移除客户端带来的 `Forwarded`/`X-Forwarded-*`，再写入受控转发头，并且客户端不能直连应用监听端口时，才能启用：

```yaml
server:
  forward-headers-strategy: framework
```

此模式由 Spring 的 `ForwardedHeaderFilter` 处理，不会让 Mimir Starter 自行信任任意头。普通或默认部署保持 `framework`/`NATIVE` 关闭（使用 `NONE`），本 Starter 仍只读取 `request.getRemoteAddr()`。若只需移除而不使用转发头，可由应用自行注册 `ForwardedHeaderFilter` 并设置 `removeOnly`；Starter 不新增该 Bean。

配置项定义见 [Spring Boot 3.3 嵌入式 Web Server 指南](https://docs.spring.io/spring-boot/3.3/how-to/webserver.html) 和 [Spring Framework 6.1 ForwardedHeaderFilter](https://docs.spring.io/spring-framework/docs/6.1.x/javadoc-api/org/springframework/web/filter/ForwardedHeaderFilter.html)。

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
- 默认关闭，需显式配置 Origin 白名单后启用

**默认配置**：

```yaml
mimir:
  boot:
    web:
      cors:
        enabled: false
```

启用后必须提供至少一个具体 Origin；`allowCredentials: true` 时禁止使用 `"*"` Origin。

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
  - 仅恢复此前的 IP MDC 值
  ↓
返回客户端
```

## 与 Micrometer Tracing 集成

### 共存方式

如果项目中引入了 Micrometer Tracing：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

Starter Web 保留内置 Trace 拦截器；Micrometer Tracing 可共存。需要完全替换时，声明自定义 `TraceInterceptor` Bean。

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

1. **自定义 TraceInterceptor**：

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
        enabled: true
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

## 📚 相关文档

- [Mimir Boot 项目主页](../../README.md) - 项目总体说明
- [Mimir Boot Common](../../mimir-boot-common/README.md) - 公共组件说明
- [Mimir Boot Starter Exception](../mimir-boot-starter-exception/README.md) - 异常处理启动器（本模块依赖）

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs
