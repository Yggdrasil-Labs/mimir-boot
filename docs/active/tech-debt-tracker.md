---
updated: 2026-07-24
---

# 技术债务追踪

<!--!
  本文件是智能体了解代码库"已知问题"的入口。
  智能体在修改某个领域前，应先查看该领域是否有已知债务。
  智能体在完成任务后发现新的债务，应记录到此文件。

  本文件属于"长期维护清单"，永不归档；由 doc-gardening agent 持续维护。
-->

| 编号 | 领域 | 问题描述 | 优先级 | 记录日期 | Owner | 关联计划 |
|------|------|----------|--------|----------|-------|----------|
| TD-004 | product | 规划中的治理、指标、安全 starter 尚无正式产品规格（已从 README 移除，待正式立项时再补） | 低 | 2026-04-11 | ORPHAN | — |
| TD-005 | ci | release.yml publish-gpr 与 publish-maven-central 大量重复步骤，待用 reusable workflow 重构 | 中 | 2026-06-26 | — | 已通过 `.github/actions/maven-release-prepare/` composite action 解决 |
| TD-006 | bom | BOM 中约 60% 的依赖版本声明未被任何 starter 引用（如 elasticsearch、mongodb、redis、kafka、xxl-job 等），增加 Dependabot 噪音和维护负担，且模糊了"版本基线"的边界 | 中 | 2026-06-29 | ORPHAN | 需评估是否精简为仅本仓库实际使用的依赖，或将生态系统版本管理拆分到独立 BOM |
| TD-007 | nacos | 自动配置 Bean 监听 `ApplicationEnvironmentPreparedEvent`，启动期解密不会执行；属性前缀、动态刷新和密码学默认值亦存在缺口 | 紧急 | 2026-07-17 | ORPHAN | [能力复审修复清单](./capability-review-2026-07/fix-checklist.md#phase-0恢复-nacos-启动期能力) |

## 优先级含义（智能体行为指南）

- **紧急**：阻碍智能体有效工作或导致生产事故。智能体应优先处理。
- **高**：降低质量评分，应在本迭代解决。智能体遇到时应顺手修复。
- **中**：已知缺口，已有改进计划。智能体按计划处理。
- **低**：锦上添花。智能体在有空闲时间时处理。

## Owner 与孤儿模块规则

- **每条债务必须有 Owner**：可以是人、团队、或专项智能体（如 `doc-gardening`、`cleanup`）
- **孤儿模块**：代码路径没有对应的 product-spec / design-doc，或长期无人维护的模块
  - 发现时在本表登记一行，Owner 填 `ORPHAN`
  - 30 天内未指派 Owner 的孤儿模块，建议在下一次架构回顾中**归档或删除**
- 解决后删除该行，并在关联计划的决策日志中记录解决方式

## 如何记录新债务

1. 在上方表格中添加一行
2. 根据定义分配优先级
3. 填写 Owner（无 Owner 写 `ORPHAN`）
4. 如有关联计划，链接到 `docs/active/{需求}/plan.md`
5. 解决后删除该行，并在关联计划的决策日志中记录解决方式
