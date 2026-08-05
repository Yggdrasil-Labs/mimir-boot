---
id: overall-design-review
status: in-progress
owner: Yggdrasil Labs
created: 2026-07-30
updated: 2026-08-05
---

# 整体设计审查治理清单

## 审查结论

项目的 `Parent -> BOM -> Common -> Starters` 分层方向合理，但当前存在 5 项 P1
和 5 项 P2。工程门禁当前通过，不等同于设计治理完成。

## 优先级定义

- **P1**：影响质量门禁、安全边界、运行时正确性或 Starter 组合性，本版本必须关闭。
- **P2**：影响长期兼容性、维护成本或事实一致性，本版本必须形成决策并纳入可追踪计划。

## 2026-08-02 范围冻结决策

| ID | 已确认方向 | 本版本边界 |
|----|------------|------------|
| GOV-001 | 激活 Failsafe，使正常 `verify -Pci` 执行集成测试 | 报告缺失或测试失败均阻断 CI |
| GOV-002 | 每个组件只恢复自己拥有的 MDC 键 | 不再调用全局 `MDC.clear()` |
| GOV-003 | 默认只信任连接来源地址 | 转发头只在容器显式配置可信代理后生效 |
| GOV-004 | Jackson Module 使用追加语义 | 不替换 Boot 或消费者已注册的 Module |
| GOV-005 | 前置 Hook/Tracer fail-closed；后置与清理 best-effort | 业务异常始终是主异常，所有清理均尝试执行 |
| GOV-006 | 用户 `MybatisPlusInterceptor` 完整替换 Starter 默认实例 | 不自动合并双方内部拦截器 |
| GOV-007 | Spring Boot Maven Plugin 与依赖平台使用同一版本属性 | 不维护跨版本组合矩阵 |
| GOV-008 | v2.x 保留 `Serializable` 上界 | 只记录为 3.0 候选，不在本版本实施 |
| GOV-009 | 修正文档并增加客观事实 CI 校验 | 不自动覆盖人工文档 |
| GOV-010 | 保留企业级宽 BOM，区分“已验证”和“仅管理” | 不拆分、不精简，未消费依赖不得宣称已验证 |

以上决策完成了范围讨论，但仍需通过本目录的 Spec/Design 确认门后才能进入实施。

## P1

### GOV-001：CI 未执行现有集成测试

- **领域**：build / CI
- **证据位置**：`mimir-boot-parent/pom.xml`、`.github/workflows/ci.yml`、
  `WebAutoConfigurationIT.java`、`ExceptionAutoConfigurationIT.java`
- **问题**：Failsafe 仅存在于 `pluginManagement`，正常 `verify -Pci` 不生成 Failsafe
  报告；现有 11 个自动配置集成测试未进入 CI 门禁。
- **影响**：CI 通过不能证明 Starter 自动配置集成行为通过。
- **建议方向**：激活 Failsafe 生命周期，并让 CI 对集成测试报告缺失或测试失败显式失败。
- **关闭条件**：Java 17 全量 `verify -Pci` 成功，Failsafe 报告非空且 11 个现有
  `*IT` 均由正常生命周期执行。
- **状态**：讨论中（方向已确认）

### GOV-002：Web Starter 清空非本模块 MDC 数据

- **领域**：web / observability
- **证据位置**：`WebInterceptor.java`、`WebInterceptorTest.java`、`TraceInterceptor.java`
- **问题**：请求完成时调用 `MDC.clear()`，会删除 Micrometer、业务应用或其他 Starter
  写入的 MDC 字段。
- **影响**：破坏 Starter 组合性和调用链上下文所有权。
- **建议方向**：仅恢复或移除本 Starter 拥有的 MDC 键，并保留进入拦截器前的外部值。
- **关闭条件**：回归测试证明本 Starter 键按约定清理，同时至少 2 个无关 MDC 键保持原值。
- **状态**：讨论中（方向已确认）

### GOV-003：代理请求头可伪造审计 IP

- **领域**：common / web / log / security
- **证据位置**：`IpUtils.java`、`WebInterceptor.java`、`AccessLogFilter.java`
- **问题**：未建立可信代理边界即优先采用 `X-Forwarded-For`、`X-Real-IP` 等请求头。
- **影响**：客户端可以污染 MDC 和访问日志；该工具被用于风控、限流或授权时风险扩大。
- **建议方向**：默认使用连接来源地址，仅在显式可信代理策略下解析转发头。
- **关闭条件**：直连请求伪造转发头时审计 IP 不被覆盖；可信代理场景按确认后的策略解析，
  Web 与 Log 两个消费方行为一致。
- **状态**：讨论中（方向已确认）

### GOV-004：Jackson 定制覆盖其他模块

- **领域**：web / serialization
- **证据位置**：`JacksonConfig.java`
- **问题**：使用替换语义注册 Java Time Module，可能关闭 Jackson 和 Spring 的其他模块自动发现。
- **影响**：引入 Web Starter 后可能静默改变业务或第三方类型的序列化行为。
- **建议方向**：改为追加式模块注册，保持已有 Jackson Module 可用。
- **关闭条件**：自动配置集成测试同时验证 Mimir 时间格式和一个外部 Module 均生效。
- **状态**：讨论中（方向已确认）

### GOV-005：RPC 前置阶段异常绕过清理

- **领域**：rpc-core / reliability
- **证据位置**：`RpcHook.java`、`RpcExecutionTemplate.java`、`RpcExecutionTemplateTest.java`
- **问题**：Hook 前置处理和 tracer 注入发生在清理用 `finally` 之外，与“无论成功失败均清理”
  的接口承诺不一致。
- **影响**：前置 Hook 或 tracer 异常可能遗留上下文或资源，清理异常还可能覆盖业务异常。
- **建议方向**：统一 RPC 生命周期异常策略，记录已完成的前置步骤并按确定顺序清理。
- **关闭条件**：测试覆盖前置 Hook、注入、业务调用、后置 Hook 和清理分别抛错的路径，
  且每条路径的清理次数和最终异常均可断言。
- **状态**：讨论中（方向已确认）

## P2

### GOV-006：MyBatis 拦截器缺少完整覆盖点

- **领域**：mybatis / extensibility
- **证据位置**：`MybatisPlusAutoConfiguration.java`、`MybatisPlusAutoConfigurationTest.java`
- **问题**：Starter 创建的 `MybatisPlusInterceptor` 未建立明确的用户覆盖规则，可能与业务自定义
  实例并存或产生重复分页能力。
- **建议方向**：确认“整体 Bean 可覆盖”或“仅通过有序 `InnerInterceptor` 扩展”中的一种契约，
  再补上下文测试。
- **关闭条件**：形成兼容性决策，并有测试证明用户自定义实例不会导致重复拦截。
- **状态**：讨论中（方向已确认）

### GOV-007：Spring Boot 依赖与构建插件版本线不一致

- **领域**：parent / bom / compatibility
- **证据位置**：`mimir-boot-bom/pom.xml`、`mimir-boot-parent/pom.xml`
- **问题**：依赖平台为 Spring Boot 3.3.13，构建插件为 3.5.16，当前没有消费方兼容矩阵证明。
- **建议方向**：统一版本线，或以真实消费方构建夹具证明跨版本组合受到支持。
- **关闭条件**：版本策略被文档化，且至少一个最小 Spring Boot 应用完成 package 验证。
- **状态**：讨论中（方向已确认）

### GOV-008：公共响应模型强制业务数据实现 Serializable

- **领域**：common / public-api
- **证据位置**：`R.java`、`PageResult.java`、`DefaultExceptionResponseFactory.java`
- **问题**：HTTP/Jackson 响应数据被 Java Serialization 泛型上界约束；异常响应工厂会丢弃
  非 Serializable 附加数据。
- **建议方向**：先评估二进制、源码和序列化兼容性，再决定放宽泛型上界或保留兼容适配层。
- **关闭条件**：形成兼容性决策，消费方编译测试覆盖 Serializable 与非 Serializable 数据类型，
  且异常附加数据不被静默丢弃。
- **状态**：已关闭（v2.x 不修改，3.0 候选）

### GOV-009：架构与质量文档存在事实漂移

- **领域**：docs / governance
- **证据位置**：`ARCHITECTURE.md`、`README.md`、`docs/DOMAINS.md`、
  `docs/QUALITY_SCORE.md`
- **问题**：Starter 数量、依赖版本、模块路径和实现类名称与当前代码事实不一致。
- **建议方向**：更新既有权威文档，并评估从 POM 和源码生成或校验模块事实表。
- **关闭条件**：10 个 Starter、15 个 Reactor 模块和当前依赖版本在既有文档中一致，
  文档健康检查与 Markdown lint 均通过。
- **状态**：讨论中（方向已确认）

### GOV-010：BOM 管理范围过宽

- **领域**：bom / maintenance
- **证据位置**：`mimir-boot-bom/pom.xml`、`docs/active/tech-debt-tracker.md` 中 `TD-006`
- **问题**：约 60% 的版本声明未被当前 Starter 使用，增加依赖升级噪音并模糊平台边界。
- **建议方向**：在“精简为已消费依赖”和“拆分独立生态 BOM”之间形成产品与兼容性决策。
- **关闭条件**：完成依赖使用清单和消费方影响评估；选定方案进入版本计划，未选方案记录原因。
- **状态**：讨论中（方向已确认，保留 TD-006 直至分类文档完成）

## 实施门禁

| 门禁 | 通过标准 |
|------|----------|
| 方案确认 | GOV-001 至 GOV-010 的冻结方向均映射到已确认的 Spec 与 Design |
| P1 关闭 | GOV-001 至 GOV-005 全部有回归测试和实现证据，状态为已完成 |
| Java 门禁 | Java 17 下 15 个 Reactor 模块执行 `./mvnw -B -Pci verify` 全部成功 |
| 集成测试 | 正常 Maven 生命周期生成非空 Failsafe 报告，已有 11 个 `*IT` 全部通过 |
| 文档门禁 | 文档健康检查、Markdown lint、未暂存与精确暂存后的 diff check 全部通过，新建文件不可绕过 |
| 发布门禁 | 无 P0/P1 未关闭；P2 均已完成或记录 Owner、延期理由与目标版本 |

## 基线证据

2026-07-30 在 Java 17 下执行 `./mvnw -B -Pci verify`，15 个 Reactor 模块全部成功，
Surefire 共执行 871 个测试；正常生命周期未生成 Failsafe 报告。额外定向执行现有两个
`*IT` 类时 11 个用例全部通过，因此 GOV-001 的缺口是生命周期绑定，而不是测试当前失败。
