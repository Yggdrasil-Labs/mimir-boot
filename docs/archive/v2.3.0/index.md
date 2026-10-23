---
version: v2.3.0
status: archived
archived: 2026-10-23
updated: 2026-10-23
---

# v2.3.0 归档需求

本目录的五项需求及版本记录已于 2026-10-23 归档。v2.3.0 的 GitHub Release 与补偿发布工作流已核实，发布日期为 2026-10-14；交付汇总、验证范围和证据见 [release.md](./release.md)。下表的本地验收摘要来自原计划，不代表本次重新执行；历史正文中的“未发布”、旧执行状态和 `active/` 路径保留当时语义。

| 需求 | 状态 | 说明 |
|---|---|---|
| [Agent 文档治理与本地质量门禁](./docs-quality-governance/plan.md#当前状态与后续工作) | 已完成并归档 | T7 已提交，T8 实施提交两次完整快照 full 与 27 场景映射通过；TD-043、TD-044 关闭 |
| [日志 JSON 脱敏补全](./log-json-masking/plan.md) | 已完成并归档 | [规格](./log-json-masking/spec.md)、[设计](./log-json-masking/design.md)、4 个串行任务已完成；复合值边界见 README 与计划 |
| [MongoDB 驱动族兼容](./mongodb-driver-alignment/plan.md) | 带已接受的快照豁免完成并归档 | [规格](./mongodb-driver-alignment/spec.md)、[设计](./mongodb-driver-alignment/design.md)、4 个串行任务和本地完整 quality 报告；专项 fixture 按要求移除，保留一次性验收边界 |
| [Springdoc 与 Boot 基线兼容](./springdoc-boot-alignment/plan.md) | 已完成并归档 | TD-041 已处置：保持 Boot 3.3 基线，Springdoc 2.6.0 通过两种消费者及完整 quality 验收 |
| [运行时技术债后续修复](./runtime-debt-followup/plan.md#当前状态2026-10-09) | 约定框架修复已完成并归档 | RPC MDC、字段加密注册及质量门禁已修复；TD-039 默认 Nacos 框架修复完成，历史完整 quality 的 1181 项常规 Java 测试与独立 nacos-live 的 333 项单元测试、17 项 IT 通过；实际业务消费者迁移转入 [TD-047](../../active/tech-debt-tracker.md#td-047-nacos-消费者迁移) |

## 文档治理工作入口

质量门禁、hooks 与 CI 实现已单独提交；本轮根 README 仅补场景组合提示，不改主体内容，不保留产品规格或长期文档的迁移入口。

| 文档 | 职责 |
|---|---|
| [需求规格](./docs-quality-governance/spec.md) | 8 个行为、27 个场景及验收约束 |
| [技术设计](./docs-quality-governance/design.md) | 工具链、快照、检查入口和结果协议 |
| [实施计划](./docs-quality-governance/plan.md) | 8 个任务、依赖、验证步骤和执行记录 |
| [迁移矩阵](./docs-quality-governance/migration.md) | 旧文档内容归属、引用切换和人工验收 |
| [验收记录](./docs-quality-governance/verification.md) | 文档修复、T8 收尾证据、人工替代及外部未验证边界 |
| [治理 RFC](../../design-docs/arch-docs-quality-governance.md) | 文档职责与长期约束的变更依据 |

交付依据为实施计划的“当前状态与后续工作”和 T7/T8；迁移矩阵记录内容去向，验收记录区分文档检查与工程整体验收。旧执行字段和 T1–T6 保留历史含义，不按旧接口重新执行。TD-013、TD-016、TD-023、TD-047 仍由[技术债台账](../../active/tech-debt-tracker.md)跟踪；独立交付及最终验证摘要见 [release.md](./release.md#独立交付)。
