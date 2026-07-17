---
status: active
reviewed-date: 2026-07-17
baseline-commit: 0fd2f61
---

# 已实现能力复审报告

## 结论

当前版本不满足直接发布条件。复审发现 1 项 P0 和 8 项 P1：Nacos 配置加密的启动期解密不会在正常 Spring Boot 生命周期中执行；此外，CORS 默认策略、日志脱敏、密钥管理和 RPC 上下文传播均存在高风险缺口。

在 P0 与所有 P1 关闭并通过对应验收测试前，不应将相关能力作为生产级默认能力推广。

## 范围与验证证据

审查范围：`common`、`web`、`log`、`mybatis`、`nacos`、`rpc-core`、`dubbo`、`feign`、`test` Starter，以及其公开 README、自动配置和 Maven 构建配置。

已执行的验证命令：

```bash
mise exec java@17 -- ./mvnw -B -pl \
  mimir-boot-starters/mimir-boot-starter-web,\
  mimir-boot-starters/mimir-boot-starter-log,\
  mimir-boot-starters/mimir-boot-starter-mybatis,\
  mimir-boot-starters/mimir-boot-starter-nacos,\
  mimir-boot-starters/mimir-boot-starter-rpc-core,\
  mimir-boot-starters/mimir-boot-starter-dubbo,\
  mimir-boot-starters/mimir-boot-starter-feign \
  -am test
```

该 Maven Reactor 以 `BUILD SUCCESS` 结束，Surefire 报告未包含失败或错误。该命令未启用 `ci` profile，因此 JaCoCo 报告在此次针对性测试中被跳过；测试通过不能证明下述生命周期、协议联调和安全行为正确。

## 分级说明

| 级别 | 含义 | 当前数量 |
|------|------|---------:|
| P0 | 核心目标无法达成或可能造成生产事故 | 1 |
| P1 | 高概率功能、安全或数据可用性问题 | 8 |
| P2 | 工程质量、可维护性或验证缺口 | 5 |

## P0：发布阻塞

### R-001：Nacos 启动期解密监听器不会收到目标事件

- **位置**：`mimir-boot-starter-nacos/.../NacosEncryptAutoConfiguration.java:41-61`
- **证据**：`ApplicationEnvironmentPreparedEvent` 在 ApplicationContext 创建及自动配置 Bean 实例化之前发布。作为自动配置 Bean 的 `ApplicationListener` 不会收到已发生的事件。
- **影响**：文档承诺的“启动早期解密”不成立；依赖 Nacos 密文的连接配置、凭证配置可能以 `ENC(...)` 形式参与初始化并导致应用启动失败。
- **修复方向**：以 Spring Boot 注册机制实现 `EnvironmentPostProcessor`，在 Environment 准备阶段处理；补充真实启动上下文和 Nacos 属性源的集成测试。

## P1：高优先级修复项

### R-002：CORS 默认允许任意来源携带凭证

- **位置**：`mimir-boot-starter-web/.../WebProperties.java:62-88`、`CorsConfig.java:46-71`
- **影响**：默认 `*` Origin、任意请求头与 `allowCredentials=true` 组合会向任意 Origin 反射放行凭证，Cookie/会话认证接入方存在跨站数据访问风险。
- **修复方向**：默认关闭或空白名单；凭证开启时拒绝 `*`；增加真实预检与带凭证请求测试。

### R-003：Nacos 属性前缀、动态刷新与密码学策略不可靠

- **位置**：`NacosEncryptAutoConfiguration.java:34-39`、`NacosEncryptProperties.java:18`、`ConfigCryptoUtils.java:81-91`、`ConfigDecryptProcessor.java:66-77`
- **影响**：条件前缀与属性绑定前缀不一致；已添加解密属性源后，刷新事件被直接跳过；默认 `AES` 未指定认证模式与随机 IV。
- **修复方向**：统一属性前缀；刷新时按来源重建解密属性源；改用版本化 AES-GCM 密文格式，并为旧密文提供迁移路径。

