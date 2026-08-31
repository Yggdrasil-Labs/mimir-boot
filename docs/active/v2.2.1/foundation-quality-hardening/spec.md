---
id: foundation-quality-hardening
version: v2.2.1
status: shipped
owner: YoungerYang-Y
created: 2026-08-30
updated: 2026-08-31
---

# 底座质量强化

## Overview

让基础框架在打包发现、异步请求、日志安全、RPC 组合、分页边界和公共 API 生命周期上表现可预测，避免“开发环境正常、发布制品或异常路径失效”的底座级技术债。

## Behavior: 可执行制品发现外部 Mapper

### Scenario: 发布制品中的 Mapper 可被发现

Given 应用依赖的可执行 JAR 包含 `org.example.order.mapper` 下的 Mapper 类文件
When 应用启动并启用 Mapper 自动发现
Then `org.example.order.mapper` 被纳入实际扫描范围
And 对应 Mapper 可以被应用正常使用

### Scenario: 默认包与外部包去重

Given classpath 同时包含框架默认包覆盖范围内的 Mapper 和 `org.example.order.mapper` Mapper
When 应用计算有效 Mapper 包集合
Then 每个包只出现一次
And 外部包仍被保留在实际扫描范围内

### Scenario: 单个资源无法解析

Given classpath 中存在受控 Resource，其 `getURL()` 返回缺少 `!/` 分隔符的标识 `jar:file:/fixtures/bad.jar!broken/mapper/UserMapper.class`，且同时存在可正常解析的 `jar:file:/fixtures/good.jar!/org/example/mapper/OrderMapper.class`
When 应用执行自动发现
Then 该资源被跳过
And WARN 日志包含该资源标识和解析失败原因
And 其他可解析包仍继续进入扫描范围

## Behavior: 异步 HTTP 访问日志记录最终结果

本 Behavior 的 `Duration` 定义为过滤器首次接收请求到生命周期结束的单调时钟差值：同步请求以 filter chain 的 finally（正常返回或抛异常）为终点，异步请求以首个完成/超时/错误终态回调为终点，listener 注册失败或检测到异步已完成则以回退处理结束为终点；向下取整为毫秒。日志字段格式固定为 `Status=[整数] Outcome=[COMPLETED|TIMEOUT|ERROR|REGISTRATION_ERROR] ErrorType=[标识] Duration=[非负整数]ms`。

### Scenario: 同步请求记录一次结果

Given 一个未被排除的同步 HTTP 请求最终返回 200
When 请求处理结束
Then 访问日志恰好记录一次
And 日志包含 `Status=[200]`、`Outcome=[COMPLETED]`、`ErrorType=[-]` 和匹配 `Duration=[0-9]+ms` 的非负耗时字段

### Scenario: 同步 5xx 响应仍标记正常完成

Given 一个未被排除的同步 HTTP 请求由处理器正常返回 HTTP 500，未抛出异常
When 请求处理结束
Then 访问日志恰好记录一次
And 日志包含 `Status=[500]`、`Outcome=[COMPLETED]`、`ErrorType=[-]` 和匹配 `Duration=[0-9]+ms` 的非负耗时字段

### Scenario: 同步 4xx 响应仍标记正常完成

Given 一个未被排除的同步 HTTP 请求由处理器正常返回 HTTP 404，未抛出异常
When 请求处理结束
Then 访问日志恰好记录一次
And 日志包含 `Status=[404]`、`Outcome=[COMPLETED]`、`ErrorType=[-]` 和匹配 `Duration=[0-9]+ms` 的非负耗时字段

### Scenario: 同步处理异常记录 ERROR

Given 一个未被排除的同步 HTTP 请求在 `FilterChain.doFilter` 中抛出 `java.lang.IllegalStateException`，响应状态为 500
When 请求处理结束
Then 访问日志恰好记录一次
And 日志包含 `Status=[500]`、`Outcome=[ERROR]`、`ErrorType=[java.lang.IllegalStateException]` 和匹配 `Duration=[0-9]+ms` 的非负耗时字段

### Scenario: 异步请求等待完成

Given 一个未被排除的 HTTP 请求转为异步处理并最终返回 201
When 初始派发返回且异步处理随后完成
Then 初始派发前后 access logger 的事件数均为 0
And 异步完成后恰好记录一次 `Status=[201]`、`Outcome=[COMPLETED]`、`ErrorType=[-]` 和匹配 `Duration=[0-9]+ms` 的非负耗时字段

### Scenario: 异步超时

Given 一个 HTTP 异步请求在完成前超时，响应状态为 503
When 容器触发超时终态并结束该异步请求
Then 访问日志恰好记录一次 `Status=[503]`、`Outcome=[TIMEOUT]` 和 `ErrorType=[ASYNC_TIMEOUT]`
And 不再追加第二条该请求的访问日志

