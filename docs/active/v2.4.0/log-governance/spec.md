---
version: v2.4.0
status: draft
created: 2026-11-08
updated: 2026-11-08
---

# Log JSON 日志治理行为规格

## Overview

接入方在容器或服务器日志预设下显式启用 JSON，即可获得统一外层、结构化访问字段、MDC 上下文与沿用原规则的脱敏输出，供通用采集平台检索、告警并关联已有 Trace/Span；应用可注册字段扩展组件补充业务字段。默认文本输出与旧接入方式不变。

来源为[方案大纲](./brainstorm.md)及版本决定 D-111～D-113。本规格描述行为目标，实施与验收状态见[版本记录](../release.md)；设计与实施入口见 [design](./design.md)、[plan](./plan.md)。下文“预设”指 [L-01](../log-runtime/spec.md) 的 container、server 两个内置日志配置。

## Behavior: 选择输出格式

### Scenario: 预设下启用 JSON

Given 应用选择容器预设，设置 `mimir.boot.log.format=json`，并声明 JSON 编码器依赖
When 应用启动后输出一条 INFO 业务日志
Then 标准输出对应该事件恰好一行，该行可被解析为 JSON 对象
And 对象含 `timestamp`、`level`、`service`、`thread`、`logger`、`message`

### Scenario: 未设置格式

Given 应用选择服务器预设，未设置 `mimir.boot.log.format`，也未声明 JSON 编码器依赖
When 应用启动并输出 INFO、ERROR 与访问日志
Then 对同一组事件（时间戳、线程名相同），四类日志文件写入的字节与本需求实施前的配置相同
And 应用依赖树不新增 JSON 编码器或 Jackson

### Scenario: 非预设下启用 JSON

Given 应用未设置 `logging.config`（使用 Starter 自带旧配置），或设置为自定义 XML
When 设置 `mimir.boot.log.format=json` 并启动
Then 启动失败，诊断指出 `mimir.boot.log.format=json` 仅适用于 container、server 预设，并提示改用预设或在自定义 XML 中引用公开编码器
And 不回退文本输出

### Scenario: 非 Logback 后端启用 JSON

Given 应用排除 Logback、使用其他日志后端，设置 `mimir.boot.log.format=json`（无论是否同时指向预设）
When 应用启动
Then 启动失败，诊断指出 JSON 输出需要 container、server 预设及 Logback 后端

### Scenario: 格式值非法

Given 应用选择容器预设，设置 `mimir.boot.log.format=yaml`
When 应用启动
Then 启动失败，诊断包含属性名 `mimir.boot.log.format`，不回显配置原值

### Scenario: 缺少编码器依赖

Given 应用选择服务器预设并设置 `mimir.boot.log.format=json`，未声明 JSON 编码器依赖
When 应用启动
Then 启动失败，诊断指出启用 JSON 需要声明的依赖坐标
And 不创建任何 JSON 或文本日志文件

## Behavior: JSON 公共外层

### Scenario: 普通日志的完整字段

Given JSON 已启用；`spring.application.name=order-service`，唯一 active Profile 为 `prod`，`MIMIR_BOOT_LOG_INSTANCE=order-01`；MDC 含 `traceId=4bf9`、`spanId=00f0`、`tenantId=t1`
When `com.example.OrderService` 输出 INFO `开始处理请求`
Then JSON 中 `service` 为 `order-service`，`environment` 为 `prod`，`instance` 为 `order-01`
And `traceId` 为 `4bf9`，`spanId` 为 `00f0`，`context` 为 `{"tenantId":"t1"}`，不含 `traceId`、`spanId`
And `timestamp` 为带时区偏移的 ISO 8601 字符串，`logger` 为 `com.example.OrderService`，`message` 为 `开始处理请求`

### Scenario: 可选元信息缺失

Given JSON 已启用；未设置 `spring.application.name`、`mimir.boot.log.environment`、`mimir.boot.log.instance`；active Profiles 为 `prod,east`；MDC 为空
When 输出一条 INFO
Then `service` 为 `application`
And 不出现 `environment`、`instance`、`traceId`、`spanId`、`context`、`attributes`、`exception`、`processing` 字段

### Scenario: 链路标识为空串

Given MDC 含 `traceId=""`、`spanId=""`、`tenantId=t1`
When 输出一条 INFO
Then 不出现 `traceId`、`spanId` 字段，`context` 为 `{"tenantId":"t1"}`

### Scenario: 显式环境优先

Given active Profiles 为 `prod,east`，设置 `mimir.boot.log.environment=prod-east`
When 输出一条 INFO
Then `environment` 为 `prod-east`

### Scenario: MDC 排除

Given 设置 `mimir.boot.log.json.exclude-mdc-keys=[internalDebug]`，MDC 含 `internalDebug=1`、`tenantId=t1`
When 输出一条 INFO
Then `context` 为 `{"tenantId":"t1"}`

### Scenario: 特殊字符与异常

Given JSON 已启用
When 输出消息 `第一行\n第二行 "引号" \t`，并携带 `IllegalStateException("bad")`，其原因为 `IOException("io")`
Then 该事件仍为一行合法 JSON，`message` 解析后与原文本一致
And `exception.type` 为 `java.lang.IllegalStateException`，`exception.message` 为 `bad`，`exception.stackTrace` 含两个异常类型及 `Caused by`

### Scenario: 原有级别与路由不变

Given 服务器预设并启用 JSON
When 输出一条 ERROR 业务日志与一条 INFO 业务日志
Then ERROR 事件以 JSON 各写入 `info.log`、`error.log` 一次，两处字段值相同
And INFO 事件只写入 `info.log`

## Behavior: 访问与 SQL 日志

### Scenario: 访问日志结构化字段

Given 启用 JSON 与访问日志，慢阈值为 3000ms
When `GET /orders/123?token=x` 由 IP `192.0.2.10` 发起，返回 200，User-Agent 为 `example-client`，耗时低于阈值
Then `logger` 为 `access.log`，`attributes` 含 `"method":"GET"`、`"path":"/orders/123"`、`"statusCode":200`、`"clientIp":"192.0.2.10"`、`"userAgent":"example-client"`、`"outcome":"COMPLETED"`、`"slow":false`
And `durationMs` 为非负 JSON 整数，`statusCode` 为 JSON 数字，`slow` 为布尔，`errorType` 不出现
And `message` 与文本模式下该请求的访问日志消息相同，不含查询串

### Scenario: 异步超时终态

Given 启用 JSON，异步请求超时
When 访问日志输出
Then 级别与 `attributes.statusCode` 和文本模式下同一请求一致，`attributes.outcome` 为 `TIMEOUT`，`attributes.errorType` 为 `ASYNC_TIMEOUT`
And 同一请求只输出一条访问日志，事件不含 `exception` 字段

### Scenario: 失败终态

Given 启用 JSON，同步请求处理中抛出 `IllegalStateException`
When 访问日志输出
Then 级别与 `attributes.statusCode` 和文本模式下同一请求一致，`attributes.outcome` 为 `ERROR`，`attributes.errorType` 为 `java.lang.IllegalStateException`

### Scenario: 文本模式访问日志不变

Given 未启用 JSON
When 发出与第一个场景相同的请求
Then 访问日志文本行与实施前一致，不出现任何附加键值内容

### Scenario: 保留访问日志 TurboFilter 语义

Given 应用配置了按完整消息或参数判定的 Logback TurboFilter
When 访问日志输出，无论采用 text 还是 JSON
Then 过滤器获得原有模板与参数，并对本次事件判定一次
And DENY 不输出；ACCEPT 可越过 Logger 的级别限制；NEUTRAL 遵守有效级别
And 重复消息过滤器仍能抑制重复访问事件

### Scenario: SQL 日志

Given 启用 JSON，MyBatis 慢 SQL 记录输出一条消息为 JSON 文本的 WARN
When 该事件写出
Then `logger` 为 `SQL.SLOW`，`message` 为原 JSON 文本对应的字符串值，不被展开为对象
And 存在追踪标识时输出 `traceId`

## Behavior: 结构化脱敏

### Scenario: 字段名命中

Given 预设缺省脱敏规则生效（password、token、secret、api_key），替换串为缺省值
When MDC 含 `userPassword=abc`，事件键值对含 `token=123`（数字）
Then `context.userPassword` 与 `attributes.token` 均为替换串
And 其他未命中字段保持原值与原类型

### Scenario: 值正则命中

Given 设置 `mimir.boot.log.mask.enabled-patterns=[phone_number]`
When 事件键值对含 `contact="13800138000"`、`count=13800138000`（数字）
Then `attributes.contact` 为替换串 `****`（缺省值），`attributes.count` 为字符串 `****`

### Scenario: 消息与异常沿用文本规则

Given 预设缺省规则生效
When 消息为 `login password=alpha`，异常消息为 `token=beta`
Then `message` 为 `login password=` 加替换串，`exception.message` 与 `exception.stackTrace` 中不含 `beta`

### Scenario: 规则显式为空

Given 设置 `mimir.boot.log.mask.enabled-patterns=[]` 且无自定义规则
When MDC 含 `password=abc`
Then `context.password` 为 `abc`

### Scenario: 旧文本正则不改写为字段规则

