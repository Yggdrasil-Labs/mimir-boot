---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# Log 日志预设与任务上下文实施计划

## Goal

实施 L-01 两类可选日志预设与 L-02 默认业务任务 MDC 传播，满足 [spec](./spec.md) 的六项行为和 [design](./design.md) 的 IC-1 至 IC-6。本文件是静态实施输入；**本轮只补 SDD，以下产品改动和运行验收均未实施**。除 2026-10-31 用户确认的 D-100（Spring Cloud legacy bootstrap 所有权）外，具体方案是自主推荐草案，未将新参数写成用户已确认决定，也不构成 Git、部署或发布授权。

## Context

- 工作目录为仓库根；主要产品范围为 `mimir-boot-starters/mimir-boot-starter-log/`。Java 17 / Boot 3.3.13 / Spring 6.1.21 / Logback 1.5.18，不升级依赖基线。
- 旧 `logback-spring.xml` 已自动生效，prod root WARN，访问与 SQL 走文件；不得借本需求改旧默认输出或给旧配置自动加四组脱敏。
- 原脱敏在 ContextRefreshedEvent 发布，原转换器有静态快照和首 Context 引用。启动期保护必须补时序，不能只改配置 Bean 默认值。
- Mimir 令牌不控制 Boot 的 LoggingApplicationListener；独立子 SpringApplication 失败及再次启动可能清理、重置共享日志系统，活跃子失败事件还可能传播给父监听器。父子保障限定为不另启 Boot 日志生命周期的普通子上下文。
- Boot 的默认执行器只有没有用户 Executor 时才存在；Builder 通过 getIfUnique 选 TaskDecorator，并在设置 decorator 后执行新/旧 customizer。因此“新增普通 TaskDecorator Bean”与“仅追加 customizer”都不满足完整兼容。
- MyBatis 的独立慢 SQL Logger 约定为 `SQL.SLOW` / WARN；语句安全处理和生成开关由 MyBatis 自己负责，Log 只提供路由。不新增 Starter 横向依赖。

## Key Decisions

1. 部署公共模板设置 `logging.config` 选择 `classpath:com/yggdrasil/labs/log/logback/container.xml` 或 `.../server.xml`，不新增第二套选择属性；旧 XML 保留。
2. container 只向标准输出，server 只写 info/error/access/sql 四类文件。SQL.SLOW 与 SQL.JSON 共用 sql.log；专用 Logger 不向 root 叠加。新 root INFO，prod 系列复用 13 条 WARN 前缀，`logging.level.*` 由 Boot 在 XML 之后覆盖。
3. EPP 与根上下文刷新共用同一规则选择：Binder 已绑定时使用原列表，未绑定且按 design IC-1 识别为新预设时使用 password/token/secret/api_key 四组，否则使用旧空列表默认值。显式列表整体替换，空列表关闭预置规则；自定义规则相加。ConfigData 后先归位 Boot 默认源，确保后续绑定优先级一致；直接发布有效列表，不向 Environment 写入 mask 缺省规则，旧路径不引入新严格校验。
4. 服务器推荐统一队列容量 1024、discardingThreshold=0、neverBlock=false、每 appender 排空等待 1s；允许显式 never-block=true，需说明包括 ERROR 的丢失。参数、范围、文件保留与总量以 design IC-3 为准。
5. MDC 采用全部 Map 的提交时快照和 finally 整体恢复；不代替 Span 传播。统一入口支持 Runnable、Callable 和与业务 decorator 的显式组合。
6. 默认适配启用：没有用户 Executor 或新/旧 Builder Bean、平台线程且开关开启时注册一个旧 TaskExecutorCustomizer，供新/旧 Builder 共用。执行 customize 时发现任何其他新/旧 customizer 即退让；否则按 Boot 的 getIfUnique 选择业务 decorator 并加 MDC 外层。保留 Primary 选择，不自动组合全部 Bean，不要求业务调整 order；用户定制路径使用现有 compose 显式接入。

7. 父应用管理唯一 Boot 日志生命周期，普通子上下文设置 parent 后复用其日志；独立子 SpringApplication 的启动、失败与重试不在自动保障内。保留 Mimir 非所有者退出和身份校验，不包装 Boot 全局监听器；独立日志生命周期采用独立进程。
8. 输出故障只承诺保留后端实际检测到的诊断，不增加 stdout 主动检测或逐事件失败计数。PrintStream 吞掉的写入异常可能没有 Logback 错误状态，无异常/无状态错误不等于写出或采集成功。
9. 无条件注册的 EPP/监听器先做安全类检查和 Boot 后端选择检查；无可用 Logback 且未选新预设时无状态退出，显式选择新预设时明确失败。转换器操作隔离在检查后才加载的内部桥接类；独立 MDC 不依赖该桥接。保留旧自动配置的类条件和实际 ILoggerFactory 检查。
10. （D-100，用户确认）Spring Cloud legacy bootstrap 应用不取得日志所有权：EPP 与脱敏刷新以字符串检查 Environment 是否含名为 `bootstrap` 的属性源，是则直接返回，不取令牌、不重置/发布、不写 LoggerContext 属性、不清缓存；由主应用取得令牌并按 `application.yml` 发布。不引入 spring-cloud 编译依赖，详见 design IC-4“Spring Cloud legacy bootstrap”。
11. （审查修订）取得令牌时只重置 Context 引用与启动期缓存，不置空快照；步骤 5 原子替换快照，仅新预设验证失败时置空，消除空快照窗口。转换器跨包内部入口放在 `converter` 包的 public `SensitiveDataConverterInternals`，标注非公共 API。
12. （审查修订）预设 XML 的每个 `<springProperty>` 自带与 IC-3 一致的 `defaultValue`，内部源只覆盖，保证 legacy bootstrap 应用与测试缓存非所有者在无内部源时仍可加载；刷新判定顺序为 bootstrap 源返回 → 无活跃所有者走旧路径 → 所有者根发布 → 其余跳过。