### Scenario: 异步错误

Given 两个 HTTP 异步请求在完成前发生错误且响应状态均为 500，其中一个 `AsyncEvent.getThrowable()` 为 `java.lang.IllegalStateException`，另一个返回 null
When 容器分别触发错误终态并结束对应异步请求
Then 两个请求各自恰好记录一次 `Status=[500]` 和 `Outcome=[ERROR]`
And 有异常对象的请求记录 `ErrorType=[java.lang.IllegalStateException]`，无异常对象的请求记录 `ErrorType=[ASYNC_ERROR_WITHOUT_THROWABLE]`
And 任一请求都不再追加第二条访问日志

### Scenario: 多轮异步派发重新注册监听器

Given 一个未被排除的 HTTP 请求先后两次调用 `startAsync()`，第二轮异步处理最终返回 204
When 两个不同的异步上下文 C1、C2 依次进入生命周期，C2 已进入后再次受控触发迟到的 C1 `onStartAsync` 和重复的 C2 `onStartAsync`，随后第二轮触发完成终态
Then `AsyncContext.addListener` 被调用恰好 2 次
And C1、C2 各恰好注册一次 listener，迟到旧上下文和当前上下文的重复通知均不增加注册次数
And 初始派发及第一轮异步派发均不产生访问日志
And 第二轮完成后恰好记录一次 `Status=[204]`、`Outcome=[COMPLETED]`、`ErrorType=[-]` 和匹配 `Duration=[0-9]+ms` 的非负耗时字段

### Scenario: 异步监听器注册失败回退

Given 一个未被排除的 HTTP 请求已进入异步状态且 `AsyncContext.addListener` 抛出 `IllegalStateException`
When 过滤器处理监听器注册失败
Then 访问日志恰好记录一次当前响应状态 `Status=[200]`、`Outcome=[REGISTRATION_ERROR]`、`ErrorType=[java.lang.IllegalStateException]`
And 后续迟到的完成、超时或错误回调不再追加访问日志

### Scenario: 异步已完成注册回退

Given 一个未被排除的 HTTP 请求在注册 listener 前已经完成异步生命周期，当前响应状态为 204
When 过滤器检测到异步已完成而未执行 listener 注册
Then 访问日志恰好记录一次 `Status=[204]`、`Outcome=[REGISTRATION_ERROR]`、`ErrorType=[ASYNC_ALREADY_COMPLETED]`
And 不再尝试注册 listener 或追加终态访问日志

### Scenario: 后续异步派发注册失败回退

Given 一个未被排除的 HTTP 请求首轮 listener 注册成功并进入 `REGISTERED`，第二轮 `onStartAsync` 的 `AsyncContext.addListener` 抛出 `IllegalStateException`，当前响应状态为 200
When 过滤器处理第二轮 listener 注册失败
Then 访问日志恰好记录一次 `Status=[200]`、`Outcome=[REGISTRATION_ERROR]`、`ErrorType=[java.lang.IllegalStateException]`
And 后续完成、超时或错误回调不再追加访问日志

## Behavior: HTTP 请求上下文不泄漏

### Scenario: 同步请求恢复进入前上下文

Given 请求进入时线程 MDC 固定为 `traceId=trace-before`、`requestId=request-before`、`ip=ip-before`、`unrelated=unrelated-before`
When 请求处理完成
Then 线程 MDC 恢复为上述完整快照
And 本次请求生成的 `traceId/requestId/ip` 不影响后续任务

### Scenario: 异步初始派发释放上下文

Given 请求进入时线程 MDC 固定为 `traceId=trace-before`、`requestId=request-before`、`ip=ip-before`、`unrelated=unrelated-before`，初始派发写入新的三项值后转为异步
When 初始派发线程离开请求
Then 该线程 MDC 恢复为上述完整快照
And 后续复用该线程的新请求看不到上一次请求的值

### Scenario: 异步错误路径清理上下文

Given 异步请求因 `java.lang.IllegalStateException` 以 HTTP 500 错误结束且没有后续派发
When 容器完成异步生命周期
Then 初始派发线程恢复进入请求前的完整 MDC 快照（进入前没有值的 key 断言为不存在）
And 无关 MDC key 保持进入前的固定值 `unrelated-before`
And HTTP 状态仍为 500，异常类型仍为 `java.lang.IllegalStateException`

### Scenario: 异步超时路径清理上下文

Given 异步请求在没有后续派发时超时并以 HTTP 503 结束，容器不提供异常对象
When 容器完成异步生命周期
Then 初始派发线程恢复进入请求前的完整 MDC 快照（进入前没有值的 key 断言为不存在）
And 无关 MDC key 保持进入前的固定值 `unrelated-before`
And HTTP 状态仍为 503，异常对象断言为不存在

