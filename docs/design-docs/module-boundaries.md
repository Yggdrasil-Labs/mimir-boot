# 模块边界

本文档描述本仓库模块的职责边界、依赖方向与新增能力的落位规则。

## 1. 顶层边界

### `mimir-boot-parent`

- 负责构建插件、profile、质量门禁、发布辅助配置
- 不承载运行时 Java 代码

### `mimir-boot-bom`

- 负责统一依赖版本
- 不承载构建逻辑和运行时逻辑

### `mimir-boot-common`

- 负责稳定、可共享、规范性的基础抽象
- 不应演化为无边界工具包

### `mimir-boot-starters/*`

- 每个 starter 承担一个清晰能力域
- 通过自动装配暴露能力
- 提供独立 README 说明接入方式与配置

## 2. 推荐依赖关系

允许的典型依赖：

- starter -> `mimir-boot-common`
- starter -> 第三方库
- 接入方项目 -> `mimir-boot-parent` + `mimir-boot-bom` + 若干 starter

不建议的关系：

- `common` -> 某个 starter
- starter 之间形成隐式环依赖
- `bom` 或 `parent` 承担运行时抽象

## 3. 新能力如何落位

### 放进 `common`

只有当它满足下面至少两个条件时才考虑：

- 多个 starter 或接入方都会使用
- 语义稳定、生命周期长
- 与业务域无关，属于基础规范

示例：

- 错误码约定
- 响应模型
- 分页请求与分页结果

不建议放入：

- 与具体 Spring 运行时强相关的自动装配逻辑
- 只服务于某一个 starter 的内部实现细节

### 放进现有 starter

当能力是现有模块的自然延伸时，优先扩展已有 starter。

示例：

- `starter-web` 下的 Web 层响应增强
- `starter-log` 下的访问日志能力
- `starter-rpc-core` 下的统一上下文与 Hook 抽象

### 新建 starter

当能力满足以下条件时才考虑新建：

- 能力域独立
- 依赖集独立
- 配置项独立
- 接入方可能按需选装

新建后至少同步：

- 聚合模块注册
- BOM 版本声明
- 模块 README
- 产品规格或能力索引

## 4. 文档边界

不同信息应放在不同位置：

- 模块总体定位：`ARCHITECTURE.md`
- 长期设计原则：`docs/design-docs/`
- 产品能力说明：`docs/product-specs/`
- 单次执行计划：`docs/exec-plans/`
- 自动导出的事实：`docs/generated/`
- 模块接入细节：模块 README

## 5. 发布与版本边界

涉及 `parent`、`bom`、`revision`、flatten、GPG、Maven Central 的内容，属于仓库级工程边界，而不是某个 starter 自己的局部实现。

因此：

- 不要把发布规则散落写进各 starter README
- 不要让某个 starter 私自改变全局发布语义
- 发布相关知识应集中沉淀在顶层架构和可靠性文档中

## 6. 变更边界判断

下面这些改动通常不应直接做：

- 把临时功能塞进 `common`
- 在 starter 中引入与能力域无关的大依赖
- 修改默认配置语义却不更新 README 和产品说明
- 通过复制粘贴新建一个高度重叠的 starter

## 7. 审查问题清单

发起跨模块变更前，先回答：

- 这个能力最自然属于哪个模块？
- 是否会让某个模块承担双重职责？
- 是否引入新的公共 API、配置或行为默认值？
- 是否需要同步更新文档索引、产品规格和计划记录？
