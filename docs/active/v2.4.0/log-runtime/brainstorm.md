---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Log 日志预设与任务上下文方案大纲

本需求承接[版本级 Brainstorm](../brainstorm.md) 的 L-01、L-02。既有决定继续有效，本轮按“先给推荐方案并补齐每个需求的 SDD”形成下列草案；推荐不等于用户已逐项确认，也不构成产品实现、Git 或部署授权。

## SDD 导航与本轮推荐

| 文档 | 内容 |
|---|---|
| [spec](./spec.md) | 六项行为的正常、边界、失败场景与验收约束 |
| [design](./design.md) | 完整配置表、启动时序、MDC公开签名、Boot3.3.13接入和兼容取舍 |
| [plan](./plan.md) | 五项有依赖的实施任务、真实文件路径、验证与决策边界；尚未实施 |

- 推荐用 `logging.config` 选择独立 `classpath:com/yggdrasil/labs/log/logback/container.xml` / `server.xml`，由部署公共模板指定；未选择时保留原 XML。容器仅标准输出，服务器仅四类文件，SQL.SLOW 与 SQL.JSON 共用 sql.log，不开启日志生产开关。
- 新预设 root INFO，prod系列复用13个有效WARN前缀，删除新预设中的三个Spring子Logger例外；通过 `logging.level.*` 显式覆盖。旧资源不随之改写。
- 早期发布与根上下文刷新共用同一 mask 规则选择：Binder 已绑定则使用原列表，包括空列表、空字符串、索引和 relaxed 写法；未绑定且识别为新预设（大小写敏感 `classpath:` 前缀，见 design IC-1）则选四组，否则选旧空默认。直接发布有效列表，不将四组缺省规则写入 Environment；EPP 保留 Boot 默认源的优先级归位。早期入口先检查 Logback 类和 Boot 后端选择；不兼容后端未选新预设时退出，显式选择时明确失败。新预设非法规则启动失败，旧规则错误处理保留；（2026-10-31 审查修订）所有者失败或根关闭时清空所有权槽、移除服务器数值规范化源并清除启动期规则缓存，保留最后规则快照以覆盖失败堆栈与关闭日志，由下一所有者发布时直接替换（取得令牌时不置空，避免空快照窗口）；2026-10-31 用户确认 D-100：开启 Spring Cloud legacy bootstrap 时，以属性源名 `bootstrap` 识别 bootstrap 应用，其不取得所有权、不发布，由主应用按主 Environment 发布，保持旧 mask 配置兼容并使预设缺省生效；细节以 design IC-4 为准。
- 服务器推荐单文件100MB、普通/访问/SQL各30天10GB、错误60天5GB；四个队列各1024，discardingThreshold=0、neverBlock=false、每appender排空等待1s。默认保留日志但允许业务等待；显式非阻塞可能丢弃包括ERROR的事件。完整范围见design。
- MDC推荐提交时全Map快照、finally恢复。默认执行器通过单个旧 TaskExecutorCustomizer 适配新/旧Builder，保留Boot的getIfUnique业务候选选择，包括唯一Primary；不注册普通Mimir TaskDecorator Bean，不自动组合全部业务Bean。
- 存在任何其他新/旧执行器customizer时自动退让，保留业务原有装饰器与参数；下游通过已有compose显式组合，无需调整order或新增开关。自定义Executor/Builder同样显式接入，不分析或反射接管业务装饰策略。

以下保留原方案大纲的现状事实与候选比较；候选段中的“尚未选定/需讨论”描述的是收敛前状态，当前建议以以上三份draft SDD为准。尚无Java测试、性能基准或运行环境验收结果。

## 目标与范围

