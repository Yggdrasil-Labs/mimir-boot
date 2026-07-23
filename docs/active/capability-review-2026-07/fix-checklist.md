---
status: active
created: 2026-07-17
---

# 能力复审修复清单

本清单对应[复审报告](./review-report.md)。勾选某项前，必须完成其验收标准和回归测试；不得以“单元测试通过”替代端到端行为验证。

## 发布门禁

- [ ] R-001 至 R-009 已关闭。
- [ ] 安全默认值变更已提供迁移说明和示例配置。
- [ ] `./mvnw -B verify -Pci` 通过。
- [ ] 新增或修改的安全、Nacos、RPC 行为均有集成测试。
- [ ] 复审报告与技术债追踪器状态已同步。

## Phase 0：恢复 Nacos 启动期能力

### R-001：替换错误的生命周期监听方式

- [ ] 使用 `EnvironmentPostProcessor` 或等效的 Spring Boot 早期扩展点处理密文。
- [ ] 将扩展点注册到 Spring Boot 可发现的位置。
- [ ] 确保解密属性源的优先级只覆盖原始密文来源。
- [ ] 增加启动集成测试：包含 `ENC(...)` 的连接配置在 Bean 创建前可被读取为明文。
- [ ] 增加动态刷新测试：替换为新的 `ENC(...)` 后重新获得对应明文。

**验收标准**：Nacos 初始化和后续刷新都能解密；密钥缺失或密文非法时按明确策略失败，不静默以密文继续运行。

## Phase 1：修复安全默认值与敏感数据泄露

### R-002：收紧 CORS

- [x] 默认改为关闭或空 Origin 白名单。
- [x] `allowCredentials=true` 时拒绝 `*` 与等价的全匹配 pattern。
- [x] 提供显式生产白名单示例。
- [x] 增加 MockMvc/WebTestClient 预检测试和带 Cookie 请求测试。

**R-002 完成记录（2026-07-19）**：提交 `2e8e798` 默认关闭 CORS，启用时要求配置显式 Origin 白名单，并由 Spring 校验拒绝携带凭证的通配 Origin。README 补充生产白名单示例；MockMvc 回归测试覆盖预检、拒绝非白名单来源和携带 Cookie 的请求。

### R-003、R-004：重建 Nacos 加密策略与日志策略

- [x] 统一为单一 `mimir.boot.nacos.encrypt` 配置前缀，保留兼容迁移期时给出弃用警告。
- [x] 改为 AES-GCM，密文包含格式版本与随机 IV。
- [x] 密钥长度、算法和值格式在启动时校验。
- [x] 日志只记录属性名和统计数，不记录密文或明文。
- [x] 增加篡改密文、错误密钥、旧格式迁移和日志捕获测试。

**R-003、R-004 完成记录（2026-07-19）**：提交 `44ca087` 统一当前配置前缀并在迁移期兼容旧前缀、输出弃用警告；默认加密格式升级为版本化 AES-GCM（随机 IV），启动期校验密钥与算法。解密日志仅记录属性名或统计数量，测试覆盖错误密钥、篡改密文、旧格式迁移和日志不泄露密文/明文。

### R-005、R-006、R-007：保护持久化与日志数据

- [x] 启用 MyBatis 字段加密但未配置稳定密钥时，在所有 profile 启动失败。
- [x] 禁止自动生成临时密钥；自定义 `CryptoKeyProvider` 必须返回稳定密钥。
- [x] SQL 日志对 password、token、secret、authorization 等内置敏感参数名统一脱敏。
- [x] 访问日志仅记录 request URI，不提供写入 query string 的配置开关。
- [x] 移除 `ContentCachingResponseWrapper` 的无必要使用，验证 SSE、下载和大响应场景。
- [x] 增加跨上下文重启解密测试，验证同一稳定密钥可解密先前写入的密文。
- [x] 增加 SQL 参数脱敏日志捕获测试。
- [x] 增加访问日志 query string 脱敏、SSE、下载和大响应测试。

**R-005 完成记录（2026-07-19）**：提交 `a63fdf2` 在启动期校验 Base64 AES 密钥格式与长度，并覆盖无密钥、非法密钥、自定义 Provider 与两个独立应用上下文间的加密/解密场景。验证命令：`mise exec java@17 -- ./mvnw -B -pl mimir-boot-starters/mimir-boot-starter-mybatis -am -Pci test`（302 项通过）。

