---
id: docs-quality-governance-migration
version: v2.3.0
status: not-started
owner: 项目维护者
created: 2026-09-14
---

# 文档体系迁移计划

本文档把[文档治理 RFC](../../../design-docs/arch-docs-quality-governance.md)转成可执行的目录、职责和验收映射。它只描述仓库内迁移，不修改外部 skill、插件缓存或 `.worktrees/fix-markdown-prepush` 中另一会话的未提交草稿。v2.2.1 保持在 `active/`，本计划不据此认定它已发布或应归档。

需求、设计和实施记录：

- [需求规格](./spec.md)
- [设计说明](./design.md)
- [实施计划](./plan.md)
- [治理 RFC](../../../design-docs/arch-docs-quality-governance.md)

## 迁移原则

- `README.md` 和模块 README 面向人，维护接入、配置、限制和可验证使用契约。
- `docs/` 面向 Agent，维护边界、修改位置、工程方法、状态和历史证据。
- 当前终态文档直接改写为当前有效事实；过程证据只进入版本计划、发布记录或归档正文。
- 旧锚点保留显式 anchor 或迁移映射；迁移完成后删除重复正文，避免双重维护。
- 根 README 保留核心版本徽章和稳定技术基线，不引用技术债台账或 TD；普通依赖及 Maven 插件版本以 POM 为准，不在全局文档重复维护。

## 现有长期文档映射

下表是当前仓库中长期文档的目标位置和章节归属。迁移阶段先切换引用并补齐新目标，再删除已被完全吸收的旧文件；不改写 `archive/` 中历史正文。

| 当前文件 | 目标位置 | 目标章节/职责 | 处理方式 |
|---|---|---|---|
| `AGENTS.md` | `AGENTS.md` | Agent 唯一入口、约束摘要、任务导航 | 保留；只更新到新目录和检查入口。 |
| `ARCHITECTURE.md` | `ARCHITECTURE.md` | 系统边界、分层、核心技术基线、依赖方向 | 保留；不重复工程操作规程。 |
| `docs/index.md` | `docs/index.md` | Agent 文档地图、阅读路径、职责说明 | 保留；新增 `engineering/` 和迁移映射入口。 |
| `docs/DOMAINS.md` | `docs/design-docs/module-boundaries.md` | “模块职责与领域归属” | 将领域表和归属决策并入模块边界；迁移后删除旧独立事实源。 |
| `docs/SECURITY.md` | `docs/design-docs/security.md` | “安全长期约束” | 迁入小写 design-doc；约束与操作分开，操作步骤链接 `engineering/` 或模块 README。 |
| `docs/RELIABILITY.md` | `docs/design-docs/reliability.md` | “可靠性长期约束” | 迁入小写 design-doc；发布/测试操作步骤移至 `engineering/release.md`、`engineering/testing.md`。 |
| `docs/PRODUCT_SENSE.md` | `docs/design-docs/core-beliefs.md`、`README.md` | 产品原则、接入方价值和边界 | 稳定原则并入核心信条；接入方可见内容保留在 README；不保留第三份重复正文。 |
| `docs/QUALITY_SCORE.md` | `docs/engineering/testing.md`；当前观察进入 `docs/active/tech-debt-tracker.md` | 评分维度与质量观察方法 | 评分规则并入测试/质量门禁；当前仍成立的问题逐项对应技术债条目并补齐 owner/复核条件；已失效观察在本矩阵逐项记录不迁移依据，不静默删除。 |
| `docs/SONAR_QUALITY_DISCIPLINE.md` | `docs/engineering/testing.md` | Sonar 新代码门禁、处理流程、责任边界 | 将可执行门禁并入测试规程；长期原则链接 `design-docs/reliability.md`。 |
| `docs/design-docs/core-beliefs.md` | `docs/design-docs/core-beliefs.md` | 长期工程信条 | 保留；只吸收稳定的产品原则，维护当前终态。 |
| `docs/design-docs/module-boundaries.md` | `docs/design-docs/module-boundaries.md` | 模块边界、依赖方向、领域归属 | 保留并吸收 `DOMAINS.md`；新增 Starter 仍按该文档验收。 |
| `docs/design-docs/documentation-governance.md` | `docs/design-docs/documentation-governance.md` | 文档分类、更新触发器、索引和新鲜度 | 保留作为治理基线；与本 RFC 的具体迁移规则通过链接分工，不复制全文。 |
| `docs/design-docs/arch-technical-debt-remediation.md` | `docs/design-docs/arch-technical-debt-remediation.md` | 已有技术债同步 RFC | 保留历史语义和状态；本轮不改其批准或验证结论。 |
| `docs/design-docs/_template.md` | `docs/design-docs/_template.md` | 新 design-doc 模板 | 保留；补充新 RFC 的索引要求由治理文档说明。 |
| `docs/active/index.md` | `docs/active/index.md` | 活跃版本导航 | 保留；增加 v2.3.0 入口时不移动 v2.2.1。 |
| `docs/active/tech-debt-tracker.md` | `docs/active/tech-debt-tracker.md` | 技术债唯一台账 | 保留；序号按数字 ID 升序，README 不引用。 |
| `docs/archive/index.md` | `docs/archive/index.md` | 版本归档导航 | 保留；明确归档状态与远程发布状态独立。 |
| `docs/product-specs/index.md` | `README.md`、`docs/index.md` | 使用入口与开发导航 | 链接分别归入对应入口，全部迁移后删除旧索引及空目录。 |
| `docs/product-specs/new-user-onboarding.md` | `README.md`、`docs/engineering/development.md` | 最小接入路径与成功标准 | 使用步骤归入 README；开发验收规则归入工程规程，仍有效的需求约束按所属需求保留；完成后删除旧正文。 |
| `docs/product-specs/starter-capabilities.md` | `README.md`、各模块 README、`docs/design-docs/module-boundaries.md` | Starter 选择、使用契约与开发验收 | 能力概览归入根 README，配置和限制归入模块 README；开发边界及验收约束逐项迁移，完成后删除旧正文。 |

