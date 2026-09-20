---
version: v2.2.1
status: released
released: 2026-09-12
retain_until: 2027-09-20
updated: 2026-09-20
---

# v2.2.1 归档需求

本目录记录随 v2.2.1 发布的需求历史。两项需求已完成验收，后续改进在新的活跃版本中维护；发布说明见 [release.md](./release.md)。

| 需求 | 状态 | 说明 |
|------|------|------|
| [技术债修复](./technical-debt-remediation/) | 已随 v2.2.1 发布 | DG-1、DG-2、DG-3 已选择 A；T1-T9 已完成验证，残余风险继续在活跃技术债台账跟踪 |
| [底座质量强化](./foundation-quality-hardening/) | 已随 v2.2.1 发布 | 代码复核采纳的 6 组质量改进；8 个 Behavior、36 个 Scenario、9 个 IC 保持不变，终审代码补丁已分阶段提交并通过最终验证 |

## 归档边界

- 本目录是已发布历史，不作为新需求的实施入口。
- 后续修复和新增需求必须在新的 `docs/active/{version}/` 目录维护。
- 残余技术债继续由 [`docs/active/tech-debt-tracker.md`](../../active/tech-debt-tracker.md) 跟踪。
