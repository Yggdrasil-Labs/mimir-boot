---
id: docs-quality-governance-migration
version: v2.3.0
status: in-progress
owner: 项目维护者
created: 2026-09-14
updated: 2026-09-20
---

# 文档体系迁移计划

本文档把[文档治理 RFC](../../../design-docs/arch-docs-quality-governance.md)转成可执行的目录、职责和验收映射。它只描述仓库内迁移，不修改外部 skill、插件缓存或 `.worktrees/fix-markdown-prepush` 中另一会话的未提交草稿。v2.2.1 保持在 `active/`，本计划不据此认定它已发布或应归档。

需求、设计和实施记录：

- [需求规格](./spec.md)
- [设计说明](./design.md)
- [实施计划](./plan.md)
- [治理 RFC](../../../design-docs/arch-docs-quality-governance.md)

## 迁移原则

- `README.md` 和模块 README 面向接入方，维护接入、配置、限制和可验证使用契约。
- `docs/` 服务维护者与 Agent，维护边界、修改位置、工程方法、状态和历史证据。
- 架构主题与 RFC 保留在 `design-docs/`，长期规范归入 `standards/`，操作指南归入 `engineering/`；普通修订不强制创建整套需求文档，本需求沿用原计划的独立验收报告要求。
- 当前终态文档直接改写为当前有效事实；过程证据只进入版本计划、发布记录或归档正文。
- 先切换仓库内引用，再删除已吸收的旧路径；不保留迁移页、兼容入口或重复正文。
- 根 README 保留核心版本徽章和稳定技术基线，不引用技术债台账或 TD；普通依赖及 Maven 插件版本以 POM 为准，不在全局文档重复维护。

## 现有长期文档映射

下表是当前仓库中长期文档的目标位置和章节归属。迁移阶段先切换引用并补齐新目标，再删除已被完全吸收的旧文件；不改写 `archive/` 中历史正文。

| 当前文件 | 目标位置 | 目标章节/职责 | 处理方式 |
|---|---|---|---|
| `AGENTS.md` | `AGENTS.md` | Agent 唯一入口、约束摘要、任务导航 | 保留；只更新到新目录和检查入口。 |
| `ARCHITECTURE.md` | `ARCHITECTURE.md` | 系统边界、分层、核心技术基线、依赖方向 | 保留；不重复工程操作规程。 |
| `docs/index.md` | `docs/index.md` | 文档地图、阅读路径、职责说明 | 保留；新增 `standards/`、`engineering/` 和工程工具入口。 |
| `docs/DOMAINS.md` | `docs/design-docs/arch-module-dependencies.md` | “模块职责与领域归属” | 将领域表和归属决策并入模块边界；迁移后删除旧独立事实源。 |
| `docs/SECURITY.md` | `docs/standards/security.md` | “安全长期约束” | 迁入 `standards/`；约束与操作分开，操作步骤链接 `engineering/` 或模块 README。 |
| `docs/RELIABILITY.md` | `docs/standards/reliability.md` | “可靠性长期约束” | 迁入 `standards/`；发布/测试操作步骤移至 `engineering/release.md`、`engineering/testing.md`。 |
| `docs/PRODUCT_SENSE.md` | `docs/standards/core-beliefs.md` | 产品原则、接入方价值和边界 | 稳定原则并入核心信条；根 README 不作本轮大改；删除旧正文。 |
| `docs/QUALITY_SCORE.md` | 当前问题由 `docs/active/tech-debt-tracker.md` 承接；检查证据由 `docs/engineering/testing.md` 说明 | 退役主观评分模型，保留具体风险 | A/B/C/D 评分及分数快照不再维护，不声称已迁入测试指南；原观察中的风险逐项映射现有 TD，Owner 与验收条件由台账维护。 |
| `docs/SONAR_QUALITY_DISCIPLINE.md` | `docs/engineering/testing.md` | Sonar 新代码门禁、处理流程、责任边界 | 将可执行门禁并入测试规程；长期原则链接 `standards/reliability.md`。 |
| `docs/design-docs/core-beliefs.md` | `docs/standards/core-beliefs.md` | 长期工程信条 | 迁入规范目录；只吸收稳定的产品原则，维护当前终态。 |
| `docs/design-docs/module-boundaries.md` | `docs/design-docs/arch-module-dependencies.md` | 模块边界、依赖方向、领域归属 | 统一架构文档命名并吸收 `DOMAINS.md`；旧路径删除，新增 Starter 仍按该文档验收。 |
| `docs/design-docs/documentation-governance.md` | `docs/standards/documentation-governance.md` | 文档分类、更新触发器、索引和新鲜度 | 迁入规范目录；与本 RFC 的具体迁移规则通过链接分工，不复制全文。 |
| `docs/design-docs/arch-technical-debt-remediation.md` | `docs/active/v2.2.1/technical-debt-remediation/rfc-doc-sync.md` | T9 文档同步的历史过程记录 | 迁入所属需求，由需求索引引用；保留原 id、历史正文、批准与验证结论，调整标题与相对链接；删除旧路径，不提前归档版本。 |
| `docs/design-docs/_template.md` | `docs/design-docs/_template.md` | 架构主题与 RFC 的结构参考 | 按主题选择章节；强调方案、关键流程与取舍，RFC 沿用原状态模型。 |
| `docs/active/index.md` | `docs/active/index.md` | 活跃版本导航 | 保留；增加 v2.3.0 入口时不移动 v2.2.1。 |
| `docs/active/tech-debt-tracker.md` | `docs/active/tech-debt-tracker.md` | 技术债唯一台账 | 保留；序号按数字 ID 升序，README 不引用。 |
| `docs/archive/index.md` | `docs/archive/index.md` | 版本归档导航 | 保留；明确归档状态与远程发布状态独立。 |
| `docs/product-specs/index.md` | `README.md`、`docs/index.md` | 使用入口与开发导航 | 链接分别归入对应入口，全部迁移后删除旧索引及空目录。 |
| `docs/product-specs/new-user-onboarding.md` | `README.md`、`docs/engineering/development.md` | 最小接入路径与成功标准 | 使用步骤归入 README；开发验收规则归入工程规程，仍有效的需求约束按所属需求保留；完成后删除旧正文。 |
| `docs/product-specs/starter-capabilities.md` | `README.md`、各模块 README、`docs/design-docs/arch-module-dependencies.md` | Starter 选择、使用契约与开发验收 | 能力概览归入根 README，配置和限制归入模块 README；开发边界及验收约束逐项迁移，完成后删除旧正文。 |

实施 T7 在本文件追加逐章节执行表，每行填写旧路径/锚点、内容类别、唯一目标、外部固定路径消费者、兼容入口、验收证据和处理结果。QUALITY_SCORE 的每条观察必须有对应行。技术债编号不属于文档质量门禁的校验对象；本矩阵也不承担编号事实源职责。

## T7 执行矩阵

本轮只核对仓库内引用，外部固定路径消费者标记为“未核实”，不据此推断外部没有使用方。用户已决定不保留兼容入口。下表的“内容核对”表示对照旧正文与目标职责的人工结果，不等于远端行为验证；格式、内部链接和导航检查的命令、范围与结果统一见[验收记录](./verification.md)。

| 旧路径/锚点 | 内容类别 | 唯一目标 | 外部固定路径消费者 | 兼容入口 | 验收证据 | 处理结果 |
|---|---|---|---|---|---|---|
| `docs/DOMAINS.md` | 领域归属 | `docs/design-docs/arch-module-dependencies.md#4-领域归属` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |
| `docs/SECURITY.md` | 安全约束 | `docs/standards/security.md` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |
| `docs/RELIABILITY.md` | 可靠性约束 | `docs/standards/reliability.md` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |
| `docs/PRODUCT_SENSE.md#产品原则` | 产品原则 | `docs/standards/core-beliefs.md#9-接入体验优先` | 未核实 | 不保留 | 内容核对 | 删除旧文件；根 README 未作主体改写。 |
| `docs/SONAR_QUALITY_DISCIPLINE.md` | Sonar 规则与操作规程 | `docs/standards/reliability.md`、`docs/engineering/testing.md` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |
| `docs/QUALITY_SCORE.md#2-建议维度` | 主观评分模型 | 本矩阵的退役决定 | 未核实 | 不保留 | 内容核对 | 不再维护 A/B/C/D 或分数快照；使用可复核的门禁报告、人工语义审查和 TD 证据，不恢复评分表。 |
| `docs/QUALITY_SCORE.md（当前观察：parent）` | 当前观察 | `docs/active/tech-debt-tracker.md#td-036-parent-flatten-属性覆盖`、`#td-043-jacoco-集成测试覆盖率`、`#td-044-spotless-子模块门禁` | 未核实 | 不保留 | 内容核对 | 仅保留仍活跃债项。 |
| `docs/QUALITY_SCORE.md（当前观察：bom）` | 当前观察 | `docs/active/tech-debt-tracker.md#td-037-okhttp-jvm-制品`、`#td-040-mongodb-驱动族`、`#td-041-springdoc-boot-兼容性` | 未核实 | 不保留 | 内容核对 | 仅保留仍活跃债项。 |
| `docs/QUALITY_SCORE.md（当前观察：common）` | 当前观察 | `docs/active/tech-debt-tracker.md#td-042-分页参数校验` | 未核实 | 不保留 | 内容核对 | 仅保留仍活跃债项。 |
| `docs/QUALITY_SCORE.md（当前观察：Starter）` | 当前观察 | `docs/active/tech-debt-tracker.md#td-013-rpc-mdc-scope`、`#td-016-字段加密-aad`、`#td-023-rpc-hook-legacy-api`、`#td-038-日志-json-脱敏`、`#td-039-nacos-解密覆盖层`、`#td-045-日志断言索引边界`、`#td-046-测试清理顺序` | 未核实 | 不保留 | 内容核对 | 仅保留仍活跃债项。 |
| `docs/QUALITY_SCORE.md（当前观察：文档体系）` | 当前观察 | `docs/index.md`、本迁移矩阵 | 未核实 | 不保留 | 内容核对 | 评分快照不再维护；以门禁和迁移证据为准。 |
| `docs/product-specs/index.md` | 使用入口 | 根 README、`docs/index.md` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |
| `docs/product-specs/new-user-onboarding.md` | 接入路径 | 根 README、模块 README、`docs/engineering/development.md` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |
| `docs/product-specs/starter-capabilities.md` | 能力概览 | 模块 README、`docs/design-docs/arch-module-dependencies.md` | 未核实 | 不保留 | 内容核对 | 删除旧文件。 |

