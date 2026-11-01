# Mimir Boot Common

## 概述

Mimir Boot Common 是 Yggdrasil-Labs 企业级基础框架的**核心规范模块**，提供统一的异常处理、响应格式、分页等**项目级规范**，用于统一所有子项目的开发标准。

## 设计原则

遵循"不重复造轮子"原则，只提供项目规范层面内容：

- ❌ **不提供通用工具集合**：字符串、日期、集合等通用能力使用 Hutool、Commons-Lang 等成熟库
- ❌ **不提供自定义校验**：使用 Spring Validation 标准注解
- ❌ **不提供领域功能**：MyBatis Plus、Security 等由子项目各自引入
- ✅ **提供项目规范与边界适配**：异常、响应、分页、枚举，以及 `IpUtils`、`LogSanitizer` 这类框架边界工具

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

#### 按错误定义创建（v2.4.0）

业务错误枚举实现 `ErrorDefinition` 后，可通过命名工厂创建失败响应或异常；内置 `ErrorCode` 也已实现该接口。

```java
public enum OrderError implements ErrorDefinition {
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在");
    // 构造方法与 getCode()/getMessage() 省略
}

R<OrderVO> r = R.failCode(OrderError.ORDER_NOT_FOUND);                 // 默认消息
R<OrderVO> r2 = R.failCode(OrderError.ORDER_NOT_FOUND, "订单 123 不存在"); // 覆盖消息
throw BizException.fromError(OrderError.ORDER_NOT_FOUND);
throw SystemException.fromError(OrderError.ORDER_NOT_FOUND, null, cause); // 默认消息 + cause
```

- 工厂只创建对象，不自动抛出；`BizException` 与 `SystemException` 按调用方选择创建，不根据 code 自动切换，经异常 Starter 处理时仍分别为 HTTP 200、500。
- 错误定义为 null，或 code 为 null、空白、精确的 `00000` 时抛 `IllegalArgumentException`；其他 code 原样保留，不 trim（例如 `" 00000 "` 允许且不视为成功）。
- 覆盖消息非 null 时原样采用（包括 `""`、空白，不做 `{}`/`%s` 格式化）；为 null 或未提供时使用默认消息，默认消息也为 null 时结果 message 为 null。经 Web 默认空值策略（`write-nulls=false`）输出时 JSON 不含 `message` 键，开启 write-nulls 才输出 `"message": null`。
- 旧入口保持不变：`R.fail(String)`、`R.fail(String, String)` 及两类异常的四个构造仍可用，`R.fail(null)` 仍可编译，新入口的校验不追溯旧入口。
- `ErrorDefinition` 表示可复用的错误定义，`IException` 表示已发生的异常实例。二者签名相同但不可互换：异常的消息可能已被覆盖，不应反向当作错误定义；错误枚举也不应实现 `IException`。

#### 错误码枚举

- **ErrorCode** - 错误码枚举（通用/系统/业务/权限/网络/第三方服务）

### 2. 响应格式

- **R** - 统一响应结果

```java
return R.success(data);
return R.fail("20001", "用户不存在");
```

`R<T>` 和 `PageResult<T>` 的类型参数都必须实现 `Serializable`；下方示例中的 `User` 需满足该类型约束。

**JSON 中的 `fail` 字段**：Common 单独使用或手工创建的 `ObjectMapper` 序列化 `R` 时同时输出 `success` 与 `fail`，Common 不改变这一行为。v2.4.0 规划由 Web Starter 让受管 mapper 默认只输出 `success`（前提为 Servlet 应用且 `mimir.boot.web.enabled=true`，提供 `mimir.boot.web.json.include-legacy-fail=true` 过渡开关，业务自定义 `R` MixIn 优先；非 Servlet 应用或关闭 Web 时共享 mapper 仍输出 `fail`）。该能力尚待 Web Starter 实施，以 [Web Starter README](../mimir-boot-starters/mimir-boot-starter-web/README.md) 的实际说明为准。

