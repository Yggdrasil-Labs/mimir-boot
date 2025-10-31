# Mimir Boot Starter Exception

全局异常处理启动器，提供统一的异常处理机制和最佳实践。

## 概述

Mimir Boot Starter Exception 提供了开箱即用的全局异常处理功能：

- ✅ **统一异常处理**：自动捕获并处理所有异常
- ✅ **业务异常**：`BizException` - 业务层面的可预期异常
- ✅ **系统异常**：`SystemException` - 系统层面的不可预期异常
- ✅ **参数校验异常**：自动处理 `@Valid`、`@ModelAttribute` 等校验异常
- ✅ **HTTP 异常**：处理 404、405、400 等 HTTP 相关异常
- ✅ **统一响应格式**：所有异常统一返回 `R` 格式
- ✅ **日志记录**：自动记录异常日志，支持日志安全清理
- ✅ **可配置开关**：支持通过配置文件启用/禁用

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-exception</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**注意**：此模块已依赖 `mimir-boot-common`，会自动引入异常体系（`BizException`、`SystemException`、`ErrorCode` 等）。

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
  "code": "30001",
  "message": "参数校验失败",
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
  "code": "30001",
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
  "code": "30001",
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
  "code": "30003",
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

### 9. 所有未捕获的异常

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
  统一响应格式 R     统一响应格式 R     统一响应格式 R
        ↓                  ↓                  ↓
                  返回给客户端
```

## 日志记录

所有异常都会自动记录日志：

- **业务异常**：`WARN` 级别
- **系统异常**：`ERROR` 级别
- **参数校验异常**：`WARN` 级别
- **未捕获异常**：`ERROR` 级别

**日志示例**：
```
2024-01-01 10:00:00.123 [http-nio-8080-exec-1] WARN  [a1b2c3d4e5f6] com.yggdrasil.labs.exception.handler.GlobalExceptionHandler - 业务异常: code=20001, message=用户不存在, uri=/api/user/123
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

// ✅ 自定义错误码（需要定义在 ErrorCode 枚举中）
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
    public R<Void> handleCustomException(CustomException e) {
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

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs

