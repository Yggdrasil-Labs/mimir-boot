---
id: arch-docs-quality-governance
status: draft
owner: 项目维护者
tags: [architecture, documentation, agent, quality]
created: 2026-09-14
---

# 文档体系与质量门禁治理 RFC

## 状态与范围

本文档是已获用户同意方向的治理草案，当前仍为 `draft`，尚未代表实施完成或验证通过。它只约束仓库内文档的职责、迁移边界和质量检查方式，不修改 Java 代码、公开 API、依赖方向、发布结构或外部技能目录。

本 RFC 的需求、设计和执行记录分别见：

- [需求规格](../active/v2.3.0/docs-quality-governance/spec.md)
- [设计说明](../active/v2.3.0/docs-quality-governance/design.md)
- [实施计划](../active/v2.3.0/docs-quality-governance/plan.md)
- [文档迁移计划](../active/v2.3.0/docs-quality-governance/migration.md)

## 背景与动机

Mimir Boot 的文档同时服务接入方、仓库维护者和 Agent。相同能力在根 README、模块 README、产品规格和长期约束中重复描述，导致版本、默认行为、模块职责和接入前提容易漂移。已有 Markdown、链接和 Maven 质量检查也需要统一入口，使提交前、推送前和 GitHub CI 的结果可比较、可复核。

本 RFC 采用 Agent 优先的内部文档模型：

- `README.md` 与模块 README 维护接入方可执行的使用契约；人主要从这些入口开始。
- `docs/` 维护 Agent 的开发契约，包括架构边界、修改位置、验证要求、任务状态和历史证据。
- 代码、POM、工作流和测试是实现事实来源；文档与实现冲突时回写文档，不用文档掩盖实现问题。

## 设计原则

1. **单一事实来源。** 一个易变事实只在一个职责正确的文档中维护，其他入口只保留必要摘要和链接。
2. **使用与开发分离。** README 说明如何接入、配置、验证和处理限制；`docs/design-docs/` 与 `docs/engineering/` 说明为什么这样设计、改哪里和如何验收。
3. **稳定约束与执行方法分离。** `design-docs/` 保存长期约束；`engineering/` 保存可执行的开发、测试、发布和新增 Starter 方法。
4. **维护当前终态。** 全局说明、模块 README 和产品能力文档直接维护当前有效状态，不追加“某日复核发现”式过程记录。日期只出现在元数据、计划、发布记录和历史证据中。
5. **历史语义不重写。** `active/` 中的当前需求保留过程证据；`archive/` 正文保留当时语义，只修复失效链接、路径和必要格式。版本归档不等于制品发布，二者必须分别核验。
6. **兼容优先。** 稳定旧锚点通过显式 anchor 或迁移映射保留；迁移完成后不长期复制旧正文。
7. **版本信息适度维护。** 根 README 保留 Java、Spring Boot、Maven、许可证和 Sonar 徽章，以及稳定核心版本描述；由 Dependabot 频繁升级的普通依赖和 Maven 插件只写名称、用途和 POM 来源，不在多份文档重复版本号。
8. **内部追踪与公开入口分离。** 根 README 不引用技术债台账或 TD 编号；Agent 通过 `AGENTS.md`、`docs/index.md` 和 `active/` 导航到内部状态。
9. **先报告再判断。** 检查器必须区分通过、失败、执行错误、未执行和提示；未执行不能被汇总为通过。

## 目标目录与职责

```text
docs/
├── design-docs/       # 长期约束、边界和跨需求决策
├── engineering/       # Agent 执行任务的方法与验收标准
├── active/            # 当前版本、需求、技术债和过程证据
└── archive/           # 已结束版本与历史过程证据
```

现有 `product-specs/` 的使用说明并入 README，仍有效的开发验收规则迁入对应设计或需求文档；全部吸收后删除原目录，不保留第二套使用入口。

目标 `engineering/` 仅创建有实际内容的页面：

- `index.md`：开发规程地图与入口。
- `development.md`：模块定位、构建入口、修改位置和日常开发流程。
- `testing.md`：测试选择、Spotless、覆盖率、报告完整性和质量门禁。
- `release.md`：发布前置条件、发布步骤、结果判定和失败补偿。
- `new-starter.md`：新增 Starter 的注册点、边界、README 和验收清单。

`design-docs/` 保留核心信条、模块边界和文档治理，并将安全与可靠性长期约束迁入小写命名的 `security.md`、`reliability.md`。具体映射与章节归属以[迁移计划](../active/v2.3.0/docs-quality-governance/migration.md)为准。

## 质量检查模型

检查逻辑由仓库维护，并由 Maven 提供统一入口。Node/npm 仍是 Markdown 检查器的运行时，由项目工具链固定和复用；它不成为 Java 应用运行依赖，也不写入发布制品。日常入口应统一为 Maven profile，例如 `./mvnw -N -Pdocs-check verify`，具体插件、锁文件和脚本由实施计划落地。

