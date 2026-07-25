---
status: completed
reviewed-date: 2026-07-17
updated: 2026-07-25
baseline-commit: 0fd2f61
---

# 已实现能力复审报告

## 结论

基线提交 `0fd2f61` 不满足直接发布条件。复审当时发现 1 项 P0、8 项 P1 和 5 项 P2：
Nacos 启动期解密不会在正常 Spring Boot 生命周期中执行，CORS 默认策略、日志脱敏、密钥管理、
RPC 上下文传播和工程质量门禁也存在缺口。

截至 2026-07-25，R-001 至 R-014 均已修复并通过对应回归测试；全仓 `-Pci verify`、覆盖率门禁、
发布 POM 检查和文档 lint 均通过。当前代码满足本轮复审定义的发布质量门槛；实际发布仍应遵循签名、
制品上传和远端 CI 等常规发布流程。

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
- **修复状态（2026-07-18）**：提交 `be073c1` 新增并注册 `NacosEncryptEnvironmentPostProcessor`；真实 `SpringApplication` 测试验证 Bean 创建前解密，刷新测试验证密文变化后更新明文，属性源优先级测试确认高优先级明文不被覆盖。

## P1：高优先级修复项

### R-002：CORS 默认允许任意来源携带凭证

- **位置**：`mimir-boot-starter-web/.../WebProperties.java:62-88`、`CorsConfig.java:46-71`
- **影响**：默认 `*` Origin、任意请求头与 `allowCredentials=true` 组合会向任意 Origin 反射放行凭证，Cookie/会话认证接入方存在跨站数据访问风险。
- **修复状态（2026-07-19）**：已通过提交 `2e8e798` 修复。默认不注册 CORS Filter；启用时要求显式 Origin 白名单，并由 Spring 校验拒绝携带凭证的通配 Origin。MockMvc 覆盖预检、非白名单来源和携带 Cookie 的请求。

### R-003：Nacos 属性前缀、动态刷新与密码学策略不可靠

- **位置**：`NacosEncryptAutoConfiguration.java:34-39`、`NacosEncryptProperties.java:18`、`ConfigCryptoUtils.java:81-91`、`ConfigDecryptProcessor.java:66-77`
- **影响**：条件前缀与属性绑定前缀不一致；已添加解密属性源后，刷新事件被直接跳过；默认 `AES` 未指定认证模式与随机 IV。
- **修复状态（2026-07-19）**：已通过提交 `44ca087` 修复。当前前缀统一为 `mimir.boot.nacos.encrypt`，旧前缀在迁移期会告警；默认格式为含版本与随机 IV 的 AES-GCM，启动期校验密钥和算法，并覆盖刷新、错误密钥、篡改密文及旧格式迁移测试。

### R-004：Nacos 解密流程会写出敏感配置

- **位置**：`ConfigDecryptProcessor.java:141,174`
- **影响**：DEBUG 日志输出明文，ERROR 日志输出完整密文；日志系统成为密钥、数据库口令和令牌的泄露渠道。
- **修复状态（2026-07-19）**：已通过提交 `44ca087` 修复。正常日志只记录解密数量，失败日志只记录属性名；日志捕获测试确认明文和密文均不会写入日志。

### R-005：MyBatis 字段加密会生成进程内临时密钥

- **位置**：`MybatisPlusCryptoConfiguration.java:25-33`
- **影响**：启用加密但未显式配置密钥时，重启后无法解密已有数据。
- **修复状态（2026-07-19）**：已通过提交 `a63fdf2` 修复。启用字段加密时，所有 profile 均必须配置稳定密钥或显式提供 `CryptoKeyProvider`，不再生成临时密钥；同时增加两个独立 Spring 应用上下文之间的加密/解密回归测试。

### R-006：SQL 日志未可靠脱敏标量及 Map 参数

- **位置**：`SqlLogMaskUtils.java:50-60,152-170`、`JsonSqlLogInnerInterceptor.java:34-40`
- **影响**：`@Param("password") String password` 和 `Map<String, Object>` 的敏感键值可原样进入 SQL 日志。
- **修复状态（2026-07-19）**：已在当前分支修复。对 `Map` 形式的 SQL 参数，内置拒绝表会在 JSON 序列化前脱敏 password、token、secret、authorization 及常见变体；同时保留 `@SensitiveField` 对对象字段的脱敏，并增加日志捕获回归测试。

### R-007：访问日志缓存完整响应并记录查询参数

