# 设计文档索引

本目录保存长期有效的设计知识，不存放一次性讨论纪要。

## 当前文档

- [`core-beliefs.md`](./core-beliefs.md)：本仓库的核心信念与设计原则
- [`module-boundaries.md`](./module-boundaries.md)：模块边界、依赖方向、扩展方式
- [`documentation-governance.md`](./documentation-governance.md)：文档新鲜度、索引更新和维护要求

## 使用方式

### 常用路径

- 理解“为什么”：`core-beliefs.md`
- 判断边界与落位：`module-boundaries.md`
- 判断文档是否过期：`documentation-governance.md`

## 维护规则

- 新增关键模块、约束或设计决策时，必须更新本索引。
- 如果某篇文档只对单次任务有效，应放进 `docs/exec-plans/`，而不是本目录。
- 如果某项内容已经能从代码自动导出，优先放进 `docs/generated/`。
