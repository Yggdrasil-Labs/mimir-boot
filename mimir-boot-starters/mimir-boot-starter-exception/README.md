# Mimir Boot Starter Exception

全局异常处理启动器，提供统一的异常处理机制和最佳实践。

## 概述

Mimir Boot Starter Exception 提供了开箱即用的全局异常处理功能：

- ✅ **全局异常处理**：在 Web 应用中处理业务、系统、参数校验及常见 HTTP 异常
- ✅ **业务异常**：`BizException` - 业务层面的可预期异常
- ✅ **系统异常**：`SystemException` - 系统层面的不可预期异常
- ✅ **参数校验异常**：自动处理 `@Valid`、`@ModelAttribute` 等校验异常
- ✅ **Spring 6 HTTP 异常**：处理 400、404、405、406、413、415 等 HTTP 相关异常
- ✅ **默认响应格式**：默认响应工厂返回 `R`，可通过 `ExceptionResponseFactory` 自定义
- ✅ **日志记录**：自动记录异常日志，支持日志安全清理
- ✅ **可配置开关**：支持通过配置文件启用/禁用

默认异常响应由 `DefaultExceptionResponseFactory` 构造为 `R`；接入方提供 `ExceptionResponseFactory` 后可以返回其他响应对象。下方示例中的 `traceId` 由 Web Starter 的响应增强在存在 MDC 追踪标识时填充，单独使用本 Starter 时可能为空。

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-exception</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</dependency>
```

**注意**：此模块已依赖 `mimir-boot-common`，会自动引入异常体系（`BizException`、`SystemException`、`ErrorCode` 等）。

如果使用下方 `@Valid`、`@NotBlank` 等 Bean Validation 注解，请在应用中额外引入 `spring-boot-starter-validation`，以提供实际的校验实现。本 Starter 仅提供 `jakarta.validation-api` 用于异常处理签名，不会自动引入校验实现。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

### 使用示例

#### 业务异常

```java
import com.yggdrasil.labs.common.exception.BizException;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.response.R;

@RestController
public class UserController {
    
    @GetMapping("/api/user/{id}")
    public R<UserVO> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            // 方式1：使用错误码枚举
            throw new BizException(ErrorCode.DATA_NOT_FOUND);
            
            // 方式2：自定义错误码和消息
            // throw new BizException("20001", "用户不存在");
        }
        return R.success(UserVO.from(user));
    }
}
```

**响应示例**：

```json
{
  "code": "20001",
  "message": "数据不存在",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

#### 系统异常

```java
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.exception.ErrorCode;

@Service
public class PaymentService {
    
    public void processPayment(PaymentRequest request) {
        try {
            // 调用第三方支付接口
            paymentGateway.process(request);
        } catch (Exception e) {
            // 系统异常：用于系统层面的不可预期异常
            throw new SystemException(ErrorCode.SYSTEM_ERROR, e);
        }
    }
}
```

**响应示例**（HTTP 500）：

```json
{
  "code": "10000",
  "message": "系统错误",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

#### 参数校验异常

```java
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import com.yggdrasil.labs.common.response.R;

@RestController
public class UserController {
    
    @PostMapping("/api/user")
    public R<UserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.create(request);
        return R.success(UserVO.from(user));
    }
}

// 请求对象
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

**请求示例**（参数校验失败）：

```json
POST /api/user
{
  "username": "",
  "email": "invalid-email"
}
```

**响应示例**（HTTP 400）：

```json
{
  "code": "00004",
  "message": "参数无效",
  "data": [
    "username: 用户名不能为空",
    "email: 邮箱格式不正确"
  ],
  "traceId": "a1b2c3d4e5f6"
}
```

## 配置参数

### 启用/禁用全局异常处理

**配置项**：`mimir.boot.exception.enabled`

**默认值**：`true`

**示例**：

```yaml
mimir:
  boot:
    exception:
      enabled: true  # 启用全局异常处理（默认值）
```

**禁用全局异常处理**：

```yaml
mimir:
  boot:
    exception:
      enabled: false  # 禁用全局异常处理
```

### 结构化校验明细

`mimir.boot.exception.validation.structured-details-enabled` 默认为 `false`。开启后，已确认的输入校验错误在 `data` 中返回结构化明细：

```yaml
mimir:
  boot:
    exception:
      validation:
        structured-details-enabled: true
```

```json
{
  "errors": [{"source": "FIELD", "path": "name", "parameterIndex": null, "constraint": "NotBlank", "message": "不能为空"}],
  "totalCount": 1,
  "truncated": false
}
```

