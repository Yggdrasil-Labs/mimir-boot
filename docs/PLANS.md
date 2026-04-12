# 计划体系

本文件说明计划放在哪里、何时需要写、最少写到什么程度。

## 1. 计划分层

### 轻量计划

适用于：

- 中小型多文件改动
- 文档体系建设
- 模块内能力扩展

位置：

- `docs/exec-plans/active/`

本目录入口同时收录在 [`index.md`](./index.md) 中。

## 2. 计划目录

- 进行中：[`exec-plans/active/`](./exec-plans/active)
- 已完成：[`exec-plans/completed/`](./exec-plans/completed)
- 技术债：[`exec-plans/tech-debt-tracker.md`](./exec-plans/tech-debt-tracker.md)

## 3. 何时必须写计划

- 新功能
- 跨模块变更
- 行为变化
- 重构
- 架构调整
- 高风险可靠性 / 安全改动

## 4. 执行计划最小模板

建议包含：

- 背景
- 目标
- 范围
- 非目标
- 影响模块
- 风险
- 步骤
- 验证方式
- 决策记录

## 5. 与 AGENTS 的关系

- `AGENTS.md` 负责告诉智能体“什么时候该先提计划”
- `docs/PLANS.md` 负责告诉智能体“计划放哪里、怎么分层”
