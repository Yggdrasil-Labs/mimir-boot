# mimir-boot-starter-dubbo

Dubbo 接入层，基于 RPC Core 统一过滤调用并暴露可插拔钩子，治理/观测/安全能力由外部 Starter（log/metrics/security/governance 等）接入。

## v2.1.2 治理验证

Consumer/Provider 会传播并校验合法 traceId/requestId；Provider 在返回前关闭自身 Scope，异步 Hook 仍由完成回调唯一收尾，见 T7、T8 与 T12 AC2。

## 模块概述和用途

- 定位：Dubbo 适配层，复用 RPC Core 抽象
- 目标：统一日志/观测/安全的钩子接入，减少重复增强逻辑

## 功能特性列表

- Dubbo Filter 自动装配（Consumer/Provider）
- 统一调用元数据与上下文，调用前/后/异常/清理钩子
- 默认 MDC Bridge 在 Consumer 注入合法 `traceId`/`requestId`，Provider 在调用返回前恢复自身两个 MDC 键；可由外部实现覆盖
- 配置开关：`mimir.boot.dubbo.enabled`（默认开启）、`context-propagation-enabled`

## 快速开始指南

1) 引入依赖

```xml
<dependency>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-starter-dubbo</artifactId>
</dependency>
```

1) （可选）实现 `RpcHook` / `RpcTracerBridge` Bean，自动参与调用链

## 配置说明

```yaml
mimir:
  boot:
    dubbo:
      enabled: true
      context-propagation-enabled: true
```

## 使用示例

- 自定义 Hook：同 RPC Core 示例，直接被 Filter 调用
- 上下文透传：覆盖 `RpcTracerBridge` 以注入/提取 Trace 头；异步完成回调使用调用上下文，不依赖 Provider 线程 MDC

## 最佳实践

- 在观测/安全模块内注册 Hook，避免在业务侧重复实现
- 过滤器必须保持无副作用；重载前确认开关配置

## API 文档

- 本模块不直接暴露 HTTP/API 接口，提供 Dubbo Filter 与自动装配扩展点。

## 常见问题

- **未生效？** 检查 `mimir.boot.dubbo.enabled`，以及 Dubbo SPI 文件 `META-INF/dubbo/org.apache.dubbo.rpc.Filter`

## 相关文档

- RPC 核心：`mimir-boot-starter-rpc-core`
- Feign 接入：`mimir-boot-starter-feign`
