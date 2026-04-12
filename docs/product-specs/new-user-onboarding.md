# 新接入方上手规格

本文档描述一个新项目如何以最小成本接入 Mimir Boot。

## 1. 目标用户

适用于以下用户：

- 新建的 Spring Boot 项目
- 希望统一依赖和工程基线的团队
- 需要按需引入日志、Web、MyBatis、RPC 等基础能力的项目

## 2. 目标

接入方通常希望在最短路径内获得：

- 统一依赖版本
- 统一构建与质量门禁
- 开箱即用的基础 starter
- 尽量少的样板配置

## 3. 推荐接入路径

### 第一步：继承 `mimir-boot-parent`

目的：

- 获得统一构建 profile
- 获得统一插件与质量规则

### 第二步：确认是否需要显式引入 `mimir-boot-bom`

目的：

- 避免对当前继承方式做重复配置
- 在特殊场景下仍能显式控制依赖版本来源

当前仓库事实是：`mimir-boot-parent` 已在 `dependencyManagement` 中引入 `mimir-boot-bom`。

因此默认推荐路径是：

- 继承 `mimir-boot-parent`
- 直接声明所需依赖

只有在以下场景下，才需要额外显式引入 `mimir-boot-bom`：

- 不继承 `mimir-boot-parent`
- 需要把 BOM 作为独立版本矩阵使用
- 有意采用不同于默认 parent 的继承策略

### 第三步：按需选择 starter

典型起步组合：

- `mimir-boot-starter-log`
- `mimir-boot-starter-exception`
- `mimir-boot-starter-web`
- `mimir-boot-starter-test`

数据项目通常再增加：

- `mimir-boot-starter-mybatis`
- `mimir-boot-starter-mybatis-processor`

RPC 项目通常再增加：

- `mimir-boot-starter-rpc-core`
- `mimir-boot-starter-dubbo` 或 `mimir-boot-starter-feign`

如果你已经继承 `mimir-boot-parent`，这里通常不需要再单独 import BOM。

## 4. 成功标准

一个“成功接入”的项目应满足：

- 可以稳定构建
- 依赖版本由 parent/bom 管理
- 选用的 starter 可在最小配置下正常工作
- README 中提到的默认能力能够被验证

## 5. 非目标

Mimir Boot 不负责：

- 替接入方生成完整业务代码
- 规定接入方所有业务架构
- 提供单一的业务数据库 schema

## 6. 继续阅读

- 架构关系：[`../../ARCHITECTURE.md`](../../ARCHITECTURE.md)
- Starter 全景：[`starter-capabilities.md`](./starter-capabilities.md)
- 计划与治理：[`../PLANS.md`](../PLANS.md)
