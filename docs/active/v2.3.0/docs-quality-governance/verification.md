---
id: docs-quality-governance-verification
version: v2.3.0
status: in-progress
owner: 项目维护者
updated: 2026-10-02
---

# 文档治理验收记录

本记录区分 T7 文档验收与 T8 整体验收。2026-09-20 的修复针对人工审查发现的文档问题，未修改 Java、POM、hook、脚本或工作流，也未提交、推送、安装 hook 或执行发布。2026-10-02 的本地专项复核与修复另列于下方，不替代此前输入的记录。

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
