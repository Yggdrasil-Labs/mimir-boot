# Mimir Boot Starter Web

Web 层增强启动器，提供统一的 Web 层配置和最佳实践。

## 概述

Mimir Boot Starter Web 提供了开箱即用的 Web 层增强功能：

- ✅ **CORS 跨域配置**：统一配置跨域资源共享策略
- ✅ **Jackson 序列化配置**：统一日期时间格式、空值处理等
- ✅ **Trace 拦截器**：处理受限格式的 traceId/requestId，写入 MDC，并将 traceId 写入响应头
- ✅ **Web 拦截器**：记录容器提供的直连 IP，并只恢复自己写入的 MDC 键
- ✅ **响应体增强器**：自动为实际 `R` 响应对象（含 `HttpEntity<R>`/`ResponseEntity<R>` 响应）填充 traceId
- ✅ **上传限制迁移**：使用 Spring Boot multipart 配置管理请求和文件大小
- ✅ **可配置开关**：支持通过配置文件启用/禁用各项功能

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-web</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</dependency>
```

**注意**：此模块已依赖 `mimir-boot-starter-exception` 和 `mimir-boot-common`，会自动引入异常处理和统一响应格式。

### 使用示例

引入依赖后，Web 增强会自动装配。Trace、MDC、响应增强和 Jackson 配置按默认值生效；CORS 默认关闭，启用时必须显式配置 Origin 白名单。

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
- `writeLargeIntegersAsString: false` - 默认不改变大整数的 JSON 类型

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

### 日期参数与 JSON 迁移

以下配置仅在 Servlet 应用且 `mimir.boot.web.enabled=true` 时生效：

```yaml
mimir:
  boot:
    web:
      mvc:
        date-format-enabled: true
      serialization:
        write-large-integers-as-string: false
      json:
        include-legacy-fail: false
```

日期参数默认按上述 `dateFormat`、`timeFormat` 和 `dateTimeFormat` 绑定 `LocalDate`、`LocalTime`、`LocalDateTime`，仍支持 ISO 格式。业务设置对应 `spring.mvc.format.*` 或字段 `@DateTimeFormat` 时优先使用业务格式。需要保留旧的 locale SHORT 输入时，可关闭 `mvc.date-format-enabled` 或显式配置格式。`Date`、`Calendar` 不受新增缺省格式器影响；业务有序 `WebMvcConfigurer` 需要覆盖该缺省时，顺序应大于 -1，或关闭此开关。

开启 `write-large-integers-as-string` 后，超出 JavaScript 安全整数范围 `[-9007199254740991, 9007199254740991]` 的 `Long`、`BigInteger` 和长整型数组元素输出为字符串，安全范围内仍输出数字。`R.timestamp`、`PageResult` 的 `totalCount`、`pageIndex`、`pageSize`、`totalPages` 以及 `PageRequest` 的 `pageIndex`、`pageSize`、`offset` 保持数字，包括对应模型子类继承或覆盖的属性；同名业务字段不享受豁免。属性级格式或序列化器优先；普通业务值的消费者 Module 可覆盖框架兜底。元数据如需其他输出格式，应使用属性级 MixIn 或自定义模型序列化器。

API 文档若描述可能超出安全范围的业务数值，可自行声明联合类型；本模块不会自动修改 OpenAPI 模型或引入 springdoc：

```yaml
oneOf:
  - type: integer
    format: int64
  - type: string
    pattern: '^-?[0-9]+$'
```

默认受管 Jackson 输出 `R.success` 而省略旧的 `fail` 字段；迁移期间可开启 `json.include-legacy-fail` 保留两个字段。该策略影响 Boot 自动配置的共享 `ObjectMapper` 以及从受管 builder 创建的 mapper，包括复用它们的消息、缓存或 HTTP 客户端；业务自行创建的 mapper 和 fastjson2 不受影响。Web 总开关关闭或应用不是 Servlet 时，默认 Jackson 行为保持不变。

业务为 `R` 本身预先注册 MixIn 时，框架保留业务 MixIn，并在首次构建受管 mapper 时记录提示；仅为 `R` 子类注册 MixIn 不会阻止框架安装基础 MixIn。业务在框架后执行的 builder 后处理器或 mapper 构建后为 `R` 添加 MixIn 会覆盖框架 MixIn，框架无法对此记录提示；需要检测提示时可通过 builder `mixIn`、Module 或较早执行的后处理器注册。

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

- 自动为实际的 `R` 响应对象填充 traceId；支持直接返回 `R`，以及声明为 `HttpEntity<R>`/`ResponseEntity<R>` 的接口
- `@RestControllerAdvice` 方法即使声明返回 `Object` 也可处理，但实际响应体必须是 `R`
- 仅处理 `@RestController` 或 `@RestControllerAdvice` 注解的类

当 Exception 处理器以 `Object` 声明返回 `ResponseEntity` 时，Web 和 Exception 需要配合交付：默认增强器对符合条件的异常响应检查解包后的实际 body，仅对 `R` 委托增强。字符串、其他对象和 null 不会被构造或转换为 `R`；已有 traceId 不覆盖，关闭响应增强或由业务增强器拒绝时不额外补填。

自定义 `ResponseBodyAdvice` 的 `supports` 在这些异常分支看到运行期 `ResponseEntity` 外层类型。仅以声明类型或泛型判断 `R` 的增强可能不再执行；应让 `supports` 接受需要的异常处理器，并在 `beforeBodyWrite` 中使用 `body instanceof R<?>` 判断实际响应体。详细迁移示例见 [Exception Starter](../mimir-boot-starter-exception/README.md#升级和兼容性)。

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
