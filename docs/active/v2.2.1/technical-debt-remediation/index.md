---
id: technical-debt-remediation
version: v2.2.1
status: draft
owner: YoungerYang-Y
branch: main
created: 2026-08-16
updated: 2026-08-17
---

# 技术债修复

本需求处理 2026-08-16 核对后仍成立的技术债，并以已确认的兼容性决策为边界。原“敏感信息保护加固”专项已合并至本需求：其日志脱敏、SQL 脱敏、Nacos 遗留 ECB 告警和 MyBatis 应用级 AAD 均在此处统一设计、计划和验收。

| 文档 | 状态 | 说明 |
|------|------|------|
| [brainstorm.md](./brainstorm.md) | 已确认 | 18 项兼容性与安全决策 |
| [spec.md](./spec.md) | 草案 | 可验收行为契约 |
| [design.md](./design.md) | 草案 | 跨模块实现设计 |

代码实施必须以经确认的 `plan.md` 为准。