明细最多返回 100 项，按固定规则排序并保留重复项；映射的动态键使用 `[*]`，不返回拒绝值、约束参数或自定义插值消息。旧模式继续返回原始字符串列表或 `null`，其中自定义校验消息可能包含业务插值，不应视为已经脱敏。客户端开启新模式后需按对象而非字符串列表解析 `data`。

字段路径只使用能由声明类型确认的信息。`BindException` 的 Bean Validation 错误可以使用包装的违例取得声明类型，并按结构化路径节点隐藏 Map 键；只有扁平字段名、无法确认 Map 键边界时，路径在该 Map 的 `[*]` 处截断。手工字段错误或类型转换错误等无法安全确认类型时，`FIELD` 的 path 使用 `$` 表示具体位置不可确认。方法校验中容器整体约束和元素约束无法区分时，保守定位到参数名。这些降级均不影响旧模式。

## 支持的异常类型

### 1. 业务异常（BizException）

- **HTTP 状态码**：200
- **用途**：业务层面的可预期异常
- **场景**：数据不存在、数据已存在、操作不允许等

**示例**：

```java
throw new BizException(ErrorCode.DATA_NOT_FOUND);
throw new BizException("20001", "用户不存在");
```

### 2. 系统异常（SystemException）

- **HTTP 状态码**：500
- **用途**：系统层面的不可预期异常
- **场景**：系统错误、系统繁忙、系统超时、系统不可用等

**示例**：

```java
throw new SystemException(ErrorCode.SYSTEM_ERROR);
throw new SystemException("10000", "系统错误", cause);
```

### 3. 参数校验异常

#### MethodArgumentNotValidException（@Valid）

- **HTTP 状态码**：400
- **触发条件**：使用 `@Valid` 注解校验方法参数失败

**示例**：

```java
@PostMapping("/api/user")
public R<UserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
    // ...
}
```

#### BindException（@ModelAttribute）

- **HTTP 状态码**：400
- **触发条件**：使用 `@ModelAttribute` 绑定时失败

**示例**：

```java
@GetMapping("/api/user")
public R<UserVO> getUser(@Valid @ModelAttribute GetUserRequest request) {
    // ...
}
```

### 4. 缺少请求参数异常

#### MissingServletRequestParameterException

- **HTTP 状态码**：400
- **触发条件**：缺少必需的请求参数（如 `@RequestParam(required = true)`）

**示例**：

```java
@GetMapping("/api/user")
public R<UserVO> getUser(@RequestParam(required = true) Long id) {
    // 如果请求中没有 id 参数，会触发此异常
}
```

### 5. 参数类型不匹配异常

#### MethodArgumentTypeMismatchException

- **HTTP 状态码**：400
- **触发条件**：请求参数类型不匹配

**示例**：

```java
@GetMapping("/api/user/{id}")
public R<UserVO> getUser(@PathVariable Long id) {
    // 如果路径变量 id 不是数字，会触发此异常
}
```

**响应示例**：

