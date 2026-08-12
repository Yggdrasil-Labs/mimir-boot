---
id: project-governance
status: verified
owner: Yggdrasil Labs
created: 2026-07-30
updated: 2026-08-13
---

# Project Governance

本目录是 v2.1.2 的独立项目治理需求入口。治理正文、决策和验证证据只在本目录维护；
版本索引只保留状态与链接，不承载技术方案。

## 导航

- [治理需求规格](./spec.md)：冻结后的行为范围、验收边界和排除项。
- [治理技术设计](./design.md)：跨模块实现边界、接口契约和测试策略。
- [治理推进计划](./plan.md)：按“CI 与文档治理优先、功能代码优化随后”的顺序实施。
- [整体设计审查](./overall-design-review.md)：GOV-001 至 GOV-010。
- [单人维护提效](./solo-maintainer-efficiency.md)：GOV-011 至 GOV-015。
- [Starter 功能完整性](./starter-functional-completeness.md)：GOV-016 至 GOV-020。

## 治理议题

| 议题 | 范围 | 收益 | 状态 | 入口 |
|------|------|------|------|------|
| 整体设计审查问题整改 | GOV-001—GOV-010 | 中—高 | 已验证/已关闭 | [overall-design-review.md](./overall-design-review.md) |
| 单人维护提效 | GOV-011—GOV-015 | 中—高 | 已验证 | [solo-maintainer-efficiency.md](./solo-maintainer-efficiency.md) |
| Starter 功能完整性 | GOV-016—GOV-020 | 中—高 | 已验证 | [starter-functional-completeness.md](./starter-functional-completeness.md) |

## 实施主线

| 主线 | GOV 范围 | 目标 | 顺序 |
|------|----------|------|------|
| B：CI 与文档治理 | GOV-001、007、009—015 | 先建立本地可复现的质量门禁、单次 CI 构建、可靠发布前检和事实校验 | 先实施 |
| A：功能代码优化 | GOV-002—006、016—020 | 再修复运行时上下文、失败语义和 Starter 默认能力缺口 | 后实施 |

GOV-008 是 v2.x 兼容性边界决策，不修改生产 API；编译回归并入 T1，不形成第三条实施主线。
其余 GOV-001—GOV-020 的实现与版本级门禁证据见各专题的 `验证证据` 和 T12 AC1—AC3。

## 范围原则

1. 只纳入已确认具有中等或高收益的问题，不以“规范更完整”为理由扩张范围。
2. 优先保证现有功能真实可用、失败语义确定、默认行为安全，并减少单人重复维护。
3. 自动化用于校验和减少重复劳动，不自动覆盖人工文档，不自动合并依赖更新。
4. 保持 2.x 公共兼容性；安全默认值修正必须提供迁移路径。
5. 新治理议题继续进入本目录，但冻结范围之后新增内容必须重新评估收益和版本影响。

## 已排除内容

- 消费者契约工程和独立兼容夹具。
- 新增 observability/trace、缓存、安全或指标 Starter。
- Dependabot 自动合并、降低更新频率或季度批量更新。
- GitHub Packages 与 Maven Central 共用发布产物。
- 自动生成并覆盖人工文档。
- 未经失败证据直接重写 Nacos 刷新机制。
- v2.x 移除公共响应模型的 `Serializable` 上界。
- 拆分或精简企业级宽 BOM。

## 状态说明

| 状态 | 含义 |
|------|------|
| 已登记 | 问题和证据已记录，尚未完成范围讨论 |
| 讨论中 | 正在确认收益、边界或方案 |
| 已设计 | Spec 与 Design 已确认，可进入实施计划 |
| 实施中 | 已按确认计划进入代码或文档修改 |
| 已验证 | 定向验证和版本级门禁均通过 |
| 延期 | 已记录 Owner、理由和目标版本 |
| 已关闭 | 已验证，或经决策确认本版本无需实施 |

Spec 的 front matter 使用 `in-progress` 表示范围已确认且需求仍活跃；Design 使用 `verified` 表示设计
审查已完成；Plan 使用 `not-started` 对应“待实施”。治理入口和三个专题文件在版本关闭前保持
`in-progress`，表示需求仍活跃，不表示方案仍待确认。