### 内容承接说明

- 原核心信条与文档治理规则分别迁入 `standards/core-beliefs.md` 和 `standards/documentation-governance.md`；前者更新元信息，后者明确 Product Spec 的按需建立条件，旧路径删除。
- 原 Sonar 页的门禁原则、仓库既定基线、兼容修复边界、问题分类与责任已分别归入规范和测试指南；远端项目配置未在本轮读取，不把旧阈值描述成已核实的远端状态。
- 原 QUALITY_SCORE 中 Parent、BOM、Common、Starter 的风险仍有对应 TD 详情、Owner 和验收条件；仅退役主观评级，不删除风险或以门禁通过关闭债项。文档体系观察由当前索引、迁移核对和版本状态记录承接。
- 原上手页与能力页中的 Web、数据、RPC 组合提示由根 README 的“按场景组合提示”表承接；配置与限制仍链接各模块 README。生命周期按任务规模选择文档，不恢复强制完整文档链。
- 所有机器检查只证明其扫描范围；本表的内容承接由人工对照确认，Sonar、CI、发布等外部事实须另行核实。

## 模块 README 的边界

根 README 与下列模块 README 继续是使用契约的权威来源：

- `mimir-boot-parent/README.md`：继承、构建 profile 和质量入口。
- `mimir-boot-bom/README.md`：BOM 导入和版本来源。
- `mimir-boot-common/README.md`：公共模型、响应、分页和枚举。
- `mimir-boot-starters/*/README.md`：各 Starter 的依赖、自动配置、配置键、示例、限制和兼容入口。

模块 README 不复制完整架构原则、技术债编号或发布过程。新增/修改 Starter 时，`engineering/new-starter.md` 规定注册点和验收；模块 README 只保留接入方需要执行的部分。代码、POM 和测试发生变化时，先更新对应 README 的当前终态，再由 Agent 复核其他入口是否仍只保留摘要和链接。

## 迁移步骤

实施顺序以 plan 的 T1–T8 为准；以下为 T7 的内容迁移顺序，检查器和 hook 在 T7 前已建立。

