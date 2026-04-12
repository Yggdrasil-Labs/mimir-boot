# Starter 能力全景

本文档从产品能力视角概览当前 Mimir Boot 已交付的 starter。

## 已交付能力

| Starter | 核心价值 | 典型场景 |
|---|---|---|
| `mimir-boot-starter-log` | 统一日志、脱敏、访问日志、链路标识 | 绝大多数服务 |
| `mimir-boot-starter-exception` | 统一异常处理与返回格式 | Web / API 服务 |
| `mimir-boot-starter-web` | Web 层增强、CORS、Trace、响应增强 | HTTP 服务 |
| `mimir-boot-starter-mybatis` | MyBatis-Plus 增强、审计、字段加解密 | 数据驱动服务 |
| `mimir-boot-starter-mybatis-processor` | 编译期 Mapper 生成与扫描辅助 | 需要减少样板代码的持久层项目 |
| `mimir-boot-starter-nacos` | Nacos 配置加密与解密 | 使用 Nacos 的配置中心场景 |
| `mimir-boot-starter-test` | 测试基线与常用测试依赖 | 所有接入方项目 |
| `mimir-boot-starter-rpc-core` | 统一 RPC 调用抽象与 Hook 扩展点 | RPC 能力治理底座 |
| `mimir-boot-starter-dubbo` | Dubbo 接入与治理扩展 | Dubbo 服务 |
| `mimir-boot-starter-feign` | Feign 接入与治理扩展 | Feign 调用项目 |

## 规划中能力

根据当前 README，以下方向处于规划态：

- 服务治理
- 指标监控
- 安全治理

正式落地前，至少补齐产品规格、模块边界说明以及与现有 starter 的关系说明。

## 组合建议

### 最小 Web 服务组合

- `starter-log`
- `starter-exception`
- `starter-web`
- `starter-test`

### 数据服务组合

- 最小 Web 服务组合
- `starter-mybatis`
- `starter-mybatis-processor`

### RPC 服务组合

- 最小 Web 服务组合
- `starter-rpc-core`
- `starter-dubbo` 或 `starter-feign`

## 决策提示

如果某个能力看起来“只是一点小扩展”，优先判断它是否应放进现有 starter，而不是立即创建新 starter。
