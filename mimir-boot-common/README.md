# Mimir Boot Common

## 概述

Mimir Boot Common 是 Yggdrasil-Labs 企业级基础框架的**核心规范模块**，提供统一的异常处理、响应格式、分页等**项目级规范**，用于统一所有子项目的开发标准。

## 设计原则

遵循"不重复造轮子"原则，只提供项目规范层面内容：

- ❌ **不提供工具类**：使用 Hutool、Commons-Lang 等成熟库
- ❌ **不提供自定义校验**：使用 Spring Validation 标准注解
- ❌ **不提供领域功能**：MyBatis Plus、Security 等由子项目各自引入
- ✅ **只提供项目规范**：异常、响应、分页、枚举

## 核心组件

### 1. 异常处理

- **BusinessException** - 业务异常
```java
throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
throw new BusinessException("20001", "用户不存在");
```

- **ErrorCode** - 错误码枚举（通用/系统/业务/权限/网络/第三方服务）
- **ValidationException** - 参数校验异常

### 2. 响应格式

- **ApiResponse** - 统一响应结果
```java
return ApiResponse.success(data);
return ApiResponse.fail("20001", "用户不存在");
```

### 3. 分页

- **PageRequest** - 分页请求参数（自动校验）
```java
// 构造时自动校验参数
PageRequest request = new PageRequest(1L, 10L);
PageRequest request = PageRequest.of(1L, 10L, "createTime", "DESC");
```

- **PageResult** - 分页结果
```java
PageResult<User> result = PageResult.of(users, total, current, size);
```

### 4. 枚举

- **CommonStatus** - 通用状态（启用/禁用）
- **DeleteFlag** - 删除标志
- **OperationType** - 操作类型
- **OrderDirection** - 排序方向

### 5. 常量

- **CommonConstants** - 通用常量（分页、编码、日期格式等）
- **CacheConstants** - 缓存常量（缓存名称、过期时间等）

### 6. 注解

- **Loggable** - 日志记录注解

## 使用指南

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-common</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. 使用示例

```java
// Controller 层（自动校验，无需手动调用）
@RestController
public class UserController {
    @GetMapping("/users")
    public ApiResponse<PageResult<User>> getUsers(
            @RequestBody PageRequest pageRequest) {
        // 自动校验已在构造时完成
        PageResult<User> result = userService.list(pageRequest);
        return ApiResponse.success(result);
    }
}

// Service 层
@Service
public class UserService {
    public PageResult<User> list(PageRequest pageRequest) {
        List<User> users = userMapper.selectPage(pageRequest);
        Long total = userMapper.selectCount();
        return PageResult.of(users, total, 
            pageRequest.getCurrent(), pageRequest.getSize());
    }
}

// 业务异常
if (user == null) {
    throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
}
```

## 开发规范

### 响应格式
- ✅ 所有接口返回 `ApiResponse` 格式
- ✅ 分页接口使用 `PageResult` 包装数据
- ✅ 错误码使用 `ErrorCode` 枚举

### 异常处理
- ✅ 业务异常使用 `BusinessException`
- ✅ 使用 `ErrorCode` 枚举定义错误码
- ❌ 不直接抛出 `RuntimeException`

### 工具类
- ✅ 使用 Hutool、Commons Lang、Guava 等成熟库
- ❌ Common 模块不提供工具类

### 校验
- ✅ 使用标准 JSR-303 注解（`@NotNull`、`@NotBlank`、`@Email`）
- ✅ 使用 Spring Validation
- ❌ Common 模块不提供自定义校验注解