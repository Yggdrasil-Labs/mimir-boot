---
version: v2.4.0
status: exploring
created: 2026-10-23
updated: 2026-10-26
---

# v2.4.0 版本级 Brainstorm

本文件是 v2.4.0 的版本级头脑风暴，记录跨模块的候选方向、逐模块讨论结论、依据要点与决策日志；后续需求从此收敛拆分。全部 13 个现有模块已按顺序讨论完毕；方向纳入不代表详细设计获批或实现完成，版本最终交付范围待整体收敛后定稿，再按需求拆分规格、设计与实施计划。2026-10-25 对本文件做了梳理精简：只保留结论、关键约束、依据要点与决策编号，逐轮选项比较不再展开。

## 目标与约束

- 按 v2.4.0 能力迭代规划，允许新功能或较大的协议演进（D-01）；不新增模块，在现有模块职责内丰富能力（D-02）。
- 产品定位是其他项目的基座，优先跨项目复用、接入体验、稳定性与兼容性（D-03）；公共配置、接口、依赖与发布行为遵循[兼容性与接入体验原则](../../../docs/standards/core-beliefs.md)，未明确调整的行为保持兼容。
- 逐模块讨论，允许模块保持现状（D-04）。
- 依据来源：本地提交 `8d33639` 的源码、POM 与模块文档的静态阅读，以及 template-project、valhalla-project、mealmate-project 三个内部业务项目的源码核对。未运行 Java 构建、测试或消费者验证；三个内部项目不代表全部接入方。

## 范围总览

| 模块 | 本轮方向 | 暂缓、撤销或未采纳 |
|---|---|---|
| Parent | P-01 编译目标约束；P-02 保留 classpath 处理器发现；P-04 测试与质量检查开关统一；P-03、P-05 只做使用说明与验证 | 插件问题 PC-01 至 PC-03 待评估；跨 Maven 版本可复现构建未纳入 |
| BOM | 管理 `lombok-mapstruct-binding`、`poi-ooxml`、`cola-component-dto`；验证聚焦本轮变更；保持框架基线 | B-04 安全补丁版本覆盖未采纳 |
| Common | C-01 错误码定义接口；C-02 分页逐条转换及 COLA 分页响应支持；C-04 批量操作结果；C-07 R 的 JSON 只保留 success | C-05、C-08 暂缓；C-03、C-06、C-09 未纳入 |
| Log | L-01 容器输出预设与少量属性入口；L-02 业务任务 MDC 传播 | L-03 访问日志采样、完整 JSON 日志未纳入 |
| Exception | E-01 标准 HTTP 异常语义补齐；E-02 统一校验错误明细 | E-03 安全、异步超时与客户端断开语义未采纳 |
| Web | W-01 日期格式统一接入；W-02 大整数 JSON 输出 | W-03 回传 requestId 保留候选 |
| MyBatis | M-04 慢 SQL 记录；`IPage` 转 COLA `PageResponse`；`searchCount=false` 使用说明 | M-01 撤销；M-02、以 `PageResult` 输出的 M-03 暂缓；防全表更新与删除、单页上限、审计人类型可配置未纳入 |
| MyBatis Processor | 支持的源版本跟随编译器；纠正 README 生成冲突说明 | 生成控制能力未纳入 |
| Nacos | 保持现状；核对结果写入 TD-047 | — |
| RPC Core | 保持现状；旧入口改标 `forRemoval = true` 并明确废弃周期 | — |
| Feign | 保持现状 | 对接 Spring HTTP Service Client 未采纳 |
| Dubbo | 保持现状 | Consumer 端到端验证未纳入 |
| Test | 保持现状 | Testcontainers 共享容器配置未纳入 |

## 各模块已确认方向

### Parent

[Parent POM](../../../mimir-boot-parent/pom.xml) 已提供 BOM 导入、编译配置、单元与集成测试、格式与覆盖率门禁、构建 Profile 及源码与 Javadoc 产物；发布 Parent 的属性覆盖已在此前修复，不重复计为新增能力。

- **P-01 编译目标约束（D-07）**：保留 `java.version`（默认 17）入口，改用 `release` 约束语言、字节码与标准 API；构建 JDK 由接入方选择。依据为 [Maven Compiler 说明](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)。待设计：发布 POM 的属性继承、旧 `source`/`target` 配置迁移、不兼容 API 的负向验证；清理同时存在的 `parameters=true` 与 `-parameters`，以及未被插件引用的 `maven.compiler.source`/`target` 属性。
- **P-02 注解处理器（D-41，替代 D-08）**：不默认配置 `annotationProcessorPaths`，保留 classpath 处理器发现；构建 JDK 为 21 及以上时显式开启 `proc=full`，可用 `maven.compiler.proc` 覆盖；17 至 20 不传该参数（17u 自 17.0.11 才支持）。依据：显式处理器路径会关闭 classpath 发现（[Maven Compiler compile 说明](https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html)），Mimir 自身 8 个 Starter 与三个内部项目均依赖 classpath 上的处理器；JDK 23 起未显式配置时不执行注解处理（[JDK 23 发布说明](https://www.oracle.com/java/technologies/javase/23-relnote-issues.html)）。待设计：按 JDK 启用的方式与 Flatten 发布后的保留，以及在 JDK 17、21、25 下对 Lombok、配置元数据、MapStruct、Processor 生成结果的验证。
- **P-03 构建产物（D-09、D-42）**：默认继续生成源码包与 Javadoc；关闭使用原生 `maven.source.skip`、`maven.javadoc.skip`，不新增 Mimir 属性。交付为 [Parent README](../../../mimir-boot-parent/README.md) 使用说明，以及在发布 Parent 的独立消费者中验证开关与各 Profile 组合；不计为新增能力。
- **P-04 测试与质量检查开关统一（D-11、D-13）**：保留 `dev`、`precheck`、`ci`、`prod` 的名称、职责与默认行为，统一可覆盖开关、优先级与使用说明，优先使用插件原生属性。须处理的现状：`dev` 默认激活且跳过 JaCoCo 与 Spotless；`dev`、`prod` 对 JaCoCo 写死 `skip=true`；`precheck` 实际也执行 Spotless；Surefire 固定 `skipTests=false`；Failsafe 3.6.0 跳过集成测试应使用 `skipITs`（[Failsafe 说明](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/skipping-tests.html)）。建议同时处理 PC-01、PC-02，是否修复仍按 D-10 由用户决定。
- **P-05 私有仓库发布（D-12、D-14、D-43）**：保留 Central 默认地址与默认跳过部署，不新增属性入口；接入方使用自身 `distributionManagement` 覆盖，或原生 `altReleaseDeploymentRepository`、`altSnapshotDeploymentRepository`（[Deploy 插件说明](https://maven.apache.org/plugins/maven-deploy-plugin/deploy-mojo.html)），凭据仍由 settings 或 CI 提供。交付为 README 说明与示例，需提示默认正式版地址是 Central Portal 发布接口、标准 `deploy` 预计不能直接使用（推断，待验证），并在消费者中验证正式版与快照发布；不计为新增能力。

插件问题（D-10，待评估，尚未决定修复）：

| 编号 | 问题 | 依据 |
|---|---|---|
| PC-01 | `ci` Profile 的 GPG 签名在发布 Parent 中固定为 `skip=false`，下游 `-Pci verify` 可能进入签名 | 根 POM 的 `maven-central` Profile 设 `gpg.skip=false`，Parent Flatten 为 `profiles=resolve`，发布时 `${gpg.skip}` 被解析为字面量；本地 v2.3.0 发布 Parent 的有效 POM 可见 |
| PC-02 | `ci` 下 Spotless 的 `spotless-check` 与 `check` 两个 execution 重复调度 | Parent POM 源码 |
| PC-03 | Clean、Resources、Install 的版本属性未接入插件管理，消费者实际版本与属性值不一致 | 临时消费者有效 POM 对照 |

### BOM

- **B-01 配套依赖（D-16、D-67）**：管理 `org.projectlombok:lombok-mapstruct-binding` 与 `org.apache.poi:poi-ooxml`；Common 讨论中另确认管理 `cola-component-dto`，对齐 5.0.0，不导入 `cola-components-bom`。消费方按需声明依赖，BOM 托管不默认引入运行时组件。验收：消费者中验证 Lombok 与 MapStruct 联用生成映射、POI 读写 `.xlsx`，覆盖 Parent 继承与独立导入 BOM 两种接入。
- **B-02 组合验证（D-17）**：保留已有验证，本轮聚焦新增与实际变更的组合，不额外扩展 JWT、Retrofit 与 OkHttp 联用验证。
- **B-03 框架基线（D-18）**：保持 Spring Boot 3.3.13、Spring Cloud 2023.0.6、Spring Cloud Alibaba 2023.0.3.4，升级及专项评估暂缓。代价：Boot 3.3.x 开源支持已于 2025-06 结束；Mimir BOM 以 import 方式引入 `spring-boot-dependencies`，下游无法用 `tomcat.version` 等属性覆盖托管版本，只能自行声明 `dependencyManagement`（对应未采纳候选 B-04）。

### Common

三个内部项目均未引用 Mimir Common 的任何类型，接口层使用 COLA 的 `Response`、`PageResponse`、`MultiResponse`；以下方向在该事实下经用户复核后保留（D-62 至 D-64）。Common 继续不引入 Spring 运行时依赖，`R<T>`、`PageResult<T>` 的 `Serializable` 上界保持不变。

- **C-01 错误码定义接口（D-20、D-21）**：新增独立接口直接提供字符串 code 与默认 message，由内置 `ErrorCode` 与业务枚举实现，并为业务异常与失败响应提供接入入口；`IException` 仍表示异常实例契约。待设计：命名与签名；新增 `R.fail(错误码接口)` 会使 `R.fail(null)` 与 `R.fail(String)` 产生歧义；消息参数化（如“用户 {} 不存在”）纳入或明确排除。
- **C-02 分页逐条转换（D-22、D-55、D-62、D-66 至 D-69）**：
  - 为 `PageResult` 提供逐条转换入口，返回新分页结果并保留分页信息；不提供整页列表转换。
  - 转换契约：null 元素不交给转换函数、结果中保留 null，记录数与分页信息不变；转换异常原样抛出。
  - COLA 支持：Common 以 optional 方式依赖 `cola-component-dto`，转换放在独立工具类，`PageResult` 自身不出现 COLA 类型（避免 Jackson 扫描 getter 时在未引入 COLA 的项目中触发 `NoClassDefFoundError`）；提供 `PageResult` 转 COLA `PageResponse`，MyBatis Starter 另提供 `IPage` 加转换函数转 `PageResponse`；totalCount、pageIndex、pageSize 超出 int 范围时抛参数异常；不提供 `MultiResponse` 转换（COLA 已有 `MultiResponse.of`）。
  - 待设计：工具类命名与包位置、MyBatis Starter 的可选依赖与类缺失行为、`PageResponse` 的 success 设置、返回集合是否可变，以及有无 COLA 两种消费者的编译与运行验证。
- **C-04 批量操作结果（D-23、D-24、D-63）**：汇总总数、成功数、失败数，保留全部逐项结果，每项关联输入标识或序号，可携带成功数据或错误码与消息；执行、事务与重试策略由业务决定。三个内部项目暂无批量场景，作为结果协议预留。待设计：输入关联形式、总体状态、空批次、计数一致性与外层响应约定。
- **C-07 R 的 JSON 只保留 success（D-39、D-64）**：JSON 不再输出 `fail`，Java 保留 `isSuccess()`/`isFail()`，成功仍由 code 派生。三个内部项目不读取 `fail`；对外部使用 `R` 的接入方仍属 JSON 协议变更，迁移安排单独制定。待设计：生效范围按 mapper 来源核对——若在 Web 的 `Jackson2ObjectMapperBuilderCustomizer` 中注册 MixIn，会作用于 Boot 自动配置的共享 `ObjectMapper`（Spring MVC 之外，复用它的消息、缓存、HTTP 客户端等序列化也可能受影响）以及通过 Boot 的 `Jackson2ObjectMapperBuilder` 创建的 mapper；业务自行 `new` 的 ObjectMapper 与 fastjson2 等非 Jackson 路径不受影响。另需确定过渡方式、升级说明，并同步调整 `RTest` 中对 `fail` 的断言。

### Log

[Log Starter](../../../mimir-boot-starters/mimir-boot-starter-log/README.md) 已在 classpath 根提供 `logback-spring.xml` 并引入即生效，已有统一文本输出、分流、滚动、异步队列与脱敏能力（D-30）。

- **L-01 统一默认日志方案（D-27 至 D-29、D-31、D-32、D-44）**：
  - 目标：补齐容器输出预设，使应用、访问与 SQL 日志都能被容器标准输出采集；为滚动大小、保留天数、总量上限与异步队列开放少量属性入口，调整这些参数无需复制整份 XML。
  - 同时提供容器与服务器两类预设，由部署侧公共模板指定；未指定时保留既有输出行为；复用已有文本格式。
  - 新预设生产 root 默认 INFO，第三方降噪直接复用现有 prod 清单（13 条有效规则，去掉与 `io.lettuce` 重复的 `io.lettuce.core`），不拆分 `org.apache`、`com.alibaba` 等宽前缀，使用说明写明可用 `logging.level` 恢复。
  - 新预设在未显式配置时默认启用 `password`、`token`、`secret`、`api_key` 四组已有脱敏规则，允许覆盖或关闭。
  - 现状依据：prod 分支 root 为 WARN 并同时写控制台与文件，`com.yggdrasil.labs`、访问与 SQL 日志只写文件，容器中 `com.example` 一类业务包的 INFO 日志会丢失；滚动与队列参数写死，只能调整 `LOG_PATH` 与 `spring.application.name`。
  - 待设计：属性命名与优先级、早期初始化与已有自定义 Logback 配置的兼容；默认脱敏启用前补吞吐与延迟基准（[可靠性标准](../../../docs/standards/reliability.md)），并核对 ContextRefreshedEvent 前后的有效规则；异步 appender 为 `discardingThreshold=0` 且未开启 `neverBlock`，队列满时会阻塞业务线程，服务器预设默认值需一并考虑。
- **L-02 业务任务 MDC 传播（D-33、D-34）**：优先适配 Spring Boot 默认执行器，自建执行器提供统一接入入口；传播任务提交时的全部 MDC 字段快照，完成或异常后恢复执行线程原上下文；不等同于 Span 传播。硬约束：Boot 3.3.13 默认执行器以 `getIfUnique()` 选取 `TaskDecorator`，Mimir 不得以普通 `TaskDecorator` Bean 默认接入，否则与业务已有装饰器同时存在时两者都不生效；设计时评估复用 Spring 6.1 的 `ContextPropagatingTaskDecorator`。`MdcUtil` 类注释所称“子线程自动传递”与代码不符，需校准。

### Exception

[MimirExceptionHandler](../../../mimir-boot-starters/mimir-boot-starter-exception/src/main/java/com/yggdrasil/labs/exception/handler/MimirExceptionHandler.java) 已覆盖业务、系统、校验与常见 HTTP 异常，并支持替换响应工厂；`BizException` 默认 HTTP 200 的契约保持不变。

- **E-01 标准 HTTP 异常语义补齐（D-36、D-37）**：同时支持 Spring 标准 HTTP 异常（如 `ResponseStatusException`、`ErrorResponseException`）与业务自定义异常类型上的 `@ResponseStatus`，保留状态与必要协议头（如 405 的 `Allow`、`Retry-After`）。现状：`Exception` 兜底先于 `ResponseStatusExceptionResolver` 处理这些异常并返回 500，405 处理未读取异常自带的 `Allow` 头。待设计：类型清单、状态与业务错误码的关系、消息暴露、协议头合并与工厂失败回退。
- **E-02 统一校验错误明细（D-38）**：默认保持现有 data 形态（字符串列表或 null），显式配置后启用统一的字段级、参数级或对象级结构化明细；不默认回传被拒绝的原始值，返回值校验仍按服务端错误处理。待设计：字段协议、排序与跨字段错误表达。

### Web

[Web Starter](../../../mimir-boot-starters/mimir-boot-starter-web/README.md) 已有 JSON 日期配置、CORS、traceId 与 requestId、Servlet 异步 MDC 恢复与响应 traceId 增强。

- **W-01 JSON 与 MVC 参数日期格式统一接入（D-45 至 D-47）**：默认启用、可关闭，MVC 查询参数与表单中的 LocalDate、LocalTime、LocalDateTime 跟随 Mimir JSON 日期格式；Mimir 格式只作为 `spring.mvc.format.*` 的默认值，优先级为 `@DateTimeFormat`、业务显式配置、Mimir 格式、ISO 回退。依据（Boot 3.3.13 与 Spring 6.1.21 源码）：未配置时先按 locale SHORT 解析再回退 ISO，`yyyy-MM-dd HH:mm:ss` 目前不能用于查询参数；自定义格式后 ISO 回退仍保留，只有 locale SHORT 写法会失效，需写入升级说明。待设计：Boot 的 `registerJavaDate` 会把 `spring.mvc.format.date` 同时用于 `java.util.Date`，可能截掉带时间输入的时间部分，需避免或说明；默认值注入方式与开关命名。
- **W-02 大整数 JSON 输出（D-45、D-48、D-49）**：Long/long 与 BigInteger 的值超出 JavaScript 安全整数范围（±(2^53-1)）时输出字符串，否则保持数字，`R.timestamp` 与分页数量等保持数字；默认关闭、显式开启，开启后需提示非 Java 调用方与取值跨越边界的字段。待设计：Map 值与基本类型数组的覆盖、与字段级 `@JsonFormat`/`@JsonSerialize` 及消费者 Module 的优先级、OpenAPI 类型描述一致性。

### MyBatis

- **M-04 慢 SQL 记录（D-51、D-56、D-57）**：默认开启，只输出超过阈值的 SQL，阈值可配置也可整体关闭；使用独立 Logger（如 `SQL.SLOW`）以 WARN 输出语句 ID、耗时与脱敏 SQL，现有 `SQL.JSON` 日志不变。现有 JSON SQL 日志在未配置 `mimir.boot.mybatis.enable-json-sql-log` 时只在 `dev`、`local`、`development`、`test` Profile 下启用，其他环境需显式开启；它在执行前的 `beforePrepare` 阶段输出，无法得到耗时，需另增标准 MyBatis 拦截器计时。慢 SQL 记录默认开启的决定不受此影响。待设计：Executor 或 StatementHandler 层的计时口径、批量与游标查询、是否输出参数、与 L-01 预设的路由衔接、开关与阈值命名。
- **`IPage` 转 COLA `PageResponse`（D-69）**：见 Common 的 C-02，转换函数遵循 D-55 契约。
- **`searchCount=false` 使用说明（D-59）**：在 [MyBatis README](../../../mimir-boot-starters/mimir-boot-starter-mybatis/README.md) 说明单独 count 或只取前 N 条时使用 `new Page<>(current, size, false)`，避免重复 COUNT。

### MyBatis Processor

三个内部项目共 24 个实体使用 `@AutoMybatis` 生成 Mapper、Service 与 ServiceImpl，均以 `provided` 声明并依赖 classpath 发现处理器，无手写同名类与自定义 SQL；该事实佐证 D-41。

- **小修与文档纠错（D-71）**：支持的源版本改为跟随编译器（当前 `@SupportedSourceVersion(RELEASE_17)`，编译目标提高到 21 时会告警，origin 已有 `feature/upgrade_jdk21` 分支）；纠正 [Processor README](../../../mimir-boot-starters/mimir-boot-starter-mybatis-processor/README.md)“问题4”：生成类名由实体名派生，实际冲突场景是 `UserDO` 与 `User` 同时标注后都生成 `UserMapper`。不计为新增能力。

### Nacos、RPC Core、Feign、Dubbo、Test

- **Nacos（D-73）**：保持现状。仅 valhalla-auth 接入，使用 `optional:nacos:valhalla-auth.yaml?refreshEnabled=true`；配置 Bean 为未加 `@RefreshScope` 的普通 `@ConfigurationProperties`，`LoginExecutor` 的 `@Value` 不随推送刷新；未见加密值与配置删除需求。核对结果已写入 [TD-047](../tech-debt-tracker.md)，状态仍为待规划。
- **RPC Core（D-75）**：功能保持现状。TD-013（Bridge 旧 `extract()`）与 TD-023（`RpcHookChain` 旧 `before`、`after`、`onError`、`cleanup`）对应入口改标 `@Deprecated(forRemoval = true)`，在 README 与升级说明中写明迁移到受管 scope 与 `open`/`openAsync` 调用级 invocation，计划在下一个主版本删除；本轮不删除。内部仅 valhalla 经 Dubbo Starter 间接使用，未自定义 Hook 或 Bridge，未调用旧入口；唯一的 HTTP 外调是 mealmate-project 调用 DeepSeek 外部接口，不需要链路传播。
- **Feign（D-77）**：保持现状。内部无使用方；[Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/) 自 2022.0.0 起被视为功能完结，建议迁移到 Spring HTTP Service Clients。
- **Dubbo（D-79）**：保持现状。仅 valhalla-auth 以 `@DubboService` 暴露 `AuthRpcFacade`，业务错误通过 COLA Response 返回；内部仓库无 Consumer；Dubbo 配置放在 classpath 的 `bootstrap-dubbo.yaml`，Nacos 只作注册中心。
- **Test（D-81）**：保持现状。三个项目均以 `test` 范围引入，但未引用 Mimir 的任何测试 API，只用于聚合测试依赖；测试以 Mockito 单元测试为主，少量 `@SpringBootTest` 用 H2，Testcontainers 由项目自行搭建；Starter 附带的 `application-test.yml` 会被接入方同名文件覆盖。附带配置文件去留与 `@MimirBootTest` 已废弃属性留待后续评估。

## 暂缓、撤销与未采纳

| 编号 | 内容 | 状态 | 依据与重新评估条件 |
|---|---|---|---|
| M-01 | 拦截器顺序编排（多租户、动态表名排在分页前） | 已撤销（D-58，撤销 D-52、D-53） | 属于业务使用多租户组件时的考虑；MyBatis-Plus 3.5.17 字节码确认分页在 `willDoQuery` 阶段执行 count，排在其后的拦截器尚未改写 SQL |
| M-02 | 无总数分页的持久层适配 | 已暂缓（D-59，暂缓 D-54） | 内部项目无该场景；原方案为自身 `IPage` 实现不查 count、多取一条；出现真实场景时与 C-05、C-08 一并评估 |
| M-03 | `IPage` 加转换函数输出 `PageResult` | 已暂缓（D-65，替代 D-60） | `PageResult` 无使用方；已改为输出 COLA `PageResponse`（D-69） |
| C-05 | 无需总数的页码式分页结果 | 已暂缓（D-61，暂缓 D-25） | 内部项目无滚动列表或触底加载场景，接口层用 COLA `MultiResponse` |
| C-08 | 无总数分页的“多查一条”构造入口 | 随 C-05 暂缓（D-61） | 同上 |
| C-03 | 多字段排序 | 未纳入（D-20） | `PageConverters` 不消费 `PageRequest.orderBy`，若保留该字段需在文档中说明 |
| C-06 | R 数据映射 | 未纳入（D-23） | — |
| C-09 | R 的集合数据便利入口 | 未采纳 | `R<List<VO>>` 因 `Serializable` 上界无法编译；放宽上界破坏二进制兼容 |
| B-04 | BOM 安全补丁版本覆盖 | 未采纳 | 保持框架基线时为 Tomcat、Jackson、Netty、Logback 等提供补丁版本管理 |
| E-03 | 安全、异步超时与客户端断开的异常语义 | 未采纳 | `Exception` 兜底会把方法级 `AccessDeniedException`、`AuthenticationException` 处理为 500，`AsyncRequestTimeoutException` 返回 500，客户端断开按 error 记录 |
| L-03 | 访问日志采样与分路径慢阈值 | 未纳入 | 需消费方维护规则 |
| — | 完整 JSON 日志输出 | 未纳入（D-30） | 已有统一文本输出 |
| W-03 | 回传已有 requestId（`X-Request-Id`） | 保留候选（D-45） | 常量已存在，当前无响应路径使用 |
| — | 防全表更新与删除、分页单页上限、审计人类型可配置 | 未纳入（D-51） | — |
| — | Processor 已存在同名类时跳过生成、按实体只生成 Mapper | 未纳入（D-71） | 内部项目无需求 |
| — | RPC Core 对接 Spring HTTP Service Client | 未采纳（D-77） | 出现内部 HTTP RPC 场景时评估，遵守 D-02 |
| — | Dubbo Consumer 端到端验证 | 未纳入（D-79） | — |
| — | Testcontainers 共享容器配置 | 未纳入（D-81） | — |
| — | 跨 Maven 版本可复现构建基线 | 未纳入 | Wrapper 为 Maven 3.10.0，已有默认可复现构建支持 |

## 2026-10-24 代码对照复核

| 编号 | 关联 | 复核发现 | 处理 |
|---|---|---|---|
| RV-01 | P-02 | 显式处理器路径会关闭 classpath 发现，JDK 23 起默认不执行注解处理 | D-41 |
| RV-02 | P-03 | 原生 skip 属性已可分别关闭 | D-42 |
| RV-03 | P-05 | Deploy 插件原生支持分别覆盖正式版与快照仓库 | D-43 |
| RV-04 | L-01 | 默认 XML 已随 Starter 生效，prod 已有第三方降噪清单 | D-44 |
| RV-05 | P-04 | PC-01 的成因在源码中坐实，PC-02 位于同一段配置 | 并入 P-04 约束 |
| RV-06 | P-04 | 各 Profile 的跳过与写死行为 | 并入 P-04 约束 |
| RV-07 | B-03 | 框架基线的安全代价与下游无法属性覆盖 | 并入 BOM 说明，候选 B-04 |
| RV-08 | L-02 | `TaskDecorator` 的 `getIfUnique()` 风险 | 并入 L-02 硬约束 |
| RV-09 | L-01 | 默认脱敏的性能影响 | 并入 L-01 待设计 |
| RV-10 | C-07 | Common 无 Jackson 依赖，MixIn 的影响范围取决于 ObjectMapper 来源（2026-10-26 校准：不限于 Spring MVC） | 并入 C-07 待设计 |
| RV-11 | C-01 | 消息参数化 | 并入 C-01 待设计 |
| RV-12 | W-02 | 时间戳与分页数量为 Long | 并入 W-02 策略 |

## 业务项目核对摘要

2026-10-25 静态检查三个内部业务项目（template-project Parent 2.3.0，valhalla-project 与 mealmate-project Parent 2.1.0），未构建、未运行，未读取 Nacos 服务端配置：

- 接口层使用 COLA 5.0.0 的 `Response`、`PageResponse`、`MultiResponse`，前端按 `success`、`errCode`、`errMessage` 解析；源码未引用 Mimir Common 的任何类型，异常处理由项目自身 Advice 兜底。
- 分页为直接 `new Page<>(pageNum, pageSize)` 后另调 `count()`，每次分页执行两次 COUNT；template-project 的重复 COUNT 作为模板侧问题记录，不在本仓库修改。
- 未使用多租户、动态表名或自定义 `InnerInterceptor`，未使用 `PageConverters`，无无总数分页与批量结果场景。
- MyBatis Processor、Nacos、RPC、Test 的使用情况见对应模块。

## 待校准文档与技术债关联

- 已校准，无需处理：[AGENTS.md](../../../AGENTS.md) 当前的测试与质量门禁入口已是 `./mvnw -Pci clean verify`，原记录的“默认 `verify` 被误称为质量门禁”问题不成立（2026-10-26 核对）。
- [Parent README](../../../mimir-boot-parent/README.md) 将覆盖率报告描述为在 `test` 阶段生成且不含集成测试，与 POM 在 `verify` 阶段汇总不一致。
- `MdcUtil` 类注释所称子线程自动传递与代码不符。
- 技术债：TD-013、TD-023 按 D-75 明确废弃周期；TD-047 按 D-73 补充消费者核对；TD-016 字段与记录级 AAD 不纳入本轮。均见[技术债台账](../tech-debt-tracker.md)。

## 决策日志

各决定均由用户在讨论中逐项选择或明确回复确认。状态：有效；已替代、已撤销、已暂缓的条目注明去向。

| 编号 | 决定 | 状态 |
|---|---|---|
| D-01 | 按 v2.4.0 能力迭代规划，允许新功能或较大的协议演进 | 有效 |
| D-02 | 不新增模块，在现有模块内丰富能力 | 有效 |
| D-03 | 优先跨项目复用、接入体验、稳定性与兼容性 | 有效 |
| D-04 | 逐模块讨论，允许模块保持现状 | 有效 |
| D-05 | Parent 编译目标、处理器、构建产物三个方向纳入讨论 | 有效 |
| D-06 | 使用同一个 brainstorm 持续记录 | 有效 |
| D-07 | P-01 只约束编译目标，构建 JDK 由接入方选择 | 有效 |
| D-08 | P-02 默认配置 Lombok 处理器，其余按需启用 | 已替代（D-41） |
| D-09 | P-03 默认生成源码包与 Javadoc，允许分别关闭 | 有效，交付定位见 D-42 |
| D-10 | Parent 插件问题先记录，是否修复后续决定 | 有效 |
| D-11 | Parent 增加测试与质量检查开关统一 | 有效 |
| D-12 | Parent 增加私有仓库发布接入 | 有效，交付定位见 D-43 |
| D-13 | P-04 保留现有 Profile 职责与默认行为，统一开关、优先级与说明 | 有效 |
| D-14 | P-05 保留 Central 默认地址并提供仓库地址、ID 覆盖属性 | 默认仓库与默认跳过保留；属性入口由 D-43 替代 |
| D-16 | BOM 管理 `lombok-mapstruct-binding` 与 `poi-ooxml` | 有效 |
| D-17 | BOM 保留已有验证，聚焦本轮变更组合 | 有效 |
| D-18 | 保持 Boot、Cloud、Alibaba 现有基线，升级暂缓 | 有效 |
| D-20 | Common 纳入错误码接入与分页结果转换，多字段排序不纳入 | 有效 |
| D-21 | C-01 新增独立错误码定义接口 | 有效 |
| D-22 | C-02 逐条转换并返回新分页结果 | 有效 |
| D-23 | Common 补充纳入批量结果与无总数分页，R 数据映射不纳入 | 无总数分页部分已暂缓（D-61） |
| D-24 | C-04 汇总数量加全部逐项结果 | 有效 |
| D-25 | C-05 复用 PageRequest 的页码式无总数分页结果 | 已暂缓（D-61） |
| D-27 | Log 优先降低接入方维护与配置成本 | 有效，表述按 D-44 校准 |
| D-28 | Log 同时支持容器采集与服务器落盘两类预设 | 有效 |
| D-29 | 预设由部署侧公共模板指定，未指定时保留既有输出 | 有效 |
| D-30 | 统一文本输出为已有能力，不重复列为新增 | 有效 |
| D-31 | 新预设生产 root 默认 INFO，常见第三方降噪 | 有效，降噪清单见 D-44 |
| D-32 | 新预设默认启用四组凭据字段脱敏规则，可覆盖或关闭 | 有效 |
| D-33 | L-02 纳入，优先适配默认执行器，自建执行器提供统一入口 | 有效 |
| D-34 | L-02 传播提交时的全部 MDC 快照并恢复原上下文 | 有效 |
| D-36 | Exception 纳入 E-01、E-02 | 有效 |
| D-37 | E-01 同时支持标准 HTTP 异常与 `@ResponseStatus` | 有效 |
| D-38 | E-02 默认保持现有 data 形态，显式启用结构化明细 | 有效 |
| D-39 | C-07 JSON 只保留 success，Java 保留两个判断方法 | 有效，复核见 D-64 |
| D-41 | P-02 保留 classpath 处理器发现，JDK 21 及以上显式开启 `proc=full` | 有效 |
| D-42 | P-03 使用原生 skip 属性，交付为说明与验证 | 有效 |
| D-43 | P-05 不新增属性，交付为说明与示例 | 有效 |
| D-44 | L-01 校准为容器输出预设与少量属性入口，复用现有降噪清单 | 有效 |
| D-45 | Web 纳入 W-01、W-02，W-03 保留候选 | 有效 |
| D-46 | W-01 默认启用、可关闭，ISO 回退保留 | 有效 |
| D-47 | W-01 Mimir 格式仅作为 `spring.mvc.format.*` 默认值 | 有效 |
| D-48 | W-02 超出 JavaScript 安全整数范围才输出字符串 | 有效 |
| D-49 | W-02 默认关闭、显式开启 | 有效 |
| D-51 | MyBatis 纳入 M-01 至 M-04 | M-01 已撤销（D-58），M-02 已暂缓（D-59），M-03 已暂缓（D-65） |
| D-52 | M-01 按类型归位、`@Order` 覆盖 | 已撤销（D-58） |
| D-53 | M-01 默认生效、可回退 | 已撤销（D-58） |
| D-54 | M-02 两步式无总数分页入口 | 已暂缓（D-59） |
| D-55 | 转换契约：跳过并保留 null，异常原样抛出 | 有效，用于 C-02 与 `IPage` 转 `PageResponse` |
| D-56 | M-04 默认开启、阈值可调可关闭 | 有效 |
| D-57 | M-04 独立 Logger 以 WARN 输出，`SQL.JSON` 不变 | 有效 |
| D-58 | M-01 移出本轮，不改代码也不补说明 | 有效 |
| D-59 | M-02 暂缓，只补 `searchCount=false` 使用说明 | 有效 |
| D-60 | M-03 保留为低成本便利 | 已替代（D-65） |
| D-61 | C-05 暂缓，C-08 一并暂缓 | 有效 |
| D-62 | C-02 保留并增加 COLA 分页响应支持 | 有效 |
| D-63 | C-04 保留 | 有效 |
| D-64 | C-07 保留，迁移安排单独制定 | 有效 |
| D-65 | 以 `PageResult` 输出的 M-03 暂缓 | 有效，COLA 形式见 D-69 |
| D-66 | COLA 支持放在 Common 的可选依赖与独立工具类 | 有效 |
| D-67 | BOM 只管理 `cola-component-dto` 5.0.0 | 有效 |
| D-68 | 转 COLA 时超出 int 范围抛参数异常 | 有效 |
| D-69 | `PageResult` 与 `IPage` 均可转 COLA `PageResponse`，不做 `MultiResponse` | 有效 |
| D-71 | Processor 只做源版本与 README 两项小修 | 有效 |
| D-73 | Nacos 保持现状，核对结果写入 TD-047 | 有效 |
| D-75 | RPC Core 保持现状，旧入口改标 `forRemoval = true` 并明确废弃周期 | 有效 |
| D-77 | Feign 保持现状，HTTP Service Client 对接记为未采纳候选 | 有效 |
| D-79 | Dubbo 保持现状 | 有效 |
| D-81 | Test 保持现状 | 有效 |
| D-15、D-19、D-26、D-35、D-40、D-50、D-70、D-72、D-74、D-76、D-78、D-80 | 各模块依次收口并进入下一模块；不代表详细设计定稿或授权实施 | 过程记录 |

## 当前产物状态

本文件为未定稿 brainstorm，尚未形成需求规格、设计、实施计划或验收标准；版本阶段见[版本记录](./release.md)。当前仅记录讨论，不代表功能已实现、测试通过或已发布。