- L-01：提供部署侧可选的容器、服务器日志预设，减少复制 XML 的需要，并保留未选择预设时的既有行为。
- L-02：优先覆盖 Boot 默认业务执行器的 MDC 传播，为自建执行器提供统一接入入口；提交时复制全部 MDC，执行结束或异常时恢复执行线程原上下文。
- 主要归属 `mimir-boot-starter-log`；与 MyBatis 慢 SQL 仅约定 Logger 和输出路由，不引入 Starter 横向运行时依赖。
- 不扩展访问日志采样、分路径慢阈值、完整 JSON 输出、链路 Span 生命周期或任意线程的自动传播。

## 已确认方向

| 来源 | 约束 |
|---|---|
| D-28、D-29 | 容器和服务器两类预设由部署公共模板选择；未选择时保留原输出 |
| D-30、D-31、D-44 | 复用文本格式；新预设生产 root 为 INFO，复用 13 条第三方降噪规则 |
| D-32 | 新预设未显式配置时启用 `password`、`token`、`secret`、`api_key` 四组规则，可覆盖或关闭 |
| D-33、D-34 | 默认执行器优先，自建执行器有统一入口；传播全部 MDC 快照并恢复旧上下文 |
| RV-08 | 不以新增普通 `TaskDecorator` Bean 的方式默认接入，避免破坏 Boot 的唯一候选选择 |

上述是版本级方向；预设选择机制、属性表、队列满策略和自动接入细节已在本轮 SDD 给出推荐，仍保留草案状态。

## 现状与依据

本轮只做本地源码和依赖源码包核对，未启动应用、运行 Java 测试或性能测量。

- [现有 XML](../../../../mimir-boot-starters/mimir-boot-starter-log/src/main/resources/logback-spring.xml) 已随 Starter 提供。生产 root 为 WARN；`com.yggdrasil.labs`、`access.log`、`SQL.JSON` 走文件，普通业务包的 INFO 可能被过滤。
- 文件滚动大小均为 100MB；普通、访问、SQL 保留 30 天、总量各 10GB，错误保留 60 天、总量 5GB。异步队列分别为 512、1024、512、256，不能把这些现状值直接当作新预设已批准默认值。
- 所有异步 appender 使用 `discardingThreshold=0`，未设置 `neverBlock`。Logback 1.5.18 `AsyncAppenderBase` 默认 `neverBlock=false`，满队列使用阻塞写入；“异步”不代表业务线程不会等待。
- [脱敏自动配置](../../../../mimir-boot-starters/mimir-boot-starter-log/src/main/java/com/yggdrasil/labs/log/config/LogMaskAutoConfiguration.java) 在 `ContextRefreshedEvent` 才发布配置；[转换器](../../../../mimir-boot-starters/mimir-boot-starter-log/src/main/java/com/yggdrasil/labs/log/converter/SensitiveDataConverter.java) 使用静态不可变快照与 Logback 上下文引用，启动和重复建上下文的行为需要单独核对。
- Boot 3.3.13 `LoggingApplicationListener` 在环境准备阶段初始化日志并读取 `logging.config`，早于上下文刷新。仅给普通配置 Bean 加默认值不足以覆盖启动阶段。
- Boot 3.3.13 `TaskExecutorConfigurations` 通过 `ObjectProvider<TaskDecorator>.getIfUnique()` 选择装饰器；多个无唯一候选的 Bean 会得到空结果。
- `ThreadPoolTaskExecutorBuilder.configure` 先设置 decorator、后运行 customizers；自动配置还兼容旧 `TaskExecutorCustomizer`。后续 customizer 可能覆盖之前的组合，不能只讨论 Bean 数量。
- Spring 6.1.21 的 `ContextPropagatingTaskDecorator` 使用 Micrometer `ContextSnapshotFactory.captureAll().wrap(...)`；能否满足全部 MDC 字段传播，还取决于依赖和 accessor 注册。
- [现有基准](../../../../mimir-boot-starters/mimir-boot-starter-log/src/test/java/com/yggdrasil/labs/log/converter/SensitiveDataConverterBenchmark.java) 只测固定约 1KiB 消息、三组规则、平均增量；没有证明四组规则的吞吐、尾延迟或真实输出链路成本。