Given 自定义正则 `accessCode=\w+`
When 事件键值对含 `accessCode="xyz"`，消息为 `accessCode=xyz`
Then `message` 中 `xyz` 被替换，`attributes.accessCode` 仍为 `xyz`

## Behavior: 字段扩展组件

### Scenario: 注册扩展补充业务字段

Given 应用注册一个扩展组件，返回 `{"orderId":"o-1","items":[1,2]}`
When 刷新完成后输出一条 INFO
Then `attributes` 含 `orderId="o-1"`、`items=[1,2]`
And 该组件对这条事件只被调用一次，调用发生在产生日志的线程

### Scenario: 多个扩展的覆盖与排序

Given 扩展 A（order=1）返回 `{"k":"a"}`，扩展 B（order=2）返回 `{"k":"b","x":1}`，事件键值对含 `k="e"`；扩展 C、D 无 order，Bean 名分别为 `alpha`、`beta`，分别返回 `{"y":"from-alpha"}`、`{"y":"from-beta"}`
When 输出一条 INFO
Then `attributes.k` 为 `a`，`attributes.x` 为 1，`attributes.y` 为 `from-alpha`
And 调换 Bean 注册顺序后结果相同

### Scenario: 受保护字段

Given Bean 名为 `tagger` 的扩展返回 `{"level":"x","method":"PUT"}`
When 普通业务日志与访问日志各输出一条
Then 两条事件的顶层 `level` 均为原级别；访问日志 `attributes.method` 保持原请求方法
And `processing.errors` 含 `{"field":"/attributes/level","provider":"tagger","reason":"protected_field"}`，访问日志另含 `/attributes/method` 的同类记录

### Scenario: 扩展抛异常或返回非法值

Given 扩展 A（order=1）抛出运行时异常，扩展 B（order=2）返回按插入顺序的 `{"bean": new Object(), "nan": NaN, "ok": 1}`
When 输出一条 WARN
Then 事件仍输出，级别为 WARN，`attributes.ok` 为 1，`bean`、`nan` 不出现
And `processing.errors` 依次含 A 的 `provider_error`（无 field）、B 的 `/attributes/bean` `unsupported_type`、`/attributes/nan` `invalid_value`；输出中不含异常消息或对象内容
And 下一条日志再次调用 A

### Scenario: 扩展无补充或返回异常键

Given 扩展 `empty` 返回空 Map，扩展 `nulls`（Bean 名）返回 null；扩展 `badkey` 返回含 null 键的 Map `{null:"v","ok":"y"}`
When 输出一条 INFO
Then `attributes.ok` 为 `y`；`empty`、`nulls` 不产生字段与处理标记
And `processing.errors` 含 `{"provider":"badkey","reason":"invalid_value"}`（无 field）

### Scenario: 扩展内部打日志

Given 扩展在执行中输出一条 INFO
When 业务输出一条 INFO
Then 业务事件正常输出且含该扩展字段；嵌套事件若被输出，不含任何扩展字段
And 两条事件都不产生 `processing.errors`

### Scenario: 启动早期与关闭阶段

Given 注册了扩展组件
When 上下文刷新完成前、刷新后、开始关闭后各输出一条 INFO
Then 第一条与第三条立即输出且无扩展字段、无处理标记；第二条含扩展字段
And 开始关闭后扩展不再被调用

### Scenario: 扩展只读输入

Given 扩展尝试修改事件视图中的 `context` 或 `attributes`
When 扩展执行
Then 修改被拒绝并按 `provider_error` 记录；后续扩展看到的视图与原事件一致，看不到其他扩展结果，也取不到原始异常对象

## Behavior: 事件自带键值对

### Scenario: 支持的类型

Given 业务以 SLF4J 键值对附加 `id=7`、`ok=true`、`tags=List.of("a")`、`meta={"k":null}`
When JSON 输出
Then `attributes` 中各值保持数字、布尔、数组、对象与 null

### Scenario: 不支持的类型

Given 业务附加 `order=<JavaBean>`，其 `toString()` 含 `secret-x`
When JSON 输出
Then `attributes` 不含 `order`，输出任何位置不含 `secret-x`
And `processing.errors` 含 `{"field":"/attributes/order","reason":"unsupported_type"}`，无 `provider`

### Scenario: 循环与非法键

Given 附加 `m={"a":1,"self":<m 自身>}` 与 `n={1:"x"}`（键为整数）
When JSON 输出
Then `attributes.m` 为 `{"a":1}`，`attributes` 不含 `n`
And `processing.errors` 含 `{"field":"/attributes/m/self","reason":"circular_reference"}` 与 `{"field":"/attributes/n","reason":"invalid_value"}`

### Scenario: 复用日志 builder 不串事件