实施 T7 在本文件追加逐章节执行表，每行必须填写：旧路径/锚点、内容类别、唯一目标文件/锚点、外部固定路径消费者（没有则填 none）、兼容入口、验收证据和处理结果。QUALITY_SCORE 的每条观察必须有对应行。技术债编号不属于文档质量门禁的校验对象；本矩阵也不承担编号事实源职责。

## 模块 README 的边界

根 README 与下列模块 README 继续是使用契约的权威来源：

- `mimir-boot-parent/README.md`：继承、构建 profile 和质量入口。
- `mimir-boot-bom/README.md`：BOM 导入和版本来源。
- `mimir-boot-common/README.md`：公共模型、响应、分页和枚举。
- `mimir-boot-starters/*/README.md`：各 Starter 的依赖、自动配置、配置键、示例、限制和兼容入口。

模块 README 不复制完整架构原则、技术债编号或发布过程。新增/修改 Starter 时，`engineering/new-starter.md` 规定注册点和验收；模块 README 只保留接入方需要执行的部分。代码、POM 和测试发生变化时，先更新对应 README 的当前终态，再由 Agent 复核其他入口是否仍只保留摘要和链接。

## 迁移步骤

实施顺序以 plan 的 T1–T8 为准；以下为 T7 的内容迁移顺序，检查器和 hook 在 T7 前已建立。