检查分三层：

1. **提交前。** Git `pre-commit` 检查暂存区中的 Markdown 与 Java 格式，检查类别由暂存变更和控制文件触发清单决定，不自动修改文件或暂存区。
2. **推送前。** Git `pre-push` 检查待推送内容的完整文档规则、仓库内链接与锚点、索引导航、技术债编号及维护约定，并运行完整 Maven 编译、测试、覆盖率和报告核验。未准备工具时明确失败并指向初始化命令。
3. **CI 兜底。** GitHub Actions 调用同一套仓库脚本和配置，通过共享入口执行文档检查，再由 `scripts/ci-preflight.sh` 唯一调用 `./mvnw -Pci clean verify`；本地通过不能替代独立 CI 结果。

检查器至少覆盖：

- Markdown 格式与列表编号；
- 相对链接、锚点、索引与实际目录；
- 技术债编号唯一、数字序号升序、摘要与明细对应；
- 根 README 不出现技术债台账或 TD 编号；
- 关键长期文档和模块 README 的维护规则；
- Spotless 实际扫描范围；
- Surefire/Failsafe 测试报告、JaCoCo XML 和覆盖率门禁产物完整性。

格式化是显式修复动作，例如 `./mvnw spotless:apply`；检查 hook 只报告并阻断。完整构建在一次 Maven 生命周期中执行，避免用重复命令造成虚假的覆盖率或测试结论。现有覆盖率门槛继续以 POM 为准，首轮治理不重新定义阈值。

## 迁移与验收门禁

先建立自动检查基线，再按“职责归位 → 引用切换 → 删除重复正文”的顺序迁移。迁移期间不归档 v2.2.1；不把已有 `tag`、版本号或本地验收推断为远程发布成功；不触碰 `.worktrees/fix-markdown-prepush` 中另一会话的未提交草稿。

迁移完成必须满足：

1. 每个有效文档可从 `AGENTS.md` 或 `docs/index.md` 找到，且新目标章节有明确 owner/来源。
2. 同一易变事实只保留一个维护位置；README、产品规格和长期约束之间通过链接表达关系。
3. 旧的稳定章节链接仍可解析；已迁移页面保留显式 anchor 或短期迁移映射，不保留整段重复正文。
4. 根 README 的核心版本描述和徽章保留，且不出现 TD/技术债台账引用；普通依赖版本没有被重复写入全局文档。
5. 本地 hook、Maven 统一入口和 GitHub CI 对同一变更给出一致的检查结果；任一检查未执行或执行出错时整体不得报告通过。
6. `git diff --check`、Markdown lint、仓库内链接检查、文档治理脚本和 `./mvnw -Pci clean verify` 均有可复核输出；失败项在 `active/` 记录，不伪造为已完成。

本 RFC 在迁移和门禁全部落地、索引已同步、代码与文档抽样复核通过后，才可由维护者将 `status` 从 `draft` 更新为 `verified`。当前草案不授权目录迁移、Git hook 安装、提交或推送。

## 反模式

| 反模式 | 为什么禁止 |
|---|---|
| 在 README、产品规格和 design-docs 分别维护同一份默认配置或依赖版本 | 后续 Dependabot 或实现变更会产生多处漂移。 |
| 把 `archive/` 的历史描述改写成当前终态 | 会丢失当时的决策和验证证据，破坏追溯。 |
| 以版本归档或本地 tag 代替远程发布确认 | 目录状态和制品状态是两个独立事实。 |
| hook 修改工作区或自动重写暂存区 | 会破坏部分暂存提交，且难以判断提交内容。 |
| 某个检查因工具缺失或前置失败而未执行，却汇总为通过 | 结果不可复核，会把质量门禁变成形式。 |
| 为了保留旧链接长期复制整篇旧文档 | 复制正文会重新制造漂移，应使用 anchor 或迁移映射。 |
| 修改外部 skill 路径、插件缓存或另一 worktree 来“补齐”本仓库治理 | 超出仓库文档治理范围，且不可由本仓库 CI 复现。 |

## 适用范围

本 RFC 适用于仓库根入口、`docs/` 下长期约束、工程规程、产品规格、活跃版本与归档文档，以及模块 README 的职责划分和质量检查。它不替代需求级 `spec.md`、`design.md`、`plan.md`，不改变运行时实现，也不要求一次性重写所有历史归档正文。

## 参考

- [文档治理现状](./documentation-governance.md)
- [模块边界](./module-boundaries.md)
- [迁移计划](../active/v2.3.0/docs-quality-governance/migration.md)
- [需求规格](../active/v2.3.0/docs-quality-governance/spec.md)
- [设计说明](../active/v2.3.0/docs-quality-governance/design.md)
- [实施计划](../active/v2.3.0/docs-quality-governance/plan.md)