## Behavior: 日志中的敏感信息完整遮蔽

本 Behavior 的明文为零保证以每次 `%mask` 或 `%maskThrowable` converter 调用为边界；动态刷新恰好发生在同一 event 的两个独立 converter 调用之间时，不承诺跨 converter 的同代快照，但每次调用自身必须使用完整快照。

### Scenario: 普通键值和编码键遮蔽

Given 已启用 password、secret 或 token 脱敏规则
When 日志包含 `{"password":"secret","publicKey":"public-value","other":"visible"}`、`password=secret` 或 `%70assword=secret`
Then `secret` 出现次数为 0
And 键名、分隔符和非敏感字段结构保持可读

### Scenario: 转义引号值整体遮蔽

Given 日志为 `password="safe\\\"secret"; tail=visible` 且已启用 password 脱敏规则
When 日志完成脱敏
Then 输出精确包含 `password="****"; tail=visible`
And `safe`、`secret` 等敏感值明文出现次数均为 0

### Scenario: 异常链消息遮蔽且堆栈保留

Given `RuntimeException("password=message-secret")` 的 cause 为 `IllegalStateException("secret=cause-secret")`，并含 `IllegalArgumentException("token=suppressed-secret")` suppressed 异常
When 日志渲染异常
Then `message-secret`、`cause-secret` 和 `suppressed-secret` 出现次数均为 0
And 输出各出现 1 次 `RuntimeException`、`IllegalStateException`、`IllegalArgumentException`、`Caused by` 和 `Suppressed`
And 输出至少 1 条包含 `SensitiveThrowableProxyConverterTest.java` 的堆栈帧

## Behavior: RPC 适配器服从 Core 开关组合

### Scenario: Core 与适配器均启用

Given RPC Core、Feign 和 Dubbo 均使用默认启用配置
When 应用上下文启动
Then Core、Feign 和 Dubbo 治理 Bean 均存在
And 上下文无缺失依赖错误

### Scenario: Core 关闭时适配器默认不安装治理能力

Given `mimir.boot.rpc.core.enabled=false` 且 Feign、Dubbo 保持默认配置
When 应用上下文启动
Then 应用启动成功
And Feign、Dubbo 的 RPC Core 治理适配器均不注册

### Scenario: Core 关闭且适配器显式开启

Given `mimir.boot.rpc.core.enabled=false`、`mimir.boot.feign.enabled=true` 和 `mimir.boot.dubbo.enabled=true`
When 应用上下文启动
Then 应用启动成功
And Feign、Dubbo 的 RPC Core 治理适配器均不注册

### Scenario: 单独关闭适配器

Given RPC Core 保持启用且仅 Feign 或 Dubbo 的开关为 false
When 应用上下文启动
Then Core Bean 仍可用
And 被关闭的适配器治理 Bean 不注册，另一个启用的适配器不受影响

## Behavior: Feign 观测地址不携带凭证

### Scenario: 成功调用不记录查询凭证

Given Feign 请求 URL 为 `https://api.example.test:8443/orders?token=secret#detail`
When 请求成功并输出 DEBUG 观测日志
Then 日志包含规范化地址 `https://api.example.test:8443/orders`
And 不包含 query、fragment 或 `secret`

### Scenario: URL userinfo 不进入观测元数据

Given Feign 请求 URL 为 `https://user:password@api.example.test:8443/orders`
When 框架生成调用观测元数据和 DEBUG 日志
Then `RpcCallMetadata.target` 精确为 `api.example.test:8443`
And DEBUG 日志包含 `https://api.example.test:8443/orders`
And userinfo 不出现在元数据或日志中

### Scenario: 相对 URL 仍可执行

Given Feign 使用相对 URL `/orders?token=secret`
When 委托客户端执行请求并输出 DEBUG 日志
Then 委托客户端收到原始 URL `/orders?token=secret` 并返回预设响应
And DEBUG 日志包含 `/orders` 但不包含 `token=secret`

### Scenario: 缺失 authority、非层级或非法 URL 的元数据不泄露

Given Feign 观测夹具依次收到可解析但缺少 host 的绝对层级 URL `https:/orders?token=secret`、opaque URL `mailto:user:password@example.test`、非法 URL `http://[bad` 和 null URL（`Request` 与 `Request.Options` 均为非 null，null 仅表示 `Request.url()` 返回 null）
When 框架生成 `RpcCallMetadata` 与 DEBUG 观测日志
Then 四组结果分别精确为以下 `service/target/debugUrl`，且输出不包含输入中的 userinfo、query 或非法原文