## Behavior Contract

| Spec 行为 | 必须观察的结果 | 对应任务 |
|---|---|---|
| 选择日志预设并保持旧接入兼容 | 旧输出不变；两个明确资源；非法资源失败；无 Logback 且未选预设可启动，显式新预设明确失败 | T1、T2、T5 |
| 路由业务、访问与 SQL 日志 | 正常介质每出口计数精确；container 无文件、server 无 stdout；故障无隐式回退，诊断限后端可检测错误 | T2、T5 |
| 调整滚动、队列与级别 | 真实 appender 参数匹配；边界校验；满队列策略可观察 | T1、T2、T5 |
| 从日志正式初始化起执行凭据字段脱敏 | 三阶段消息/异常保护；setDefaultProperties 覆盖/空列表不受 Boot 重排影响；普通子上下文不改父日志，顺序独立启动无残留；发布过程无空规则窗口；legacy bootstrap 下主应用持有令牌（D-100） | T1、T2、T5 |
| 默认业务任务传递完整日志上下文 | 所有自定义键；正常/异常/空/嵌套/拒绝后恢复 | T3、T4、T5 |
| 与业务执行器扩展协作 | 保留 Boot 业务候选；其他定制器存在即退让；显式 compose 后传播，用户原配置不被覆盖 | T3、T4、T5 |

## Task Dependencies

| Task | Depends on | Ownership |
|---|---|---|
| T1：预设属性与启动期脱敏 | 无 | log/config 预设属性、EPP、mask配置；converter静态状态；spring.factories；对应配置测试 |
| T2：两个输出预设 | T1 | 新 logback XML 资源与资源/输出集成测试；不修改旧 XML |
| T3：MDC 统一入口 | 无，可与 T1 并行 | 新 log/task 包的包装/装饰器；MdcUtil 注释；任务单元测试 |
| T4：Boot 默认执行器适配 | T3 | MDC自动配置、内部旧Customizer适配器、自动配置 imports；上下文测试 |
| T5：组合验收与接入说明 | T1–T4 | 模块README、独立启动/异步组合测试、已有基准的定向扩展 |

T1/T3 可并行；T2/T4 各自等待对应前置任务。T5 集成时不同时让多人编辑相同 README、imports 或 converter 文件。未列出的同模块测试 helper 可按测试需要新增到 `src/test`；不得新增 `tools/` 一次性验收脚本。

## Tasks

### T1：预设属性与启动期脱敏

Purpose：让新预设的配置在日志正式初始化前已有效，且上下文刷新和重复启动不改变其语义。

Files（均以模块目录 `mimir-boot-starters/mimir-boot-starter-log/` 为前缀）：

- Create: `src/main/java/com/yggdrasil/labs/log/config/LogPresetProperties.java` — file/async 的精确类型、默认值、适用范围与校验。
- Create: `src/main/java/com/yggdrasil/labs/log/config/LogPresetEnvironmentPostProcessor.java` — ConfigData 后归位 Boot 默认源、识别资源、安全检查后端、绑定/校验；复用内部规则选择逻辑，经隔离桥接直接发布有效快照；（审查推荐）构造器接收 Boot 的 `DeferredLogFactory`，以预设文件名结尾但未识别时记录一条延迟 INFO（在 bootstrap 排除与读取 `logging.config` 之后、Logback 类/后端检查之前，与后端无关）；（D-100）入口最先检查 Environment 是否含名为 `bootstrap` 的属性源，是则不做任何处理直接返回。
- Create: `src/main/java/com/yggdrasil/labs/log/config/LogbackMaskBootstrap.java` — 仅在检查通过后加载的内部 Logback 桥接，负责转换器重置、发布与释放，并唯一驱动启动期规则缓存的写入（新预设 EPP 发布）与清除（所有者根刷新、释放、令牌重置）；取得令牌时只重置 Context 引用与缓存，不置空快照，发布时原子替换，验证失败时置空快照（审查修订）；经 `SensitiveDataConverterInternals` 调用转换器内部入口；提供包级可见测试重置钩子；不暴露公共 API。
- Create: `src/main/resources/META-INF/spring.factories` — 注册 EnvironmentPostProcessor 与早期 ApplicationListener；若实施时该文件已有他人条目，合并保留。
- Create: `src/main/java/com/yggdrasil/labs/log/config/LogPresetBootstrapListener.java` — 在普通 Bean 创建前接收启动/失败事件，按 SpringApplication 身份绑定根 Context；无匹配令牌立即返回，所有者失败或根关闭清空整个所有权槽（令牌、SpringApplication、Environment、根 Context 引用）并移除服务器内部规范化源，经桥接类清除启动期规则缓存，保留最后规则快照与转换器 LoggerContext 引用直至下一所有者发布时原子替换快照、重绑 Context（审查修订），子事件不得释放；类加载不得引用 Logback。
- Modify: `src/main/java/com/yggdrasil/labs/log/config/LogMaskAutoConfiguration.java` — 根上下文复用与 EPP 相同的规则选择，将有效列表用于原有 LoggerContext 属性同步，随后经桥接类在同一锁内发布快照并清除启动期规则缓存（审查修订）；（审查修订）刷新判定顺序固定为：①（D-100）刷新上下文的 Environment 含名为 `bootstrap` 的属性源时直接返回，不写属性、不发布、不清缓存，且先于②判断；② 无活跃所有者时沿用现有旧路径，任何刷新上下文均写属性并发布（新预设按 EPP 同一选择，否则直接用注入的 `LogMaskProperties`），不读写缓存，覆盖 ApplicationContextRunner、手工上下文及直接调用 `transferConfig` 的既有测试；③ 所有者根按所有者路径发布；④ 有所有者时子上下文与非所有者根上下文不覆盖；所有者根按令牌绑定的身份判断，不以 `getParent() == null` 判断，主上下文带 bootstrap 父上下文时仍是所有者根；保留旧路径处理策略与公共签名。
- Modify: `src/main/java/com/yggdrasil/labs/log/converter/SensitiveDataConverter.java` — bootstrap 重置和本次 Context 绑定（`start()` 仅在 Context 非 null 时重绑）；（审查修订）启动期规则缓存字段与快照同处转换器静态状态，写入、清除与快照更新在同一 `CONFIGURATION_LOCK` 临界区完成，仅由桥接类经内部静态入口驱动；缓存存在时（仅新预设、EPP 发布至所有者根刷新之间）快照置空后按缓存重建并叠加编程式规则，否则读 LoggerContext/系统属性；增加包级可见测试重置钩子（清除快照与缓存并恢复全局 Context 引用）；内部入口改为包级静态方法，不在本类新增 public 方法；保留所有既有公共静态方法和编程式规则语义。
- Create: `src/main/java/com/yggdrasil/labs/log/converter/SensitiveDataConverterInternals.java` —（审查修订）public final、私有构造器，public static 方法仅委托转换器包级入口（重置、发布、写入/清除缓存、验证失败置空），供 `config` 包桥接类跨包调用；Javadoc 标注内部实现、非公共 API，不进入 README；“仅桥接类调用”为约定，由评审和生命周期测试保证。
- Create: `src/test/java/com/yggdrasil/labs/log/config/LogPresetEnvironmentPostProcessorTest.java` — 资源识别、Boot 默认源归位、有效规则选择、所有权与服务器规范化源清理、后端守卫、边界数值与无值错误诊断。
- Modify: `src/test/java/com/yggdrasil/labs/log/config/LogMaskAutoConfigurationTest.java`、`src/test/java/com/yggdrasil/labs/log/converter/SensitiveDataConverterTest.java` — 根/子事件和既有 API 回归；（审查补充）转换器测试中启动的实例（含自建 LoggerContext 或不设 Context）会重绑全局 Context 引用，`@AfterEach` 经包级钩子恢复为 SLF4J 当前 LoggerContext 并清除快照与缓存。

Behavior and constraints：按 design IC-3/IC-4。明确单位转换；列表必须 Binder 原样绑定，不能用逗号拆自定义正则。EPP 在读取预设与规则前将已有 Boot 默认源 moveToEnd，只归位、不修改源及键值。早期发布与根刷新共用同一选择：BindResult.isBound 为 true 时使用绑定列表，包括空列表、空字符串、索引与 relaxed 写法；未绑定且按 design IC-1 识别为新预设时选四组，否则选旧空默认。直接发布有效列表；其他 mask 属性照常绑定，不写入缺省规则属性。所有者失败或根关闭清空整个所有权槽并清除启动期规则缓存（快照已被置空时先按缓存重建），不清空规则快照与 LoggerContext 引用（ApplicationFailedEvent 早于 Boot reportFailure 堆栈，ContextClosedEvent 早于 Bean 销毁），由下一次取得令牌的启动在发布时原子替换（取得令牌时只重置 Context 引用与缓存，不置空快照；新预设验证失败才置空）；启动期规则缓存只对新预设写入，旧配置保持 reload 从 LoggerContext/系统属性读取的原语义；`mimirLogPresetResolved` 仍仅清理属于本次的源实例。资源识别先规范化（审查修订）：`String.strip()` 后要求大小写敏感的 `classpath:` 前缀，去掉前缀及其后一个前导 `/` 再精确比较两个预设位置；无前缀、大小写不同前缀均不识别，以预设文件名结尾的未识别位置输出一条延迟 INFO。新预设规则无效启动失败，旧规则无效仍按既有策略。无条件入口、所有权记录和 MDC 类无 Logback 类型依赖；通过 ClassUtils 字符串类检查及 Boot LoggingSystem 类型链检查后才触达桥接类，EPP 不主动获取 LoggerFactory。普通 Spring Bean 不充当早期初始化入口。

Acceptance：

- 有针对性的失败用例先证明旧刷新期方案不能覆盖新要求，再补实现；四组默认、单规则覆盖、显式空列表、自定义正则含逗号均得到完整快照。
- （审查修订）`classpath:/com/yggdrasil/labs/log/logback/server.xml`、带前后空白的写法识别为同一预设；`/com/...`、`com/...`、`CLASSPATH:com/...`、`classpath*:`、`file:`、复制 XML 不识别，其中以预设文件名结尾的位置恰好输出一条未识别 INFO，复制改名的 XML 不输出。
- （审查补充）EPP 发布后、刷新前调用 `reloadConfig`、`addCustomPattern`、`clearCustomPatterns`，下一次转换仍含四组缺省（显式列表时仍为显式列表）及编程式规则。
- （审查修订）旧配置经过 EPP 后不写缓存：刷新前后先 `putProperty` 或 `System.setProperty` 再 `reloadConfig` 均读到新值；新预设根刷新后缓存已清除、LoggerContext 属性含有效列表，reload 结果不变；关闭后缓存为空，同一 JVM 中后续 `SensitiveDataConverterTest` 类用例不受影响，测试间以包级钩子重置。
- （审查补充）模拟所有者 ApplicationFailedEvent 与 ContextClosedEvent 后，快照仍可脱敏 `password=alpha`，所有权槽不再引用 SpringApplication、Environment 与根 Context，启动期缓存已清除，下一次启动取得令牌时完成重置。
- 对 setDefaultProperties 的缺省、单规则、空 List、空字符串、索引/relaxed 列表，比较 EPP 与刷新时的选择结果，并真实经过 Boot moveToEnd；另测 ConfigData YAML `[]` 与更高优先级覆盖。新预设缺省三阶段均选四组，显式列表不追加默认项，空列表关闭，旧配置缺省为空；用户默认属性保留，复用同一 Environment 的新→旧启动无规则残留。
- （审查修订）刷新判定顺序：无活跃所有者时，`LogMaskAutoConfigurationTest` 现有直接 `transferConfig` 用例及 ApplicationContextRunner 刷新均按旧路径写属性并发布，既有断言不改；Environment 含 `bootstrap` 源且无所有者时不发布、不写属性；有所有者时子上下文与非所有者根不发布。
- （D-100）EPP 单元测试：Environment 含名为 `bootstrap` 的 MapPropertySource 时，不取令牌、不输出未识别 INFO、不生成内部源、不发布、不写缓存，即使 `logging.config` 指向新预设或后端不兼容也不报错；随后同一 JVM 中不含该源的主 Environment 可取得令牌并发布四组缺省。`LogMaskAutoConfiguration` 用 ApplicationContextRunner 为上下文 Environment 加入同名源后刷新，断言 LoggerContext 属性、快照与缓存均未改变；带父上下文但已绑定令牌的所有者根仍正常发布。
- （审查修订）空快照窗口：上一轮发布含 password 的快照后，模拟下一启动取得令牌，在步骤 5 前另一线程输出 `password=alpha` 仍被遮蔽且快照未被系统属性空规则覆盖；步骤 5 后为本次规则；新预设验证失败时快照被置空。未识别 INFO 在 Logback 类缺失或后端为 none 时仍恰好输出一条。
- 对无 Logback 类、Logback 在场但 LoggingSystem=none/其他有效后端分别验证守卫：未选新预设不取令牌、不生成内部源、不发布；显式预设报可解释的兼容性错误。无匹配令牌的失败/关闭事件不能加载桥接类；旧自动配置的类条件和 ILoggerFactory 分支保持回归。
- max-size 两端、超界、total-size-cap 小于 max-size、Duration 亚毫秒/1ms/60s/超界有确定结果；仅服务器绑定服务器专用参数。
- “新→旧”“旧→新”“新→新不同规则”在旧父应用关闭后重复启动，无上轮快照/Context 残留；普通子上下文刷新、刷新失败或关闭不覆盖根配置。旧转换器 API 与原回归测试保持通过。

### T2：两个输出预设

Purpose：以独立资源提供可采集的容器输出与可配置的服务器文件路由。

Files：

- Create: `src/main/resources/com/yggdrasil/labs/log/logback/common.xml`、`container.xml`、`server.xml` — 分别承载新预设共享文本/规则、console-only 和 file-only 输出。
- Create: `src/test/java/com/yggdrasil/labs/log/config/LogPresetResourceTest.java` — XML、共享片段、真实 appender 参数和级别覆盖。
- Create: `src/test/java/com/yggdrasil/labs/log/config/LogPresetOutputIntegrationTest.java` — 标准输出/临时文件事件计数、profile 与错误介质边界。
- 不修改：原 `src/main/resources/logback-spring.xml`；不删除任何资源。

Behavior and constraints：见 design IC-1 至 IC-3。通用文件不设置 INFO threshold，允许显式 DEBUG；error 文件保持 ERROR 过滤。三个专用 Logger additivity=false；prod 系列 13 个 WARN 前缀无 Spring 子 Logger 例外。无 NopStatusListener，无 scan=true，不借选择预设打开 SQL 日志生产。（审查补充）三个 XML 中每个 `<springProperty>` 均声明与 design IC-3 默认值一致的 `defaultValue`（规范化字节/毫秒值，见 design IC-4 步骤 5），内部源只覆盖，使 legacy bootstrap 应用与测试缓存非所有者在无内部源时仍能加载。

Acceptance：

- 输出独一事件 ID 并按 spec 路由表逐出口断言；普通 ERROR 进 info/error 各一次，专用 ERROR 只进对应专用文件。
- container 在可写临时工作目录下仍产生 0 个日志文件；server 产生预期四类文件且 stdout 无业务事件；排空后再统计异步输出。
- 实际 appender 的滚动大小/保留/总量、队列/neverBlock/flush 与覆盖值一致。暂停下游消费验证满队列默认等待，显式非阻塞能观察丢失，清理测试时不遗留阻塞线程。
- （审查补充）`LogPresetResourceTest` 以不含 `mimirLogPresetResolved` 的 Environment 加载 container/server XML，无 Logback 状态错误，appender 参数等于 IC-3 默认值；逐个解析 `<springProperty>`，断言均有 `defaultValue`；引用 8 个内部键的与默认值表一致，其余（如应用名 `application`）与各自既有默认值一致。
- 旧 XML 的生产/默认/开发行为沿既有测试验证；坏资源或 XML 不静默回退。文件输出等后端可检测 I/O 失败保留状态证据。
- stdout 故障用受控的失败 OutputStream 包装为 PrintStream，再作为 System.out：写入后以测试侧 checkError 证实底层失败，即使 Logback 无 ERROR 状态也符合诊断边界；断言无备用文件，不以业务未抛错判成功。该注入在独立 JVM 中执行（如采用独立 Failsafe execution，按 T5 的 summaryFile 与 classpath 规则配置），测试结束关闭自建流，不污染其他用例；不把测试侧探测变成生产轮询。

### T3：MDC 统一入口

Purpose：提供与执行器无关的完整快照包装，以及明确顺序的业务 decorator 组合。

Files：

- Create: `src/main/java/com/yggdrasil/labs/log/task/MdcTaskDecorator.java`、`MdcTasks.java` — design IC-5 的公开签名。
- Modify: `src/main/java/com/yggdrasil/labs/log/util/MdcUtil.java` — 只校准“自动传递子线程”注释与新入口说明；不顺带改现有 putAll/clear 公共语义。
- Create: `src/test/java/com/yggdrasil/labs/log/task/MdcTaskDecoratorTest.java`、`MdcTasksTest.java` — Runnable、Callable、组合与恢复。

Behavior and constraints：所有 MDC 键均传播；提交处创建快照；空快照 clear；finally 恢复原 Map；业务包装在内、MDC 在外。输入 null 立即 IllegalArgumentException，compose(null) 合法。单次包装多次执行重用原快照，不能在执行时捕获提交上下文。

Acceptance：任务提交后再修改原 Map 不影响快照；覆盖正常、异常、空/非空原上下文、caller-runs、嵌套、取消/拒绝、重复框架组合；Callable 返回值/checked exception 不变。单工作线程至少 1000 任务不同键值无残留，原工作线程 Map 精确恢复。

### T4：Boot 默认执行器适配

Purpose：保留 Boot 默认执行器及装饰器选择；自动适配不覆盖用户定制，显式组合沿用统一入口。

Files：

- Create: `src/main/java/com/yggdrasil/labs/log/config/MdcPropagationProperties.java`、`MdcTaskExecutionAutoConfiguration.java` — enabled 属性、Boot 前且无用户 Executor 或新/旧 Builder 时的条件注册。
- Create: `src/main/java/com/yggdrasil/labs/log/task/MdcTaskExecutorCustomizer.java` — 实现旧 TaskExecutorCustomizer，先检测其他新/旧 customizer 并退让（输出一条 INFO 后返回），无其他定制器时组合唯一业务候选与 MDC；不实现 Ordered、不作为新增公共支持接口。
- Create: `src/main/java/com/yggdrasil/labs/log/config/MdcVirtualThreadNotice.java` —（审查补充）内部小组件，由 `MdcTaskExecutionAutoConfiguration` 在 `@ConditionalOnThreading(Threading.VIRTUAL)`、`mdc.enabled=true` 且无用户 Executor 时注册，实现 `SmartInitializingSingleton` 输出一条“默认执行器未自动传播 MDC”INFO；虚拟线程模式下适配器不注册，此组件是该 INFO 的唯一发出者；不创建执行器、不注册 TaskDecorator。
- Modify: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 追加 MDC 自动配置，保留现有条目。
- Create: `src/test/java/com/yggdrasil/labs/log/config/MdcTaskExecutionAutoConfigurationTest.java` — 条件装配、Boot 候选选择、其他 customizer 退让及显式组合。

Behavior and constraints：完整遵守 design IC-6。只注册旧 TaskExecutorCustomizer，不再增加现代适配器或排序协议。customize 执行时用 ObjectProvider 检查其他旧 customizer 和任何现代 ThreadPoolTaskExecutorCustomizer，排除自身，存在任一即返回且不调用 setTaskDecorator，并输出一条含其他定制器数量/类名、不含配置值的 INFO（审查补充）；不靠提前注册或分析业务实现判断安全。虚拟线程模式且开关开启时同样输出一条未接入 INFO，显式关闭时不输出。无其他 customizer 时，以 getIfUnique 保持 Boot 原有业务选择，再调用 MdcTasks.compose。无普通 Mimir TaskDecorator Bean、无全量业务链、无执行器替换或私有字段反射。

Acceptance：

- 默认 Bean 的两个名称和返回类型、lazy、线程池参数与生命周期仍由 Boot 负责；用户现代/旧/多个 Builder 路径自动适配退出，显式组合后另行验证传播。
- 无其他 customizer 时：0 个 decorator 仅传播 MDC；1 个时业务与 MDC 各一次；多个有唯一 Primary 时仅该业务候选执行；多个无唯一候选时业务均不执行、仅传播 MDC。与未接入 Mimir 的 Boot 选择结果对照，不把 @Order 当作自动组合依据。
- 分别提供现代/旧 customizer，在其内部直接设置 decorator，或只改线程池参数；覆盖不同注册与执行顺序。自动适配均不调用 setter，原业务 decorator、参数和执行次数保持，并能捕获一条退让 INFO；虚拟线程开启时捕获未接入 INFO。仅有 Mimir 自身时不能误判为“存在其他 customizer”；显式 compose 路径下 MDC 与业务各一次，不需额外关闭开关。
- 用户自建同名或任意其他 Executor、自定义新/旧/多个 Builder、排除 TaskExecutionAutoConfiguration、关闭、虚拟线程等路径不改写用户执行器或新建线程池；默认 Builder 在运行期被手动复用时说明其 customizer 同样生效的边界。
- 旧API弃用适配仅限 Boot3.3.13；无法满足候选选择与退让边界时修订设计，不通过反射或无条件替换“补兼容”。

### T5：组合验收与接入说明

Purpose：用真实 SpringApplication 和异步调用证明各单项能够共同工作，并给接入方清楚的选择与取舍。

Files：

- Modify: `mimir-boot-starters/mimir-boot-starter-log/README.md` — 两预设、完整属性表、默认旧行为、关闭/覆盖、阻塞/丢失、性能与脱敏边界、自建执行器代码例。
- Create: `src/test/java/com/yggdrasil/labs/log/config/LogRuntimeApplicationIntegrationTest.java` — setDefaultProperties 与 ConfigData 覆盖的早期日志/Bean/刷新后输出、普通子上下文、顺序独立启动、默认 Async 与 MVC 执行器选择。
- Create: `src/test/java/com/yggdrasil/labs/log/config/LogBackendCompatibilityIntegrationTest.java` — 用独立 JVM 的实际依赖集合排除 logback-classic/core，选择可运行的替代 SLF4J/Boot 后端，覆盖启动/刷新/关闭、失败事件与 MDC；也覆盖类在场但 Boot 日志禁用/另选。所需替代后端制品仅作测试依赖，不进入发布制品传递依赖。（审查修订）`*IntegrationTest` 由 Failsafe 执行（Parent POM 的 Surefire 排除该后缀），“独立 JVM”指独立 Failsafe execution：仅包含本类，以 `classpathDependencyExcludes` 去掉 `ch.qos.logback:logback-classic`、`ch.qos.logback:logback-core` 与 spring-cloud-context，保留替代后端。
- Create: `src/test/java/com/yggdrasil/labs/log/config/LogLegacyBootstrapIntegrationTest.java` —（D-100）真实 SpringApplication 开启 `spring.cloud.bootstrap.enabled=true`，`bootstrap.yml` 不含日志设置，`application.yml` 分别选择新预设（无显式规则）与旧配置（显式启用规则）；断言日志正式初始化后、Bean 创建期、刷新后三阶段输出及刷新后 LoggerContext 属性，令牌属于主应用而非 bootstrap 应用，关闭后所有权槽为空。（审查补充）另覆盖：`logging.config` 经系统属性提供新预设（测试后恢复）与经环境变量提供（以自定义 Environment 中同名 `systemEnvironment` 的 `SystemEnvironmentPropertySource` 注入 `LOGGING_CONFIG` 模拟，避免 execution 级环境变量影响同类其他用例），断言 bootstrap 应用加载 XML 无 Logback 配置错误、启动成功、主应用三阶段四组缺省生效；不开启 bootstrap、仅 classpath 含 spring-cloud-context 时启动两个均不关闭的上下文（第二个选择另一新预设及不同 file/async、mask 配置），断言第二个重新加载自身 XML 且参数为 XML 默认值、脱敏仍为所有者规则、启动不失败。ApplicationContextRunner 不经过 SpringApplication 监听器与 bootstrap 流程，不能代替。spring-cloud-context 的 `LoggingSystemShutdownListener` 无条件注册且对每个 SpringApplication 清理日志系统，并带来额外自动配置，因此该类只在含 spring-cloud-context 的独立 Failsafe execution 中运行，不污染本模块其他测试。
- Modify: `mimir-boot-starters/mimir-boot-starter-log/pom.xml` — （D-100）增加 scope=test 的 `org.springframework.cloud:spring-cloud-context`，版本由 mimir-boot-bom 导入的 Spring Cloud 2023.0.6 BOM 管理（4.1.6），不进入发布制品传递依赖；仅在现有测试依赖不足时补 scope=test 的替代后端依赖；不升级生产依赖。（审查修订）测试执行按 Parent POM 的插件分工配置，不覆盖父级 includes/excludes 语义：
  - Surefire（单元测试）：以 `classpathDependencyExcludes` 去掉 spring-cloud-context 与替代后端制品。
  - Failsafe 默认 execution：父 POM 中 Failsafe execution 未写 id，实际 id 为 `default`，模块 POM 必须用 `<id>default</id>` 合并；测试类排除与 `classpathDependencyExcludes` 只写在 execution 级，不写插件级，否则会被新增 execution 继承导致目标用例 0 执行。同样排除二者，并排除 `LogLegacyBootstrapIntegrationTest`、`LogBackendCompatibilityIntegrationTest`；`AccessLogFilterServletIntegrationTest` 等既有集成测试留在此处。
  - Failsafe `legacy-bootstrap` execution：仅包含 `LogLegacyBootstrapIntegrationTest`，classpath 含 spring-cloud-context、不含替代后端。
  - Failsafe `backend-compatibility` execution：仅包含 `LogBackendCompatibilityIntegrationTest`，排除 Logback 与 spring-cloud-context，含替代后端。
  - 每个 execution 设置不同的 `summaryFile`（及报告目录），由 `verify` 目标同时配置自身 `summaryFile` 与 `summaryFiles`（列出另两个 execution 的文件）汇总。Failsafe 3.6.0 共用默认 summaryFile 时会合并写入而非覆盖，分开设置是为了各 execution 结果可单独核对并避免共享文件的顺序依赖。
  - 默认 `forkCount=1`、`reuseForks=true` 下同一 execution 的全部测试类共用一个 JVM，静态所有权槽、转换器快照和 LoggerContext 跨类共享；不同 execution 才是不同 JVM。默认 execution 内的真实启动类须在每个用例关闭上下文并经包级钩子重置，不能假设按类隔离。
- Modify: `src/test/java/com/yggdrasil/labs/log/converter/SensitiveDataConverterBenchmark.java` — 仅扩展既有基准：四规则、命中/不命中、1KiB/64KiB/异常栈、分位数与吞吐口径；不得将计时写成易抖动的普通测试断言。
- 同模块测试 fixture 仅放 `src/test`。部署公共模板在本仓库之外，交付可直接复制的配置示例，不自行修改外部系统。

Acceptance：

- 独立真实启动证明 EPP 先于受保护输出、Boot logger 覆盖后可见 INFO/DEBUG、刷新后规则不改变；同时输出异常原因链，不能仅检查内部静态快照。
- 实际用 setDefaultProperties 设置单 password 和空列表，跨三个阶段断言唯一事件的完整输出，证明 Boot 移动默认源不会反转规则；再用配置文件覆盖默认属性。记录依赖集合与启动参数，不能仅以 Binder 探针代替真实启动验收。
- 无 Logback 的替代后端消费者不选预设时能启动/关闭，失败清理无 NoClassDefFoundError；显式新预设时由兼容性检查拒绝。MdcTasks 在该后端的 MDC 能力范围内传播并恢复；不要求转换器或文件预设可用。不以仅 Mock 后端、Logback 仍在场的测试代替缺类启动；验证监听器在未获令牌时也安全。
- （审查补充）真实启动在 Bean 创建期抛出含 `password=alpha` 的异常，捕获 Boot `reportFailure` 输出的“Application run failed”堆栈；另在销毁回调输出 `token=beta`，断言两处均为 `****`，随后下一独立启动不残留规则。
- （审查修订）同一测试 JVM 中启动两个配置不同（不同预设或 mask 规则）且均不关闭的缓存上下文，断言后者沿用首个所有者的脱敏快照、无启动失败；输出出口按 classpath 分别断言：默认 execution（无 spring-cloud-context）后者沿用所有者 XML 与 appender，仅其 `logging.level.*` 作用于共享 LoggerContext；`legacy-bootstrap` execution（含 spring-cloud-context）后者按自身 `logging.config` 重新加载、XML 参数取默认值、LoggerContext 属性被清空。关闭前者后再启动者使用自身配置。README 分别记录有/无 spring-cloud-context（含经 Feign Starter 传递引入）两种行为、reload 入口在后一种情形会失去所有者规则，以及 `@DirtiesContext`/独立 JVM 的隔离方式，不笼统承诺“后者沿用首个应用的日志预设”。
- 父应用运行期间直接创建普通子上下文，在 refresh 前设置 parent；覆盖子 refresh 失败、正常关闭及再次创建。前后核对父级别、appender 集合、Context 标记属性及带唯一 ID 的实际脱敏输出，不能仅断言引用相同；先关闭子上下文，再关闭父应用，验证下一独立启动取得自身配置。
- 实际 `@EnableAsync` 消费默认执行器的方法能收到提交 MDC 并恢复；具名自建执行器只有显式统一入口时传播。MVC 默认执行器的类型/选择未因定义适配破坏。
- README 明确默认自动适配会对其他新/旧 customizer 保守退让，包括只调整线程池参数的定制器，以及退让/虚拟线程未接入时的 INFO 诊断；提供已有 compose 的接入示例，不增加排序要求或全量业务链规则。
- 既有 README 的“默认不启用规则”限定到旧配置，与新预设四组默认并存；不宣传 Logback错误状态等于远端采集成功。
- README 示例明确普通子上下文共享父日志的接入顺序，以及独立子 SpringApplication 启动/失败/重试不受自动保护；不能将仅移除子 Boot 日志监听器当作完整解决方案。说明 stdout 静默失败可能无后端诊断。
- README 明确两个预设依赖 Logback、其他后端未选预设可退出早期流程、MDC 依赖实际后端实现；列出 setDefaultProperties 的单规则/空列表示例与跨阶段一致性，不宣称任意后续 Environment 动态改写均受保证。
- （D-100）README 说明开启 Spring Cloud legacy bootstrap（`spring.cloud.bootstrap.enabled`、`spring.config.use-legacy-processing` 或 `spring-cloud-starter-bootstrap`）时由主应用取得日志配置所有权，规则按主应用最终 Environment 选择（`application.yml` 等；Spring Cloud 会把 bootstrap 配置以最低优先级合并进主 Environment，因而同样可见但可被覆盖），bootstrap 应用自身不发布；bootstrap 阶段输出不在保护起点内；以属性源名 `bootstrap` 判定，应用自行注册同名属性源会被视作 bootstrap 应用。（审查补充）`logging.config` 经系统属性/环境变量等 bootstrap 可见的源指定时，bootstrap 应用会以 XML 默认文件/队列参数加载预设，该阶段输出不受四组缺省保护。
- （审查补充）README 列出“日志预设路径未识别”边界：仅大小写敏感的 `classpath:com/yggdrasil/labs/log/logback/container.xml` / `server.xml`（允许前缀后一个 `/` 及首尾空白）启用预设缺省；复制的 XML、`classpath*:`、`file:`、文件系统路径、无前缀或大小写不同前缀均不启用四组缺省规则与预设参数，其中以预设文件名结尾的位置仅有一条启动 INFO 提示。另列出新预设在 EPP 至根刷新之间以属性改值再 `reloadConfig` 不生效、刷新后恢复原读取路径的限制。
- 按 spec 的相对门槛记录受控基准；另外报告长消息、异常栈、真实输出与满队列成本及机器/JDK参数。未达到门槛不得写成通过或用旧三规则平均增量代替。

## Integration Acceptance

从仓库根运行模块定向验证：`./mvnw -pl mimir-boot-starters/mimir-boot-starter-log -am clean verify`（审查修订：`test` 不执行 Failsafe 的 `*IntegrationTest`，见[测试规程](../../../engineering/testing.md)），并核对三个 Failsafe execution 的 summary 均被 `verify` 汇总、实际执行用例与预期一致。检查测试报告与关键输出断言，而非只看进程退出；性能基准是单独受控运行，不纳入以上命令成功的推断。实现完成后按项目[测试规程](../../../engineering/testing.md)运行适用的完整质量检查，并用本次候选制品做最小消费者启动，核对两个 XML 和 SPI 注册确实位于制品中。

每个 IC 至少有一条实际行为证据；本地检查、CI、消费者启动、部署后采集分别记录。未部署时不声称容器平台已采集、服务器磁盘策略已在生产生效。文档阶段只做本目录 frontmatter、链接、契约覆盖、空白检查；当前没有 Java 测试、性能结果、CI 或消费者运行结论。

## Decision Boundary

- 实施者可自行选择内部验证 helper、测试 fixture、不可变集合实现及命名细节；不可改变公开 MdcTasks 签名、列表覆盖/空值语义、默认输出或提交时捕获点。
- D-100 规定 legacy bootstrap 应用不取得所有权，不得改为由 bootstrap 应用发布或以 spring-cloud 编译依赖实现判定。
- 精确属性名/默认值/范围、server是否标准输出、队列丢失取舍、默认装配范围、业务候选选择与customizer退让规则及性能门槛均是本设计的契约。若测试证明推荐不可行，先给出原因和替代设计，不能静默变更、减少覆盖或宣称已获用户确认。
- 超出日志模块、升级 Spring Boot、改变旧 XML 默认行为、接管 Boot 全局日志生命周期或扩展独立子 SpringApplication 自动保护、扩展 Span/所有用户线程池自动传播、修改 tools、Git 写入、外部模板写入、发布或部署均不在本计划自动实施范围。

### 启动所有权补充验收（T1/T5）

内部 bootstrap 关联 SpringApplication 实例、原子令牌与根 Context；只有无活跃所有者的新启动可重置，非所有者 EPP 退出，失败或关闭仅由匹配所有者释放。该约束仅保护 Mimir 状态，不拦截 Boot 失败清理。T1 负责内部身份规则，T5 验证普通子上下文失败/关闭/重建及父关闭后的下一独立启动，结合实际出口、级别与脱敏输出断言；不把独立子 SpringApplication 的失败重试纳入自动通过承诺，也不能只测试 ContextRefreshedEvent 或 Context 引用身份。