### R-004：Nacos 解密流程会写出敏感配置

- **位置**：`ConfigDecryptProcessor.java:141,174`
- **影响**：DEBUG 日志输出明文，ERROR 日志输出完整密文；日志系统成为密钥、数据库口令和令牌的泄露渠道。
- **修复方向**：仅记录配置键名和数量，必要时对键名脱敏；禁止记录明文、密文及原始异常值。

### R-005：MyBatis 字段加密会生成进程内临时密钥

- **位置**：`MybatisPlusCryptoConfiguration.java:25-33`
- **影响**：启用加密但未显式配置密钥时，重启后无法解密已有数据。
- **修复方向**：除明确的 local/test profile 外，未提供稳定密钥或 `CryptoKeyProvider` 时 Fail Fast；增加跨重启读写验证。

### R-006：SQL 日志未可靠脱敏标量及 Map 参数

- **位置**：`SqlLogMaskUtils.java:50-60,152-170`、`JsonSqlLogInnerInterceptor.java:34-40`
- **影响**：`@Param("password") String password` 和 `Map<String, Object>` 的敏感键值可原样进入 SQL 日志。
- **修复方向**：默认不记录参数值，或使用内置敏感键拒绝表；覆盖标量、Map、嵌套对象和集合测试。

### R-007：访问日志缓存完整响应并记录查询参数

- **位置**：`AccessLogFilter.java:54-70,109-120`
- **影响**：下载、SSE 和大响应会产生额外内存压力或破坏流式语义；URL 内 token、验证码等会落盘。
- **修复方向**：不包装响应体，仅使用原始状态码；默认忽略 query string，必要时按键名脱敏。

### R-008：Dubbo Provider 没有提取入站追踪上下文

- **位置**：`RpcDubboFilter.java:23,53-87`
- **影响**：Filter 同时用于 Consumer 与 Provider，却只调用 `inject`；Provider 无法续接上游 Trace。
- **修复方向**：按调用角色分支：Consumer 注入、Provider 提取；以真实双端调用验证上下文连续性。

### R-009：Feign 包装可能绕过用户 Client

- **位置**：`FeignAutoConfiguration.java:24-40`
- **影响**：已有 `Client` 时 `@ConditionalOnMissingBean` 使包装器不创建；没有 delegate 时回退 `Client.Default`，可能丢失负载均衡或自定义 HTTP Client 行为。
- **修复方向**：采用 Feign 的装饰扩展点保留 delegate；增加 OpenFeign 集成测试。

## P2：后续工程债务

| 编号 | 位置 | 问题 | 修复方向 |
|------|------|------|----------|
| R-010 | `WebProperties.java:151-177` | 请求大小、文件大小、XSS 等公开配置未被运行时消费 | 实现、明确委托关系，或弃用删除 |
| R-011 | `TraceInterceptor.java:51-64` | 外部 Trace ID 未限制长度或字符集，直接进入 MDC 与响应头 | 限制格式和长度；无效值重新生成 |
| R-012 | `MimirBootTest.java:38` | 约定式注解属性覆盖已被 Spring 弃用 | 使用 `@AliasFor` 或移除无效属性 |
| R-013 | `mimir-boot-parent/pom.xml:172-178` | JaCoCo 排除全部 `config/**`，高风险自动配置不受覆盖率约束 | 缩小排除范围，以行为测试覆盖配置逻辑 |
| R-014 | `mimir-boot-parent/pom.xml:337-360` | `compile` 阶段执行 `spotless:apply`，且部分 Maven 插件版本未锁定 | 将格式化改为显式命令，CI 仅 check；锁定插件版本 |

## 通过门槛

1. R-001 至 R-009 全部关闭并有对应回归测试。
2. `./mvnw -B verify -Pci` 成功，且不再输出 Spring 注解弃用警告或 Maven 插件版本警告。
3. CORS、Nacos、字段加密、SQL 日志、Dubbo 与 Feign 均至少具备一个真实框架集成测试。
4. 安全默认值变更附带迁移说明和兼容性说明。
