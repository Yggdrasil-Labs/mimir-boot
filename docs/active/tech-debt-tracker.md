---
updated: 2026-08-25
---

# 技术债务追踪

<!--!
  本文件是智能体了解代码库"已知问题"的入口。
  智能体在修改某个领域前，应先查看该领域是否有已知债务。
  智能体在完成任务后发现新的债务，应记录到此文件。

  本文件属于"长期维护清单"，永不归档；由 doc-gardening agent 持续维护。
-->

本轮 TD-001 至 TD-029 的有效部分统一关联 [v2.2.1 技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md)，当前状态为已规划、待实施；三个决策门已选择兼容方案。DG-1 保留旧枚举 fallback 的误判风险，DG-3 接受写入 v2 后不得回退到 v1-only 二进制；TD-013 作为旧追踪 SPI 残余债务继续保留，TD-016 的字段/记录级完整性债务始终只做部分缓解，TD-023 在修复框架内部调用、Feign 与 logger 后仍保留四个弃用 Hook 直调 API 的兼容风险。

| 编号 | 领域 | 问题描述 | 优先级 | 记录日期 | Owner | 关联计划 |
|------|------|----------|--------|----------|-------|----------|
| TD-001 | starter-exception | 参数校验错误消息经 LogSanitizer（ASCII 白名单）清洗后作为响应 data 返回，中文校验消息被剥空（如"用户名不能为空"→空串），破坏公开 API 契约；MethodArgumentNotValid 与 BindException 的错误项文本格式不一致（前者含字段名前缀，后者仅含消息，`MimirExceptionHandler.java:116,135`，已运行时复现） | 高 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-002 | starter-log | 日志脱敏仅匹配 key=value 形式，JSON 日志（"password":"secret"）与 URL 编码字段名均绕过，敏感值原样落盘（`SensitiveDataPattern.java:14-41`，已运行时复现） | 高 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-003 | starter-dubbo | RpcDubboFilter 附件值为 null 时 Collectors.toMap 在 try 块外抛 NPE，整次 RPC 中断、Hook 不触发；现有测试仅覆盖整表 null 未覆盖值 null（`RpcDubboFilter.java:56-61`） | 高 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-004 | starter-rpc-core / dubbo | 异步提供端 MDC 上下文跨线程丢失（全仓无 TTL），traceScope 在异步完成前 close，业务线程与 whenCompleteWithContext 回调拿不到 traceId/requestId，链路日志断裂（`RpcDubboFilter.java:89-108`） | 高 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-005 | starter-log | 脱敏预置规则敏感字段覆盖不全且公私钥方向反：SECRET 含"公钥"却缺"私钥/privateKey/secretKey/accessKey"（`SensitiveDataPattern.java:20`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-006 | starter-log / mybatis | logback-spring.xml 访问日志与 SQL 日志 appender 用 %msg 未经过 %mask 脱敏；SQL 参数脱敏关键词覆盖不全，BoundSql 全文也未经脱敏，结构化 SQL 中的敏感参数或字面量可落盘（`logback-spring.xml:110,152`、`JsonSqlLogInnerInterceptor.java:36-40`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-007 | starter-log | SensitiveDataConverterTest.testThreadSafety 假绿：断言 6 星但默认替换为 4 星，且断言位于 executor.submit 内异常被吞，测试永不失败（`:604`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-008 | starter-log | 脱敏配置文档与实现不符：LogMaskProperties 无 enabled 字段，converter 注释示例键名 patterns 不存在、默认替换值写 ******（实际 ****），按文档配置静默不生效 | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-009 | starter-log | LogMaskAutoConfiguration 强转 (LoggerContext) getILoggerFactory()，非 Logback 绑定时抛 ClassCastException 导致启动失败（`:45`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-010 | starter-dubbo | RpcDubboSupportHolder 三个静态可变字段无 volatile/同步，Spring 写入与 Dubbo SPI 读取无可见性保证，父子/多 ApplicationContext 相互覆盖（`RpcDubboSupportHolder.java:12-27`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-011 | starter-test | 类路径 application-test.yml 对下游隐性约束：ddl-auto: create-drop（误连真实库有删表风险）、show-sql: true、硬编码 spring.application.name | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-013 | starter-rpc-core | MdcRpcTracerBridge.extract()（非 scope 版）不回滚 MDC；自定义 Bridge 仅实现 extract 时上下文泄漏（`MdcRpcTracerBridge.java:30-32`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-015 | starter-nacos | 遗留 AES 迁移 API 实为 ECB（无 IV、无认证），公开 API 可被误用于新敏感数据形成弱密文；自动解密路径已封死（`ConfigCryptoUtils.java:29,189-206`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-016 | starter-mybatis | 字段加密 GCM 无字段/记录级 AAD 完整性绑定，同密钥下密文仍可跨列/行互换并通过认证；v2.2.1 仅提供应用级上下文绑定，不能关闭该风险，需后续实体感知设计与迁移方案 | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-017 | parent/pom | 根 pom gpg sign-artifacts 在 verify 阶段 skip=false，与"默认跳过签名"注释矛盾；本地 ./mvnw verify 会触发 GPG 签名（-Pci 才跳过） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-018 | docs | AGENTS.md 将 ./mvnw verify 描述为"测试+质量门禁"，但默认 dev profile 跳过 Spotless/JaCoCo，Enforcer 仅在 ci profile 绑定 | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-019 | 仓库根 | LICENSE 文件缺失：pom 声明 Apache-2.0、BOM README 链接 ../LICENSE 断链（git 历史中亦无此文件） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-020 | docs | ARCHITECTURE.md 技术栈表与真实解析版本漂移（经 ~/.m2 产物与依赖树核实）：MyBatis-Plus 表 3.5.14→实际 3.5.17；Lombok 表 1.18.42→实际 1.18.46；Hutool 表 5.8.41→BOM 5.8.47（全仓未使用）；"Logback+SLF4J 2.0.17"行错误——Logback 不存在 2.0.17 版本（实际解析 1.5.18），slf4j-api 为 2.0.17、桥接器 2.0.18；MySQL/PostgreSQL 表 8.4/42.7→Boot 3.3.13 实际管理 8.3.0/42.7.7 | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-025 | starter-nacos | Nacos 解密 EnvironmentPostProcessor 通过 spring.factories 无条件注册，且 enabled 默认 true；未配置 `mimir.boot.nacos.encrypt` 或旧前缀的应用只要任意属性含 `ENC(` 就会进入密钥校验并可能启动失败。starter 自身已强依赖 Nacos Config，使用 Nacos 类路径作为门控无法区分该场景（`NacosEncryptEnvironmentPostProcessor.java:20-23`、`ConfigDecryptProcessor.java:57-78`） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-029 | mimir-boot-bom | BOM 两个管理坐标在 Central 不存在，下游一旦引用必然解析失败：org.apache.rocketmq:rocketmq-spring-boot-starter:5.2.0（该构件真实版本止于 2.3.6，5.2.0 疑与 rocketmq-client 5.x 混淆）；org.elasticsearch.client:elasticsearch-java:8.11.0（该 group 下无此构件，正确坐标为 co.elastic.clients:elasticsearch-java） | 中 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-021 | common | 分页模型 null/负数无防御：PageResult 构造器对 null pageSize/totalCount/pageIndex 拆箱 NPE、负数算错 totalPages；PageRequest.getOffset 经 setter 置 null 后 NPE | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-022 | common | 枚举 fromCode 默认值与 isXxx 判定矛盾（CommonStatus/DeleteFlag/ErrorCode），未知码静默归一化；MdcUtil.putAll 实为整体替换、put(null) 静默忽略 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-012 | starter-rpc-core | RpcExecutionTemplate 注册为公共 bean 但未接入任何 RPC 过滤器，与 RpcDubboFilter/RpcFeignClient 形成两套并行 Hook 语义 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-014 | starter-test | 日志断言重复实现（AssertUtils/LogTestUtils）且依赖 Logback 内部 appender.list 与消息格式，日志格式变动即失效 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-023 | starter-rpc-core / feign | RpcHookChain 废弃的 before/after/onError/cleanup 方法直接遍历 Hook，绕过 RpcHookInvocation 的调用级状态与异常隔离；Feign uri.getHost() 可能 null、多值头仅取首个；RpcHookLifecycle 日志器类名错误 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-024 | starter-mybatis | 死代码与误导注释：日志配置条件注解下的死分支、getFinalMapperPackages 无调用点、自动检测注释与实现矛盾、PROFILE_DEV/TEST 死常量；README 用过时包名 extension.service.IService；AuditMetaObjectHandler.safeAuditor 静默降级无日志 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-026 | starter-test | 测试基建三份重复 setUp/tearDown；TestUtils 魔法字符串与 randomUserId 毫秒碰撞；BaseUnitTest 注释误导；MimirBootTest 废弃属性与 TestAutoConfiguration 死类 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-027 | 构建/CI | 根/父 pom spotless googleJavaFormat 1.22.0 与 1.23.0 不一致；BOM 孤儿属性 redis.version/kafka.version；scripts/test-suite-consumer.sh 硬编码 2.1.2-SNAPSHOT；ci-preflight.sh 硬编码 failsafe 报告/用例阈值 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-028 | docs / 仓库卫生 | docs/index.md 链接不存在的 docs/generated/；module-boundaries.md 提到不存在的 docs/exec-plans/；README 徽章 Maven 3.9.9（wrapper 实为 3.9.16）与快速开始示例 2.1.0 | 低 | 2026-08-16 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |

## 优先级含义（智能体行为指南）

- **紧急**：阻碍智能体有效工作或导致生产事故。智能体应优先处理。
- **高**：降低质量评分，应在本迭代解决。智能体遇到时应顺手修复。
- **中**：已知缺口，已有改进计划。智能体按计划处理。
- **低**：锦上添花。智能体在有空闲时间时处理。

## Owner 与孤儿模块规则

- **每条债务必须有 Owner**：可以是人、团队、或专项智能体（如 `doc-gardening`、`cleanup`）
- **孤儿模块**：代码路径没有对应的 product-spec / design-doc，或长期无人维护的模块
  - 发现时在本表登记一行，Owner 填 `ORPHAN`
  - 30 天内未指派 Owner 的孤儿模块，建议在下一次架构回顾中**归档或删除**
- 解决后删除该行，并在关联计划的决策日志中记录解决方式

## 如何记录新债务

1. 在上方表格中添加一行
2. 根据定义分配优先级
3. 填写 Owner（无 Owner 写 `ORPHAN`）
4. 设计阶段先链接需求方案；创建执行计划后，将关联列统一更新为 `docs/active/{版本}/{需求}/plan.md`
5. 解决后删除该行，并在关联计划的决策日志中记录解决方式
