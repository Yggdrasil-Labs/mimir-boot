# mimir-boot-starter-rpc-core

RPC 内核抽象模块，提供统一调用模型、上下文与可插拔扩展点，供 Dubbo/Feign 等 RPC 框架适配。治理、观测、安全等具体实现由其他 Starter（log/metrics/security/governance 等）按需接入。

## 模块概述和用途

- 定位：企业级 RPC 基线抽象，不耦合具体治理/观测/安全实现
- 适配：Dubbo、Feign 等框架复用同一套调用模型与 Hook

## 功能特性列表

- 统一调用元数据与上下文模型
- Hook 链（before/after/error/cleanup），可由外部 Starter 插拔实现
- 默认 MDC Bridge：传播合法 `traceId`/`requestId`，并允许自定义 `RpcTracerBridge` 按类型覆盖
- Spring Boot 3 `@AutoConfiguration` 自动装配
- 配置开关：`mimir.boot.rpc.core.enabled`（默认开启）

## 快速开始指南

1) 引入依赖

    ```xml
    <dependency>
      <groupId>io.github.yggdrasil-labs</groupId>
      <artifactId>mimir-boot-starter-rpc-core</artifactId>
      <version>YOUR_RELEASE_VERSION</version>
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
    public RpcTraceScope extractScope(RpcCallContext ctx, Map<String,String> carrier){
        /* 提取上下文，并在 scope.close() 中恢复自定义上下文 */
        return () -> { /* 恢复先前上下文 */ };
    }
}
```

对需要在调用结束时恢复上下文的自定义 Bridge，应实现 `extractScope` 并返回可关闭的 `RpcTraceScope`；2.4.0 起旧 `extract` 有空默认实现，新 Bridge 无需覆盖。旧 `extract` 入口的调用方仍需自行管理上下文生命周期。默认 `extractScope` 会恢复旧提取逻辑新增、删除或修改的 MDC 键，提取失败也会先回滚；调用结束后无关 MDC 键的变更会保留。scope 必须在创建线程关闭，嵌套调用按 LIFO 顺序关闭。默认适配不管理自定义 Span 或其他 ThreadLocal，此类 Bridge 仍需实现自己的 scope。

## 最佳实践

- 默认 Bridge 仅接受最长 64 位、以字母或数字开头的 ASCII ID；非法 traceId 生成 32 位十六进制值，非法 requestId 不会进入当前调用 MDC
- 在治理/观测/安全模块中实现 Hook 或 Tracer 覆盖默认值
- 保持 Hook 幂等、快速返回，避免阻塞主调用

`RpcHookChain` 的 `before`、`after`、`onError`、`cleanup` 直调方法已弃用并计划在下一个主版本删除（见下文“2.4.0 升级”），仅保留兼容；新代码使用 `open` 返回的 `RpcHookInvocation`，异步调用使用 `openAsync`，以获得调用级状态和异常隔离。

## 2.4.0 升级：旧入口计划删除

以下入口最初于 2.2.1 废弃，2.4.0 改标为 `@Deprecated(since = "2.2.1", forRemoval = true)`，**计划在下一个主版本删除**（准确清单届时复核）。2.4.0 不删除任何方法，签名、行为和兼容适配层均不变，旧二进制可继续运行。

| 旧入口 | 迁移到 |
|---|---|
| `RpcTracerBridge.extract(context, carrier)` | `extractScope(context, carrier)` 返回的 `RpcTraceScope` |
| `RpcHookChain.before(context)` | `open(context)` 得到 `RpcHookInvocation` 后调用 `before()` |
| `RpcHookChain.after(context, result)` | `invocation.completeSuccess(result)` |
| `RpcHookChain.onError(context, result)` | `invocation.completeFailure(result, primaryError)` |
| `RpcHookChain.cleanup(context)` | 同步 `invocation.close()`；异步 `completeWithoutResult()` 兜底 |

**编译影响**：javac 对 `forRemoval = true` 默认输出 removal 警告（无需 `-Xlint`），普通编译仍可完成；把警告视为错误（`-Werror`）的构建会失败，需先迁移。

**外部 Bridge 实现者**：2.4.0 起 `extract` 提供空默认实现。已迁移到 `extractScope` 的实现类可直接删除 `extract` 覆盖，从而消除 removal 警告；仍只实现旧 `extract` 的 Bridge 继续可用，但覆盖本身会产生 removal 警告，可在覆盖方法上加 `@SuppressWarnings("removal")` 暂时抑制，并尽快迁移。不要让 `extract` 与 `extractScope` 互相委托形成递归。

**同步调用**：每次调用独立 `open`，在创建线程中结束。`completeFailure` 的 `primaryError` 必须是 catch 到、并用于构造 `RpcCallResult.failure` 的同一个非 null 异常；Hook 的 onError/cleanup 异常会附加为它的 suppressed，完成后由调用方重抛。没有业务异常时不要传 null，改用 `close()`。

```java
Instant start = Instant.now();
RpcHookInvocation invocation = hookChain.open(context);
try {
    invocation.before();
    T value = remoteCall.get();
    invocation.completeSuccess(RpcCallResult.success(Duration.between(start, Instant.now())));
    return value;
} catch (RuntimeException error) {
    invocation.completeFailure(
            RpcCallResult.failure(Duration.between(start, Instant.now()), error), error);
    throw error;
} finally {
    invocation.close();
}
```

**异步调用**：`openAsync` 返回的 `RpcAsyncHookInvocation` 不是 `AutoCloseable`，不要使用 try-with-resources，也不要在返回异步结果时提前清理；终态交给完成回调，重复终态不会重复清理。回调注册失败时，用实际异常调用 `completeFailure`（或无异常时 `completeWithoutResult()`）。

```java
Instant start = Instant.now();
RpcAsyncHookInvocation invocation = hookChain.openAsync(context);
CompletableFuture<T> future;
try {
    invocation.before();
    future = remoteCall.get();
} catch (RuntimeException error) {
    invocation.completeFailure(
            RpcCallResult.failure(Duration.between(start, Instant.now()), error), error);
    throw error;
}
try {
    return future.whenComplete((value, error) -> {
        Duration duration = Duration.between(start, Instant.now());
        if (error == null) {
            invocation.completeSuccess(RpcCallResult.success(duration));
        } else {
            invocation.completeFailure(RpcCallResult.failure(duration, error), error);
        }
    });
} catch (RuntimeException registrationFailure) {
    invocation.completeFailure(
            RpcCallResult.failure(Duration.between(start, Instant.now()), registrationFailure),
            registrationFailure);
    throw registrationFailure;
}
```

**仅实现旧 `extract` 的 Bridge**：仍可加载，默认 `extractScope` 只恢复提取时修改的 MDC 键，提取失败先回滚再抛出原异常；不会恢复自定义 Span 或其他 ThreadLocal，这类 Bridge 需自行实现 scope。trace scope 必须在创建线程关闭，跨线程传播沿用现有 RPC 机制。

## API 文档

- 本模块提供可供 Dubbo、Feign 等接入层使用的公共 API，包括调用上下文、Hook 链、执行模板和追踪 Bridge；不直接提供 HTTP API。

## 常见问题

- **未生效？** 检查 `mimir.boot.rpc.core.enabled` 是否为 true，且 Bean 未被覆盖

## 相关文档

- Dubbo 接入层：`mimir-boot-starter-dubbo`
- Feign 接入层：`mimir-boot-starter-feign`
