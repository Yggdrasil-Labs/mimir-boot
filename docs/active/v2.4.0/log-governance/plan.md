---
version: v2.4.0
status: draft
created: 2026-11-08
updated: 2026-11-08
---

# Log JSON 日志治理实施计划

## Goal

实施 L-04 可选 JSON 日志治理，满足 [spec](./spec.md) 的八项行为与 [design](./design.md) 的 IC-1 至 IC-8。未启用 JSON 时输出、依赖和启动行为与实施前一致。本文件是静态实施输入，不构成 Git、部署或发布授权。

## Context

- 工作目录为仓库根；产品范围为 `mimir-boot-starters/mimir-boot-starter-log/` 与 `mimir-boot-bom/pom.xml`。下文 `.../log/` 指 `mimir-boot-starters/mimir-boot-starter-log/src/main/java/com/yggdrasil/labs/log/`，`.../logback/` 指该模块 `src/main/resources/com/yggdrasil/labs/log/logback/`；未写前缀的测试类位于 `src/test/java` 对应包。基线 Java 17 / Boot 3.3.13 / Logback 1.5.18 / Jackson 2.17.3，不升级。
- 预设由 `logging.config` 选择；EPP 在 Logback 初始化前写入 `mimirLogPresetResolved`，`LogMaskAutoConfiguration` 依赖该属性源识别预设父上下文——不得改变其写入条件。
- 现有脱敏仅在 `%mask` / `%maskThrowable` 中生效，规则为 `SensitiveDataConverter` 的静态快照；编程式入口（`addCustomPattern` 等）语义保持。
- Logback 1.5.18 的 `<if>` 需 Janino，不可用作格式切换；`LoggingEvent.addKeyValuePair` 可用于挂载快照。
- `logstash-logback-encoder 8.1` 唯一非可选传递依赖为 `jackson-databind`；Provider 抛 `IOException` 会丢弃整条事件。
- 文档经四轮独立审查，第四轮为 CONDITIONAL（无 P0，1 个 P1）；第四轮问题已修复但未再经独立复核，用户于 2026-11-08 接受该剩余风险并交付。实施中发现与本计划冲突的细节按 Decision Boundary 处理。
- 模块已有 `LogArchitectureTest` 包规则、隔离 JVM 测试机制（`LogIsolatedJvmIntegrationTest` / `IsolatedScenarioRunner`）与手工基准模式（`SensitiveDataConverterBenchmark`），新增代码与测试沿用。

## Key Decisions

1. （D-111）显式启用 JSON；普通日志统一外层，访问日志结构化，SQL 消息保持字符串；MDC 全量加排除名单；扩展经 `LogFieldProvider` 补充 `attributes`；保留原级别、路由、滚动与异步策略。
2. （D-112）`format=json` 仅对 container、server 预设生效，非预设下配置即启动失败；Encoder 为 optional 依赖，版本由 BOM 管理；JSON 访问日志保留原拼接消息。
3. （D-113）公开 `MimirJsonEncoder`、`LogFieldCaptureAppender` 与 `json-support.xml` 供自定义 XML；`json.*` 配置不论是否预设均生效；事件键值对与扩展值同一类型规则，不调用 `toString()`；性能只记录基准。
4. 格式切换用 `FormatDelegatingEncoder` 委派，text 分支即原 `PatternLayoutEncoder`；JSON 相关类只在 json 分支加载。
5. 字段快照由每个 Logger 第一个出口 `LogFieldCaptureAppender` 在产生线程构建并以保留键 `mimir.log.fields` 挂载；未声明捕获出口时编码器构建不含扩展的基础快照。
6. `json.*` 内部键写入独立属性源 `mimirLogJsonResolved`；`format`、`capture-active` 写入原预设属性源。
7. 处理顺序：捕获阶段（复制 MDC、规范化键值对、调用扩展、固定脱敏规则引用）→ 输出阶段（MDC 排除 → 脱敏 → 截断 → 编码）。

