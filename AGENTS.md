# AGENTS.md

本文件是智能体的唯一入口，保持为"地图而不是手册"。

## 项目概述

Mimir Boot 是 Yggdrasil-Labs 的 Java 企业级基础框架仓库，面向内部业务团队提供统一依赖版本、公共组件和自定义 Starter。核心技术栈：Java 17 + Spring Boot 3.3.13 + Maven 多模块（parent / bom / common / starters）。这是基础设施产品仓库，不是单体业务系统。

## 全局规范

1. 智能体优先遵循项目规范（`AGENTS.md`、`ARCHITECTURE.md`、`docs/design-docs/`）。项目约束 > 智能体全局约束。
2. Git Conventional Commits，message 中文。格式：`<type>(<scope>): <中文描述>`。
3. 文档与代码冲突时以代码为准并回写文档。
4. 默认保持向后兼容，不静默修改公共配置语义、发布结构、公开接口或依赖体系。
5. 多文件变更、新功能、重构、架构/性能/安全相关调整，先给计划再实施。单文件局部调整、纯文档小修可直接执行。
6. 所有回复、计划、说明、代码注释使用简体中文，代码标识符和专有名词除外。
7. WSL 中如需 Node 运行时，先 `source ~/.nvm/nvm.sh`。

## 导航

### A. 长期约束（只读，修改需架构 RFC）

- 系统边界与依赖方向：[`ARCHITECTURE.md`](./ARCHITECTURE.md)
- 工程信条：[`docs/design-docs/core-beliefs.md`](./docs/design-docs/core-beliefs.md)
- 模块边界：[`docs/design-docs/module-boundaries.md`](./docs/design-docs/module-boundaries.md)
- 业务领域划分：[`docs/DOMAINS.md`](./docs/DOMAINS.md)
- 安全策略：[`docs/SECURITY.md`](./docs/SECURITY.md)
- 可靠性标准：[`docs/RELIABILITY.md`](./docs/RELIABILITY.md)

### B. 流转文档

- 活跃版本：[`docs/active/index.md`](./docs/active/index.md)
- 版本归档：[`docs/archive/index.md`](./docs/archive/index.md)
- 技术债：[`docs/active/tech-debt-tracker.md`](./docs/active/tech-debt-tracker.md)
- 设计决策：[`docs/design-docs/index.md`](./docs/design-docs/index.md)

### C. 参考与产物

- 产品思维：[`docs/PRODUCT_SENSE.md`](./docs/PRODUCT_SENSE.md)
- 产品能力说明：[`docs/product-specs/index.md`](./docs/product-specs/index.md)
- 质量评分：[`docs/QUALITY_SCORE.md`](./docs/QUALITY_SCORE.md)
- 文档总索引：[`docs/index.md`](./docs/index.md)

## 决策地图

| 改什么 | 去哪里 |
|--------|--------|
| 新增/升级第三方依赖版本 | `mimir-boot-bom/pom.xml` |
| 修改构建插件、质量门禁 | `mimir-boot-parent/pom.xml` |
| 修改公共模型（异常/响应/分页/枚举） | `mimir-boot-common` |
| 新增 Starter | `mimir-boot-starters/` + BOM 注册 + 聚合模块注册 |
| 修改已有 Starter 自动装配 | 对应 starter 目录 |
| 修改 CI/CD 流水线 | `.github/workflows/` |
| 升级 Spring Boot / Spring Cloud 主版本 | 高风险，需计划 + RFC |
| 修改发布策略或版本号 | 根 `pom.xml` 的 `revision` + release 工作流 |

## 开发命令

```bash
# 全量构建
./mvnw clean install

# 构建特定模块（含依赖）
./mvnw clean install -pl mimir-boot-starter-log -am

# 跳过测试
./mvnw clean package -DskipTests

# 运行测试 + 质量门禁
./mvnw verify

# 代码格式检查
./mvnw spotless:check

# 自动格式化
./mvnw spotless:apply
```
