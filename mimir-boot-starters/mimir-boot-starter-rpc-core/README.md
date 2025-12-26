# mimir-boot-starter-rpc-core

RPC 内核抽象模块，提供统一调用模型、上下文与可插拔扩展点，供 Dubbo/Feign 等 RPC 框架适配。治理、观测、安全等具体实现由其他 Starter（log/metrics/security/governance 等）按需接入。

## 模块概述和用途
- 定位：企业级 RPC 基线抽象，不耦合具体治理/观测/安全实现
- 适配：Dubbo、Feign 等框架复用同一套调用模型与 Hook

## 功能特性列表
- 统一调用元数据与上下文模型
- Hook 链（before/after/error/cleanup），可由外部 Starter 插拔实现
- Trace/Span/Request-Id 桥接接口（默认 Noop，可覆盖）
- Spring Boot 3 `@AutoConfiguration` 自动装配
- 配置开关：`mimir.boot.rpc.core.enabled`（默认开启）

## 快速开始指南
1) 引入依赖
```xml
<dependency>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-starter-rpc-core</artifactId>
</dependency>
```
2) （可选）实现自定义 `RpcHook` / `RpcTracerBridge` 并声明为 Spring Bean

## 配置说明
```yaml
mimir:
  boot:
    rpc:
      core:
        enabled: true
        context-propagation-enabled: true
```

## 使用示例
- 自定义 Hook：
```java
@Component
public class LoggingHook implements RpcHook {
    @Override public void before(RpcCallContext ctx) { /* log */ }
}
```
- 自定义 Tracer：
```java
@Component
public class MicrometerTracerBridge implements RpcTracerBridge {
    public Map<String,String> inject(RpcCallContext ctx){ /* ... */ return Map.of(); }
    public void extract(RpcCallContext ctx, Map<String,String> carrier){ /* ... */ }
}
```

## 最佳实践
- 在治理/观测/安全模块中实现 Hook 或 Tracer 覆盖默认值
- 保持 Hook 幂等、快速返回，避免阻塞主调用

## API 文档
- 本模块不暴露对外 API，仅提供 Spring Boot 自动装配与扩展点。

## 常见问题
- **未生效？** 检查 `mimir.boot.rpc.core.enabled` 是否为 true，且 Bean 未被覆盖

## 相关文档
- Dubbo 接入层：`mimir-boot-starter-dubbo`
- Feign 接入层：`mimir-boot-starter-feign`