| 输入 | service | target | debugUrl |
|------|---------|--------|----------|
| `https:/orders?token=secret` | `[unknown-service]` | `[invalid-authority]` | `[invalid-authority]` |
| `mailto:user:password@example.test` | `[unknown-service]` | `[opaque-url]` | `[opaque-url]` |
| `http://[bad` | `[unknown-service]` | `[invalid-url]` | `[invalid-url]` |
| null | `[unknown-service]` | `[missing-url]` | `[missing-url]` |

And null URL 的委托客户端仍收到同一非 null `Request` 和 `Request.Options`，实际请求不因观测地址生成失败而被拒绝

## Behavior: 分页边界保持可计算

### Scenario: 最大总记录数计算精确页数

Given totalCount 为 `Long.MAX_VALUE` 且 pageSize 为 1000
And pageIndex 为 1
When 创建分页结果
Then totalPages 为 `9223372036854776`
And totalPages 为正数且 hasNext 为 true（以 `pageIndex=1` 的结果断言）

### Scenario: 可表示的 offset 保持精确

Given pageIndex 为 `Long.MAX_VALUE` 且 pageSize 为 1
When 计算分页 offset
Then offset 为 `9223372036854775806`（即 `(pageIndex - 1) * pageSize`）
And 不改变既有 pageIndex、pageSize 和排序方向纠正规则

### Scenario: offset 超出 Long 范围

Given pageIndex 为 `Long.MAX_VALUE` 且 pageSize 为 1000
When 计算分页 offset
Then 抛出 `IllegalArgumentException("分页偏移量超出 Long 范围")`
And 不返回负数 offset 或静默截断结果

## Behavior: 无运行时实现的日志注解不误导使用方

### Scenario: 既有源码仍可编译

Given 下游源码使用 `@Loggable` 及其现有属性
When 使用 v2.2.1 编译该下游项目
Then 源码仍可编译
And 编译器给出指向 `Loggable` 的弃用诊断

### Scenario: 反射元数据保持可读

Given 运行时读取既有 `Loggable` 注解及其 `module`、`type`、`description`、`logRequest`、`logResponse`、`logExecutionTime` 属性
When 应用读取注解元数据
Then 六个属性的签名、默认值和值语义均保持不变

### Scenario: 预编译下游仍可运行

Given 下游项目已使用 v2.2.1 之前版本编译并引用 `Loggable`
When 仅替换为 v2.2.1 的 common 制品后运行
Then 下游二进制加载和调用成功
And 不发生 `NoSuchMethodError`、`NoSuchFieldError` 或 `IncompatibleClassChangeError`

### Scenario: 文档明确迁移方向

Given 使用方查阅 common 模块能力说明
When 文档展示日志能力
Then `Loggable` 被标记为 v2.2.1 弃用
And 文档明确说明目标是在 3.0 移除，且本需求不新增 AOP 行为

## Constraints

- 同步和异步的每个未排除 HTTP 请求恰好产生 1 条访问日志；异步日志必须在终态可观察后记录。
- 脱敏测试中，每次 converter 调用输出的 password、secret、token、异常消息和 URL 凭证明文出现次数均为 0；非敏感结构和异常堆栈保留；不把跨 converter 的动态刷新竞态纳入同一 event 的同代保证。
- `PageRequest.getOffset()` 永不返回负值；超出 Long 可表示范围时必须抛出明确的算术边界错误；`PageResult.totalPages` 对合法输入不得溢出。
- `mimir.boot.rpc.core.enabled=false` 与 Feign/Dubbo 默认配置组合必须通过上下文启动测试，且不得注册依赖 Core 的适配器治理 Bean。
- Core=false 且 Feign/Dubbo 显式 true 的组合必须同样通过上下文启动测试并保持适配器治理 Bean 缺失。
- v2.2.1 不删除现有公开类型、方法、注解属性或配置键；弃用 API 必须保持源码和二进制兼容。
- 本文出现的 `PageRequest.getOffset()`、`Loggable` 属性名和 `mimir.boot.rpc.core.enabled` 均是需要锁定的公开 API/配置契约；实现类、内部状态名和测试夹具路径仍以 Design 为准。
- 受影响模块的回归测试使用 Java 17，并纳入一次 `./mvnw clean verify`；日志脱敏性能证据要求每次运行以 Surefire `forkCount=0` 在 Maven JVM 内执行测试，使用同一 1 KiB 消息比较无规则基线与 3 个字段规则候选，各预热 100000 次、测量 1000000 次；连续独立启动 3 个 Maven/JVM 进程且不剔除离群值，三次差值平均不超过 20 µs。证据必须打印实际测试 JVM 的输入参数并证明 `-Xms1g -Xmx1g -XX:+AlwaysPreTouch` 生效；该基准属于发布前人工证据而非 CI 硬门禁。