## Behavior Contract

- `format` 未设置 / `text` → 两预设与旧 XML 输出逐字节不变，依赖树不变。
- 预设 + `format=json` + 已声明 Encoder → 每事件一行 JSON，字段与类型符合 spec；ERROR 在 server 预设下进 info/error 两文件各一次。
- `format=json` 且非预设或非 Logback 后端、值非法、`field-length-limits` 非法 → 启动失败，诊断含属性名不含值；缺 Encoder → 启动失败，诊断给出依赖坐标。
- 访问日志 JSON：`attributes` 九字段类型固定，`errorType` 仅在非 null 且非占位符 `-` 时出现（成功终态代码传入 `-`），`message` 与文本一致。
- 扩展：刷新后、关闭前生效；产生线程每事件最多一次；按 order 再按 Bean 名排序；受保护字段、异常、非法值隔离并记录 `processing.errors`，不回显原值。
- 截断：脱敏后按码点截断，路径为 JSON Pointer，记录 `processing.truncatedFields`。
- 自定义 XML 按公开约定接入得到相同字段；未声明捕获出口则无扩展字段。

## Task Dependencies

| Task | Depends on | Ownership |
|------|------------|-----------|
| T1: 依赖与配置校验 | none | `mimir-boot-bom/pom.xml`、log starter `pom.xml`、`config/` 下 EPP 与新增属性类、`json/JsonPointer`、`json/LogJsonSettingsCodec`、`json/LogJsonKeys`、`json/LogJsonSettings` |
| T2: 结构化脱敏入口 | none | `converter/` 包 |
| T3: 字段快照与扩展 SPI | T1（`JsonPointer`、`LogJsonKeys`、`LogJsonSettings`）、T2（`MaskRules`） | 新包 `field/`、`json/` 中快照、规范化、注册表、捕获出口；`config/LogFieldAutoConfiguration` |
| T4: 访问日志键值对 | T1（`LogJsonKeys.ACCESS_FIELDS` 字段名） | `web/AccessLogFilter.java` |
| T5: JSON 编码器与预设接入 | T1、T2、T3 | `json/` 中编码器与 Provider、`logback/*.xml` |
| T6: 集成验收、基准与文档 | T1～T5 | 集成测试、基准类、README、版本文档 |

T1、T2 可并行；T3 在 T1、T2 之后；T4 在 T1 之后，可与 T2、T3 并行。T1、T3、T5 共用 `json/` 包，按文件划分 ownership：`JsonPointer`、`LogJsonSettingsCodec`、`LogJsonKeys`、`LogJsonSettings` 归 T1，快照相关类归 T3，编码器相关类归 T5。规则 Context 绑定由 T2 实现、T5 调用。

## Tasks

### T1：依赖与配置校验

Purpose: 声明 optional Encoder 依赖，并在 EPP 中绑定、校验和发布 JSON 配置（IC-1）。

Files:

- Modify: `mimir-boot-bom/pom.xml` — 新增 `logstash-logback-encoder.version=8.1` 属性与 dependencyManagement 条目
- Modify: `mimir-boot-starters/mimir-boot-starter-log/pom.xml` — 声明该依赖 `<optional>true</optional>`；测试需要时不额外声明 Jackson（由 Encoder 传递）
- Create: `.../log/config/LogJsonProperties.java`、`.../log/config/LogFormat.java` — 前缀 `mimir.boot.log`，字段见 design IC-1；标注 `@ConfigurationProperties` 以生成元数据，不注册为 Bean（同 `LogPresetProperties`）
- Create: `.../log/json/LogJsonSettings.java` — public，标注内部实现；从 Logback Context 属性构建不可变设置（`service` 原值，其余经 codec 解码），供 T3 捕获出口与 T5 编码器共用，text 路径也会加载
- Create: `.../log/json/LogJsonSettingsCodec.java`、`.../log/json/LogJsonKeys.java` 及 `LogJsonSettingsCodecTest` — 百分号编码往返；内部键、Context 属性名、保留键、`TOP_LEVEL_FIELDS`、`ACCESS_FIELDS` 常量
- Modify: `.../log/config/LogPresetEnvironmentPostProcessor.java` — 按 design IC-1 伪代码扩展；移除两个内部源；全部校验先于写入
- Modify: `.../log/config/LogPresetSupport.java` — 仅新增属性源名称常量 `mimirLogJsonResolved`；JSON 内部键名归 `LogJsonKeys`
- Create: `.../log/json/JsonPointer.java` 及 `JsonPointerTest` — RFC 6901 解析与转义，EPP 校验与输出路径共用
- Modify: `src/test/java/com/yggdrasil/labs/log/config/LogPresetEnvironmentPostProcessorTest.java` — 补充用例

