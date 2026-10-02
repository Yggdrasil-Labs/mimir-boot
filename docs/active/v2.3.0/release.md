---
version: v2.3.0
status: planned
updated: 2026-10-02
---

# Release — v2.3.0

本目录记录[治理方案及实施](./index.md#文档治理工作入口)，工程实现已有独立提交，文档迁移与 T8 整体验收仍待收尾，尚无本版本的发布验证记录。根 POM 仍为 `2.2.2-SNAPSHOT`；本目录编号仅用于组织计划。

## 计划范围

- 收敛 Agent 文档导航、工程规程和权威事实来源。
- 本地提交与推送执行离线 quick；完整验收由匹配条件的 CI、发布流程或显式本地 full 执行。

## 发布条件

实施完成后，根据[计划](./docs-quality-governance/plan.md)记录实际变更、兼容性影响和验证证据，再决定发布版本与时间。制品发布成功必须有远程工作流和目标制品证据，本地构建或 tag 不替代发布确认。

## 日志脱敏（已实施并通过本地完整验收，未发布）

日志脱敏实施及本地完整验收于 2026-09-23 通过；TD-038 已处理。此记录不代表 v2.3.0 已发布，也不代表 CI、远端工作流或发布后消费者已验证；根 POM 仍为 `2.2.2-SNAPSHOT`。

- 显式启用后，`password`、`token`、`secret`、`api_key`、`account`、`id_card`、`phone`、`bank_card`、`email`、`name` 这 10 组字段型规则支持普通赋值和带引号键名的文本标量；默认规则集合仍为空。
- 该处理基于文本边界，不是完整 JSON 解析。`{"account":["alice","bob"]}` 等对象或数组值不保证完整脱敏，可能留下敏感残余或破坏结构；业务代码须在写日志前移除此类字段或预先脱敏。
- 别名没有左边界，宽泛规则可能遮罩更长字段名；空引用值仍会被遮罩，数字、布尔值和 `null` 不保留原 JSON 类型，未闭合引号值延伸到消息末尾。
- 字段扫描后，纯值、自定义和编程式正则仍对完整扫描结果执行，可能匹配 replacement、普通文本和复合值残余。

## MongoDB 驱动对齐（已实施并通过本地消费者验收，未发布）

本地实施移除了 Mimir BOM 的 `mongodb.version` 属性和 `mongodb-driver-sync` 单项版本固定，由 Spring Boot 3.3.13 BOM 管理 MongoDB 驱动族 5.0.1，完成从旧版 4.11.5 的版本对齐。消费者若显式覆盖版本，应整族保持一致并自行验证；5.0.1 可能包含旧 API/ABI 不兼容变更，升级方须重新编译并核对上游变更说明。

T2 一次性本地验收通过 BOM、Parent、Spring Data 三种隔离消费者的依赖解析、同步客户端初始化和 Spring Data `_id`/`name` 离线映射验证。验收不连接真实 MongoDB 服务端，不覆盖 CRUD 或生产行为；专项 fixture 已在验收后清理，不属于常规工程 consumer/quality 门禁。这不是线上发布验证，也不表示该变更已发布。根 POM 仍为 `2.2.2-SNAPSHOT`，v2.3.0 尚未发布。

## 发布 Parent 属性覆盖（TD-036，已修复，未发布）

发布 Parent 的 `pluginManagement` 保留版本和 JaCoCo 门槛的属性引用，使下游仅覆盖属性即可调整编译插件版本、Java 目标版本和覆盖率门槛；`properties` 仍只保留 Parent 自身属性，并补充 `spring.boot.version`，避免根 POM 扁平化后遗漏该属性。修复已提交为 `baafc35`。

本地隔离消费者对照：旧 Parent 仍得到 Java 17、编译插件 3.16.0、JaCoCo 门槛 0.60/0.50；将新生成的发布 POM 安装到隔离 Maven 仓库后，仅覆盖属性的消费者得到 Java 11、编译插件 3.15.0、门槛 0.31/0.27，并成功编译为 Java 11 字节码。分别提高指令或分支门槛时，JaCoCo 检查按新值失败；未覆盖的消费者仍得到原默认值。消费者在 `ci` profile 下的 effective POM 没有未解析属性。

验收基于本地生成的发布 POM 和隔离 Maven 仓库，不代表 CI、Maven Central 或发布后外部消费者验证。

## OkHttp JVM 制品坐标（TD-037，已修复，未发布）

BOM 新增 `com.squareup.okhttp3:okhttp-jvm:5.5.0` 的版本管理，保留原有 `okhttp` 坐标的版本管理。Maven/JVM 消费者需要将依赖声明改为 `okhttp-jvm` 才能直接使用 `OkHttpClient`；导入 BOM 不会自动替换原依赖坐标。

本地隔离消费者仅导入生成的发布 BOM，并声明无版本号的 `okhttp-jvm`。修改前 Maven 报缺少依赖版本；修改后消费者完成 Java 17 编译，使用 `OkHttpClient` 对本地回环服务发起请求并得到预期响应。验收使用本地生成的发布 POM 与隔离 Maven 仓库，不代表 CI、真实发布或发布后外部消费者验证；Reactor 内仍无 OkHttp 直接消费者。

## Nacos 解密覆盖层清理（TD-039，部分修复，未发布）

刷新事件发生时，若新旧 Nacos 加密配置前缀均已消失，监听器会移除历史 `decryptedProperties:*` 覆盖层。原始明文和下层配置可以重新生效；恢复加密配置后仍会重新解密。本地聚焦测试已验证这些路径，以及业务属性完全删除时 Environment 不再返回旧明文。

业务属性完全删除且没有下层值时，Spring Cloud 对已有 `@ConfigurationProperties` Bean 的重新绑定仍可能保留旧字段值，TD-039 因此尚未完全关闭。此处仅记录本地验证，不代表 CI、真实发布或发布后消费者验证。

## 技术债记录整理（2026-09-30）

用户采纳统一规则：活跃台账只保留未闭环债务，关闭项的表格行、明细和旧锚点一并移除，先切换仓库内引用；编号永不复用。已完成记录归入已有计划或验收记录，无独立计划时由 release 承接；债务关闭与制品发布分别维护。

本次迁出的五项记录分别由本页的 [TD-036](#发布-parent-属性覆盖td-036已修复未发布)、[TD-037](#okhttp-jvm-制品坐标td-037已修复未发布)、[日志脱敏计划 T4](./log-json-masking/plan.md#t4-全局验收与技术债状态)、[MongoDB TD-040 处置记录](./mongodb-driver-alignment/plan.md#td-040-处置记录)、[Springdoc 本地验收记录](./springdoc-boot-alignment/plan.md#本地验收记录2026-09-30)承接。TD-036、TD-037 的详细验收从原台账迁入本页；其余三项已有独立记录。旧计划中保留锚点的执行结论仍反映当时事实，本次仅改变后续文档归属，不重写历史验收结论。

台账继续保留九项活跃债务。TD-043、TD-044 的早期专项证据已在治理计划 T3 记录；工具迁移后的 T8 最终快照验收仍待闭环，本次不关闭这两项。此次整理仅涉及文档，没有新的 Java、消费者、CI 或发布验证。

## 日志断言索引边界（TD-045，已修复，未发布）

`LogTestUtils` 的公共取日志与断言入口通过同一个边界检查拒绝负索引和上界越界，统一抛出 `AssertionError`。负索引诊断包含实际索引和日志数量，已有上界消息保持兼容。正常索引行为不变。

2026-10-02 在 `fix/technical-debt-small` worktree 中先运行回归测试，确认 `getLogEvent(appender, -1)` 原先抛出 `IndexOutOfBoundsException`。修复后 `./mvnw -o -Pci -pl mimir-boot-starters/mimir-boot-starter-test -am clean verify` 退出 0；`LogTestUtilsTest` 23 项通过，覆盖负索引、上界和空列表诊断。所选模块及 Common 依赖分别执行 151、61 项单元测试，失败、错误、跳过均为 0，格式和覆盖率检查通过；Failsafe 未发现集成用例，不将其描述为已验证的集成行为。

TD-045 按上述约定范围关闭，记录迁入本页并移除台账旧锚点。本地验证不代表远程 CI、制品发布或发布后消费者验证。

## 测试清理顺序（TD-046，已修复，未发布）

`BaseUnitTest` 在自定义 `tearDown()` 后通过 `finally` 统一清理环境，覆盖正常返回和异常路径。自定义钩子抛出的异常实例继续向上传播。顺序变化使钩子能看到测试留下的 MDC；钩子写入的 MDC 在测试结束后也会清空，接入说明已同步到测试 Starter README。

2026-10-02 本地回归先复现正常和异常钩子写入 MDC 后残留，两项断言均失败，异常原样传播断言通过。修复后与 TD-045 共用的模块 `clean verify` 验证中，`BaseUnitTestTest` 6 项通过；测试 Starter 151 项、Common 61 项单元测试的失败、错误、跳过均为 0，格式和覆盖率检查通过，Failsafe 无集成用例。首次沙箱内运行因 Mockito JVM attach 不可用而未进入目标行为，复现与验收均在沙箱外完成。

TD-046 按上述约定范围关闭，记录迁入本页并移除台账旧锚点。本地结果不代表远程 CI、制品发布或发布后消费者验证。

## 分页参数校正边界（TD-042，已修复，未发布）

`PageRequest` 的页码、页大小和排序方向 setter 复用原有 `validateAndCorrect()`，默认 Jackson 绑定及 `PageQuery.toPageRequest()` 读取的嵌套分页参数与带参构造一致。公开方法签名、JSON 字段、默认值及上限保持原有契约；行为变化是非法 setter/绑定输入立即校正，不再原样保留到显式校验。排序方向仍接受大小写 ASC/DESC，合法值保留原样，`getOffset()` 仍校验乘法溢出。

2026-10-02 本地回归先确认 setter 写入 `-1` 后直接 getter 返回 `-1`，新增测试失败。修复后定向 `PageRequestTest`、`PageQueryTest` 共 11 项通过，覆盖负/零页码、零/负页大小、超大页大小、null、非法排序方向、Jackson 与构造一致性、嵌套绑定及引用一致性；模块 CI `clean verify` 中 Common 61 项单元测试通过，失败、错误、跳过均为 0。

特殊字段访问、反射写入及历史 Java 序列化数据仍应显式校验，不扩大默认 setter 绑定的保证。TD-042 按上述约定范围关闭，记录迁入本页并移除台账旧锚点。

本轮首批三项修复的本地完整验收：`bash scripts/engineering.sh quality --mode full --source worktree --report /tmp/mimir-small-debt-full/quality-report.json` 在 2026-10-02 16:44 至 16:54 执行，退出 0。报告输入为 `worktree-c4945af552b2fa9e2dc0b587185d29b78c43f376bb38db846c9293719b268c77`；文档、构建模型、发布契约、隔离消费者、临时签名及 Java 必需检查均 passed。manifest 的 11 个源码模块对应本次 XML 共 1070 项单元测试、43 项集成测试，失败、错误、跳过均为 0；包含 MyBatis 分页转换回归。Sonar 为 `RUN_SONAR=false`，记为不适用。验收代码与待提交代码相同；期间补正测试 Starter 的重复文档说明，随后补入本摘要，最终文档另行 full 复验，不把原输入摘要冒称最终提交快照。

附加 `docs-evolve` 结构检查退出 1，报日志规格 `verified` 状态不在其允许集合及 MongoDB 计划 HTML 注释被识别为占位符；两文件与基线 `bdcac51` 完全相同，本次未修改。项目文档 full 已通过，此附加检查不描述为通过。

上述均为本地证据，不代表远程 CI、制品发布或发布后消费者验证。TD-043/TD-044 仍需完整 T8 场景验收，不能据本次普通 full 移出台账。
