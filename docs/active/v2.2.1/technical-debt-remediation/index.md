---
id: technical-debt-remediation
version: v2.2.1
status: planned
owner: YoungerYang-Y
branch: main
created: 2026-08-16
updated: 2026-08-25
---

# 技术债修复

本需求处理经当前代码复核后仍成立的技术债有效部分，并以已确认的兼容性决策为边界。原“敏感信息保护加固”专项已合并至本需求：其日志脱敏、SQL 脱敏、Nacos 遗留 ECB 告警和 MyBatis 应用级 AAD 均在此处统一设计、计划和验收。

| 文档 | 状态 | 说明 |
|------|------|------|
| [brainstorm.md](./brainstorm.md) | 已确认 | 22 项兼容性、安全与实施边界决策，DG-1、DG-2、DG-3 均选择 A |
| [spec.md](./spec.md) | 已确认、待实施验证 | 9 个 Behavior、34 个可验收 Scenario |
| [design.md](./design.md) | 已确认、待实施验证 | 13 个接口契约及兼容、回滚与测试策略 |
| [plan.md](./plan.md) | 待执行 | 9 个 TDD/RFC 任务，分 4 个依赖组执行 |

Spec、Design 和 Plan 已确认但尚未实施；Spec/Design frontmatter 按项目状态机保持 `draft`，实施须按 `plan.md` 的 T1-T9 依赖图执行，并由 controller 维护执行 ledger。T9 先提交消费文档与技术债闭环，再通过预发布门禁发布最终状态；只有该任务的两个提交与版本级门禁全部通过后，Spec 才能标记为 `shipped`、Design 才能标记为 `verified`。