依赖依据为本地 `spring-boot(-autoconfigure)-3.3.13-sources.jar`、`spring-core-6.1.21-sources.jar`、`logback-core-1.5.18-sources.jar`；版本与 Boot 3.3.13 BOM 对齐。这是接口与执行顺序的静态证据，尚非运行验证。

## L-01 候选方案

### 预设选择

| 方案 | 优势 | 代价与限制 |
|---|---|---|
| A：部署通过 `logging.config` 选择独立 XML（优先讨论） | 复用 Boot 现有早期入口；旧 XML 保留；自定义配置选择明确 | 两个资源需要共享格式与规则以防漂移；部署需指定资源位置 |
| B：新增 Mimir 预设属性，由早期扩展选择 XML | 部署只填简短枚举，便于统一模板 | 新增初始化时序和选择优先级；必须避让显式 `logging.config` 与应用自带配置 |

建议先讨论 A，避免为两种预设引入第二套选择协议。资源名尚未确定；应用自定义 `logging.config` 保持由应用控制，不承诺任意自定义 XML 自动采用全部预设能力。

### 输出路由与属性入口

- 容器候选：应用、访问、已启用的 SQL 日志进入标准输出；同一事件不因 logger 继承和显式 appender 重复打印；是否彻底取消文件 appender 由预设语义一并确认。
- 服务器候选：沿用普通、错误、访问、SQL 分流，补齐滚动大小、保留天数、总量和队列参数；控制台保留范围需明确。
- MyBatis 新增独立 WARN Logger（候选名 `SQL.SLOW`）必须容器可见；服务器需选择合并到 `sql.log` 或单独慢 SQL 文件，并说明是否同时进入普通文件。
- `SQL.JSON` 的生成开关、消息结构和缺省启用 profile 不变；容器预设可改变承载输出的位置，不应把选择日志预设解释成启用 SQL 记录。

| 属性主题 | 候选组织方式 | 需明确的边界 |
|---|---|---|
| 预设选择 | 复用 `logging.config`，或另设预设属性 | 未配置、自定义配置、无效值的行为 |
| 文件滚动 | 公共默认值加必要的分流覆盖 | 单文件上限与每类总量上限不能混同 |
| 队列 | 公共参数加各 appender 的必要覆盖 | 容量、丢弃阈值、是否阻塞、停机排空时间 |
| 脱敏 | 尽量沿用 `mimir.boot.log.mask.*` | 未配置、显式空列表、关闭与自定义规则如何组合 |
| 日志级别 | 保留 `logging.level.*` | 业务覆盖、预设值及宽前缀降噪的优先级 |

属性清单最终需要逐项给出名称、类型、范围、默认值、适用预设与覆盖优先级；当前不新增具体参数默认值。

### 早期脱敏与安全边界

- 候选 A：新 XML 在解析时读取并设置脱敏配置；需解决列表绑定、显式空值、静态快照初始化和刷新后覆盖问题。
- 候选 B：在 ConfigData 可用、日志正式初始化之前绑定并发布规则；需要严格限定对旧 XML 和自定义 XML 的影响，验证实际事件顺序。
- 两条路径都必须使四组规则在上下文刷新前生效，刷新后仍保留显式覆盖；不能在启动阶段启用、刷新后又被空默认列表清除。
- 保留现有文本字段脱敏边界，不能声称对象、数组、所有凭据编码形式或原始日志文件均被完整保护；消息和异常链均需覆盖。
- `ApplicationEnvironmentPreparedEvent` 之前以及不使用 Mimir 转换器的日志范围需说明，不应承诺覆盖 JVM 启动后的所有输出。

### 第三方降噪与满队列取舍