- **位置**：`AccessLogFilter.java:54-70,109-120`
- **影响**：下载、SSE 和大响应会产生额外内存压力或破坏流式语义；URL 内 token、验证码等会落盘。
- **修复状态（2026-07-19）**：已在当前分支修复。访问日志只使用 `requestURI`，不提供记录 query string 的配置开关；移除响应缓存包装并直接读取 Servlet 响应状态。回归测试覆盖携带 token 的 query、SSE、下载和 1 MiB 响应，验证下游写入会立即进入原始响应。

### R-008：Dubbo Provider 没有提取入站追踪上下文

- **位置**：`RpcDubboFilter.java:23,53-87`
- **影响**：Filter 同时用于 Consumer 与 Provider，却只调用 `inject`；Provider 无法续接上游 Trace。
- **修复状态（2026-07-22）**：已在当前分支修复。Filter 按 Dubbo URL 的 `side` 参数分支：Consumer 注入并写入 attachments，Provider 在 Hook 前提取入站上下文且不再注入。单元测试覆盖 Provider 成功、异常与清理路径；进程内 Consumer → Provider 端到端测试验证 trace attachment 连续传递。

### R-009：Feign 包装可能绕过用户 Client

- **位置**：`FeignAutoConfiguration.java:24-40`
- **影响**：已有 `Client` 时 `@ConditionalOnMissingBean` 使包装器不创建；没有 delegate 时回退 `Client.Default`，可能丢失负载均衡或自定义 HTTP Client 行为。
- **修复状态（2026-07-23）**：已通过当前分支修复。自动配置改为注册 `RpcFeignCapability`，由 OpenFeign 在选择最终 `Client` 后统一装饰，因此不替换用户的自定义 Client 或 Spring Cloud 负载均衡 Client；同时以显式自动配置 Bean 名消除与 Spring Cloud `FeignAutoConfiguration` 的命名冲突。OpenFeign 应用上下文集成测试覆盖自定义 Client 与 RPC Hook 的共同执行；敏感请求头 `Authorization`、`Cookie` 不再进入 RPC attachments，原始 HTTP 请求仍保留认证信息。

## P2：后续工程债务

| 编号 | 位置 | 问题 | 修复方向 | 状态 |
|------|------|------|----------|------|
| R-010 | `WebProperties.java` | 请求大小、文件大小、XSS 等公开配置未被运行时消费 | 2.x 保留弃用 no-op API，并说明 Spring Boot multipart 与应用侧 XSS 防护迁移路径 | 已修复（2026-07-24） |
| R-011 | `TraceInterceptor.java` | 外部 Trace ID 未限制长度或字符集，直接进入 MDC 与响应头 | 限制格式和长度；请求头存在但无效时重新生成且不复用 MDC | 已修复（2026-07-24） |
| R-012 | `MimirBootTest.java` | 约定式注解属性覆盖已被 Spring 弃用 | 有效属性使用 `@AliasFor`；无效属性保留弃用 no-op 以兼容 2.x | 已修复（2026-07-24） |
| R-013 | `mimir-boot-parent/pom.xml` | JaCoCo 排除全部 `config/**`，高风险自动配置不受覆盖率约束 | 移除宽泛排除，以 Web MVC 和 RPC Core 行为测试覆盖配置逻辑 | 已修复（2026-07-24） |
| R-014 | `mimir-boot-parent/pom.xml` | `compile` 阶段执行 `spotless:apply`，且部分 Maven 插件版本未锁定 | 将格式化改为显式命令；在发布 parent 的 pluginManagement 锁定插件版本 | 已修复（2026-07-24） |

## 通过门槛

1. R-001 至 R-014 全部关闭并有对应回归测试。
2. `./mvnw -B verify -Pci` 成功，且不再输出 Spring 注解弃用警告或 Maven 插件版本警告。
3. CORS、Nacos、字段加密、SQL 日志、Dubbo 与 Feign 均至少具备一个真实框架集成测试。
4. 安全默认值变更附带迁移说明和兼容性说明。

## 最终验证

2026-07-25 在 Java 17、Spring Boot 3.3.13 环境执行：

```bash
mise exec java@17 -- ./mvnw -o -B -Pci verify
node /mnt/c/Users/YangYang/.kiro/skills/docs-evolve/scripts/lint-docs.mjs
```

Maven Reactor 的 15 个项目全部成功，JaCoCo、Spotless、Enforcer、source/javadoc 和发布 POM 检查均通过；
Dubbo 26 项、Feign 19 项、RPC Core 18 项、Web 43 项和 Test Starter 141 项测试均无失败或错误。
flattened parent 中 `maven-deploy-plugin` 为 3.1.4 且默认 `skip=true`；文档健康检查通过。