Behavior and constraints:

- 失败消息只含属性名与原因，不回显值（沿用 `bindStrict`）。
- `environment` 回退只取 `getActiveProfiles()` 恰为 1 个的情况，不读默认 Profile。
- `JsonPointer`、`LogJsonSettingsCodec`、`LogJsonKeys`、`LogJsonSettings` 为 public 并标注内部实现，不得引用 logstash 或 Jackson 类型。
- 新属性逐项 `bindStrict`，诊断为具体属性名；非 Logback 后端下 `format=json` 同样启动失败。
- 内部键值全部经 `LogJsonSettingsCodec` 编码，不复用 `MaskListCodec`。
- `LogPresetProperties` 的严格绑定与内部键保持不变；`mimirLogPresetResolved` 写入条件不变。

Acceptance:

- 单元测试覆盖 spec“选择输出格式”的非法值、非预设 json、缺类（隔离类加载器或隔离 JVM）及“字段截断与处理标记”的非法长度配置，断言失败消息含属性名、不含原值。
- 合法配置下两个内部属性源键值与 design 表一致；未选预设时只可能新增 `mimirLogJsonResolved`（含唯一 active Profile 回退产生的 `environment`），不改动其他属性源；选中预设时预设属性源仅新增 `internal.format`（JSON 时另有 `capture-active`）。
- 含 `,`、`=`、`${`、`(`、中文的 MDC 键与指针经编解码往返不变。
- starter POM 中 Encoder 为 `<optional>true</optional>`；“未声明 Encoder 时依赖与行为不变”由 T6 隔离 JVM 场景验证，本需求不修改 `tools/` 下的发布消费者检查。

### T2：结构化脱敏入口

Purpose: 在不改变 `%mask` 语义的前提下提供结构化字段脱敏（IC-4）。

Files:

- Create: `.../log/converter/StructuredMaskSupport.java` — public，标注内部实现；`MaskRules` 接口与 `bindContext(Context)`
- Modify: `.../log/converter/SensitiveDataConverter.java` — 包内暴露当前快照；把 `start()` 中的 Context 绑定与作废逻辑抽为包内静态方法，`start()` 与 `bindContext` 共用；不改发布/作废/编程式入口语义
- Modify: `.../log/converter/KeyValueMasker.java` — 新增键名后缀匹配方法，复用 `FieldNameIndex`
- Create: `.../log/converter/StructuredMaskSupportTest.java`

Behavior and constraints:

- `isSensitiveKey` 与文本扫描一致：键以任一启用别名结尾，忽略大小写。
- `maskValue` 只用值正则（预置纯值 + 自定义 + 编程式），替换逻辑与 `maskValue` 私有方法一致。
- 现有 `SensitiveDataConverterTest`、`SensitiveDataConverterEquivalenceTest` 不修改且通过。

Acceptance:

- 单元覆盖 spec“结构化脱敏”中字段名命中、值正则（含数字转字符串）、规则显式为空、旧正则不改写四个场景；“消息与异常沿用文本规则”由 T5 端到端覆盖。
- 未经 `%mask` 转换器、仅调用 `bindContext` 时：快照读到 Context 中的规则；`addCustomPattern` 后重建的快照仍含 Context 规则。
- 编程式入口新增正则后 `currentRules()` 返回新一代规则。

### T3：字段快照与扩展 SPI

Purpose: 实现事件视图、值规范化、扩展注册与产生线程捕获（IC-5、IC-6 的捕获部分）。

Files:

- Create: `.../log/field/LogFieldProvider.java`、`LogEventView.java`、`LogExceptionView.java` — 公开 SPI，仅 JDK 类型，Javadoc 说明轻量、不阻塞
- Create: `.../log/json/LogFieldCaptureAppender.java` — public，继承 `UnsynchronizedAppenderBase`；`active` 属性缺省 true；事件已含保留键时跳过
- Create: `.../log/json/FieldSnapshotKeyValuePair.java` — 继承 SLF4J `KeyValuePair`，`value` 恒为 `[mimir-fields]`，快照存于包内字段
- Create: `.../log/json/FieldSnapshot.java`、`ValueNormalizer.java`、`ProcessingError.java` — 包内实现；`LogFieldProviderRegistry.java` — public，标注内部实现（均复用 T1 的 `JsonPointer`）
- Create: `.../log/config/LogFieldAutoConfiguration.java`；Modify: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: 对应单元测试（`ValueNormalizerTest`、`LogFieldCaptureAppenderTest`、`LogFieldAutoConfigurationTest`）

Behavior and constraints:

- 本任务的类不得引用 `net.logstash` 或 Jackson 类型（text 模式与未声明 Encoder 时会加载）。
- 合并、受保护字段、重入标志按 design IC-5 伪代码；排序语义同 `ObjectProvider.orderedStream()`，同序按 Bean 名升序。
- 规范化规则按 design IC-6 表；深度上限 32；不调用未知对象 `toString()`。
- 注册仅限主应用上下文（`getParent() == null`，或父上下文 `getId()` 为 `"bootstrap"`）且自身非 legacy bootstrap 环境；不得用父 Environment 的 `bootstrap` 属性源识别（主上下文刷新时已被移除）；只处理本上下文的刷新事件；只有 owner 可清空。`LogFieldProviderRegistry` 为 public 并标注内部实现。
- 保留键 `mimir.log.fields` 经 `FieldSnapshotKeyValuePair` 挂载，其 `value` 恒为字符串 `[mimir-fields]`；快照不得经公开字段或 getter 暴露。

Acceptance:

- 覆盖 spec“字段扩展组件”与“事件自带键值对”全部场景的单元/切片测试，包括调用线程、单事件单次、调换注册顺序结果不变、重入后恢复、刷新前/关闭后不调用、视图不可变、返回 null/空 Map/null 键/遍历抛异常的隔离。
- 普通父子上下文：子上下文 Provider 不生效，子上下文关闭不清空父注册（`LogFieldAutoConfigurationTest`）。
- legacy bootstrap 场景（主应用父上下文为 bootstrap）扩展正常注册，由 T6 隔离 JVM 场景验证。
- 使用 `%kvp` 或 Logback `JsonEncoder` 的出口对保留键只输出字符串 `[mimir-fields]`。
- `LogArchitectureTest` 通过；如需为新包补规则，只追加不放宽。

### T4：访问日志键值对

Purpose: 访问日志携带结构化字段，文本输出不变（IC-3）。

Files:

- Modify: `.../log/web/AccessLogFilter.java` — `logAccessByStatus` 改用流式 API 并追加九个键值对
- Modify: `src/test/java/com/yggdrasil/labs/log/web/AccessLogFilterTest.java`、同目录 `AccessLogFilterServletIntegrationTest.java` — 补充断言

Behavior and constraints:

- 消息模板、参数顺序、级别判定、`[慢接口]`、排除路径与终态归并不变；`errorType` 为 null 或占位符 `-` 时不添加；键名取自 `LogJsonKeys`。
- `statusCode` 为 Integer、`durationMs` 为 Long、`slow` 为 Boolean。

Acceptance:

- 现有访问日志测试不改期望即通过（文本不变）。
- 新断言：同步完成、异常、异步超时、重复回调各场景事件键值对的键、值与类型正确，且每请求一条；`COMPLETED` 终态不含 `errorType` 键。

### T5：JSON 编码器与预设接入

Purpose: 实现 JSON 输出与格式委派，接入两个预设与公开片段（IC-1 XML、IC-2、IC-6 输出部分、IC-7、IC-8）。

Files:

- Create: `.../log/json/MimirJsonEncoder.java` — public；未显式配置 Provider 时装配固定列表
- Create: `.../log/json/GovernedFieldsJsonProvider.java`、`JsonEncoderFactory.java` — 包内实现（复用 T1 的 `LogJsonSettings`）
- Create: `.../log/json/FormatDelegatingEncoder.java` — public，标注内部实现
- Create: `src/main/resources/com/yggdrasil/labs/log/logback/json-support.xml` — 公开片段，仅 `springProperty`
- Modify: `.../logback/common.xml` — include `json-support.xml`；format 与 capture-active 属性；定义 `MIMIR_LOG_FIELDS`
- Modify: `.../logback/container.xml`、`server.xml` — encoder 改为委派类，保留 pattern/charset；各 Logger 首个出口为 `MIMIR_LOG_FIELDS`
- Modify: `src/test/java/com/yggdrasil/labs/log/config/LogPresetResourceTest.java` — 资源结构断言；现有出口列表断言（如 `containsExactly("CONSOLE")`）须改为首项 `MIMIR_LOG_FIELDS` 加原出口，属预期内的期望变更；`every_spring_property_declares_default_matching_ic3` 等收集 `mimir.boot.log.internal.*` 的断言须把 `internal.format`、`internal.json.capture-active` 纳入 XML defaultValue 期望；`INTERNAL_DEFAULTS` 同时用于断言 `LogPresetProperties.toInternalProperties()`，须拆分为两份期望；解析清单加入 `json-support.xml` 后再纳入其键
- Create: `GovernedFieldsJsonProviderTest`、`FormatDelegatingEncoderTest`

Behavior and constraints:

- text 分支不得加载 `net.logstash`/Jackson 类：`JsonEncoderFactory` 隔离，工厂方法与委派字段声明类型为 `Encoder<ILoggingEvent>`；text 输出与原 `PatternLayoutEncoder` 逐字节一致。
- `MimirJsonEncoder.start()` 调用 T2 的 `StructuredMaskSupport.bindContext(getContext())`。
- 输出阶段：快照缺失时就地构建基础快照（不调用扩展）；MDC 排除 → 脱敏（T2）→ 截断 → 写出；单字段处理异常记 `field_error` 并移除该字段；Provider 不向外抛 `IOException` 以外的异常。
- `traceId`/`spanId` 从 `context` 移除；事件键值对读取跳过保留键。
- `LogJsonSettings` 只在 `start()` 读取一次。
- 不修改旧 `logback-spring.xml`。

Acceptance:

- 单元覆盖 spec“JSON 公共外层”“字段截断与处理标记”场景，包括补充平面字符截断、路径转义、无加工时无 `processing`。
- 委派 encoder 在 text/json 两分支的 header、encode、footer 与直接使用目标 encoder 一致。

### T6：集成验收、基准与文档

Purpose: 端到端验证、性能基准与接入说明。

Files:

- Create: `src/test/java/com/yggdrasil/labs/log/config/LogJsonOutputIntegrationTest.java` — 两预设 JSON 输出逐行解析；server ERROR 双文件；访问、SQL Logger、异常、特殊字符
- Modify: `src/test/java/com/yggdrasil/labs/log/config/LogPresetOutputIntegrationTest.java` — 原有断言不改期望；text 逐字节兼容由 T5 的同事件双编码器比较负责
- Modify: `src/test/java/com/yggdrasil/labs/log/config/LogIsolatedJvmIntegrationTest.java` — 保持单一 Failsafe 入口；新增剔除 logstash 与 Jackson 的 classpath 构造方法，`runScenarios(LegacyBootstrapScenarios.class, …)` 的场景计数随新增场景同步调整
- Modify: `IsolatedScenarioRunner` 相关场景类或新增场景（classpath 剔除 logstash 与 Jackson；`LegacyBootstrapScenarios.App` 追加导入 `LogFieldAutoConfiguration`）— 两预设 text 正常启动输出且无缺类错误；`format=json` 启动失败且无日志文件；自定义 XML 缺 Encoder 启动失败；`LegacyBootstrapScenarios` 新增 JSON 预设 + 扩展组件场景，断言主应用注册、bootstrap 不注册
- Modify: `src/test/java/com/yggdrasil/labs/log/architecture/LogArchitectureTest.java` — 追加规则：design 列出的 text 路径类（含 `LogJsonKeys`）与 `field` 包不依赖 `net.logstash..`、`com.fasterxml..`
- Create: `src/test/resources/` 下自定义 XML 两份（声明/未声明捕获出口）
- Create: `.../log/json/LogJsonEncoderBenchmark.java` — 非 `*Test` 命名，手工 `-Dtest=` 运行
- Modify: `mimir-boot-starters/mimir-boot-starter-log/README.md` — 启用方式、依赖声明、配置项、字段说明、自定义 XML 公开约定、扩展 SPI（仅主应用上下文生效）、MDC 在 JSON 下开始脱敏的说明；升级说明列出访问日志新增键值对对 `%kvp`/logstash/Logback JSON 编码器自定义配置可见，以及保留键 `mimir.log.fields`
- Modify: `docs/active/v2.4.0/release.md` — L-04 行改为 SDD 链接与实施状态；记录基准结果

Behavior and constraints:

- 集成测试不依赖外部网络或真实采集平台；隔离场景沿用现有子 JVM 机制。
- 基准不设断言门槛，仅输出数值。

Acceptance:

- spec 中每个 Scenario 至少由一个单元或集成测试覆盖；配置刷新、编程式入口（含预设缺省规则仍生效）、排队后规则变化、刷新前事件命中缺省规则有集成断言。
- `bash scripts/engineering.sh quality --mode full --source worktree` 通过。
- 基准结果（text/json × 0/3 扩展 × 同步控制台/异步文件，吞吐与 P95/P99）写入版本记录。

## Integration Acceptance

- 默认（未设置 format、未声明 Encoder）消费者：两预设与旧 XML 输出、文件集合、依赖树与实施前一致；自定义 XML 的键值对可见性变化仅按升级说明处理。
- 预设 + JSON 消费者：应用、访问、SQL、异常事件逐行可解析，字段与类型符合 spec；扩展字段、处理标记、截断与脱敏同时出现时互不干扰。
- 自定义 XML 消费者按 README 示例接入得到相同字段。

## Decision Boundary

- 实现者可自行决定：包内类拆分与命名（公开类名除外）、Provider 内部组织、规范化与截断的数据结构、测试组织方式。
- 需重新对齐：改变公开类名/包名（`LogFieldProvider`、`LogEventView`、`LogExceptionView`、`MimirJsonEncoder`、`LogFieldCaptureAppender`、`json-support.xml`）、配置项名称与默认值、字段名与类型、处理原因码、text 模式任何输出差异、修改旧 `logback-spring.xml`、新增非 optional 依赖、引入 Janino 或 JMH。
- 若 `FormatDelegatingEncoder` 无法做到 text 逐字节一致，暂停并报告差异，不改为其他切换机制。