新预设复用的 13 条 WARN 前缀：`org.springframework`、`org.apache`、`com.alibaba`、`com.baomidou`、`org.mybatis`、`com.zaxxer`、`com.mysql`、`org.postgresql`、`org.hibernate`、`io.netty`、`io.lettuce`、`redis.clients`、`org.reflections`。去掉重复的 `io.lettuce.core`，不拆宽前缀，说明用 `logging.level` 恢复。

现有 XML 末尾还显式将三个 Spring 子 logger 设为 INFO；新预设是否沿用这些例外需要说明，不能仅复制父前缀后宣称全部 Spring 日志均降至 WARN。旧预设保持原行为。

满队列可选阻塞以优先保留日志，或不阻塞以优先保护业务延迟；后者可能连 WARN/ERROR 也丢失。队列大小不能解决长期输出阻塞，需把丢弃范围、停机丢失与故障可观测性放在同一决策中，尚不选定默认策略。

## L-02 候选方案

| 方案 | 优势 | 需解决的问题 |
|---|---|---|
| A：专用 MDC 快照装饰器，加 Boot 执行器扩展入口（优先讨论） | 行为聚焦全部 MDC；可为自建执行器复用；避免额外上下文系统 | 与唯一业务 decorator 的组合顺序、customizer 覆盖、自动装配退出边界 |
| B：复用 `ContextPropagatingTaskDecorator` | 可与已使用 Micrometer 的上下文体系协同 | 依赖与 MDC accessor 配置、空上下文清理、短任务开销；不能默认扩大到 Span 传播 |

- 无论选择哪条路径，都不额外默认注册普通 `TaskDecorator` Bean；组合入口不能使原有业务 decorator 失效。
- 自动接入优先研究 `ThreadPoolTaskExecutorCustomizer`，但要明确如何兼容业务通过 customizer 直接设置 decorator；无法安全合并时需明确退出及显式组合入口，不用反射读取私有字段兜底。
- 空 MDC 应清除执行期间的旧字段；正常、异常、嵌套、同线程执行后恢复原上下文；捕获点是提交时，不是执行时。
- 自建执行器拟提供显式任务包装或 decorator 组合入口；具体公开签名、组合顺序、重复包装处理与开关默认值尚未确定。
- JDK 17 平台线程是当前基线；Boot 虚拟线程执行器、旧 builder、自定义 executor、`@Async` 替换路径需明确本轮覆盖范围，不能以一个默认 Bean 验证推及所有路径。
- 校准 [MdcUtil](../../../../mimir-boot-starters/mimir-boot-starter-log/src/main/java/com/yggdrasil/labs/log/util/MdcUtil.java) 的“自动传递到子线程”注释；本轮不承诺 `new Thread` 或通用线程池自动接入。

## 依赖与验收方向

- L-01 先收敛预设入口和配置优先级，再讨论早期脱敏；L-02 可独立探索，但与已有 decorator 的组合证据是确定自动接入方案的前提。
- MyBatis 对齐慢 SQL Logger 名称、WARN 等级和路由；慢 SQL 文本本身的脱敏责任不能依赖 Log Starter 一定存在。
- 启动验证覆盖未选择预设、两类预设、自定义 `logging.config`，并观察环境准备、Bean 初始化、上下文刷新之后的消息与异常输出。
- 路由验证至少包括普通业务 INFO、访问 INFO、`SQL.JSON`、慢 SQL WARN、ERROR；检查标准输出、目标文件和重复事件。
- 性能测量对照关闭规则与四组规则，涵盖命中/不命中、长消息、异常栈，报告吞吐及 P95/P99；区分纯转换、控制台、文件及满队列路径，阈值在基线采集后再讨论。
- MDC 验证覆盖已有单个/多个装饰器、业务 customizer、提交后修改、空快照、异常恢复、线程复用与自建执行器；确认业务装饰器仍执行且 MDC 不串任务。
- 原大纲列出的预设选择、满队列默认策略、早期脱敏路径和 MDC 自动接入边界现已在 SDD 收敛为推荐草案；仍无运行验收结论，产品实施与验证按 plan 另行执行。
