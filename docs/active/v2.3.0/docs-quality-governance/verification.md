---
id: docs-quality-governance-verification
version: v2.3.0
status: completed
owner: 项目维护者
updated: 2026-10-03
---

# 文档治理验收记录

本记录区分 T7 文档验收与 T8 整体验收。2026-09-20 的修复针对人工审查发现的文档问题，未修改 Java、POM、hook、脚本或工作流，也未提交、推送、安装 hook 或执行发布。2026-10-02 的本地专项复核与修复另列于下方，不替代此前输入的记录。

当前结论见 [2026-10-03 T8 收尾验收](#2026-10-03-t8-收尾验收)：本地约定范围完成，TD-043、TD-044 关闭，未发布。各历史节中的“待完成”仍表示当时状态。

## 输入与范围

- 输入：`feature/docs_quality_governance_2.3.0` 上基于 `16063e9c35f8ab42998f09e88bd08e4879683ddf` 的未提交工作区；包含新增文档，不能当作已提交快照。
- 迁移来源：上述 HEAD 的旧文档正文；目标和处理方式见[迁移矩阵](./migration.md)。
- 根 README：仅补三行场景组合提示及引导语，徽章、版本与原有主体保留。
- 自动检查覆盖格式、内部链接、导航和工具自检；语义承接通过逐项内容审查核对，二者不互相替代。

## 修复对应关系

| 发现 | 修复与核对方式 |
|---|---|
| 待执行要求与已确认决策冲突 | T7 删除兼容入口要求；T8 删除编号注册表操作；当前约束和场景映射采用离线 quick，历史工程记录保留原意 |
| Sonar 纪律承接不完整 | 可靠性规范维护唯一阈值与治理原则，测试指南维护问题分类、修复、责任和远端复验步骤；远端实际配置未读取 |
| 主观评分模型处理不明确 | 明确退役评分与快照；Parent/BOM/Common/Starter 的风险仍由原 TD 详情、Owner 和验收条件承接 |
| 接入场景提示遗漏 | README 补 Web、数据、RPC 三行按需组合，Processor 为可选；链接到实际模块 README |
| Product Spec 的定位未落文档 | 规范说明跨模块长期行为契约按需独立建规格；当前 README 承载契约，不创建空目录或新增必做任务 |
| 模块位置与文档元信息不准确 | Starter 路径补聚合目录前缀；核心信条更新维护日期，移除不能覆盖新增内容的旧 verified 日期 |

## T7 文档检查

2026-09-20 本轮修复后的文档验收通过。以下结果仅对应未提交工作区，不等于 T8 的提交快照完整验收。

| 检查 | 结果 | 证据与边界 |
|---|---|---|
| 文档 full 与自检 | 通过，exit 0 | 74 份 Markdown；格式、内部链接、导航、自检均 passed；报告 `/tmp/mimir-docs-governance-fixall.json` 保存 tree、工具版本、配置摘要与检查时间 |
| 文档治理结构检查 | 通过，exit 0 | 使用当前托管 Node 运行 docs-evolve 的 `scripts/lint-docs.mjs`；不修改外部技能或将其加入仓库门禁 |
| diff 空白检查 | 通过，exit 0 | `git diff --check` |
| 修复定向断言 | 通过，exit 0 | 临时 Node 断言核对当前 T7/T8 契约、评分退役、Product Spec 条件、Sonar 单一来源、模块路径、元信息及旧入口删除；未新增测试脚本 |
| 根 README 范围 | 通过，exit 0 | 直接读取 `git show HEAD:README.md`；移除新增组合提示后，与原文逐字节一致 |
| 非文档范围 | 通过，exit 0 | 与 HEAD 对比 Java、POM、脚本、工具实现、workflow 和 hook 均无变更 |
| 独立语义复核 | 通过 | 六类既有发现均闭合，无未解决 P0/P1/P2；正确性、完整性、可操作性、清晰性均 4/5 |

定向断言初次使用 Node 启动 Git 子进程时遭遇 `EPERM`，该次辅助命令失败，不记为通过；改为直接 Git 管道提供基线后，README 比较及独立内容断言均通过。以上记录补齐后重新运行文档 full 与结构检查，避免使用补记前的检查代替最终文档状态。

```bash
bash scripts/engineering.sh docs --root "$PWD" --mode full --self-test \
  --report /tmp/mimir-docs-governance-fixall.json
git diff --check
```

## Agent 冷启动验证与指南优化

2026-09-20 使用配置为 `gpt-5.6-luna / xhigh`、不继承会话历史的独立 Agent，从 `AGENTS.md` 自行导航，分别规划日志脱敏扩展、新增对象存储 Starter、继续文档治理、发布成功后仅补偿开发版本回写。每场景各一次；提示要求只读规划，不给标准答案，不执行构建、修改、提交或发布。

- 四个样本均能定位主要工作入口。文档治理样本区分了 T7 未提交与 T8 待验收；发布样本正确选择仅补偿 `update_dev_version`。
- 日志样本误将模块 `test` 描述为覆盖全部单元与集成测试，并串列多个检查入口而未说明是否需要重复执行。这是实际规划偏差，不能因导航成功就认定验收判断正确。
- 优化仅调整 `engineering/testing.md` 和 `engineering/development.md`：将命令覆盖范围与不能证明的结果并列，区分 Surefire / Failsafe 证据，提供交付结论格式；不改变门禁实现或增加测试脚本。
- 优化后用另一个全新上下文、相同模型配置的 Agent 重做日志场景，未提示上轮错误。该样本正确说明模块 `test` 验证单元测试、模块 `clean verify` 额外覆盖 Failsafe 集成测试，并引用新的验收结论格式；明确拟执行不等于已执行，单元测试不能代表完整验收。此次复测 1 次，无重试，正常完成；编写者随后对照 Parent POM 与指南核对上述结论。
- 自动复验使用文档 full 与自检（报告 `/tmp/mimir-docs-agent-guidance.json`）、文档结构检查和 `git diff --check`；报告中的 tree 与时间标识本次未提交输入，不复用上轮报告代表更新后的页面。

该实验验证阅读与规划，不验证实际功能实现；每场景单次结果不代表长期稳定性。禁止写操作来自实验提示，不能仅据“未修改文件”证明 Agent 会自主识别所有授权边界。本节不替代 T8 的完整工程验收。

## 架构命名与索引精简

2026-09-20 按用户要求统一命名与减少重复入口：

- `module-boundaries.md` 改为 `arch-module-dependencies.md`；原模块设计正文仅更新工程指南链接，依赖约束不变。设计索引与模板明确 `arch-` 用于架构主题和 RFC，前缀不代表批准或验证状态。
- 删除规范索引、工程索引和 v2.3.0 文档治理需求索引。规范与任务导航合入文档总索引，环境准备与工作约定合入开发指南，需求导航合入版本索引；全部入链已切换，不保留旧路径入口。
- `index.md` 从 14 个减至 11 个；文档 full 扫描范围从 74 份减至 71 份。保留设计库、版本集合以及既有历史需求索引，不机械改写历史记录。
- 独立只读调查确认这三个索引和旧模块文件名均非门禁强制路径。未改检查规则；文档 full、自检、文档结构检查与 `git diff --check` 通过，当前报告为 `/tmp/mimir-docs-index-slim.json`。
- 删除前的内容备份在 `/tmp/mimir-docs-index.98J2He/`，仅用于本地恢复，不作为仓库迁移入口或长期证据。此前冷启动实验使用精简前的路径，本轮未重新运行 Agent 冷启动实验，不能用其结果证明新导航的实际使用表现。

## 技术债文档同步 RFC 归位

2026-09-20 将仅服务 v2.2.1 T9 的同步授权记录从设计库迁入[归档需求](../../../archive/v2.2.1/technical-debt-remediation/rfc-doc-sync.md)，由版本归档索引承接导航，不保留旧路径入口。设计库继续保留跨版本架构设计与决策。

迁移前副本为 `/tmp/mimir-rfc-move.q0L024/original.md`。临时断言确认 frontmatter 和“背景与动机”至“参考”之前的全部历史正文逐字一致，包含批准字段与历史命令；仅调整标题、阅读说明和相对链接。v2.2.1 原计划、技术设计与根 README 的文件哈希未变。文档 full、自检、结构检查和 `git diff --check` 通过，报告 `/tmp/mimir-rfc-relocation.json` 记录最终文档输入；本次未重新验证历史工程结果。

## T8 剩余证据与边界

以下为 2026-09-20 的剩余边界；2026-10-02 已补充的部分证据及仍未完成事项见下一节。

- 本记录尚不代表 T8 完成；最终提交快照的完整质量门禁、当前场景断言映射及相关工程证据仍需按[实施计划](./plan.md#t8-独立验收与实施交付记录)核对。过时的 pre-push full 断言按计划记录替代依据，不重新实现。
- 本轮没有重跑 Java、发布消费者、签名或真实 hook 故障注入；也未确认远程 CI、Sonar 项目设置与公开制品状态。既有工程测试结果只作为历史证据，不改写失败报告或宣称本次远端通过。
- 不在文档迁移中关闭 TD-043/TD-044、不将 RFC 改为 verified；v2.2.1 的归档由本次独立收尾动作完成，相应状态与证据分开维护。
- 临时 JSON 报告用于本地复核，不保证长期保留；提交前应将最终证据摘要写入本记录，或保留对应 CI artifact 链接。

## 2026-10-02 专项复核与局部修复

输入为 `fix/technical-debt-small` 的 `45955272b4fa2b1278bab47cc71aee7f5f418f30`，tree 为 `27a8d154dd7eb95f14c20c91589cf46bdd8f1b86`。专项检查在项目内普通副本执行，故障注入不修改源工作树；真实 push 仅发送至本地 bare fixture。该批证据不包含后续报告核验器修复。

| 范围 | 本地结果 | 证据与限制 |
|---|---|---|
| B1/B3/B8 文档 | full、自检和 20 行迁移矩阵通过；orphan、格式与坏锚点、缺工具均按预期拒绝 | `/tmp/t8-docs-evidence/`；79 份 Markdown，78 份格式检查，CHANGELOG 按策略豁免。六份 v2.2.1 正文逐字不变；两份计划仅 TD 链接变化；历史 RFC 批准字段与主体不变。独立归档提交的发布依据未重新验证 |
| B2 初始化 | 无全局 Node PATH 首次 setup 26.074 秒、重复 14.072 秒；冲突及缺 JDK 拒绝 | `/tmp/mimir-t8-hooks-evidence/`；重复执行配置与源码不变。复用官方运行时归档缓存，不证明全冷缓存联网准备；首次 npm 沙箱 EPERM 失败单独保留 |
| B4/B5 真实 hook | 成功提交、新分支与 annotated tag 通过；错误索引、格式、缓存、非 HEAD 和多 ref 故障拒绝 | 同上，21 个独立场景；拒绝后 HEAD、暂存 blob/mode/stage、源码及本地 bare refs 不变。纯删除 51 毫秒、零 Maven；同 tree 同基线双 ref 仅一次 Spotless；所有 hook 零测试调用 |
| B6 Spotless | 11 个源码模块 effective POM 均含 main/test；11 个 main 格式反例和 common test 反例均拒绝 | `/tmp/t8-maven-evidence/`；其余十模块 test 范围只有配置证据。移除反例后 reactor Spotless 通过 |
| B6 JaCoCo | 仅由 IT 调用的方法进入最终 XML；阈值相等通过，略大阈值拒绝 | 同上；方法指令 covered=10/missed=0、分支 covered=2/missed=0；Surefire 61、Failsafe 1 均无失败或跳过。真实比例 0.70/0.50，阈值 0.7001/0.5001 分别拒绝；未改原始 0.60/0.50 门槛 |
| 测试与覆盖率失败 | 真实失败单测、失败 IT、skipped、低覆盖率门禁均返回 1 | 同上；skipped 构建返回 0、报告核验拒绝。低覆盖率由 Maven 拒绝，汇总将测试/覆盖率记为 not_run，报告核验本身 passed，不能声称报告定位了阈值失败 |

首次拒绝提交的 raw index 摘要变化保留于 hook 报告。独立无 hook 的 `git write-tree` 基线复现 cache-tree 元数据更新，暂存内容未变化；正常刷新 Git 缓存后的重跑 raw 字节也不变。未修改工具以补偿该元数据行为。

文档 B7 仅验证共享 docs 子入口在本地与 `CI=true GITHUB_ACTIONS=true RUN_SONAR=false` 下的 checks、findings、tree 和配置摘要一致；不代表远程 CI 或完整 full 双方通过。B3-S2 当前没有自动归档 warning 生成器，只有真实 results API 的 warning 保留与 active/archive 前后摘要不变断言，属于有限替代证据。

专项发现并局部修复两个实际问题：文档 CLI 在报告不可写时输出原始原因及恢复指引，仍返回 2；Java 核验器记录 Surefire/Failsafe XML 摘要并检测替换、新增或丢失，清理后使旧记录失效，使用现有 XML 解析器验证套件与根级覆盖计数。临时回归先复现五项错误放行，修复后十项正常/反例全部通过。修复后的真实全模块 Java 子检查返回 0，11 个源码模块记录 91 份单元和 9 份集成 XML；报告 `/tmp/mimir-report-fix/java-report.json`、清单 `build-manifest.json`。此 Java 验证输入包含报告核验器修改，启动后另行修改的文档 CLI 不属于该报告的配置快照。

Maven 专项实际使用 OpenJDK 17.0.2、Maven 3.9.16、Node 22.22.3；hook 及上述 Java 修复验证使用 Temurin 17.0.19。不同进程的 JDK 补丁版本不合并描述。

局部修复提交 `d8bede4cd484c0a573ce3a306e8fecf2de95a69c`、tree `22605d9328c8f33dd3a3c75a3b274e53e7e26fc9` 的完整快照门禁返回 0：文档、构建模型、发布契约、隔离消费者、临时签名、Java 测试与覆盖率均 passed，0 findings；默认 Sonar 为 not_applicable。报告 `/tmp/mimir-report-final-full-retry/quality-report.json` 标明 source=commit，UTC 起止为 09:29:34–09:40:54；Java 子清单记录 11 个源码模块、91 份单元与 9 份集成 XML。本节及设计、台账的后补文档不属于该提交快照，另外运行文档 full 与自检核验。

同一提交首次启动返回 2，报告 `/tmp/mimir-report-final-full/quality-report.json` 和日志保留 `--bootstrap-duration 必须是非负整数`，当次未进入检查。日志没有原始参数，负差值只是根据 Bash 整数生成路径的推断，系统时钟回拨未核实。`/tmp/mimir-bootstrap-timer-probe.log` 的跟踪复查传入耗时 0 且 quick 通过；未修改实现的重跑才取得上述 full 通过结果，没有绕过校验或覆盖失败证据。

T8 仍未完成，TD-043/TD-044 保持开放，RFC 不改为 verified。剩余至少包括：从 effective POM 生成完整期望矩阵、显式记录纯 POM/无源码豁免与 JaCoCo 排除；完整 27 场景的缺失/清理/中断/Sonar 断言映射；后续 T8 最终实施提交 full 首次与缓存耗时。远程 CI、Sonar、正式发布及发布后消费者未验证。临时证据可能被清理，本节摘要作为仓库内长期记录。

## 2026-10-03 T8 收尾验收

### 输入与修复范围

本轮在 `.worktrees/technical-debt-next`、`fix/technical-debt-next` 实施，基线为 `2e833cd14ba851e3dc0c50771efa374fe5be0929`。实施提交为 `41d8b7aeffab862e58ba1fbf5dbdeac2575c3f96`，tree 为 `8a34eb1c40eafca763322b64a445af0da8025335`，配置摘要为 `382572f2ae11c311eb4829ef167768d858ae32a14bd39ce63271126fe89675b9`。

- 有效模型来自实际 `-Pci help:effective-pom`，核对 reactor、源码与测试选择器、报告路径、JaCoCo 时序/门槛/排除及 Spotless 扫描范围。无法支持的选择器、路径或 JVM 参数明确拒绝，不回退固定目录或空矩阵；支持边界见[测试规程](../../../engineering/testing.md)及[工具说明](../../../../tools/engineering/README.md)。
- 失败构建只能登记诊断产物；清理先使旧成功记录失效，中断不登记成功清单。XML 根级计数独立核验阈值，原始阈值不降低。
- 独立代码审查发现 JaCoCo 配置属性、Spotless 跳过/限制文件、agent 消费和低覆盖率分类缺口，已逐项修复。最后的带引号 `argLine` 反例由 controller 复现并修复；最终 20 项参数断言通过，不把它写成审查者另行给出了最终 PASS。
- 工程工具承担跨版本的 Java 门禁职责，实际由 Java 子入口、完整 quality 与 CI 复用；一次性探针和故障 fixture 仅放临时文件或项目内忽略的普通副本。本轮无 Java 公共 API、POM、依赖、workflow 或发布结构变更。

### 完整快照与模块结果

对同一实施提交执行两次以下真实命令；报告路径分别为 `/tmp/mimir-next-final-full-first/quality-report.json` 和 `/tmp/mimir-next-final-full-cache/quality-report.json`。使用 Temurin 17.0.19、Maven 3.9.16、受管 Node 22.22.3；`RUN_SONAR=false`。

```bash
bash scripts/engineering.sh quality --mode full --source commit \
  --commit 41d8b7aeffab862e58ba1fbf5dbdeac2575c3f96 \
  --report <上述对应报告绝对路径>
```

| 运行 | UTC 起止 | 完整入口耗时 | 结果 |
|---|---|---|---|
| 本批首次 | 00:31:27.415–00:38:30.427 | 423.012 秒 | exit 0、passed、0 findings |
| 同提交缓存运行 | 00:39:30.422–00:45:29.058 | 358.636 秒 | exit 0、passed、0 findings |

两次 commit、tree、配置摘要一致；初始化、文档 full、自检、构建模型、发布契约、隔离消费者、临时签名、Java 质量、测试、覆盖率全部通过，Sonar 明确为 not_applicable。这是依赖缓存已具备的本地运行，不证明全冷缓存下载或远程 CI。

清单记录完整 15 模块：根、Parent、BOM、Starter 聚合四个纯 POM 模块各有无 Java 产物豁免；其余 11 模块格式/覆盖率均 required。实际选择 91 份单元、9 份 IT 源文件，对应 91/9 份 XML；Maven 分阶段汇总为 1072 项单元、43 项集成测试，失败、错误、跳过均为 0。11 模块均使用指令 ≥ 0.60、分支 ≥ 0.50，保留 `entity`、`dto`、`vo` 和 `Application.class` 排除；Spotless 包含 main/test Java，排除 target 与生成代码。

两次 `build-manifest.json` 均有成功 buildStatus、cleanedAt、recordedAt 和产物摘要，没有诊断时间戳；相对路径、选择器、豁免、门槛和扫描范围相同。effective XML 的绝对执行目录每次不同，不能要求原始 XML 摘要相等。报告、日志、有效模型及 manifest 保留于报告目录；提交快照清理后原始测试 XML 不再保留，测试数量由实际 Maven 分阶段日志复核。另一个本地 CI 样式普通副本保留了原始 XML。

### 专项正反例

| 范围 | 实际结果 | 证据与限制 |
|---|---|---|
| 有效模型 | 16 项通过，exit 0 | `/tmp/java-build-model-probe.mjs`；实际模型的完整/缺失模块、选择/路径、跳过、报告时序、exec 追加、排除和格式范围反例；定制目录正例 |
| 参数与阈值 | 20 项通过，exit 0 | `/tmp/mimir-next-parameters-green.log`；先复现错误接受 haltOnFailure、Spotless 限制及 `argLine`，修复后拒绝；独立 agent token 正例和根 XML 阈值边界 |
| 报告生命周期 | 12 项通过，exit 0 | `/tmp/mimir-next-reports-probe.mjs`；一个 required 模块缺报告而其余完整、XML/exec 丢失或摘要替换、新增、skipped、畸形 XML、仅嵌套计数、清理失败、旧记录失效、缺 manifest。临时 exec 字节仅证明摘要契约 |
| 真实 JUnit 失败 | CLI exit 1，测试 failed，报告 verify failed | `/tmp/mimir-next-lifecycle-evidence/summary.json`；UTC 00:09:38–00:09:46；只登记 diagnosticsRecordedAt，没有 recordedAt |
| 真实构建中断 | CLI exit 2，build/cancelled error，测试及覆盖率 not_run | 同上；UTC 00:09:46–00:09:52；在 JUnit 正在执行时 SIGTERM，中断 Maven 进程组；无成功或诊断登记时间戳 |
| 真实低覆盖率 | Maven/CLI exit 1，coverage failed，明确两个阈值 finding | `/tmp/mimir-next-low-coverage-evidence/verification.json`；只提高 fixture 门槛为 1/1，源 POM、Parent/BOM、工具指纹匹配。指令 7/24、分支 1/4，实际 1 个测试通过。失败构建后测试汇总仍保守为 not_run，不能据此说测试未执行；没有 recordedAt |
| 已启用 Sonar 失败 | 实际 scanner/CLI exit 1，required=true、failed，整体失败 | `/tmp/mimir-next-sonar-evidence/verification.json`；真实 scanner 对回环 503 服务请求版本端点失败；包装器只替换固定 host 参数。未进入远端 Quality Gate，不证明其配置或结果 |
| CI 样式完整反例 | actual full exit 1，仅坏锚点 finding，其余必需阶段通过 | `/tmp/mimir-next-ci-evidence/summary.json`；UTC 00:30:46–00:38:00，434.100 秒。配置摘要与上述实现一致；预置假成功报告被新 runId 替换，源码前后摘要相同。使用 `CI=true GITHUB_ACTIONS=true RUN_SONAR=false`，不是远端 CI |

临时辅助命令的首次路径漏参失败、`argLine` 红阶段、Sonar 外层 harness 初次误报 exit 0 均不计为通过；真实子报告及修正后的外层退出码分别核实，未覆盖原失败日志。低覆盖率新证据取代旧汇总分类的证明范围。

### 27 场景映射

复用来源为 `45955272b4fa2b1278bab47cc71aee7f5f418f30`、tree `27a8d154dd7eb95f14c20c91589cf46bdd8f1b86` 的三组专项目录。本轮逐项比较该提交到实施提交：hooks、setup、快照、runner、results、POM、锁文件和 workflow 未变，相关机制证据可复用；文档 CLI 的 stderr 修复和新的 Java 模型/报告核验以新证据覆盖。旧全量测试计数和旧 full 不作为新提交结果。

| Scenario | 当前证据与结论 | 范围/替代依据 |
|---|---|---|
| B1-S1 | T7 四类任务入口人工走读及当前文档 full | 静态导航；旧冷启动 Agent 不代表精简后实验 |
| B1-S2 | T7 README 接入语义、迁移断言与当前 full | 根 README 无 TD，主体与徽章约定保留 |
| B1-S3 | 真实 orphan exit 1，重复职责人工内容对照 | `/tmp/t8-docs-evidence/`；不声称自动检测语义重复 |
| B2-S1 | 无全局 Node PATH 的真实 setup exit 0、hook 可执行 | 首次 26.074 秒，使用官方归档缓存 |
| B2-S2 | 重复及多 worktree setup 幂等 | `/tmp/mimir-t8-hooks-evidence/`；重复 14.072 秒，状态不变 |
| B2-S3 | 五类配置/hook 冲突、缺 JDK exit 2，失败不写配置 | 真实下载 EPERM 单独保留；缺/坏缓存由 quick 拒绝，setup 可准备缓存 |
| B3-S1 | 本批两次完整快照 docs-full/self-test 通过 | 新文档输入以收尾文档检查另证 |
| B3-S2 | 指定人工语义替代：人工核对归档/发布、检查只读 | 无自动归档 warning 生成器；真实 results API 只证明 warning 不升级失败。实际 full 坏锚点后仍执行其余检查，不冒称归档提示自动 E2E |
| B3-S3 | 同时真实格式/锚点错误 exit 1，导航仍 passed | 历史 docs 本地与 CI 样式对照及本轮 full 锚点反例 |
| B3-S4 | 缺受管工具 error、overall 2；不可写报告诊断修复 | links/navigation 独立 passed，不是其依赖 not_run；不可写 stderr 由前批局部回归验证 |
| B4-S1 | 真实 Git commit 与真实 Spotless 成功，零测试调用 | 历史 hook fixture；本轮实施提交也由真实 quick hook 通过 |
| B4-S2 | 暂存坏格式、工作树已修仍拒绝 commit | blob/mode/stage 不变；首次 cache-tree 变化及无 hook 对照保留，不虚报所有 raw 字节恒定 |
| B4-S3 | 格式/缺缓存/坏缓存均拒绝，暂存内容不变 | 历史三类实际 commit 反例 |
| B5-S1 | 实际待推送提交 quick 通过才更新 bare ref | 批准的日常 quick 替代旧 push full；只向本地 fixture 推送 |
| B5-S2 | 非 HEAD 错误提交拒绝，bare ref 不变 | 历史真实 push 反例 |
| B5-S3 | 多 ref 一坏整体拒绝，新分支/tag 成功，删除零构建 | 同 tree 同基线双 ref 一次 Spotless；纯删除 51 毫秒，不外推不同基线去重 |
| B5-S4 | quick 缺工具拒绝 push，零测试 | 编译失败归 full；新真实 JUnit 失败阻断 Java/full 依赖路径，不恢复 push 编译 |
| B6-S1 | 新提交 1072 单元/43 IT 通过；IT-only 方法入 XML | Parent 未变，复用真实方法指令 10/分支 2 覆盖；旧单模块计数不代表当前全仓库 |
| B6-S2 | 实际 15 模块矩阵、4 个 POM 豁免，阈值边界正反例 | 历史实际 0.70/0.50 相等通过、略大拒绝；新模型/参数/低覆盖专项 |
| B6-S3 | 真实 Spotless、失败单测/IT、skipped、低覆盖拒绝 | 11 模块 main 及 common test 有真实格式反例，其余 test 范围有有效模型证据；新低覆盖分类明确 failed |
| B6-S4 | 12 项报告反例及真实失败/中断不登记成功产物 | clean 失败、旧 record 失效、缺一个 required 模块、缺 manifest、新增/替换/丢失摘要 |
| B7-S1 | 共享 full 清单/CI 调用链未变，配置摘要一致 | 历史同 fixture docs 本地/CI 样式结果一致；本批真实 CI 样式 full 拒绝。组合证据，不冒称远端 full 双跑 |
| B7-S2 | 无凭据明确 NA；已启用真实 scanner 503 失败不可降级 | 本地分类，未达远端 Quality Gate；不声称远端 Sonar 已验收 |
| B7-S3 | 预置假 passed 仍实际重跑 full，坏锚点拒绝 | 新 runId/报告替换，其他阶段继续通过，非远端 CI |
| B8-S1 | 20 行迁移矩阵、目标/旧路径断言和人工语义对照 | 新链接由收尾 docs full 再核验 |
| B8-S2 | 六份历史正文不变，两份计划仅 TD 链接变化 | 历史 RFC 元信息/主体不变；本轮不改 archive 正文 |
| B8-S3 | 指定人工语义证据：归档与发布独立、不自动归档 | v2.2.1 归档归于既有独立动作，外部发布依据未重验；v2.3.0 未发布 |

### 关闭决定与剩余边界

T8 AC1 的 27 场景均有上述运行证据或明确指定人工语义依据；AC2 的最终实施提交 full 首次及缓存运行通过；AC3 按上述边界关闭 TD-043、TD-044，记录迁入 [release](../release.md#覆盖率与格式门禁td-043td-044已修复未发布)，旧台账锚点及入链移除，治理 RFC 更新为本地 verified。历史失败与当时未完成结论保留。技术债关闭不代表制品发布。

后补仅同步文档的提交不属于 `41d8b7a` 的 tree；其文档 full、自检、链接和 diff 单独验证，不声称该文档提交又执行过完整 Maven full。临时路径可能被清理，因此本节保留输入、计数、退出码、时间、指纹及限制作为仓库内记录。

后补文档首轮检查于 UTC 00:55:38–00:55:39 完成：`bash scripts/engineering.sh docs --root "$PWD" --mode full --self-test --report /tmp/mimir-next-closure-docs.json` exit 0，78 份格式检查、链接、导航及自检均通过；`git diff --check` exit 0。结构 lint 返回 1，仅报告基线已有的日志规格 status 与 MongoDB 计划 HTML placeholder 两项，两个文件与 `2e833cd` 均无 diff，本次未引入结构失败；不称全库结构 lint 通过。本段及最后措辞同步后的文档检查另存 `/tmp/mimir-next-closure-docs-final.json`。

远程 CI、Sonar 项目设置/Quality Gate、正式发布及发布后消费者均未核实。TD-013、TD-016、TD-023、TD-039 仍保留原处置与兼容边界；本轮只读调查不能替代其后续迁移设计和验收。没有自动归档新版本、部署、对外推送或清理其他工作树。
