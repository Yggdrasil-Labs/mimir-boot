---
version: v2.3.0
status: released
branch: main
created: 2026-09-14
released: 2026-10-14
archived: 2026-10-23
retain_until: 2027-10-23
updated: 2026-10-23
---

# Release — v2.3.0

本目录的五项需求于 2026-10-23 归档，包含[治理方案及实施](./index.md#文档治理工作入口)、日志脱敏、MongoDB/Springdoc 兼容及运行时修复。v2.3.0 已于 2026-10-14 创建 GitHub Release，下列发布复核与需求历史验收分别记录。历史正文中的“未发布”、`2.2.2-SNAPSHOT`、旧命令和执行路径均指当时状态，不代表归档时的当前状态。

## 发布与归档复核（2026-10-23）

- 本地 `v2.3.0` tag 指向发布提交 `d311685dfb54f436f4c58c67ad8236e3aa1b935f`（PR #321）；[GitHub Release](https://github.com/Yggdrasil-Labs/mimir-boot/releases/tag/v2.3.0) 的 `publishedAt` 为 `2026-10-14T06:13:49Z`，非草稿、非预发布。
- [首次发布运行](https://github.com/Yggdrasil-Labs/mimir-boot/actions/runs/38326710849) 失败；[2026-10-14 补偿发布运行](https://github.com/Yggdrasil-Labs/mimir-boot/actions/runs/38519561590) 成功。后者的共享完整质量门禁、Maven Central 上传、公开制品验证及 GitHub Release 创建步骤均为 success；该次 GitHub Packages 任务为 skipped，不据此声称补偿运行重新上传了 Packages。
- 补偿工作流由 `fc27369cb695a3de4009193332728819c217d518` 启动，源码检查与发布按输入检出原 tag；工作流提交与发布源码提交分别记录。开发版本随后由 `f34fcf3` 推进到 `2.3.1-SNAPSHOT`，与归档时根 POM 一致。
- 本次只读查询使用 `gh release view v2.3.0`、`gh run list --workflow release.yml`、`gh run view 38519561590`，均指定 `Yggdrasil-Labs/mimir-boot`。以上核实的是远端记录；本次未重新下载制品或运行 Java、真实 Nacos、实际业务消费者测试，也未核实 Sonar Quality Gate。
- 按项目约定保留 `v2.3.0` 目录名、历史计划和验收原文；治理计划中的旧 Plan Verdict/Execution 模板已有历史说明，不回填为本次执行证据。当前仓库没有 `docs/generated/`，无需刷新生成文档。

## 版本摘要

本版本完成文档职责迁移与统一质量门禁，补齐日志标量字段脱敏、MongoDB/Springdoc/Redisson 依赖兼容及 Parent/OkHttp 消费契约，修复分页、测试清理、RPC MDC、字段加密注册和默认 Nacos 刷新路径。需求完成范围以以下验收记录为准；配置消费者迁移、旧 RPC 接口和字段级 AAD 继续独立跟踪。

## 需求交付

| 需求 | 路径 | 状态 | 简述 |
|---|---|---|---|
| docs-quality-governance | docs/archive/v2.3.0/docs-quality-governance/ | completed | 文档迁移与 T8 约定本地验收完成，TD-043/TD-044 关闭 |
| log-json-masking | docs/archive/v2.3.0/log-json-masking/ | completed | 标量文本脱敏补全，保留复合值不保证完整脱敏的边界 |
| mongodb-driver-alignment | docs/archive/v2.3.0/mongodb-driver-alignment/ | completed_with_concerns | 驱动族对齐及一次性隔离消费者验收完成，保留已接受的快照豁免 |
| springdoc-boot-alignment | docs/archive/v2.3.0/springdoc-boot-alignment/ | completed | Springdoc 2.6.0 与 Boot 3.3 基线对齐，两种消费者验收完成 |
| runtime-debt-followup | docs/archive/v2.3.0/runtime-debt-followup/ | completed | 按已确认范围完成框架修复，消费者迁移等剩余工作留在活跃台账 |

## 独立交付

| 日期 | 类型 | 描述 | Commit | 状态 |
|---|---|---|---|---|
| 2026-09-30 | fix | 发布 Parent 属性覆盖 | baafc35588dc0d5ee9d936506f37d2a75850771d | completed |
| 2026-09-30 | fix | OkHttp JVM 制品坐标 | f30858b66479e22fa675979b36143cff2b997db1 | completed |
| 2026-10-02 | fix | 日志断言索引边界 | e601ce32a74e181ad18cdda9355a10e476c1e08e | completed |
| 2026-10-02 | fix | 测试清理顺序 | 40e61b8525ea61a65a09b32f5f35eb9c563246d8 | completed |
| 2026-10-02 | fix | 分页参数校正边界 | 45955272b4fa2b1278bab47cc71aee7f5f418f30 | completed |
| 2026-10-13 | fix | Redisson 适配器对齐 | 9de2cc054b7c355ca13643b4375d3db370d98b5f | completed |

## 风险接受

以下仅汇总 MongoDB 计划已有的 R-PLAN-SNAPSHOT-WAIVER 与 R-T2-SNAPSHOT-001，不新增风险接受；原接受人、时间和原文见[计划](./mongodb-driver-alignment/plan.md)。

| 交付项 | 状态 | Accepted By | Accepted At | 遗留风险 |
|---|---|---|---|---|
| mongodb-driver-alignment | accepted | 用户（原记录 User / YoungerYang-Y） | 2026-09-24 | 已豁免逐任务及最终快照比较，759 个忽略构建产物变化缺少完整基线副本；可见 Git 差异已核对 |

## 最终验证摘要

下表汇总已有交付证据；“无”仅指该交付约定范围内没有待接受的遗留风险，不表示取消兼容限制或清空独立技术债。MongoDB 不覆盖真实服务端/CRUD，日志不保证复合值完整脱敏，Springdoc 不覆盖业务自定义安全与代理，Nacos 不替代实际消费者迁移验收。临时报告路径保留为历史索引，本次没有重新读取或重跑其原始报告。

| 交付项 | 状态 | 验证命令及结果 | 遗留风险 | 风险接受 |
|---|---|---|---|---|
| docs-quality-governance | completed | 2026-10-03 `quality --mode full --source commit --commit 41d8b7aeffab862e58ba1fbf5dbdeac2575c3f96` 两次 exit 0，1072 单测/43 IT 零失败；[T8 与 27 场景](./docs-quality-governance/verification.md#2026-10-03-t8-收尾验收) | 无 | 无 |
| log-json-masking | completed | 2026-09-23 模块 `clean verify` 与 `quality --mode full --source worktree` exit 0，1062 单测/40 IT 零失败；[T4 证据](./log-json-masking/plan.md#t4-全局验收与技术债状态) | 无 | 无 |
| mongodb-driver-alignment | completed_with_concerns | 2026-09-24 `quality --mode full --source worktree` passed，三个隔离消费者模式、六份各 3 项测试的报告通过；[TD-040 证据](./mongodb-driver-alignment/plan.md#td-040-处置记录)；专项 fixture 后按要求删除 | 已豁免逐任务及最终快照比较，759 个忽略构建产物变化缺少完整基线副本；可见 Git 差异已核对 | 已接受：用户（原记录 User / YoungerYang-Y）；2026-09-24 |
| springdoc-boot-alignment | completed | 2026-09-30 两种消费者 HTTP 验收与 `quality --mode full --source worktree` exit 0、0 findings；后续 Dependabot 补丁单独核验 YAML/文档/diff；[验收记录](./springdoc-boot-alignment/plan.md#本地验收记录2026-09-30) | 无 | 无 |
| runtime-debt-followup | completed | 2026-10-09 `-Pci,nacos-live clean verify` 的 333 单测/17 IT 通过；常规 `quality --mode full --source worktree` exit 0，1126 单测/55 IT 通过；[真实服务与常规验收](./runtime-debt-followup/plan.md#第八阶段本地验收2026-10-09真实服务通过未发布)；后续门禁修订由上述发布运行的 full 覆盖 | 无 | 无 |
| 独立：发布 Parent 属性覆盖 | completed | 本地隔离消费者有效 POM、编译及 JaCoCo 正负例通过；[历史验收](#发布-parent-属性覆盖td-036已修复未发布) | 无 | 无 |
| 独立：OkHttp JVM 制品坐标 | completed | 本地隔离消费者编译及回环 HTTP 请求通过；[历史验收](#okhttp-jvm-制品坐标td-037已修复未发布) | 无 | 无 |
| 独立：日志断言索引边界 | completed | 2026-10-02 测试 Starter `clean verify` exit 0，LogTestUtilsTest 23 项通过；[历史验收](#日志断言索引边界td-045已修复未发布) | 无 | 无 |
| 独立：测试清理顺序 | completed | 2026-10-02 同轮 `clean verify` exit 0，BaseUnitTestTest 6 项通过；[历史验收](#测试清理顺序td-046已修复未发布) | 无 | 无 |
| 独立：分页参数校正边界 | completed | 2026-10-02 PageRequestTest/PageQueryTest 11 项通过，随后完整 `quality --mode full --source worktree` exit 0；[历史验收](#分页参数校正边界td-042已修复未发布) | 无 | 无 |
| 独立：Redisson 适配器对齐 | completed | 2026-10-14 发布运行的 `bash scripts/engineering.sh quality --mode full --source worktree` 成功；[适配与消费者迁移契约](#redisson-适配器对齐2026-10-12未发布)保留，未据通用门禁推定实际 Redisson 应用验收通过 | 无 | 无 |

## 遗留债务

[活跃技术债台账](../../active/tech-debt-tracker.md)继续保留 TD-013（RPC MDC 旧入口）、TD-016（字段/记录级 AAD）、TD-023（RPC Hook 旧 API）、TD-047（Nacos 消费者迁移）。本次只更新证据链接，不关闭这些条目、不新增风险接受，也不承诺后续版本。

## 归档前记录

TD-039 当前已按约定默认框架范围关闭，消费者迁移转入 TD-047，见[拆分与关闭记录](#td-039-框架修复关闭2026-10-09未发布)。详细历史验收统一保留在[运行时技术债后续计划](./runtime-debt-followup/plan.md)，其中各阶段的结论表示当时状态。

## Redisson 适配器对齐（2026-10-12，未发布）

Redisson 核心与 Starter 保持 3.52.0；BOM 排除 Starter 默认引入的 `redisson-spring-data-35`，新增同版本 `redisson-spring-data-33` 的管理，以匹配 Boot 3.3.13 / Spring Data Redis 3.3.13。纯 BOM 不会自动增加依赖，原来只声明 Starter 的消费者必须补上 `-33`；继承 Parent 与独立导入 BOM 的完整声明见 [BOM 接入说明](../../../mimir-boot-bom/README.md#redisson-与-boot-兼容边界)。显式固定其他版本或适配器的消费者须重新核验实际依赖树。

本条记录依赖与迁移契约，不代表远程 CI、正式发布或实际业务应用已验收；根 POM 仍为 `2.2.2-SNAPSHOT`。

## 运行时技术债后续修复（截至 2026-10-09，未发布）

Nacos 修复了默认 ConfigData 刷新中的源同步、删除键事件及失败恢复，保留显式刷新作用域接入与自动配置退让；关闭范围和消费者迁移见[TD-039 关闭记录](#td-039-框架修复关闭2026-10-09未发布)。

RPC Bridge 的 `extractScope` 恢复提取期间净修改的 MDC 键，并在异常时回滚；旧 `extract()` 及自定义 Span/ThreadLocal 生命周期仍需迁移，TD-013 保留。字段加密 Handler 改为专用注册键，保留显式映射及 v1/v2 行为；依赖旧版意外全局加密行为的应用需迁移，此修复不提供字段/记录级 AAD，TD-016 不关闭。MyBatis、RPC Core、Dubbo/Feign 相关修复见[早期实施与验收](./runtime-debt-followup/plan.md#本地实施与验收2026-10-03未发布)；TD-023 仍活跃。Java 门禁的当前支持范围见[测试规程](../../engineering/testing.md#结果如何判断)，逐阶段实现与验收保留在原计划。

以上均为本地实施记录，不代表远程 CI、Sonar、正式发布或发布后消费者验证；v2.3.0 尚未发布。

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

## 覆盖率与格式门禁（TD-043、TD-044，已修复，未发布）

TD-043 的历史根因是 JaCoCo 报告早于 Failsafe，遗漏 IT 覆盖；旧 Web 报告重生成时从 75.45% 升至 95.85% 仅为历史观察。现有 Parent 的 report/check 已在 verify 阶段读取同一追加 exec。TD-044 的历史根因是根 Spotless 无源配置继承到子模块；根配置已设置 `inherited=false`，源码模块扫描 main/test Java。

2026-10-03 实施提交 `41d8b7aeffab862e58ba1fbf5dbdeac2575c3f96` 补齐迁移后的有效模型矩阵和产物生命周期核验，拒绝配置绕过、缺失/陈旧报告及失败构建成功登记，按 XML 根计数独立核验原门槛。15 模块中四个 POM 模块有明确豁免，11 个 Java 模块格式/覆盖率 required；原指令 0.60、分支 0.50 与排除范围不降低。

同一实施提交的真实完整快照 full 首次 423.012 秒、缓存运行 358.636 秒均 exit 0、0 findings，1072 单元/43 IT 无失败、错误或跳过。真实 IT-only 覆盖、Spotless 格式负向、阈值边界证据按未变的 POM 复用；新模型、缺单模块报告、清理/中断、低覆盖分类与 CI 样式重检反例另行核验。27 场景映射及明确的人工语义替代见 [T8 收尾验收](./docs-quality-governance/verification.md#2026-10-03-t8-收尾验收)。关闭依据不是仅一次正常 full。

两项债务按约定本地范围关闭，台账表格、明细和旧锚点删除；历史记录由本节和验收记录承接。治理 RFC 的 verified 表示本地落地验证，不代表远程 CI、Sonar Quality Gate、制品发布或发布后消费者通过；这些外部状态未核实。本次不承诺新的发布版本，也不自动归档。

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

## TD-039 框架修复关闭（2026-10-09，未发布）

经用户确认，TD-039 的默认 ConfigData Nacos 框架修复按已验证范围关闭；详细实现与验收见[第九阶段](./runtime-debt-followup/plan.md#第九阶段td-039-框架关闭与消费者迁移拆分)。关闭范围受标准枚举 Nacos 源、成功加载资源归属及既定失败恢复契约约束；升级框架基线时须重新核验。真实服务验收仅覆盖指定本地 Nacos 场景，普通 Bean、缓存裸目标/字段快照及实际消费者迁移独立登记为 [TD-047](../../active/tech-debt-tracker.md#td-047-nacos-消费者迁移)，仍需按实际应用验收。

历史逐阶段实现与验收、包括真实 Nacos 与常规回归的不同执行范围，以及服务端时间戳导致的失败，均保留在[运行时技术债后续计划](./runtime-debt-followup/plan.md#第六阶段td-039-运行时同步与真实-nacos-验收)至[第九阶段](./runtime-debt-followup/plan.md#第九阶段td-039-框架关闭与消费者迁移拆分)。TD-039 关闭不表示所有属性源删除、任意并发读写事务隔离、CI、Sonar、生产网络、正式发布或发布后消费者已验证；根 POM 仍为 `2.2.2-SNAPSHOT`，v2.3.0 尚未发布。