1. 创建 `docs/engineering/` 共五个有实际内容的页面（一个索引、四个规程），分别接收开发、测试、发布和新增 Starter 的可执行要求。
2. 将 `SECURITY.md`、`RELIABILITY.md` 的长期约束迁入小写 design-doc；把操作步骤和检查命令移到 engineering 对应章节。
3. 将 `DOMAINS.md` 的领域清单合并到 `module-boundaries.md`，删除重复领域通信规则。
4. 将 `PRODUCT_SENSE.md` 拆分为稳定原则（`core-beliefs.md`）和接入方说明（根 README）。
5. 将 Sonar 纪律与质量评分规则合并到 `engineering/testing.md`；当前仍成立的观察统一登记到技术债台账，已失效观察在本矩阵记录依据。
6. 更新 `AGENTS.md`、`docs/index.md`、设计索引、即将移除的产品规格索引及受影响 README 的链接。每次链接迁移后先执行仓库内相对链接检查。
7. 为稳定旧锚点保留显式 anchor 或在旧路径保留短期迁移映射页；完成一个迁移批次后删除重复正文，避免旧页与新页继续分叉。
8. 使用前置任务已经实现的统一检查入口验证迁移：Maven profile 托管 Node/npm 检查器；`pre-commit` 检查暂存内容；`pre-push` 执行完整文档和 Maven 质量门禁；GitHub CI 调用同一脚本。安装 hook 必须是显式初始化动作，不覆盖既有未纳管 hook。
9. 用真实失败样例验证 Markdown 列表编号、断链、重复 TD、工具缺失、Spotless 扫描范围、测试失败、覆盖率不足和报告缺失均能阻断正确阶段；失败后其他独立检查仍应报告结果。
10. 由 Agent 逐项复核迁移后的职责、链接和源码事实，再将 RFC 状态从 `draft` 改为 `verified`。版本归档和提交/推送另行决策，不作为本计划自动副作用。

## 去重与人工职责验收

| 验收项 | 通过标准 | 责任 |
|---|---|---|
| 使用契约 | 接入依赖、配置键、默认值、限制和示例均能在对应 README 找到；原 product-specs 的有效内容均有迁移去向，旧目录无重复正文。 | 模块维护者 |
| 架构契约 | 系统边界、模块职责、领域归属、安全与可靠性规则分别只有一个长期来源。 | 架构维护者 |
| 工程规程 | 构建、格式、测试、覆盖率、发布和新增 Starter 步骤集中在 `engineering/`，命令可在仓库内复现。 | 工程维护者 |
| 状态与历史 | `active/` 表示当前工作，`archive/` 保留历史语义；归档不被当作发布证明。 | 版本维护者 |
| 技术债 | 台账编号唯一且按数字 ID 升序；根 README 没有台账/TD 引用；关闭状态有代码、测试或发布证据。 | 任务 owner |
| 链接兼容 | 新旧入口的相对链接和稳定锚点可解析；旧正文不与新正文长期重复。 | 文档维护者 |
| 人工审查 | 代码/POM/工作流变化后的语义一致性由 Agent 对照源码复核，自动检查不能替代。 | 变更作者与审查者 |

## 检查命令与交付门禁

实施完成时至少执行以下检查；命令由最终工程规程和 CI 实际入口校准：

```bash
bash scripts/quality-check.sh --mode full --source worktree
git diff --check
```

此外必须执行仓库内链接/锚点、索引目录、技术债编号和关键维护约定检查。每项结果标记为 `通过`、`失败`、`执行错误`、`未执行` 或 `提示`；存在必需项的失败、执行错误或未执行时，不得标记迁移完成。只读源码复核应覆盖根 README 徽章和核心版本、模块 README 依赖与配置、POM 插件和工作流事实。

## 未解决决策与风险

- 旧顶层页面是直接删除、保留迁移映射页，还是在一个版本周期后清理，由迁移批次结合外部链接使用情况决定。
- `pre-push` 的全量 Maven 门禁耗时需要记录实际数据，再决定是否为纯文档变更增加安全的快速路径；任何快速路径不能跳过 CI。
- 外部链接不作为每次提交的必需联网门禁，避免网络波动阻断本地开发；仓库内链接和锚点在推送前及 CI 的完整模式中检查，提交前只检查格式。
- v2.2.1 当前“待归档”提示继续保留，直到单独核对远程发布、tag 和引用关系。
