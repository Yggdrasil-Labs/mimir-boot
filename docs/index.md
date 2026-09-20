# 文档总索引

`docs/` 目录的总入口。第一次进入仓库，先看 [`../ARCHITECTURE.md`](../ARCHITECTURE.md)。

## 架构设计

- 架构设计与决策：[`design-docs/index.md`](./design-docs/index.md)
- 模块划分与依赖设计：[`design-docs/arch-module-dependencies.md`](./design-docs/arch-module-dependencies.md)

## 项目规范

规范回答“必须遵守什么”，架构设计解释方案与取舍，工程指南给出操作步骤。按任务阅读，不必每次通读全部文档。

| 规范 | 何时阅读 |
|---|---|
| [核心信条](./standards/core-beliefs.md) | 开始工作或需要判断工程取舍时 |
| [安全约束](./standards/security.md) | 涉及敏感数据、加密、请求边界或发布凭证时 |
| [可靠性约束](./standards/reliability.md) | 涉及默认行为、兼容性、构建或发布链路时 |
| [文档治理](./standards/documentation-governance.md) | 新建、修改、迁移或归档文档时 |

## 工程规程

| 你要做什么 | 从哪里开始 |
|---|---|
| 第一次参与仓库开发 | [准备环境](./engineering/development.md#准备环境) |
| 修复问题、修改已有能力、升级依赖 | [开发指南](./engineering/development.md) |
| 选择检查、读报告或排查失败 | [测试与质量](./engineering/testing.md) |
| 新增 Starter | [新增 Starter 规程](./engineering/new-starter.md) |
| 审阅 Release PR 或处理发布失败 | [发布与补偿](./engineering/release.md) |
| 维护工程工具或使用独立诊断入口 | [工程工具说明](../tools/engineering/README.md) |

## 版本流转

- 活跃版本：[`active/index.md`](./active/index.md)
- 版本归档：[`archive/index.md`](./archive/index.md)
- 技术债：[`active/tech-debt-tracker.md`](./active/tech-debt-tracker.md)

## 使用契约与产物

- 接入方从仓库根 README 和各模块 README 获取能力、配置与当前行为契约；单次需求的行为变化与验收条件按需记录在 `active/` 下的 `spec.md`。未来独立建立长期 Product Spec 的条件见[文档治理](./standards/documentation-governance.md#1-内容归属)，当前不保留空目录。
- 生成文档如有产出，须在对应版本或模块文档中登记实际存在的路径。