```json
{
  "code": "00004",
  "message": "参数类型不匹配: id，期望类型: Long",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

### 6. HTTP 消息不可读异常

#### HttpMessageNotReadableException

- **HTTP 状态码**：400
- **触发条件**：JSON 解析失败、请求体格式错误等

**示例**：

```json
POST /api/user
{
  "username": "test",
  "age": "not-a-number"  // 期望是数字，但提供了字符串
}
```

**响应示例**：

```json
{
  "code": "00004",
  "message": "请求体格式错误",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

### 7. HTTP 请求方法不支持异常

#### HttpRequestMethodNotSupportedException

- **HTTP 状态码**：405
- **触发条件**：请求方法不支持（如用 POST 请求 GET 接口）

**响应示例**：

```json
{
  "code": "20004",
  "message": "请求方法 POST 不支持，支持的方法: GET",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

### 8. 处理器未找到异常（404）

#### NoHandlerFoundException

- **HTTP 状态码**：404
- **触发条件**：请求路径不存在

**注意**：需要在配置文件中启用：

```yaml
spring:
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false
```

**响应示例**：

```json
{
  "code": "20001",
  "message": "未找到请求路径: GET /api/nonexistent",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

### 9. 未捕获的 Exception

#### Exception（兜底处理）

- **HTTP 状态码**：500
- **触发条件**：所有未被上述处理器捕获的异常

**处理逻辑**：

1. 如果异常实现了 `IException` 接口，使用其错误码和消息
2. 否则使用默认系统错误：`ErrorCode.SYSTEM_ERROR`

**响应示例**：

```json
{
  "code": "10000",
  "message": "系统错误",
  "data": null,
  "traceId": "a1b2c3d4e5f6"
}
```

### 10. Spring 6 请求与资源异常

默认响应工厂下，下列异常返回 `R` 响应，并保留 Spring 的 HTTP 语义；自定义 `ExceptionResponseFactory` 可以改变响应对象：

| 异常 | HTTP 状态 | 错误码语义 |
| --- | --- | --- |
| `HandlerMethodValidationException`（入参） | 400 | `PARAM_INVALID` |
| `HandlerMethodValidationException`（返回值） | 500 | `SYSTEM_ERROR` |
| `ConstraintViolationException`（确认是输入） | 400 | `PARAM_INVALID` |
| `ConstraintViolationException`（返回值、混合或无法确认来源） | 500 | `SYSTEM_ERROR` |
| `MissingRequestHeaderException` | 400 | `PARAM_MISSING` |
| `MissingPathVariableException` | 500 | `SYSTEM_ERROR` |
| `HttpMediaTypeNotAcceptableException` | 406 | `OPERATION_NOT_ALLOWED` |
| `HttpMediaTypeNotSupportedException` | 415 | `OPERATION_NOT_ALLOWED` |
| `MaxUploadSizeExceededException` | 413 | `PARAM_INVALID` |
| `NoResourceFoundException` | 404 | `DATA_NOT_FOUND` |

`jakarta.validation-api` 仅用于公开异常处理器签名；Starter 不会因此引入校验实现或改变应用现有的校验策略。

### 升级和兼容性

- `ResponseStatusException`、`ErrorResponseException` 及普通异常根类型的合并 `@ResponseStatus` 会参与状态映射；不会为查询状态遍历 cause。`BizException`、`SystemException` 和其他 `BaseException` 仍优先使用既有固定状态，Common 新异常工厂不改变该规则。
- 返回值校验、混合来源以及空集合或纯 Bean 路径等无法确认是输入的约束异常返回 500；此变化与结构化开关无关。明确属于业务输入的特殊场景可由应用自己的 Advice 处理。
- 响应工厂最多调用一次。工厂返回 `ResponseEntity` 时只使用其 body 和合法非冲突头，不能覆盖处理器已解析的状态或异常协议头；工厂失败时以默认 `R` 降级且不返回校验明细。旧处理器方法保留静态 `@ResponseStatus`，使返回普通对象的旧子类继续继承原状态；默认实现的最终状态由 `ResponseEntity` 表达，动态状态不能只靠静态注解判断。
- 合并结果交由原生 MVC 写出；不可表示的媒体类型、Resource、条件或 Range 请求以及实际写出失败遵循框架行为。工厂显式 Content-Type 同样经过原生协商，写出失败不属于工厂降级。
- 仅 Servlet MVC 环境自动注册处理器、默认响应工厂和 `ExceptionProperties`。Reactive 应用如需工厂或属性绑定，应自行声明或启用。
- 子类使用单参构造时沿用旧明细模式；需要结构化配置时使用双参构造。子类以不同方法名重复声明 `ResponseStatusException` 或 `ErrorResponseException` 映射会导致启动二义性，应覆盖对应新方法或移除重复映射；这些异常的业务子类也会进入新分支。
- 自动配置仍通过原 `mimirExceptionHandler(ExceptionResponseFactory)` Bean 方法创建处理器，继承配置类并覆盖该方法的旧扩展继续生效。默认实现使用 `@EnableConfigurationProperties` 注册的绑定属性 Bean，额外声明的同类型 Bean 不参与默认处理器的属性选择；自定义处理器所需属性由其构造方式决定。显式声明处理器 Bean 时自动配置仍退让。
- 与 Web Starter 同时使用时，可写的 `R` 异常响应沿用已有 traceId 增强及其开关和用户覆盖规则；`fail` 字段由 Web 的独立配置控制。工厂传入 `null` message 时，JSON 的空值策略可能省略 message 字段。
- 下游自定义 `ResponseBodyAdvice` 在异常处理分支接收到运行期 `ResponseEntity` 外层的方法参数类型；仅按声明类型或泛型判断 `R` 的增强可能不再执行。可在 `supports` 中接受异常处理器，并在 `beforeBodyWrite` 中按实际 body 类型判断：

```java
@Override
public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
        Class<? extends HttpMessageConverter<?>> converter,
        ServerHttpRequest request, ServerHttpResponse response) {
    if (body instanceof R<?> result) {
        // 对实际的统一响应执行业务增强
    }
    return body;
}
```

## 异常处理流程

```
请求 → Controller → Service → 抛出异常
                           ↓
                    全局异常处理器
                           ↓
                   判断异常类型
                           ↓
        ┌──────────────────┼──────────────────┐
        ↓                  ↓                  ↓
   业务异常           系统异常           参数校验异常
        ↓                  ↓                  ↓
   HTTP 200          HTTP 500          HTTP 400
        ↓                  ↓                  ↓
  工厂结果（默认 R）  工厂结果（默认 R）  工厂结果（默认 R）
        ↓                  ↓                  ↓
                  返回给客户端
```

## 日志记录

本处理器接管的异常会自动记录日志：

- **业务异常**：`WARN` 级别
- **系统异常**：`ERROR` 级别
- **参数校验异常**：`WARN` 级别
- **未捕获异常**：`ERROR` 级别

**日志示例**：

```
2024-01-01 10:00:00.123 [http-nio-8080-exec-1] WARN  [a1b2c3d4e5f6] com.yggdrasil.labs.exception.handler.MimirExceptionHandler - 业务异常: code=20001, message=用户不存在, uri=/api/user/123
```

**安全特性**：

- 自动使用 `LogSanitizer.sanitize()` 清理日志内容，防止日志注入攻击

## 最佳实践

### 1. 异常选择

```java
// ✅ 业务异常：数据不存在、操作不允许等
if (user == null) {
    throw new BizException(ErrorCode.DATA_NOT_FOUND);
}

// ✅ 系统异常：调用第三方服务失败、数据库连接失败等
try {
    thirdPartyService.call();
} catch (Exception e) {
    throw new SystemException(ErrorCode.SYSTEM_ERROR, e);
}

// ❌ 不要直接抛出 RuntimeException
throw new RuntimeException("错误");  // 会被当作系统异常处理
```

### 2. 错误码使用

```java
// ✅ 使用 ErrorCode 枚举（推荐）
throw new BizException(ErrorCode.DATA_NOT_FOUND);

// ✅ 自定义错误码（建议集中定义在 ErrorCode 枚举中）
throw new BizException("20001", "用户不存在");

// ❌ 避免硬编码错误码
throw new BizException("99999", "错误");  // 不易维护
```

### 3. 参数校验

```java
// ✅ 使用 @Valid + JSR-303 注解
@PostMapping("/api/user")
public R<UserVO> createUser(@Valid @RequestBody CreateUserRequest request) {
    // 校验失败会自动返回 400，无需手动处理
}

// ✅ 自定义校验消息
public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
}

// ❌ 不要手动校验参数（由全局异常处理器统一处理）
if (StringUtils.isEmpty(username)) {
    throw new BizException("用户名不能为空");  // 应该使用 @NotBlank
}
```

### 4. 异常链传递

```java
// ✅ 保留原始异常信息
try {
    thirdPartyService.call();
} catch (Exception e) {
    throw new SystemException(ErrorCode.SYSTEM_ERROR, e);  // 传入 cause
}

// ❌ 丢失原始异常信息
catch (Exception e) {
    throw new SystemException(ErrorCode.SYSTEM_ERROR);  // 没有传入 cause
}
```

### 5. 异常消息设计

```java
// ✅ 清晰的错误消息
throw new BizException("20001", "用户不存在：id=123");

// ❌ 模糊的错误消息
throw new BizException("20001", "错误");  // 不明确
```

## 与 Common 模块的配合

Starter Exception 依赖 `mimir-boot-common` 模块，自动引入：

- **异常体系**：`BizException`、`SystemException`、`BaseException`、`IException`
- **错误码枚举**：`ErrorCode`
- **统一响应格式**：`R<T>`

**无需额外配置**，直接使用即可！

## 扩展

### 自定义异常处理器

如果需要处理特定异常，可以创建自定义异常处理器：

```java
@RestControllerAdvice
public class CustomExceptionHandler {
    
    @ExceptionHandler(CustomException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleCustomException(CustomException e) {
        return R.fail("CUSTOM_ERROR", e.getMessage());
    }
}
```

**注意**：自定义异常处理器的优先级高于全局异常处理器，会优先匹配。

## 技术栈

- **Spring Boot**: 3.3.13+
- **Spring Web**: 自动配置
- **Java**: 17+
- **Mimir Boot Common**: 异常体系和响应格式

## 📚 相关文档

- [Mimir Boot 项目主页](../../README.md) - 项目总体说明
- [Mimir Boot Common](../../mimir-boot-common/README.md) - 公共组件说明（包含异常体系）
- [Mimir Boot Starter Web](../mimir-boot-starter-web/README.md) - Web 层启动器（依赖本模块）

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs
