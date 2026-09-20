---
id: arch-docs-quality-governance
status: draft
owner: 项目维护者
tags: [architecture, documentation, agent, quality]
created: 2026-09-14
updated: 2026-09-20
---

# 文档体系与质量门禁治理 RFC

## 状态与范围

本文档记录已获用户同意方向的治理方案。当前迁移与最终验收尚未完成，状态保留为 `draft`；批准方向与实施完成是两个不同事实。它只约束仓库内文档的职责、迁移边界和质量检查方式，不修改 Java 代码、公开 API、依赖方向、发布结构或外部技能目录。

本 RFC 的需求、设计和执行记录分别见：

- [需求规格](../active/v2.3.0/docs-quality-governance/spec.md)
- [设计说明](../active/v2.3.0/docs-quality-governance/design.md)
- [实施计划](../active/v2.3.0/docs-quality-governance/plan.md)
- [文档迁移计划](../active/v2.3.0/docs-quality-governance/migration.md)

## 背景与动机

Mimir Boot 的文档同时服务接入方、仓库维护者和 Agent。相同能力在根 README、模块 README、产品规格和长期约束中重复描述，导致版本、默认行为、模块职责和接入前提容易漂移。已有 Markdown、链接和 Maven 质量检查也需要统一入口，使提交前、推送前和 GitHub CI 的结果可比较、可复核。

本 RFC 按读者任务划分文档，人和 Agent 共用同一套内容：

- `README.md` 与模块 README 维护接入方可执行的使用契约。
- `docs/` 服务维护者和 Agent，包括架构边界、修改位置、验证要求、任务状态和历史证据。
- 代码、POM、工作流和测试是实现事实来源；文档与实现冲突时回写文档，不用文档掩盖实现问题。

## 设计原则

1. **单一事实来源。** 一个易变事实只在一个职责正确的文档中维护，其他入口只保留必要摘要和链接。
2. **使用与开发分离。** README 说明如何接入、配置、验证和处理限制；`docs/` 分别说明架构设计、必须遵守的规范和工程操作方法。
3. **设计、规范与执行方法分离。** `design-docs/` 积累全局架构设计与跨模块设计决策；`standards/` 保存安全、可靠性、核心信条和文档治理规则；`engineering/` 保存可执行的开发、测试、发布和新增 Starter 方法。设计说明如何满足规范，不复制规范全文。文档数量按任务复杂度选择，局部修订不强制建立完整需求文档链。
4. **维护当前终态。** 全局说明、模块 README 和产品能力文档直接维护当前有效状态，不追加“某日复核发现”式过程记录。日期只出现在元数据、计划、发布记录和历史证据中。
5. **历史语义不重写。** `active/` 中的当前需求保留过程证据；`archive/` 正文保留当时语义，只修复失效链接、路径和必要格式。版本归档不等于制品发布，二者必须分别核验。
6. **路径收敛。** 仓库内引用切换到唯一目标后删除旧路径，不保留迁移映射或重复正文；外部固定路径的兼容需求须另行明确。
7. **版本信息适度维护。** 根 README 保留 Java、Spring Boot、Maven、许可证和 Sonar 徽章，以及稳定核心版本描述；由 Dependabot 频繁升级的普通依赖和 Maven 插件只写名称、用途和 POM 来源，不在多份文档重复版本号。
8. **内部追踪与公开入口分离。** 根 README 不引用技术债台账或 TD 编号；Agent 通过 `AGENTS.md`、`docs/index.md` 和 `active/` 导航到内部状态。
9. **先报告再判断。** 检查器必须区分通过、失败、执行错误、未执行和提示；未执行不能被汇总为通过。

## 目标目录与职责

```text
docs/
├── design-docs/       # 长期架构设计、设计取舍与架构 RFC
├── standards/         # 核心信条、安全、可靠性与文档治理规范
├── engineering/       # 维护者和 Agent 的操作指南
├── active/            # 当前版本、需求、技术债和过程证据
└── archive/           # 已结束版本与历史过程证据
```

