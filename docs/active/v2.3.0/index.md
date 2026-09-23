---
version: v2.3.0
status: in-progress
updated: 2026-09-23
---

# v2.3.0 活跃需求

本目录记录文档与质量治理实施。质量门禁已有实施与本地验收记录；本轮文档内容修复及文档检查已通过，整体验收仍待收尾。规划编号不修改根 POM 的 `revision`，也不代表发布承诺；发布状态见 [release.md](./release.md)。

| 需求 | 状态 | 说明 |
|---|---|---|
| [Agent 文档治理与本地质量门禁](./docs-quality-governance/plan.md#当前状态与后续工作) | 进行中 | 文档内容修复及文档验收通过；尚未提交，T8 整体验收待完成 |
| [日志 JSON 脱敏补全](./log-json-masking/plan.md) | 实施及本地完整验收完成（未发布） | [规格](./log-json-masking/spec.md)、[设计](./log-json-masking/design.md)、4 个串行任务已完成；复合值边界见 README 与计划 |
| [MongoDB 驱动族兼容](./mongodb-driver-alignment/plan.md) | 已规划，待实施 | [规格](./mongodb-driver-alignment/spec.md)、[设计](./mongodb-driver-alignment/design.md)、4 个串行任务；尚未修改依赖 |

## 文档治理工作入口

质量门禁、hooks 与 CI 实现已单独提交；本轮根 README 仅补场景组合提示，不改主体内容，不保留产品规格或长期文档的迁移入口。

| 文档 | 职责 |
|---|---|
| [需求规格](./docs-quality-governance/spec.md) | 8 个行为、27 个场景及验收约束 |
| [技术设计](./docs-quality-governance/design.md) | 工具链、快照、检查入口和结果协议 |
| [实施计划](./docs-quality-governance/plan.md) | 8 个任务、依赖、验证步骤和执行记录 |
| [迁移矩阵](./docs-quality-governance/migration.md) | 旧文档内容归属、引用切换和人工验收 |
| [验收记录](./docs-quality-governance/verification.md) | 文档修复证据、未验证项与 T8 剩余边界 |
| [治理 RFC](../../design-docs/arch-docs-quality-governance.md) | 文档职责与长期约束的变更依据 |

当前进度以实施计划的“当前状态与后续工作”和 T7/T8 为准；迁移矩阵记录内容去向，验收记录区分文档检查与工程整体验收。T1–T6 保留历史实施证据，不按旧接口重新执行。