Given 业务复用一个带键值对的 SLF4J LoggingEventBuilder 连续记录两条日志
When 两次记录之间修改 MDC、字段或脱敏规则
Then 每条事件捕获各自的字段与规则，扩展分别执行一次
And 捕获出口不向 builder 的共享键值对列表写入保留快照键

### Scenario: 记录后修改原容器

Given 服务器预设启用 JSON；附加可变 Map `{"n":1}`，日志调用返回后立即改为 `{"n":2}`
When 异步出口稍后写出该事件
Then `attributes.n` 为 1

## Behavior: 字段截断与处理标记

### Scenario: 按码点截断

Given `mimir.boot.log.json.field-length-limits` 含 `/message: 3`
When 消息为 `a😀中文`
Then `message` 为 `a😀中`，不含孤立代理项
And `processing.truncatedFields` 为 `["/message"]`，`processing.errors` 不出现

### Scenario: 先脱敏后截断

Given 限制 `/context/password: 4`，`password` 字段名规则生效，替换串为 `******`
When MDC 含 `password=abcdefgh`
Then `context.password` 为 `****`，`truncatedFields` 含 `/context/password`

### Scenario: 路径转义

Given 限制 `/attributes/a~1b: 2`、`/context/tenant.id: 2`
When 键值对含 `a/b="xyz"`，MDC 含 `tenant.id=abc`
Then 两字段分别截为 `xy`、`ab`，`truncatedFields` 恰含 `/context/tenant.id`、`/attributes/a~1b` 两项（顺序不限）

### Scenario: 非法长度配置

Given 限制值为 `0`，或路径为 `message`（缺少前导 `/`），或含非法转义 `/a~2`
When 应用启动
Then 启动失败，诊断包含属性名 `mimir.boot.log.json.field-length-limits`

### Scenario: 无需加工

Given 无截断配置，事件字段均合法
When 输出事件
Then 不出现 `processing` 字段

## Behavior: 自定义 XML 接入

### Scenario: 引用公开编码器

Given 自定义 `logging.config` 按 README 引入 Mimir JSON 支持片段、声明字段捕获出口并在控制台出口使用公开编码器；未设置 `mimir.boot.log.format`；`json.exclude-mdc-keys=[internalDebug]`
When 应用输出带 MDC `internalDebug=1` 的 INFO
Then 输出为 JSON 且不含 `internalDebug`，字段与预设下相同

### Scenario: 未声明字段捕获出口

Given 自定义 XML 使用公开编码器，但未声明字段捕获出口；注册了扩展组件
When 输出一条 INFO
Then 事件以 JSON 输出，不含扩展字段，也不产生处理标记

### Scenario: 编码器依赖缺失

Given 自定义 XML 引用公开编码器，但未声明 JSON 编码器依赖
When 应用启动
Then 启动失败，失败原因链含缺失的 `net.logstash.logback` 类名

## Behavior: 配置仅在启动时生效

### Scenario: 运行期刷新

Given 启用 JSON，`exclude-mdc-keys=[a]`
When 运行期通过配置刷新将其改为 `[b]`
Then 后续事件仍排除 `a`、保留 `b`；重启后改为排除 `b`

### Scenario: 编程式脱敏入口

Given 启用 JSON
When 运行期以已有编程式入口新增正则 `ord-\d+`
Then 后续事件的 `message` 与 `attributes` 字段值均按新正则处理，入口行为与文本模式一致

### Scenario: 排队后规则变化

Given 服务器预设启用 JSON，一条 ERROR 事件已进入两个异步队列
When 写出前编程式入口新增正则
Then 该事件在 `info.log`、`error.log` 中使用入队时的规则，两处结果相同

## Constraints

- 单行承诺：每个经 Mimir JSON 编码器写出的事件恰好一行合法 JSON（UTF-8）；不覆盖 banner、直接标准输出和 Logback 后端诊断。
- 单字段截断限制取值 1～2,147,483,647 码点；不提供整条日志大小上限。
- 结构化值嵌套深度上限 32 层（`attributes` 下第一层为第 1 层），超出部分按 `invalid_value` 隔离；字段顺序不属于契约，消费方按字段名读取。
- 性能（D-113）：只记录基准、不设发布门槛。在同一预设下对比 text 与 json、无扩展与 3 个轻量扩展，覆盖同步控制台与 server 异步文件，记录吞吐与 P95/P99 写入延迟并写入[版本记录](../release.md)。
- 兼容：使用 container、server 预设或旧默认 XML 且未设置 `format=json` 时，输出、文件、依赖与本需求实施前一致。自定义 XML 若以 `%kvp`、logstash 或 Logback 自带 JSON 编码器输出键值对，访问日志会新增最多 9 个键值对（成功终态无 `errorType`），作为显式兼容说明写入升级说明。