**R-007 完成记录（2026-07-19）**：访问日志仅记录 request URI，且不提供记录 query string 的配置开关；过滤器不再缓存响应体，直接将原始响应交给下游。新增 SSE、下载和 1 MiB 响应回归测试，验证响应会在过滤链执行期间直接写出。验证命令：`mise exec java@17 -- ./mvnw -B -pl mimir-boot-starters/mimir-boot-starter-log -am -Pci -Dtest=AccessLogFilterTest -Dsurefire.failIfNoSpecifiedTests=false test`（14 项通过）。

**验收标准**：日志断言中不存在密钥、明文口令、token 或原始敏感查询参数；加密字段跨进程重启可读。

## Phase 2：修复 RPC 传播语义

### R-008：完成 Dubbo 双向传播

- [x] 根据 Dubbo Consumer/Provider 角色分别执行注入与提取。
- [x] Provider 处理前建立入站 Trace 上下文，结束后清理。
- [x] Hook 的前置、后置、异常和清理语义在两端保持一致。
- [x] 增加 Consumer → Provider 的真实 Dubbo 集成测试。

**R-008 完成记录（2026-07-22）**：`RpcDubboFilter` 按 Dubbo URL 的 `side` 参数区分角色：Consumer 仅注入并写入 attachments，Provider 在 Hook 前提取入站上下文且不再写入 attachments。单元测试覆盖 Provider 成功与异常路径的 Hook 顺序、错误处理和清理；进程内 `ServiceConfig` → `ReferenceConfig` 端到端测试验证 Consumer 注入的 trace attachment 被 Provider 提取。验证命令：`mise exec java@17 -- ./mvnw -o -B -pl mimir-boot-starters/mimir-boot-starter-dubbo -am -Pci test`（215 项通过）。

### R-009：保留 Feign delegate

- [x] 采用 Feign 支持的扩展机制装饰 Client，而非以缺失 Bean 条件替代它。
- [x] 验证负载均衡与自定义 Client 配置仍被调用。
- [x] 限制进入 RPC attachments 的敏感请求头，默认排除 `Authorization` 与 `Cookie`。
- [x] 增加 OpenFeign 应用上下文集成测试。

**R-009 完成记录（2026-07-23）**：以 `RpcFeignCapability` 装饰 Spring Cloud OpenFeign 已选择的最终 `Client`，不再注册 `@Primary` 替代 Bean 或回退 `Client.Default`；自动配置使用显式 Bean 名，避免与 Spring Cloud 的 `FeignAutoConfiguration` 冲突。OpenFeign 应用上下文集成测试确认自定义 `Client` 与 RPC Hook 均被调用；Spring Cloud OpenFeign 4.1.5 的 `FeignClientFactoryBean` 源码确认其在确定 `Client`（包括负载均衡 Client）后再应用 `Capability`。RPC metadata 默认不再复制大小写任意形式的 `Authorization`、`Cookie`，但原始 HTTP 请求头保持不变。验证命令：`mise exec java@17 -- ./mvnw -o -B -pl mimir-boot-starters/mimir-boot-starter-feign -am -Pci test`（受影响 Feign 模块 19 项通过；Reactor 共 209 项通过）。

**验收标准**：Dubbo 两端共享同一追踪上下文；Feign 包装不改变用户选择的 HTTP Client 与负载均衡行为。

## Phase 3：补齐工程质量

### R-010 至 R-014

- [ ] 删除或实现 `WebProperties.Security` 中未生效的公开配置。
- [ ] 为 Trace ID 增加格式、长度与控制字符校验。
- [ ] 为 `MimirBootTest` 的有效属性补齐 `@AliasFor`，移除无效属性。
- [ ] 将 JaCoCo 排除缩小到无行为的纯配置载体，恢复自动配置的行为测试覆盖。
- [ ] 移除 `compile` 阶段的 `spotless:apply`；保留显式格式化命令和 CI `spotless:check`。
- [ ] 为 deploy、source、javadoc 等 Maven 插件锁定版本。
- [ ] 更新 Starter README、产品能力说明和迁移说明。

**验收标准**：CI 无 Maven 插件版本警告；`@MimirBootTest` 不再产生 Spring 弃用警告；公开配置均有实现、弃用说明或删除记录。

## 建议执行顺序

```mermaid
flowchart LR
    P0["Phase 0：Nacos 生命周期"] --> P1["Phase 1：安全与敏感数据"]
    P1 --> P2["Phase 2：RPC 传播"]
    P2 --> P3["Phase 3：工程质量"]
    P3 --> V["全量 verify -Pci 与复审"]
```

每个 Phase 完成后应执行：

```bash
./mvnw -B test -pl <受影响模块> -am -Pci
./mvnw -B verify -Pci
```

如安全默认值涉及行为变化，应先发布迁移说明，再在下一个兼容性版本中调整默认值。
