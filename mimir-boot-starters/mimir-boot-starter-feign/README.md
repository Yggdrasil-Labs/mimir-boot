# mimir-boot-starter-feign

Feign 接入层，基于 RPC Core 统一调用钩子与上下文传播，治理/观测/安全能力由外部 Starter（log/metrics/security/governance 等）按需接入。

## 模块概述和用途

- 定位：Feign 适配层，复用 RPC Core 抽象
- 目标：在 HTTP RPC 调用中统一日志/观测/安全钩子与上下文传播

## 功能特性列表

- 包装 Feign `Client`，在调用前/后/异常/清理阶段调度 RPC Hook
- 默认 MDC Bridge 将合法 `traceId`/`requestId` 注入 `X-Trace-Id`/`X-Request-Id`，可由自定义 Bridge 覆盖
- 配置开关：`mimir.boot.feign.enabled`（默认开启）、`context-propagation-enabled`

## 快速开始指南

1) 引入依赖

```xml
<dependency>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-starter-feign</artifactId>
</dependency>
```

1) （可选）实现 `RpcHook` / `RpcTracerBridge` Bean，自动参与调用链

## 配置说明

```yaml
mimir:
  boot:
    feign:
      enabled: true
      context-propagation-enabled: true
```

## 使用示例

- 自定义 Hook：与 RPC Core 示例一致
- 自定义 Tracer：覆盖 `RpcTracerBridge`，在 HTTP 头中注入/提取追踪上下文；Feign 只负责出站注入，HTTP 入站由 Web Starter 处理

## 最佳实践

- 将观测/安全逻辑放入独立 Starter，通过 Hook 注入
- 保持 Hook 快速、无阻塞，避免影响 HTTP 调用延迟

## API 文档

- 本模块不直接暴露业务 API，提供 Feign Client 包装与自动装配扩展点。

## 常见问题

- **未生效？** 检查 `mimir.boot.feign.enabled`，并确认项目启用了 OpenFeign

## 相关文档

- RPC 核心：`mimir-boot-starter-rpc-core`
- Dubbo 接入：`mimir-boot-starter-dubbo`
