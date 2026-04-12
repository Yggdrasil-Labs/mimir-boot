# Mimir Boot 架构地图

本文档提供本仓库的顶层架构地图，帮助开发者和智能体快速理解模块职责、依赖方向和变更边界。

## 1. 仓库定位

`mimir-boot` 是 Yggdrasil-Labs 的 Java 企业级基础框架仓库，核心目标是：

- 统一依赖版本
- 统一构建与发布规范
- 提供公共基础模型和约定
- 以 Starter 形式沉淀可复用能力
- 为接入方提供稳定、可组合、可演进的基础设施基座

这不是单体业务系统仓库，而是一个“基础设施产品仓库”。

## 2. 顶层模块

当前聚合结构如下：

```text
mimir-boot
├── mimir-boot-parent
├── mimir-boot-bom
├── mimir-boot-common
└── mimir-boot-starters
    ├── mimir-boot-starter-log
    ├── mimir-boot-starter-exception
    ├── mimir-boot-starter-web
    ├── mimir-boot-starter-mybatis
    ├── mimir-boot-starter-mybatis-processor
    ├── mimir-boot-starter-nacos
    ├── mimir-boot-starter-test
    ├── mimir-boot-starter-rpc-core
    ├── mimir-boot-starter-dubbo
    └── mimir-boot-starter-feign
```

## 3. 模块职责

### `mimir-boot-parent`

职责：

- 统一 Maven 插件版本
- 统一构建 profile
- 统一质量门禁
- 提供测试、格式化、覆盖率、发布等基线

适合放置：

- `pluginManagement`
- 构建 profile
- JaCoCo、Spotless、Enforcer 等工程规则
- `distributionManagement` 与发布约束

不适合放置：

- 业务代码
- 运行时框架逻辑

### `mimir-boot-bom`

职责：

- 统一第三方依赖版本
- 统一 Mimir Boot 自身模块版本引用

适合放置：

- `dependencyManagement`
- 与 Spring Boot / Spring Cloud 对齐的版本矩阵
- 本仓库各可发布模块的版本对齐

不适合放置：

- 构建插件配置
- 运行时自动装配逻辑

### `mimir-boot-common`

职责：

- 定义全仓库共享的基础约定与公共模型
- 提供统一异常、响应、分页、枚举、基础 DTO/VO 等

当前已知内容：

- `annotation`
- `constant`
- `dto`
- `enums`
- `exception`
- `page`
- `response`
- `util`

约束：

- 应聚焦“规范性公共能力”，避免演化成杂物工具箱
- 对外暴露内容应稳定，变更要谨慎

### `mimir-boot-starters`

职责：

- 以独立 starter 提供可组合基础能力
- 每个 starter 保持单一职责，避免耦合膨胀

当前已落地的能力族：

- 日志与链路：`starter-log`
- 异常与统一响应：`starter-exception`
- Web 层增强：`starter-web`
- 持久层增强：`starter-mybatis`
- 编译期生成：`starter-mybatis-processor`
- 配置安全：`starter-nacos`
- 测试支持：`starter-test`
- RPC 抽象：`starter-rpc-core`
- RPC 适配：`starter-dubbo`、`starter-feign`

## 4. 推荐依赖方向

建议始终遵循下面的依赖方向：

```text
应用项目
  -> mimir-boot-parent
  -> mimir-boot-bom
  -> 选用的 starter
  -> mimir-boot-common（通常通过 starter 间接获得）
```

仓库内部建议方向：

```text
parent  -> 只管构建
bom     -> 只管版本
common  -> 基础模型与规范
starters -> 运行时能力与自动装配
```

禁止出现的倾向：

- `common` 反向依赖具体 starter
- `bom` 承担构建职责
- `parent` 承担运行时逻辑
- 一个 starter 未经设计评审就横向耦合多个不相关 starter

## 5. Parent / BOM / Common / Starter 细化边界

### 根 POM

职责：

- 多模块聚合
- 统一 `revision`
- 承载本仓库级别的构建与发布入口

