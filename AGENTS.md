# AGENTS.md

本文件是智能体的唯一入口，保持为"地图而不是手册"。

## 项目概述

Mimir Boot 是 Yggdrasil-Labs 的 Java 企业级基础框架仓库，面向内部业务团队提供统一依赖版本、公共组件和自定义 Starter。核心技术栈：Java 17 + Spring Boot 3.3.13 + Maven 多模块（parent / bom / common / starters）。这是基础设施产品仓库，不是单体业务系统。

## 全局规范

1. 智能体优先遵循项目规范（`AGENTS.md`、`docs/standards/`、`ARCHITECTURE.md`、`docs/design-docs/`）。项目约束 > 智能体全局约束。
2. Git Conventional Commits，message 中文。格式：`<type>(<scope>): <中文描述>`。
3. 文档与代码冲突时以代码为准并回写文档。
4. 默认保持向后兼容，不静默修改公共配置语义、发布结构、公开接口或依赖体系。
5. 多文件变更、新功能、重构、架构/性能/安全相关调整，先给计划再实施。单文件局部调整、纯文档小修可直接执行。
6. 所有回复、计划、说明、代码注释使用简体中文，代码标识符和专有名词除外。
7. WSL 中如需 Node 运行时，先 `source ~/.nvm/nvm.sh`。
8. 修改 `tools/` 前必须读取并遵守[工具准入约束](./tools/engineering/README.md#保留范围)。仅允许项目全局、跨需求和跨版本长期重复使用的工程工具及必要支持文件；禁止新增一次性验收、专项功能验证、迁移、排障或实施过程脚本，也禁止将这些逻辑嵌入既有通用工具。写入前须在当次变更说明中说明长期职责与实际复用入口；无法说明则不得写入。接入 CI、重复调用、改名或包装成通用函数均不构成准入依据。

## 导航

### A. 架构与规范（实质性变更需架构 RFC）

- 项目规范与文档治理：[`docs/index.md#项目规范`](./docs/index.md#项目规范)
- 架构设计与决策：[`docs/design-docs/index.md`](./docs/design-docs/index.md)
- 系统边界与依赖方向：[`ARCHITECTURE.md`](./ARCHITECTURE.md)
- 工程信条：[`docs/standards/core-beliefs.md`](./docs/standards/core-beliefs.md)
- 模块划分与依赖设计：[`docs/design-docs/arch-module-dependencies.md`](./docs/design-docs/arch-module-dependencies.md)
- 安全约束：[`docs/standards/security.md`](./docs/standards/security.md)
- 可靠性约束：[`docs/standards/reliability.md`](./docs/standards/reliability.md)

### B. 工作指南与流转文档

- 活跃版本：[`docs/active/index.md`](./docs/active/index.md)
- 版本归档：[`docs/archive/index.md`](./docs/archive/index.md)
- 技术债：[`docs/active/tech-debt-tracker.md`](./docs/active/tech-debt-tracker.md)
- 开发与环境准备：[`docs/engineering/development.md`](./docs/engineering/development.md)

### C. 参考与产物

- 文档总索引：[`docs/index.md`](./docs/index.md)

## 决策地图

| 改什么 | 去哪里 |
|--------|--------|
| 新增/升级第三方依赖版本 | `mimir-boot-bom/pom.xml` + [`工程规程`](./docs/engineering/development.md) |
| 修改构建插件、质量门禁 | `mimir-boot-parent/pom.xml` + [`测试规程`](./docs/engineering/testing.md) |
| 新增或修改 `tools/` 内任何内容 | 先核对[工具准入约束](./tools/engineering/README.md#保留范围)，包括既有脚本内部新增逻辑 |
| 修改公共模型（异常/响应/分页/枚举） | `mimir-boot-common` |
| 新增 Starter | [`新增 Starter 规程`](./docs/engineering/new-starter.md) |
| 修改已有 Starter 自动装配 | 对应 starter 目录 |
| 修改 CI/CD 流水线 | `.github/workflows/` + [`测试规程`](./docs/engineering/testing.md) / [`发布规程`](./docs/engineering/release.md) |
| 升级 Spring Boot / Spring Cloud 主版本 | 高风险，需计划 + RFC |
| 修改发布策略或版本号 | 根 `pom.xml` 的 `revision` + [`发布规程`](./docs/engineering/release.md) |

## 开发命令

```bash
# 全量构建
./mvnw clean install

# 构建特定嵌套模块（含依赖；-pl 使用模块路径）
./mvnw clean install -pl mimir-boot-starters/mimir-boot-starter-log -am

# 跳过测试
./mvnw clean package -DskipTests

# Java 子检查入口（完整验收会调用；默认 RUN_SONAR=false）
bash scripts/engineering.sh java

# Java 子检查中的 Maven 基础构建步骤
./mvnw -Pci clean verify

# 统一完整验收（本地与 CI 基础检查共用）
bash scripts/engineering.sh quality --mode full --source worktree

# 代码格式检查
./mvnw -Pci spotless:check

# 自动格式化
./mvnw spotless:apply
```
