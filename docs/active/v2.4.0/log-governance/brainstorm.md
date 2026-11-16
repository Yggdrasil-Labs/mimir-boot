---
version: v2.4.0
status: draft
created: 2026-11-06
updated: 2026-11-16
---

# Log JSON 日志治理

[L-04](../brainstorm.md)：面向通用采集平台，便于检索、监控告警和关联已有 Trace/Span。范围已确认，已于 2026-11-15 实施；最终验收与基准见[版本记录](../release.md)。

## 已确认决策

- **接入**：`mimir.boot.log.format=text|json` 格式开关，默认 `text`，仅对 container、server 两个内置预设生效；未选用预设时 jar 内旧 `logback-spring.xml` 保持原样，配置 `json` 即启动失败并提示改用预设或自定义 XML（D-112）。自定义 `logging.config` 可在 XML 中显式引用公开的 Mimir JSON 编码器，不经 `format` 开关；`json.*` 配置无论是否选用预设均由 EPP 规范化，预设与自定义 XML 读取同一份配置（D-113）。保留业务日志写法。
- **依赖**：`logstash-logback-encoder` 在 log starter 中声明为 optional，版本由 BOM 管理；需要 JSON 的应用自行声明，默认文本应用的依赖不变（D-112）。
- **配置校验**：格式值错误、非预设下启用 JSON、显式长度限制不是正整数，或启用 JSON 时缺少编码器依赖，启动失败并指出配置项及原因，不回退文本；缺少可选字段或未注册扩展组件不影响启动。校验沿用预设 EPP 在 Logback 初始化前执行。
- **配置生效**：新增格式、元信息、MDC 排除和长度限制配置仅在启动时加载，变更需重启生效；不新增 JSON 配置热更新能力，已有编程式脱敏入口保持原语义。
- **范围**：普通日志统一外层，访问日志提供独立字段并保留原拼接消息作为 `message`（D-112）；SQL 使用相同外层，原消息保持字符串。均不自动解析 `message` 中的 JSON。
- **MDC**：默认全量进入 `context`，支持排除名单；值保持字符串，标准链路字段不重复输出，使用事件自身的上下文。
- **扩展入口**：接收日志事件已有的结构化键值，并提供 Mimir 字段补充接口，允许应用注册组件补充 `attributes`；接口不暴露 Jackson 或 Encoder 的第三方类型。
- **扩展输入输出**：各组件读取同一份只读原始事件，独立返回字段 Map，不读取其他扩展结果；框架按既定优先级合并。有依赖的字段由同一组件生成。
- **扩展时机**：在产生日志的线程中执行一次，生成字段快照供多个输出目标共用，编码时不重复调用；扩展组件应只做轻量取值。
- **扩展重入**：同一线程执行扩展期间，嵌套日志跳过所有字段扩展，交由原日志链处理；保留 Logback 防重入保护，不保证嵌套日志一定输出。正常跳过不计入 `processing.errors`，扩展结束或异常后清理重入状态。
- **启动早期**：JSON 编码、脱敏和事件自带字段不依赖业务扩展组件；组件未就绪时直接输出，不等待、不缓存、不补写，就绪后仅增强后续事件。正常初始化期间的未就绪不计入 `processing.errors`。
- **关闭阶段**：容器开始关闭后不再发起新的扩展调用；已生成快照继续按原输出和队列策略处理，后续日志保留基础字段、事件自带字段及脱敏后的异常。正常停用扩展不计入 `processing.errors`。
- **字段冲突**：顶层字段及框架访问日志字段禁止扩展覆盖；其余业务字段允许按扩展优先级覆盖，包括事件原有的同名业务字段。
- **扩展优先级**：沿用 Spring 的 `@Order` / `Ordered`，数值越小优先级越高，同名业务字段以高优先级扩展的值为准；不新增优先级覆盖配置项。
- **同级冲突**：优先级相同时按 Bean 名称升序排列，同名业务字段保留前者的值；需要指定覆盖关系时设置不同的 `@Order`。
- **扩展值**：支持字符串、有限数字、布尔、null 及嵌套 Map/List；Map 键为字符串，不遍历 JavaBean，不覆盖顶层字段。扩展结果统一经过类型检查、脱敏与编码。
- **事件键值对**：SLF4J 2 `addKeyValue` 等事件自带键值对与扩展值适用同一类型规则；其他类型按 `unsupported_type` 隔离该字段，不调用 `toString()`（D-113）。
- **脱敏**：沿用应用及原预设的有效规则，不增加默认规则。覆盖字段名、纯值及适用的自定义/编程式正则；字段名命中时整体遮蔽值。未命中保持类型，命中可转为替换字符串。
- **正则输入**：`message`、异常消息和堆栈沿用原文本规则；`context`、`attributes` 的字段名规则匹配键名，自定义正则逐字段值匹配，不处理整条 JSON。依赖 `accessCode=...` 等文本格式的旧正则不自动改写为结构化字段规则。
- **异常与失败**：默认完整堆栈；非法扩展值、循环引用只隔离对应字段，保留基础事件并标记失败，诊断不回显原值。
- **截断与编码**：字段排除 → 脱敏 → 按配置截断 → JSON 编码。指定字符串字段按 Unicode 码点计数并截断，不切断代理项对；有独立截断标记，每条编码事件保持合法单行 JSON。单字段限制不代表整条日志大小上限。
- **处理线程**：事件产生时固定字段快照及有效治理配置；字段排除、脱敏、截断和编码在输出阶段执行。异步 Appender 使用后台线程，同步出口仍在调用线程处理；多个出口使用同一份数据及规则快照，允许各自执行治理处理。
- **字段路径**：长度配置及处理标记统一使用 [JSON Pointer（RFC 6901）](https://www.rfc-editor.org/rfc/rfc6901.html) 字符串格式；层级用 `/` 分隔，键名中的 `~`、`/` 分别写成 `~0`、`~1`，点号按普通字符处理。
- **输出**：沿用原级别、路由、滚动和异步策略；每个既定出口只输出一次，保留服务器 ERROR 同时进入 `info.log`、`error.log` 的分流。

## 编码器选型

2026-11-07 采纳 **`logstash-logback-encoder 8.1` 的 `LoggingEventCompositeJsonEncoder`**，复用字段组合、嵌套对象、MDC 筛选和自定义 Provider，兼容当前 Jackson 2 基线。

- **依赖控制**：Encoder 为 optional 依赖，启用 JSON 的应用自行声明；最小日志应用随之传递引入 Jackson 三件套，已有完整 Jackson 的应用只新增 Encoder，未启用 JSON 的应用不受影响。BOM 新增 Encoder 版本管理。Jackson、Logback 继续由现有 BOM 管理，不额外引入 `logback-access`、UUID 或其他数据格式模块。Encoder 内嵌 Disruptor，但沿用现有异步 Appender。
- **适配职责**：Mimir 负责字段协议、原脱敏语义、类型限制和扩展失败隔离。原编码器的 Provider 抛出 `IOException` 会丢弃整条事件，应用扩展需先隔离失败再交给编码器。
- **验证边界**：隔离探针已验证 Java 17、Logback 1.5.18、SLF4J 2.0.17、Jackson 2.17.3 下的嵌套字段、类型保留、MDC 排除、Provider、异常及路径/值脱敏；尚未验证 Spring 启动生命周期、原脱敏规则完整兼容和性能。

依据：[8.1 官方说明](https://github.com/logfellow/logstash-logback-encoder/blob/logstash-logback-encoder-8.1/README.md)、[发布 POM](https://repo.maven.apache.org/maven2/net/logstash/logback/logstash-logback-encoder/8.1/logstash-logback-encoder-8.1.pom)。本轮仅确定选型，尚未修改项目依赖或实现。

## 配置草案

以下配置均在 `mimir.boot.log` 下，尚未实现。

| 配置项 | 默认行为 | 用途 |
|---|---|---|
| `format` | `text` | 选择文本或 JSON |
| `environment` | 未配置时仅回退到唯一 active Profile，否则省略 | 显式值优先，使用 Spring 最终生效的 active Profiles，不取默认 Profile |
| `instance` | 未配置时省略 | 由部署侧通过配置、启动参数或 `MIMIR_BOOT_LOG_INSTANCE` 注入 |
| `json.exclude-mdc-keys` | 空列表 | 按 MDC 原始键名排除 |
| `json.field-length-limits` | 空 Map，不截断 | JSON Pointer → 最大 Unicode 码点数，值须为正整数 |

配置示例：

```yaml
spring:
  application:
    name: order-service
  profiles:
    active: prod

logging:
  config: classpath:com/yggdrasil/labs/log/logback/container.xml

mimir:
  boot:
    log:
      format: json
      # environment: prod  # 多 Profile 时可显式指定
      # instance 由部署侧注入
      json:
        exclude-mdc-keys: [internalDebug]
        field-length-limits:
          "[/message]": 2000
          "[/exception/stackTrace]": 16000
      mask:
        enabled-patterns: [password, token, secret, api_key]
```

长度数值和脱敏列表仅为示例。Map 键外的 `[]` 用于 Spring 绑定时保留 `/`，实际路径仍为 `/message` 等；已用 Boot 3.3.13 Binder 核对该绑定行为。

Kubernetes 容器的 `env` 注入示例：

```yaml
env:
  - name: MIMIR_BOOT_LOG_INSTANCE
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
```

该值是 Pod 名称，不是容器 ID；也可使用 `metadata.uid`。程序启动时读取部署提供的值，不自行生成 UUID 或查询容器平台。参考 [Kubernetes Downward API](https://kubernetes.io/docs/concepts/workloads/pods/downward-api/)。

## 字段扩展接口

```java
public interface LogFieldProvider {
    Map<String, ?> provide(LogEventView event);
}
```

`LogEventView` 由框架提供，只读且不暴露 Logback、Jackson 类型。返回值仅补充 `attributes`，空 Map 表示无补充。组件作为 Spring Bean 注册；接口本身不继承 `Ordered`，实现类按需使用 `@Order` 或 `Ordered`。

异常输入仅提供只读的类型、消息摘要，不暴露原始 `Throwable`；日志输出仍保留完整异常堆栈。

### 事件视图

| 字段 | Java 类型 | 内容与缺省行为 |
|---|---|---|
| `timestamp` | `Instant` | 事件时间 |
| `level` | `String` | 日志级别 |
| `service` | `String` | 服务名，沿用公共字段取值 |
| `environment` | `String` | 显式配置或唯一 active Profile，多个或缺失且未配置时为空 |
| `instance` | `String` | 部署侧提供的实例标识，允许缺失 |
| `logger` | `String` | 原 Logger 名称 |
| `thread` | `String` | 事件产生线程名 |
| `message` | `String` | 原事件消息，允许缺失 |
| `traceId` | `String` | 已有 Trace ID，允许缺失 |
| `spanId` | `String` | 已有 Span ID，允许缺失 |
| `context` | `Map<String, String>` | MDC 快照，值保持字符串；无内容时为空 Map |
| `attributes` | `Map<String, ?>` | 事件原有结构化字段，不包含其他扩展结果 |
| `exception` | `LogExceptionView` | 只读 `type`、`message` 字符串摘要；无异常时为空 |

视图提供脱敏前的只读事件快照，嵌套 Map/List 同样只读；不直接暴露原始可变容器。Map 用于应用自定义的动态键，JSON 值类型仍由框架校验。`processing` 是加工结果，不进入扩展输入。

## 字段草案

### 公共外层

| 字段 | 类型 | 含义与出现条件 |
|---|---|---|
| `timestamp` | 字符串 | 事件发生时间，带时区的 ISO 8601 格式 |
| `level` | 字符串 | 原日志级别 |
| `service` | 字符串 | 复用 `spring.application.name`，缺失时沿用默认值 `application` |
| `environment` | 字符串，可选 | 显式配置优先；未配置且只有一个 active Profile 时使用该值，多个或缺失时省略 |
| `instance` | 字符串，可选 | 由部署侧通过配置、启动参数或环境变量注入；缺失时省略，不使用应用名或自动生成 UUID 兜底 |
| `thread` | 字符串 | 产生日志事件的线程名 |
| `logger` | 字符串 | 原 Logger 名称 |
| `message` | 字符串，可选 | 已格式化的原消息；没有消息时省略，不解析内嵌 JSON |
| `traceId` | 字符串，可选 | 事件当时已有的 Trace ID；缺失时省略 |
| `spanId` | 字符串，可选 | 事件当时已有的 Span ID；缺失时省略 |
| `context` | 对象，可选 | 经排除和脱敏后的 MDC；值保持字符串 |
| `attributes` | 对象，可选 | 本次事件的结构化扩展字段 |
| `exception` | 对象，可选 | 仅存在异常时输出，内部字段见下表 |
| `processing` | 对象，可选 | 日志加工出现截断或处理失败时输出，由框架生成，扩展不能覆盖 |

`context` 只承载 MDC，扩展组件只补充 `attributes`，不跨对象合并或覆盖同名字段。`exception` 记录原事件携带的异常；扩展组件执行失败归入 `processing`。

### 异常字段

| 字段 | 类型 | 含义与出现条件 |
|---|---|---|
| `exception.type` | 字符串 | 异常类型名 |
| `exception.message` | 字符串，可选 | 脱敏后的异常消息；缺失时省略 |
| `exception.stackTrace` | 字符串 | 脱敏后的完整堆栈及原因链；换行转义，配置截断时带标记 |

### 访问日志字段

使用原 `access.log` Logger，以下字段放入 `attributes`；`message` 保留与文本模式相同的原拼接消息，已有检索与告警规则可继续使用。字段由访问日志过滤器以 SLF4J 2 键值对携带，文本 pattern 不输出键值对，文本模式不变。

| 字段 | 类型 | 含义与出现条件 |
|---|---|---|
| `method` | 字符串 | 请求方法 |
| `path` | 字符串 | 请求路径，不含查询串 |
| `statusCode` | 整数 | 终态记录时的 HTTP 状态码 |
| `durationMs` | 整数 | 请求生命周期耗时，单位毫秒 |
| `clientIp` | 字符串 | 沿用容器提供的对端 IP，不新增转发头信任 |
| `userAgent` | 字符串 | 请求 User-Agent，沿用现有清理及缺省值 |
| `outcome` | 字符串 | 现有终态：`COMPLETED`、`ERROR`、`TIMEOUT`、`REGISTRATION_ERROR`；不由状态码推导 |
| `errorType` | 字符串，可选 | 异常类型或终态原因，如 `ASYNC_TIMEOUT`；无错误时省略占位符 |
| `slow` | 布尔 | 沿用现有慢请求阈值判断 |

字段类型指未命中脱敏时的类型；扩展值允许显式 null。访问日志保留现有级别、排除路径与终态归并，同步/异步终态只记录一次，不采集请求/响应正文。

`attributes.errorType` 表示请求终态原因，不一定对应实际异常对象，不据此生成 `exception`。

### 日志处理标记

| 字段 | 类型 | 含义与出现条件 |
|---|---|---|
| `processing.truncatedFields` | 字符串数组，可选 | 被截断字段的 JSON Pointer，如 `/message`、`/exception/stackTrace` |
| `processing.errors` | 对象数组，可选 | 字段或扩展组件处理失败的记录 |
| `processing.errors[].field` | 字符串，可选 | 失败字段的 JSON Pointer，如 `/attributes/extra` |
| `processing.errors[].provider` | 字符串，可选 | 执行失败的扩展组件 Bean 名称 |
| `processing.errors[].reason` | 字符串 | 固定原因码，见下表 |

| 原因码 | 场景 |
|---|---|
| `unsupported_type` | 不支持的字段值类型，如 JavaBean |
| `invalid_value` | 非有限数字、非字符串 Map 键等不合法值 |
| `circular_reference` | Map/List 存在循环引用 |
| `protected_field` | 扩展尝试覆盖受保护字段 |
| `provider_error` | 扩展组件的 `provide()` 执行抛异常 |
| `field_error` | 其他可隔离的字段处理异常，作为兜底 |

仅记录定位信息和原因码，不回显失败字段原值或扩展异常消息。正常事件、可选字段缺失和主动排除字段均不产生处理标记；原事件级别不因此改变。

路径示例：`/context/tenant.id` 定位 MDC 中名为 `tenant.id` 的键；`/attributes/a~1b` 定位名为 `a/b` 的键。字段表中的点分写法仅用于说明嵌套成员，不作为配置路径语法。

### 输出示例

普通日志：

```json
{
  "timestamp": "2026-11-06T10:20:30.123+08:00",
  "level": "INFO",
  "service": "order-service",
  "environment": "prod",
  "instance": "order-01",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.example.OrderService",
  "message": "开始处理请求",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "context": {
    "requestId": "req-001",
    "tenantId": "tenant-01"
  }
}
```

访问日志沿用公共外层，`attributes` 示例：

```json
{
  "method": "GET",
  "path": "/orders/123",
  "statusCode": 200,
  "durationMs": 28,
  "clientIp": "192.0.2.10",
  "userAgent": "example-client",
  "outcome": "COMPLETED",
  "slow": false
}
```

## 现状对齐

2026-11-08 对照 `mimir-boot-starter-log` 现有实现校准，以下为设计输入，不改变上述范围。

- **预设与校验**：预设由 `logging.config` 指向 `classpath:com/yggdrasil/labs/log/logback/container.xml` 或 `server.xml` 选定，无独立属性。[`LogPresetEnvironmentPostProcessor`](../../../../mimir-boot-starters/mimir-boot-starter-log/src/main/java/com/yggdrasil/labs/log/config/LogPresetEnvironmentPostProcessor.java) 在 Logback 正式初始化前严格绑定并写入 `mimir.boot.log.internal.*`，XML 经 `springProperty` 读取；新增格式、元信息与长度限制沿用该路径，Map 型 `field-length-limits` 不能直接经 `springProperty` 传递，需由 EPP 规范化。
- **脱敏层级**：现有脱敏只在 `%mask`、`%maskThrowable` 转换器中处理消息与异常文本，MDC 不脱敏。JSON 下 `context`、`attributes` 的结构化脱敏是新增行为，需从 `KeyValueMasker` 抽取字段名与别名匹配入口，按键名判断；文本模式不变。
- **链路字段**：`traceId`、`spanId` 为 Micrometer 写入的 MDC 键，本模块不生成；“标准链路字段不重复输出”即这两个键从 `context` 中移除。
- **访问日志**：`AccessLogFilter` 当前仅以占位符拼接消息，无 MDC 或键值对；级别规则（5xx ERROR、4xx WARN、慢请求升 WARN）与终态归并保持不变。
- **SQL**：`SQL.JSON`、`SQL.SLOW` 的消息已是 MyBatis 侧 fastjson2 生成并预脱敏的 JSON 字符串，原样作为 `message`；当前 SQL pattern 不含链路字段，JSON 外层会补出 `traceId`、`spanId`。
- **生命周期**：模块现无关闭阶段监听，需新增容器关闭处理，并沿用 `LogMaskAutoConfiguration` 的 bootstrap 与父子上下文守卫。
- **快照挂载点**：server 预设下 ERROR 事件同时经过 `ASYNC_FILE_INFO`、`ASYNC_FILE_ERROR`，“产生线程计算一次、多出口共用”需要确定挂载方式（如扩展异步 Appender 的入队预处理或按事件缓存），作为正式设计的主要技术风险。

## 设计默认

以下为不改变对外契约的实现取向，正式设计可在同等语义下调整。

- **快照挂载**：在产生日志的线程计算一次字段快照并挂到事件上，各出口复用；具体采用前置 Appender 或扩展异步 Appender 的入队预处理，由设计比较后确定。
- **时间戳**：使用 JVM 默认时区并输出偏移，如 `+08:00`。
- **元信息范围**：`environment`、`instance` 只进入 JSON 外层，文本 pattern 不变。
- **结构化脱敏**：字段名命中时整体替换为 `replacement`，与文本模式遮蔽值的效果一致；MDC 仅在 JSON 的 `context` 中开始脱敏，属新开启模式内的行为，在 README 说明，不作为兼容性变更。

## 首期边界

主要修改 `mimir-boot-starter-log`，并在 BOM 新增 Encoder 版本管理、在本模块的 `AccessLogFilter` 中补充键值对；沿用 [L-01/L-02](../log-runtime/brainstorm.md) 的既有能力。SQL 开关、生产逻辑和 MyBatis 脱敏保持原样。

暂不做业务步骤链路、AOP、Java Agent、完整 tracing、指标 SDK、告警或可视化平台，以及访问采样和分路径慢阈值。仅输出已有追踪标识，不创建 Span。单行 JSON 承诺不覆盖 banner、直接标准输出和后端诊断。

## 验收重点

- 启动校验覆盖上述无效配置及可选项缺失，分别断言启动失败和正常启动；运行期扩展失败仍按字段隔离处理。
- 验证运行期配置刷新不改变已加载的新增 JSON 配置，重启后才采用新值；已有编程式脱敏入口单独按原契约验证。
- 实际控制台/文件输出逐条解析，覆盖应用、访问、SQL、异常及特殊字符；检查字段类型和消息内容。
- 验证服务名取值及默认值；环境显式配置优先，其次回退到唯一 active Profile，多个或缺失时省略且不阻止启动。
- 验证实例标识可由 `MIMIR_BOOT_LOG_INSTANCE` 注入；未提供时省略，不用应用名、主机信息或自动生成 UUID 填充。
- 验证事件字段与多个扩展之间的重名处理：业务字段按优先级覆盖，顶层及框架访问字段保持原值。
- 验证 `@Order` / `Ordered` 排序语义：较小数值的扩展在同名业务字段上胜出。
- 验证同级扩展按 Bean 名称升序处理，同名字段保留前者；改变注册顺序不改变结果。
- 验证扩展在事件产生线程执行且每个事件仅调用一次；多出口使用同一份字段快照，后续上下文变化不改变结果。
- 验证扩展内部再次打日志不会递归触发扩展，原事件正常处理；扩展正常结束或抛异常后，下一条普通事件恢复扩展调用，正常重入跳过不产生处理错误标记。
- 验证治理处理线程随现有 Appender 的同步/异步方式运行；事件排队后有效规则发生变化，不影响该事件各出口使用的原规则快照。
- 验证各扩展获得相同的只读原始事件，不可修改输入或读取前序扩展结果；返回字段仍按已确认的冲突规则合并。
- 验证事件视图包含上述字段，MDC 缺失时返回空 Map；顶层和嵌套 Map/List 均不可修改，原始容器后续变化不影响快照。
- 验证业务扩展仅需使用 Mimir 接口与 JDK 类型，空 Map 不产生额外字段或处理错误。
- 验证扩展只能读取异常类型与消息摘要，无法通过事件视图取得原始 `Throwable`；输出堆栈保持完整或按显式限制截断。
- 验证组件就绪前日志立即输出、无扩展调用或处理错误标记，就绪后仅后续事件包含扩展字段，早期日志不补写。
- 验证容器开始关闭后不再发起扩展调用，已生成快照和后续基础日志仍按原策略输出，正常停用不产生处理错误标记。
- 对照原配置、预设默认、显式空规则及自定义规则，验证字段名、纯值、嵌套数据与异常文本的脱敏结果。
- 验证旧消息中的键值文本仍按原正则处理，结构化字段仅以字段值作为自定义正则输入，不隐式拼接键名或改写规则。
- 验证非法值、循环引用和截断：基础事件仍可解析，标记可区分且不泄露原值。
- 验证 `processing` 仅在截断或加工失败时出现，字段路径、组件名称与原因码正确；扩展异常不替换原事件的 `exception`，不改变原级别。
- 覆盖六类处理原因码；截断仅进入 `truncatedFields`，可选字段缺失、主动排除和正常跳过扩展不进入 `errors`。
- 截断覆盖英文、中文和补充平面字符，按脱敏后的 Unicode 码点数判断，输出不含因截断产生的孤立代理项。
- 验证长度配置与处理标记使用相同的 JSON Pointer；键名包含点号、斜杠或波浪号时仍能准确定位，不混淆字面键名与嵌套层级。
- 覆盖同步、异步、超时及重复回调，核对访问终态和事件上下文；保留各出口分流，关闭 JSON 后兼容原行为。
- 未选用预设时配置 `json` 启动失败；旧默认 XML 与自定义 XML 下 `text` 行为不变。
- 未声明 Encoder 的应用在 `text` 下依赖树与启动行为不变，声明后 `json` 正常输出。
- JSON 访问日志的 `message` 与文本模式拼接消息一致。
- 自定义 XML 直接引用公开编码器时，`json.*` 配置与预设下生效结果一致；该场景不设置 `format` 也能输出 JSON。
- 事件自带键值对含 JavaBean 等非支持类型时，仅该字段标记 `unsupported_type`，输出不含其 `toString()` 内容。
- 性能基准（D-113，不设发布门槛）：同一预设下对比 `text` 与 `json`、有无扩展组件的吞吐及 P95/P99，覆盖同步控制台与 server 异步文件，结果写入版本记录。

## 后续设计

2026-11-08 已按上述共识形成 [规格](./spec.md)、[设计](./design.md) 与 [实施计划](./plan.md) 草稿；2026-11-15～16 实施与验收已完成，结果见[版本记录](../release.md)。
