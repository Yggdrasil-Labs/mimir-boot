---
version: v2.3.0
status: in-progress
updated: 2026-10-03
---

# v2.3.0 活跃需求

本目录记录文档与质量治理实施。文档迁移与 T8 约定本地验收已完成，远程 CI、Sonar 与制品发布未验证。规划编号不修改根 POM 的 `revision`，也不代表发布承诺；发布状态见 [release.md](./release.md)。

| 需求 | 状态 | 说明 |
|---|---|---|
| [Agent 文档治理与本地质量门禁](./docs-quality-governance/plan.md#当前状态与后续工作) | 实施及约定本地验收完成（未发布） | T7 已提交，T8 实施提交两次完整快照 full 与 27 场景映射通过；TD-043、TD-044 关闭 |
| [日志 JSON 脱敏补全](./log-json-masking/plan.md) | 实施及本地完整验收完成（未发布） | [规格](./log-json-masking/spec.md)、[设计](./log-json-masking/design.md)、4 个串行任务已完成；复合值边界见 README 与计划 |
| [MongoDB 驱动族兼容](./mongodb-driver-alignment/plan.md) | 本地实施及完整验收完成（未发布） | [规格](./mongodb-driver-alignment/spec.md)、[设计](./mongodb-driver-alignment/design.md)、4 个串行任务和本地完整 quality 报告；发布状态见 [release.md](./release.md) |
| [Springdoc 与 Boot 基线兼容](./springdoc-boot-alignment/plan.md) | 本地实施及完整验收完成（未发布） | TD-041 已处置：保持 Boot 3.3 基线，Springdoc 2.6.0 通过两种消费者及完整 quality 验收 |
| [运行时技术债后续修复](./runtime-debt-followup/plan.md) | 三阶段实施及本地模块验收完成（未发布） | RPC MDC 回滚、字段加密注册隔离：831 项测试通过；Nacos 显式重建与事件桥接：306 项测试通过；迁移和服务端验收边界保留 |

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

当前进度以实施计划的“当前状态与后续工作”和 T7/T8 为准；迁移矩阵记录内容去向，验收记录区分文档检查与工程整体验收。T1–T6 保留历史实施证据，不按旧接口重新执行。