### 3. 分页

- **PageRequest** - 分页请求参数（构造、setter 与默认 Jackson 绑定均自动校正）

```java
// 带参数构造会自动校验参数
PageRequest request = new PageRequest(1L, 10L);
PageRequest sortedRequest = PageRequest.of(1L, 10L, "createTime", "DESC");
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

- 带参数构造、`PageRequest.of()`、页码/页大小/排序方向 setter 均调用 `validateAndCorrect()`，默认 Jackson 绑定通过 setter 使用同一规则：
  - `pageIndex` 必须 >= 1，否则修正为默认值 1
  - `pageSize` 必须在 1 到 MAX_PAGE_SIZE(1000) 之间，否则修正为默认值 10 或最大值
  - `orderDirection` 必须是 ASC 或 DESC，否则修正为 ASC

**注意事项：**

- 无参构造设置有效默认值；setter 写入后立即校正，直接 getter 和 `PageQuery.toPageRequest()` 可读取已校正的值。与旧行为相比，非法输入不再原样保留到显式校验时。
- `validateAndCorrect()` 仍可显式调用，`getOffset()` 仍再次校验并在乘法溢出时抛出 `IllegalArgumentException`。反射直接写字段、自定义 Jackson 字段访问和历史 Java 序列化数据不属于 setter 绑定保证范围，读取后应显式校验。

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
- `pageResult.map(mapper)` - 逐条映射为新的分页结果（v2.4.0）

**逐条映射（`map`）：**

```java
PageResult<UserVO> voPage = userPage.map(UserVO::from);
```

- 返回新的结果对象与新的可变列表，不修改源对象；六个分页元数据按原值复制（包括 null 或不一致的值），不重新计算。
- 源数据为 null 时结果数据仍为 null；null 元素不交给函数并原位保留，函数返回 null 也原位保留。
- mapper 为 null 时抛 `IllegalArgumentException`（空页同样如此）；函数抛出的异常原样传播，后续记录不再处理，已产生的副作用不回滚。
- 输出类型仍须实现 `Serializable`。

**转换为 COLA `PageResponse`（optional）：**

Common 以 optional 方式依赖 `com.alibaba.cola:cola-component-dto`，只声明 Common 不会获得 COLA。使用转换工具时需自行声明（版本由 BOM 管理）：

```xml
<dependency>
    <groupId>com.alibaba.cola</groupId>
    <artifactId>cola-component-dto</artifactId>
</dependency>
```

```java
PageResponse<User> same = ColaPageConverters.toCola(userPage);
PageResponse<UserVO> mapped = ColaPageConverters.toCola(userPage, UserVO::from); // UserVO 无需 Serializable
```

- 先校验后映射：source/mapper 为 null，或 `totalCount` 为 null/负数、`pageIndex`/`pageSize` 为 null/小于 1、任一值超过 `Integer.MAX_VALUE` 时抛 `IllegalArgumentException`，映射函数调用 0 次，不返回 COLA 失败响应。
- 结果 `success=true`，`errCode`/`errMessage` 为 null；总数、页码、页大小按原值保留，页码超过总页数也允许；总页数由 COLA 计算，源对象的派生字段不复制。
- 映射的 null/顺序/异常语义与 `map` 相同；源数据为 null 时 COLA 读取为空列表。
- 不使用 `ColaPageConverters` 的代码无需 COLA，`R`、`PageResult`、异常与批量模型都不暴露 COLA 类型。Common 不感知 MyBatis 的 `IPage`；`IPage` 到 COLA 的转换由 MyBatis Starter 负责（v2.4.0 规划，待实施）。

### 3.1 批量结果（v2.4.0）

`com.yggdrasil.labs.common.batch` 提供不可变的完整批量结果，只承载已完成的逐项结果，不执行任务、不决定事务或重试。

```java
List<BatchItemResult<OrderVO>> items = new ArrayList<>();
items.add(BatchItemResult.success(0, "order-1", vo));
items.add(BatchItemResult.failure(1, "order-2", OrderError.ORDER_NOT_FOUND));
items.add(BatchItemResult.failure(null, "order-3", OrderError.ORDER_NOT_FOUND, "订单 order-3 不存在"));