1. 创建 `docs/engineering/` 四个规程页，分别接收开发、测试、发布和新增 Starter 的可执行要求；环境准备并入开发指南，任务导航由文档总索引承接。
2. 将安全、可靠性、核心信条与文档治理规则迁入 `standards/`，阅读入口合入 `docs/index.md`；把操作步骤和检查命令移到 engineering 对应章节，不另建规范索引。
3. 将 `DOMAINS.md` 的领域清单合并到 `arch-module-dependencies.md`，删除重复领域通信规则。
4. 将 `PRODUCT_SENSE.md` 拆分为稳定原则（`core-beliefs.md`）和接入方说明（根 README）。
5. 将 Sonar 长期纪律归入可靠性规范，执行与闭环归入测试指南；退役主观评分模型，仍成立的问题由现有技术债条目承接，不重复建立风险台账。
6. 更新 `AGENTS.md`、`docs/index.md`、设计索引和受影响 README 的链接，并删除产品规格页；当前单需求的导航合入版本索引，删除重复需求索引。每次链接迁移后先执行仓库内相对链接检查。
7. 切换仓库内全部入链后直接删除旧路径；不保留显式 anchor、迁移页或兼容入口，避免旧页与新页继续分叉。
8. 使用已实现的统一检查入口验证迁移：提交与推送 hook 对各自快照执行离线 quick；完整文档与 Maven 验收由 CI、发布或显式本地 full 执行。触发条件见[测试与质量](../../../engineering/testing.md)。安装 hook 必须是显式初始化动作，不覆盖既有未纳管 hook。
9. T7 对文档执行格式、链接、导航和自检，并人工核对迁移内容及技术债引用。技术债编号不属于自动门禁，不要求重复 TD 被检查器阻断；工具、Spotless、测试、覆盖率和报告的行为证据由 T8 按当前门禁范围核对，不在文档迁移中重复实现。
10. 由 Agent 逐项复核迁移后的职责、链接和实现事实，记录 T7 文档验收；仅在 T8 整体验收证据满足 RFC 条件后，将其从 `draft` 改为 `verified`。版本归档和提交/推送另行决策，不作为本计划自动副作用。

## 去重与人工职责验收

| 验收项 | 通过标准 | 责任 |
|---|---|---|
| 使用契约 | 接入依赖、配置键、默认值、限制和示例均能在对应 README 找到；原 product-specs 的有效内容均有迁移去向，旧目录无重复正文。 | 模块维护者 |
| 架构与规范 | 系统边界、模块职责、领域归属由架构文档维护；安全、可靠性与文档治理规则由 standards 维护，各自只有一个长期来源。 | 架构维护者 |
| 工程规程 | 构建、格式、测试、覆盖率、发布和新增 Starter 步骤集中在 `engineering/`，命令可在仓库内复现。 | 工程维护者 |
| 状态与历史 | `active/` 表示当前工作，`archive/` 保留历史语义；归档不被当作发布证明。 | 版本维护者 |
| 技术债 | 台账编号唯一且按数字 ID 升序；根 README 没有台账/TD 引用；关闭状态有代码、测试或发布证据。 | 任务 owner |
| 链接收敛 | 新入口的相对链接和锚点可解析；旧路径、迁移页和重复正文均不存在。 | 文档维护者 |
| 人工审查 | 代码/POM/工作流变化后的语义一致性由 Agent 对照源码复核，自动检查不能替代。 | 变更作者与审查者 |

## 检查命令与交付门禁

实施完成时至少执行以下检查；命令由最终工程规程和 CI 实际入口校准：

```bash
bash scripts/engineering.sh docs --root "$PWD" --mode full --self-test
git diff --check
```

链接/锚点和索引目录由上述文档 full 检查；技术债编号、风险承接和关键维护约定由人工复核，不新增自动编号校验。每项结果标记为 `通过`、`失败`、`执行错误`、`未执行` 或 `提示`；存在必需项的失败、执行错误或未执行时，不得标记迁移完成。只读源码复核应覆盖根 README 徽章和核心版本、模块 README 依赖与配置、POM 插件和工作流事实。

## 未解决决策与风险

- 用户已决定不保留迁移入口；仓库内引用完成切换后直接删除旧路径。外部固定路径消费者如出现，须在后续变更中单独评估。
- `pre-push` 已采用离线 quick；其通过不代表完整验收，适用 CI 的 full 结果仍须单独确认。
- 外部链接不作为每次提交的必需联网门禁；内部链接和锚点由文档 full 或 quality full 检查，quick 只检查适用格式。
- v2.2.1 当前“待归档”提示继续保留，直到单独核对远程发布、tag 和引用关系。
