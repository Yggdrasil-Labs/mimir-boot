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

#### 异常体系层级结构

```
IException (接口)
    └── BaseException (抽象基础异常)
            ├── BizException (具体业务异常)
            │
            └── SystemException (具体系统异常)
```

#### 异常体系说明

- **IException** - 框架统一异常接口
  - 定义：`getCode()`, `getMessage()`
  - 作用：提供统一访问接口，便于全局处理器统一处理

- **BaseException** - 抽象基础异常
  - 继承：`RuntimeException`，实现 `IException`
  - 作用：封装错误码与消息的通用逻辑
  - 构造：支持 ErrorCode 枚举、自定义 code/message，支持异常链

- **BizException** - 业务异常
  - 继承：`BaseException`
  - 作用：用于业务层面的异常（可预期、可处理）
  - 场景：数据不存在、数据已存在、操作不允许等

- **SystemException** - 系统异常
  - 继承：`BaseException`
  - 作用：用于系统层面的异常（不可预期、系统级）
  - 场景：系统错误、系统繁忙、系统超时、系统不可用等

#### 使用示例

```java
// 业务异常
throw new BizException(ErrorCode.DATA_NOT_FOUND);
throw new BizException("20001", "用户不存在");

// 系统异常
throw new SystemException(ErrorCode.SYSTEM_ERROR);
throw new SystemException("10000", "系统错误", cause);

// 参数校验请使用 Spring Validation 并在各自项目中处理
```

#### 错误码枚举

- **ErrorCode** - 错误码枚举（通用/系统/业务/权限/网络/第三方服务）

### 2. 响应格式

- **R** - 统一响应结果
```java
return R.success(data);
return R.fail("20001", "用户不存在");
```

### 3. 分页

- **PageRequest** - 分页请求参数（自动校验）
```java
// 构造时自动校验参数
PageRequest request = new PageRequest(1L, 10L);
PageRequest request = PageRequest.of(1L, 10L, "createTime", "DESC");
// 语义：pageIndex 从 1 开始；pageSize 上限 1000；orderDirection 支持 ASC/DESC
// 偏移量：request.getOffset()
```

#### PageRequest 使用说明

**字段说明：**
- `pageIndex`：页码，从 1 开始（默认 1）
- `pageSize`：页大小，最大 1000（默认 10）
- `orderBy`：排序字段（可选）
- `orderDirection`：排序方向，ASC/DESC（默认 ASC）

**自动校验规则：**
- 构造时调用 `validateAndCorrect()` 自动校验并修正参数：
  - `pageIndex` 必须 >= 1，否则修正为默认值 1
  - `pageSize` 必须在 1 到 MAX_PAGE_SIZE(1000) 之间，否则修正为默认值 10 或最大值
  - `orderDirection` 必须是 ASC 或 DESC，否则修正为 ASC

**注意事项：**
- 使用无参构造方法时，需要手动调用 `validateAndCorrect()` 进行校验
- 或使用 `PageRequest.of()` 静态方法，会自动校验

- **PageResult** - 分页结果
```java
PageResult<User> result = PageResult.of(users, totalCount, pageIndex, pageSize);
```

#### PageResult 使用说明

**字段说明：**
- `data`：数据列表
- `totalCount`：总记录数
- `pageIndex`：页码
- `pageSize`：页大小
- `totalPages`：总页数（自动计算）
- `hasNext`：是否有下一页（自动计算）
- `hasPrevious`：是否有上一页（自动计算）

**使用方式：**
- `PageResult.of(data, totalCount, pageIndex, pageSize)` - 创建分页结果
- `PageResult.empty(pageIndex, pageSize)` - 创建空分页结果
- `PageResult.empty(PageRequest)` - 从分页请求创建空分页结果

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

### 7. DTO/VO 基础模型（COLA 4.0 架构）

- **BaseDTO** - 基础数据传输对象
  - 字段：createTime, updateTime, createBy, updateBy, remark
  - 用途：应用层之间传输数据，不包含业务逻辑

- **BaseVO** - 基础视图对象
  - 字段：createTime, updateTime, createBy, updateBy, remark
  - 用途：前端展示，包含必要的展示字段

- **Command** - 命令对象抽象类（CQRS 模式）
  - 字段：operatorId, operatorName, traceId
  - 用途：封装写操作（创建、更新、删除等）
  
- **Query** - 查询对象抽象类（CQRS 模式）
  - 字段：traceId
  - 用途：封装查询操作（单条查询、列表查询等）

- **PageQuery** - 分页查询对象
  - 继承：`Query`
  - 组合：`PageRequest page`
  - 用途：封装分页查询参数

#### 使用示例

```java
// Command - 写操作
public class CreateUserCommand extends Command {
    private String username;
    private String email;
}

// Query - 查询操作
public class GetUserQuery extends Query {
    private Long userId;
}

// PageQuery - 分页查询
public class ListUserPageQuery extends PageQuery {
    private String keyword;
    private Integer status;
}

// DTO - 数据传输
public class UserDTO extends BaseDTO {
    private Long id;
    private String username;
}

// VO - 视图对象
public class UserVO extends BaseVO {
    private Long id;
    private String username;
}
```

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
    public R<PageResult<User>> getUsers(
            @RequestBody PageRequest pageRequest) {
        // 自动校验已在构造时完成
        PageResult<User> result = userService.list(pageRequest);
        return R.success(result);
    }
}

// Service 层
@Service
public class UserService {
    public PageResult<User> list(PageRequest pageRequest) {
        List<User> users = userMapper.selectPage(pageRequest);
        Long totalCount = userMapper.selectCount();
        return PageResult.of(users, totalCount, 
            pageRequest.getPageIndex(), pageRequest.getPageSize());
    }
}

// 业务异常
if (user == null) {
    throw new BizException(ErrorCode.DATA_NOT_FOUND);
}
```

## 开发规范

### 响应格式
- ✅ 所有接口返回 `R` 格式
- ✅ 分页接口使用 `PageResult` 包装数据
- ✅ 错误码使用 `ErrorCode` 枚举

### 异常处理
- ✅ 业务异常使用 `BizException`
- ✅ 使用 `ErrorCode` 枚举定义错误码
- ❌ 不直接抛出 `RuntimeException`

### 工具类
- ✅ 使用 Hutool、Commons Lang、Guava 等成熟库
- ❌ Common 模块不提供工具类

### 校验
- ✅ 使用标准 JSR-303 注解（`@NotNull`、`@NotBlank`、`@Email`）
- ✅ 使用 Spring Validation
- ❌ Common 模块不提供自定义校验注解