return R.success(BatchResult.of(items)); // 请求已处理完，逐项状态决定业务结果
```

- 每项至少提供一种关联：非 null 的 `index` 必须非负；`index` 为 null 时 `inputId` 必须为非空白文本。两者可同时提供，`inputId` 允许重复。
- 成功项可携带 data（允许 null），code/message 为 null；失败项遵循上文错误定义的 code 与消息规则，data 为 null，只保存文本，不保存异常或堆栈。
- `BatchResult.of(items)` 按传入顺序保存不可修改快照，拒绝 null 列表或 null 条目；不接收原始输入总数，不校验条目是否完整、序号是否连续或唯一，完整性由业务执行方保证。
- `totalCount`、`successCount`、`failureCount` 与 `status`（`EMPTY`/`ALL_SUCCESS`/`PARTIAL_SUCCESS`/`ALL_FAILED`）在读取时由条目派生，每次读取为 O(n)；需要频繁读取时请自行缓存返回值。
- 修改创建时传入的列表不影响结果，`getItems()` 返回的列表拒绝增删改；不深复制成功数据。
- 支持 Java 序列化往返（前提是数据对象图可序列化）和普通 Jackson 输出，JSON 字段为 `totalCount`、`successCount`、`failureCount`、`status`、`items`，条目字段为 `index`、`inputId`、`success`、`data`、`code`、`message`；空值是否出现由 mapper 的空值策略决定。
- **不承诺跨服务 JSON 反序列化**：`R<BatchResult<T>>` 只作为服务端输出模型。下游通过 Feign 等 HTTP 客户端读取时，请按上述 JSON 字段自建接收 DTO，不要直接以 `BatchResult` 作为反序列化目标。

### 4. 枚举

- **CommonStatus** - 通用状态（启用/禁用）
- **DeleteFlag** - 删除标志
- **OperationType** - 操作类型
- **OrderDirection** - 排序方向

### 5. 常量

- **CommonConstants** - 通用常量（分页、编码、日期格式等）
- **CacheConstants** - 缓存常量（缓存名称、过期时间等）

### 6. 注解

- **Loggable** - 兼容性日志元数据注解；当前无内置运行时消费者，v2.2.1 起弃用并计划于 3.0 移除。

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

<a id="使用示例-1"></a>

#### DTO/VO 使用示例

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
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-common</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</dependency>
```

### 2. 使用示例

```java
// Controller 层：默认参数绑定通过 setter 自动校正
@RestController
public class UserController {
    @GetMapping("/users")
    public R<PageResult<User>> getUsers(PageRequest pageRequest) {
        PageResult<User> result = userService.list(pageRequest);
        return R.success(result);
    }
}

// UserService 接收已校验的 PageRequest，并由持久层实现分页查询

// 业务异常
if (user == null) {
    throw new BizException(ErrorCode.DATA_NOT_FOUND);
}
```

接入 MyBatis-Plus 时，可使用 `mimir-boot-starter-mybatis` 的 `PageConverters.toMybatisPage` 与 `toPageResult` 完成分页对象转换。

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
- ✅ Common 仅保留 `IpUtils`、`LogSanitizer` 等框架边界工具，不提供通用工具集合

### 校验

- ✅ 使用标准 JSR-303 注解（`@NotNull`、`@NotBlank`、`@Email`）
- ✅ 使用 Spring Validation
- ❌ Common 模块不提供自定义校验注解

## 📚 相关文档

- [Mimir Boot 项目主页](../README.md) - 项目总体说明
- [Mimir Boot Parent](../mimir-boot-parent/README.md) - 父 POM 说明
- [Mimir Boot BOM](../mimir-boot-bom/README.md) - 依赖版本管理