现有 `product-specs/` 的使用说明并入 README，仍有效的开发验收规则迁入对应设计或需求文档；全部吸收后删除原目录，不保留第二套使用入口。这不禁止未来按需建立长期行为规格：仅当跨模块行为契约超出 README 适载范围时启用，具体条件由[文档治理规范](../standards/documentation-governance.md#1-内容归属)维护，不预建空目录。

目标 `engineering/` 仅创建有实际内容的页面：

- `development.md`：环境准备、模块定位、构建入口、修改位置和日常开发流程。
- `testing.md`：测试选择、Spotless、覆盖率、报告完整性和质量门禁。
- `release.md`：发布前置条件、发布步骤、结果判定和失败补偿。
- `new-starter.md`：新增 Starter 的注册点、边界、README 和验收清单。

工程任务导航和规范阅读入口集中在 `docs/index.md`，不在 `engineering/`、`standards/` 重复创建索引；单需求版本由版本页直达需求文档。设计库和版本集合保留独立索引，历史需求入口不作机械清理。

`design-docs/` 按架构主题持续积累设计，架构概览由根 `ARCHITECTURE.md` 承接，模块划分与依赖设计保留在本目录。主题文档维护当前架构，架构 RFC 记录跨版本设计的背景、备选方案与取舍；仅服务单次需求的同步授权和验收记录归入需求目录，保留原批准与验证结论。安全性、可靠性与文档治理的规则正文迁入 `standards/`；文档体系的分层设计及其取舍仍由本 RFC 记录，两者职责不同。

需求级的规格、设计、任务和验收记录放入 `active/`，完成后随版本归档。事实修订直接更新相关页面；实质性架构约束变更先通过 RFC 评审。验收通常记在计划中，复杂任务才单列 `verification.md`；本治理需求沿用原计划的独立验收报告要求。具体映射以[迁移计划](../active/v2.3.0/docs-quality-governance/migration.md)为准。

## 质量检查模型

检查逻辑由仓库维护，完整验收统一使用 `bash scripts/engineering.sh quality --mode full --source worktree`。Maven 的 `docs-check` profile 固定并准备 Node/npm，`tools/engineering/` 统一管理文档、质量和发布验收实现及依赖锁文件；工程工具不成为 Java 应用运行依赖，也不写入发布制品。`./mvnw -N -Pdocs-check verify` 默认只检查文档格式，`./mvnw -Pci clean verify` 只执行 Maven 子门禁，均不能替代完整入口。

检查分三层：

1. **提交前。** Git `pre-commit` 检查暂存区中的 Markdown 与 Java 格式，检查类别由暂存变更和控制文件触发清单决定，不自动修改文件或暂存区。
2. **推送前。** Git `pre-push` 对待推送提交快照执行离线 quick，按变更检查 Markdown/Java 格式。缺少目标树工具或格式检查依赖缓存时失败并指向初始化命令；不在 hook 中自动联网补齐。完整验收由 CI、发布工作流或显式本地 full 执行。
3. **CI 兜底。** GitHub Actions 对检出的工作树调用同一完整入口和固定检查清单；本地通过不能替代独立 CI 结果。Sonar 默认不运行，CI 沿用 push 与凭据条件；本地只有提供三项凭据并设置 `RUN_SONAR=true` 时才覆盖同一远程分析与质量门禁，执行条件见[测试与质量规程](../engineering/testing.md)。

检查器至少覆盖：

- Markdown 格式与列表编号；
- 相对链接、锚点、索引与实际目录；
- 构建签名模型、发布工具契约、隔离消费者及签名成功与失败场景；
- Spotless 实际扫描范围；
- Surefire/Failsafe 测试报告、JaCoCo XML 和覆盖率门禁产物完整性。

技术债编号、摘要与明细对应、README 的受众约束和长期文档维护规则由文档维护及人工评审负责，不属于自动门禁，也不维护独立编号注册表。

格式化是显式修复动作，例如 `./mvnw spotless:apply`；检查 hook 只报告并阻断。Java 质量阶段在一次 `clean verify` 生命周期中生成测试和覆盖率证据；需要 Sonar 时，报告核验通过后再单独执行 `sonar:sonar` 并等待远程结果。隔离发布 fixture 与这次质量构建分别记录，不能用 fixture 的构建结果替代最终测试报告。现有覆盖率门槛继续以 POM 为准，首轮治理不重新定义阈值。

## 迁移与验收门禁

先建立自动检查基线，再按“职责归位 → 引用切换 → 删除重复正文”的顺序迁移。迁移期间不归档 v2.2.1；不把已有 `tag`、版本号或本地验收推断为远程发布成功；不触碰 `.worktrees/fix-markdown-prepush` 中另一会话的未提交草稿。

迁移完成必须满足：

1. 每个有效文档可从 `AGENTS.md` 或 `docs/index.md` 找到，且新目标章节有明确 owner/来源。
2. 同一易变事实只保留一个维护位置；README、工程规程和长期约束之间通过链接表达关系。
3. 仓库内引用全部切换到新位置；已迁移页面删除，不保留短期迁移映射或重复正文。
4. 根 README 的核心版本描述和徽章保留，且不出现 TD/技术债台账引用；普通依赖版本没有被重复写入全局文档。
5. 本地 hook 与 CI 使用共享检查实现，按各阶段选择 quick 或 full；相同模式、输入和条件采用相同判定规则。任一必需检查未执行或执行出错时整体不得报告通过；quick 成功不代表完整验收通过。
6. `git diff --check` 和统一完整验收均有可复核输出；文档格式、链接、导航、发布及 Maven 子检查分别记录，失败项在 `active/` 记录，不伪造为已完成。

本 RFC 在迁移和门禁全部落地、索引已同步、代码与文档抽样复核通过后，才可由维护者将 `status` 从 `draft` 更新为 `verified`。当前草案不授权目录迁移、Git hook 安装、提交或推送。

## 反模式

| 反模式 | 为什么禁止 |
|---|---|
| 在 README、工程规程和 design-docs 分别维护同一份默认配置或依赖版本 | 后续 Dependabot 或实现变更会产生多处漂移。 |
| 把 `archive/` 的历史描述改写成当前终态 | 会丢失当时的决策和验证证据，破坏追溯。 |
| 以版本归档或本地 tag 代替远程发布确认 | 目录状态和制品状态是两个独立事实。 |
| hook 修改工作区或自动重写暂存区 | 会破坏部分暂存提交，且难以判断提交内容。 |
| 某个检查因工具缺失或前置失败而未执行，却汇总为通过 | 结果不可复核，会把质量门禁变成形式。 |
| 为了保留旧链接长期复制整篇旧文档 | 复制正文会重新制造漂移；应先切换仓库内引用，再删除旧路径。 |
| 修改外部 skill 路径、插件缓存或另一 worktree 来“补齐”本仓库治理 | 超出仓库文档治理范围，且不可由本仓库 CI 复现。 |

## 适用范围

本 RFC 适用于仓库根入口、`docs/` 下长期约束、工程规程、活跃版本与归档文档，以及模块 README 的职责划分和质量检查。它不替代需求级 `spec.md`、`design.md`、`plan.md`，不改变运行时实现，也不要求一次性重写所有历史归档正文。

## 参考

- [文档治理](../standards/documentation-governance.md)
- [模块边界](./arch-module-dependencies.md)
- [迁移计划](../active/v2.3.0/docs-quality-governance/migration.md)
- [需求规格](../active/v2.3.0/docs-quality-governance/spec.md)
- [设计说明](../active/v2.3.0/docs-quality-governance/design.md)
- [实施计划](../active/v2.3.0/docs-quality-governance/plan.md)
