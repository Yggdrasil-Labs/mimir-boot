---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# RPC Core 旧入口废弃实施计划

静态输入，未执行。[规格](./spec.md) · [设计](./design.md)

## 目标与任务

落实RP1/RP2，不删除方法、不改执行逻辑。各任务顺序进行，独立于本轮Common、Log新能力。

### RP-T1：删除预告标记

Modify 以下路径（均在 `mimir-boot-starters/mimir-boot-starter-rpc-core/src/main/java/com/yggdrasil/labs/rpc/core/`）：

- `tracing/RpcTracerBridge.java`、`tracing/MdcRpcTracerBridge.java`、`tracing/NoopRpcTracerBridge.java`：旧extract注解与Javadoc；两个内置实现的extract覆盖方法同时加 `@Deprecated(since = "2.2.1", forRemoval = true)` 与 `@SuppressWarnings("removal")`（审查补充，原因见设计）。
- `tracing/LegacyMdcTraceScope.java`（审查补充）：静态 `extract` 方法第36行调用 `bridge.extract(context, carrier)`，加 `@SuppressWarnings("removal")` 并注释“2.x 兼容适配，随旧入口在下个主版本一并删除”；方法体不变。
- `hook/RpcHookChain.java`：before/after/onError/cleanup注解与Javadoc。

验收：forRemoval=true、since=2.2.1；方法描述符与方法体无变化；LegacyMdcTraceScope保留；rpc-core 主代码编译不再出现源自上述文件的 removal 警告。feign/dubbo 等模块测试中的 Bridge 测试实现会出现同类警告，属预期，可按需在测试类上抑制，不扩大本任务生产文件范围。

审查补充：rpc-core 自身测试编译同样会出现预期 removal 警告（路径均在 `mimir-boot-starters/mimir-boot-starter-rpc-core/src/test/java/com/yggdrasil/labs/rpc/core/` 下，已静态核对）：

- `hook/RpcHookChainTest.java:40-44`、`:72-75` 直调 `chain.before/after/onError/cleanup`；
- `tracing/NoopRpcTracerBridgeTest.java:24` 直调 `bridge.extract`；
- `tracing/LegacyRpcTracerBridgeCompatibilityTest.java:38`、`:62`、`:254` 与 `support/RpcExecutionTemplateTest.java:40`、`:68`、`:96`、`:159`、`:251`、`:323`、`config/RpcCoreAutoConfigurationTest.java:81`（审查补充）的匿名/测试 Bridge 覆盖 `extract`。

审查补充：feign/dubbo 测试中的预期警告来源（2026-10-31 静态 grep 核对）为 `mimir-boot-starters/mimir-boot-starter-feign/src/test/java/com/yggdrasil/labs/rpc/feign/config/FeignAutoConfigurationEndToEndTest.java:137` 匿名 Bridge 覆盖 `extract`；`mimir-boot-starters/mimir-boot-starter-dubbo/src/test/java/com/yggdrasil/labs/rpc/dubbo/filter/RpcDubboFilterEndToEndTest.java:145` 匿名 Bridge 覆盖 `extract` 且 `:146` 直调 `delegate.extract`（委托 `MdcRpcTracerBridge` 的已标记覆盖方法），`:322` 测试 Bridge 覆盖 `extract`。两模块测试中的 `verify(hook).before/after/onError/cleanup` 针对 `RpcHook` mock，`mock(RpcHookChain.class)` 不调用旧入口，均不在范围；feign/dubbo 主代码只引用 `RpcHookChain` 类型、不调用旧入口，不产生该警告。

`tracing/MdcRpcTracerBridgeTest.java` 只调用 `extractScope`，不产生该警告；`hook/RpcHookDefaultTest.java` 调用的是 `RpcHook` 接口方法而非 `RpcHookChain` 旧入口，同样不在范围。这些测试保留旧入口调用以验证兼容语义，验收记录列出警告来源即可，不因消除警告而删改断言；如需抑制只在测试类或方法上加 `@SuppressWarnings("removal")`。

### RP-T2：迁移与技术债说明（依赖RP-T1）

Modify 模块 `README.md` 的2.4.0升级段，涵盖同步与异步生命周期、旧抽象方法保留、线程归属、removal/Werror影响。审查补充：须给出外部 Bridge 覆盖 extract 的示例（`@SuppressWarnings("removal")` 必需，可选 `@Deprecated(since = "2.2.1", forRemoval = true)`），明确仅迁移 extractScope 不能消除覆盖产生的 removal 警告；onError→completeFailure 示例说明 primaryError 为 catch 到并放入 `RpcCallResult.failure` 的同一非 null 异常，完成后由调用方重抛。Modify `docs/active/tech-debt-tracker.md` 的TD-013/TD-023：登记本轮标记与下主版本计划，不自动关闭。不得借此补HTTP Service Client功能。

验收：示例使用真实签名；异步不使用try-with-resources；外部实现者知道2.x仍须实现extract且覆盖需抑制removal警告；primaryError来源明确；下个主版本删除仅为计划。

### RP-T3：兼容与示例验收（依赖RP-T1、RP-T2）

使用既有同模块 `src/test/java/com/yggdrasil/labs/rpc/core/tracing/LegacyRpcTracerBridgeCompatibilityTest.java`、`hook/RpcHookChainTest.java` 等测试验证原语义；按需补充模块内消费者编译测试。命令：`./mvnw -pl mimir-boot-starters/mimir-boot-starter-rpc-core -am test`。

用旧版编译产物运行新版，单独验证二进制兼容；以默认参数（removal 默认输出）和Werror两种模式验证预期告警，并验证外部 Bridge 覆盖示例加抑制后严格模式通过。迁移示例实际编译并运行必要清理断言。缺旧制品时报告未验证，不能用新源码编译替代旧二进制证据。不向tools新增专项脚本。

## 决策边界

允许调整示例布局与测试辅助代码；不得改变公开签名、取消旧抽象约束、删除适配或关闭未完成技术债。发布、提交及真正主版本删除不属于本次授权。
