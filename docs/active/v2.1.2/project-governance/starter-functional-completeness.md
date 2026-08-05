---
id: starter-functional-completeness
status: in-progress
owner: Yggdrasil Labs
created: 2026-08-02
updated: 2026-08-05
---

# Starter 功能完整性治理清单

## 目标

让现有 Starter 声明的核心能力在默认配置下真实可用，并允许消费者用最小成本替换默认组件；
不新增缺少明确消费场景的 Starter。

## GOV-016：RPC 上下文传播默认无效

- **优先级**：P1
- **收益**：高
- **领域**：rpc-core / feign / dubbo / observability
- **证据位置**：`RpcCoreAutoConfiguration.java`、`NoopRpcTracerBridge.java`、Feign 与 Dubbo README
- **问题**：三个模块默认开启上下文传播，但 RPC Core 默认注册 Noop Bridge；消费者不自行实现 Bean 时，
  traceId/requestId 不会跨调用传播。
- **确认方向**：RPC Core 内置 MDC Bridge，默认传播 traceId/requestId；用户提供 `RpcTracerBridge`
  时完整替换默认实现，不新增 observability/trace Starter。
- **边界**：只管理本 Bridge 拥有的 MDC 键；同步调用结束恢复原值，异步完成回调不得依赖调用线程 MDC。
- **关闭条件**：Feign 与 Dubbo 默认场景均能传播合法 ID；非法 ID 不进入下游 MDC；调用完成后原 MDC 恢复；
  自定义 Bridge 时默认 Bridge 不创建。
- **状态**：讨论中

## GOV-017：Spring 6 常见 HTTP 异常落入通用 500

- **优先级**：P1
- **收益**：高
- **领域**：exception / web
- **证据位置**：`MimirExceptionHandler.java`
- **问题**：方法校验、缺少请求头或路径参数、媒体类型协商、上传超限和静态资源不存在等常见异常
  没有专门映射，会被通用异常处理为 500。
- **确认方向**：补齐 400、404、406、413、415 等 Spring 6 HTTP 语义；继续使用现有响应工厂。
- **边界**：`BizException` 返回 HTTP 200 的 2.x 兼容语义不变，不重新设计业务错误模型。
- **关闭条件**：每类异常的 HTTP 状态、错误码和响应结构均有可断言测试，通用 500 只处理服务端未知异常。
- **状态**：讨论中

## GOV-018：Nacos 动态刷新闭环缺少证据

- **优先级**：P1
- **收益**：中高
- **领域**：nacos / configuration / reliability
- **证据位置**：`NacosEncryptEnvironmentPostProcessor.java`、`NacosEncryptAutoConfiguration.java`、
  `ConfigDecryptProcessorTest.java`
- **问题**：实现监听环境变更并更新解密覆盖层，但现有测试没有证明刷新事件后配置 Bean 最终绑定到新明文。
- **确认方向**：先补模块内部真实刷新集成测试；测试失败时按结构化 Red 证据只修监听顺序、回滚或
  日志安全对应路径，测试通过则不改运行时代码。
- **边界**：不直接改写 Nacos PropertySource，不引入 Nacos 私有监听 API，不在日志输出密文或明文。
- **关闭条件**：密文变更并发布环境变更事件后，Environment 与配置 Bean 都读取新明文；错误密钥明确失败。
- **状态**：讨论中

## GOV-019：JUnit Suite 对下游不可见

- **优先级**：P2
- **收益**：中
- **领域**：test / dependencies
- **证据位置**：`mimir-boot-starter-test/pom.xml`
- **问题**：Starter 声明 JUnit Suite 支持，但 Suite API 和 Engine 使用 `test` scope，不会传递到下游测试类路径。
- **确认方向**：让 Suite API 和 Engine 随测试 Starter 对下游可用；删除本模块源码未使用的
  Testcontainers test-scope 依赖，消费者继续按场景显式引入具体容器模块。
- **边界**：不把数据库、消息队列或缓存容器模块加入基础测试 Starter。
- **关闭条件**：下游只以 test scope 引入本 Starter 即可编译并运行一个 JUnit Suite；消费依赖树继续
  不包含 Testcontainers，且 BOM 版本管理保留。后两项是依赖清理回归，不作为新增功能收益。
- **状态**：讨论中

## GOV-020：默认自动装配缺少一致覆盖规则

- **优先级**：P2
- **收益**：中
- **领域**：web / log / mybatis / extensibility
- **证据位置**：`WebAutoConfiguration.java`、`AccessLogAutoConfiguration.java`、
  `MybatisPlusAutoConfiguration.java`
- **问题**：部分默认 Bean 无明确 back-off 条件，消费者可能得到重复组件或无法替换默认行为。
- **确认方向**：采用“默认 Bean + 用户 Bean 优先”；Web 组件按类型回退，访问日志注册按明确 Bean 名回退，
  用户 `MybatisPlusInterceptor` 完整替换 Starter 实例。
- **边界**：不新增 Customizer API，不自动合并用户与 Starter 的 MyBatis 内部拦截器。
- **关闭条件**：每个默认组件均有上下文测试证明默认创建、用户提供时回退且容器中只有一个有效实例。
- **状态**：讨论中

## 明确不采纳

- 新建 observability/trace Starter：现阶段会增加模块、依赖和发布维护成本。
- 将 Testcontainers 全量传递：不同消费者需要的容器模块差异过大。
- 直接重写 Nacos 刷新：没有失败证据前不引入更深的框架耦合。
- 为每个自动装配新增 Customizer：扩展面和长期兼容成本高于当前收益。