不应承担：

- 对外暴露为使用方继承的运行时基座

### Parent

职责：

- 为本仓库子模块和外部使用方提供统一构建基座
- 统一插件版本、Java 版本、测试与质量门禁

不应承担：

- 大量业务依赖
- 与具体 starter 强耦合的运行时逻辑

### BOM

职责：

- 只管理版本，不管理构建逻辑
- 对齐 Spring Boot、Spring Cloud 与本仓库模块版本

不应承担：

- 插件配置
- profile
- 自动装配

### Common

职责：

- 提供稳定、可复用、与业务无关的公共模型和规范

应警惕：

- 工具类无边界膨胀
- 为了方便把技术栈特定逻辑塞进 `common`

### Starter

职责：

- 面向接入方提供开箱即用能力单元
- 通过自动装配和配置项暴露能力

推荐结构：

- `*AutoConfiguration`
- `*Properties`
- 面向能力域的实现包
- 独立 README

## 6. 新增 Starter 的落地检查

新增 starter 前，至少确认：

- 名称是否清晰对应一个能力域
- 是否真的需要独立发布和独立引入
- 是否已在 `mimir-boot-starters` 聚合模块注册
- 是否已在 BOM 中声明版本
- 是否有最小 README、配置项说明和接入示例
- 是否避免与现有 starter 形成循环依赖

## 7. Starter 设计约束

一个 starter 应尽量满足以下特征：

- 单一能力中心明确
- 默认开箱即用，但允许通过配置关闭
- 通过 Spring Boot 自动装配暴露能力
- 对接入方的侵入性低
- README 可独立说明接入方式、配置项、示例和边界

新增 starter 前，优先回答这些问题：

- 这是新的能力域，还是已有 starter 的扩展点？
- 是否必须独立发布和独立版本演进？
- 是否会引入额外依赖、运行时成本或配置复杂度？
- 是否能保持与现有对外 API / 配置兼容？

## 8. 工程与发布架构

当前从仓库事实可见：

- 使用 GitHub Actions 作为 CI/CD
- `ci.yml` 负责格式检查、编译、测试、覆盖率与 Sonar 分析
- `release-please.yml` 负责 release PR 管理
- `release.yml` 负责 tag 发布、GitHub Packages 与 Maven Central 发布流程

这说明本仓库不仅要维护代码，还要维护“可发布性”。

当前发布链路的关键事实：

- 版本由根 `pom.xml` 中的 `revision` 统一管理
- 发布前需要通过 flatten 解析占位版本
- Maven Central 正式版发布依赖 GPG 签名
- GitHub Actions 中同时维护了 GitHub Packages 与 Maven Central 的发布流程
- 发布能力属于高风险基础设施变更，修改时必须同步更新文档

## 9. 文档架构

本仓库采用“入口文件 + 分层文档目录”的知识组织方式：

- 根 `AGENTS.md`：短入口与导航
- `ARCHITECTURE.md`：顶层架构地图
- `docs/design-docs/`：设计原则、边界、治理
- `docs/product-specs/`：产品视角的能力说明
- `docs/exec-plans/`：执行计划与技术债
- `docs/generated/`：自动生成或半自动维护的事实类文档
- `docs/references/`：供智能体快速检索的参考文本

## 10. 变更边界

默认情况下，以下改动应被视为高风险：

- 变更公共异常、响应、分页模型语义
- 变更 starter 默认配置行为
- 变更 parent/bom 的公共继承方式
- 升级 Spring Boot / Spring Cloud 主版本
- 调整发布流水线与版本策略

进行这些改动时，应先有计划，并同步更新相应文档。

## 11. 阅读顺序建议

如果你是第一次进入仓库，建议按下面顺序理解：

1. `README.md`
2. 本文档
3. `docs/design-docs/core-beliefs.md`
4. `docs/product-specs/index.md`
5. `docs/PLANS.md`

如果你要开始实施某个改动，再继续看对应模块的 README 和执行计划。
