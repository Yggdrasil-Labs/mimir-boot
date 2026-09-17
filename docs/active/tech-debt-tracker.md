---
updated: 2026-09-16
---

# 技术债务追踪

本清单只记录仍处于活跃状态、需要后续设计、修复、兼容迁移或明确接受的技术债务。条目按编号升序排列；已解决的债务从本表移除，并在关联计划的决策日志中保留处理记录。

## 维护规则

- 新增债务时使用下一个未占用的 `TD-xxx` 编号，并在本页补全领域、优先级、状态、Owner、记录日期和处置目标。
- **高**：存在发布消费者不可用、数据泄露、配置错误或核心行为错误的风险，应优先纳入最近迭代。
- **中**：质量门禁、测试可靠性或兼容性存在缺口，需要排期处理。
- **低**：影响受限的兼容包袱或测试维护项，可随相关改动一并处理。
- `兼容保留` 表示框架已规避内部主路径，但公开旧接口仍保留已知风险；只有完成迁移或废弃后才能移除。
- Owner 不明确时填写 `ORPHAN`；30 天内未指派的条目应在架构回顾中决定归档、删除或分配维护者。

## 活跃清单

| 编号 | 主题 | 领域 | 优先级 | 状态 | Owner | 记录日期 | 关联计划 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [TD-013](#td-013-rpc-mdc-scope) | RPC MDC scope 兼容入口 | starter-rpc-core | 中 | 兼容保留 | YoungerYang-Y | 2026-08-29 | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| [TD-016](#td-016-字段加密-aad) | 字段加密 AAD 绑定 | starter-mybatis | 中 | 待规划 | YoungerYang-Y | 2026-08-29 | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| [TD-023](#td-023-rpc-hook-legacy-api) | RPC Hook 旧 API 兼容 | starter-rpc-core / feign | 低 | 兼容保留 | YoungerYang-Y | 2026-08-29 | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| [TD-036](#td-036-parent-flatten-属性覆盖) | 发布 Parent 属性覆盖 | 发布 Parent / 构建 | 高 | 待规划 | YoungerYang-Y | 2026-09-10 | 待制定修复计划 |
| [TD-037](#td-037-okhttp-jvm-制品) | OkHttp JVM 制品坐标 | BOM / 依赖治理 | 高 | 待规划 | YoungerYang-Y | 2026-09-10 | 待制定修复计划 |
| [TD-038](#td-038-日志-json-脱敏) | 日志 JSON 脱敏 | starter-log / 数据安全 | 高 | 待规划 | YoungerYang-Y | 2026-09-10 | 待制定修复计划 |
| [TD-039](#td-039-nacos-解密覆盖层) | Nacos 解密覆盖层清理 | starter-nacos / 配置刷新 | 高 | 待规划 | YoungerYang-Y | 2026-09-10 | 待制定修复计划 |
| [TD-040](#td-040-mongodb-驱动族) | MongoDB 驱动族兼容 | BOM / MongoDB 兼容性 | 高 | 待规划 | YoungerYang-Y | 2026-09-13 | 待制定修复计划 |
| [TD-041](#td-041-springdoc-boot-兼容性) | Springdoc 与 Boot 基线兼容 | BOM / Springdoc 兼容性 | 高 | 待规划 | YoungerYang-Y | 2026-09-13 | 待制定修复计划 |
| [TD-042](#td-042-分页参数校验) | 分页参数校验边界 | common / 分页绑定 | 高 | 待规划 | YoungerYang-Y | 2026-09-13 | 待制定修复计划 |
| [TD-043](#td-043-jacoco-集成测试覆盖率) | JaCoCo 集成测试覆盖率 | Parent / 覆盖率报告 | 中 | 验收中 | YoungerYang-Y | 2026-09-16 | 配置已调整，待专项证据闭环 |
| [TD-044](#td-044-spotless-子模块门禁) | Spotless 子模块格式门禁 | Parent / 格式门禁 | 中 | 验收中 | YoungerYang-Y | 2026-09-16 | 配置已调整，待专项负向验收 |
| [TD-045](#td-045-日志断言索引边界) | 日志断言索引边界 | starter-test / 日志断言边界 | 中 | 待规划 | YoungerYang-Y | 2026-09-13 | 待制定修复计划 |
| [TD-046](#td-046-测试清理顺序) | 测试清理顺序 | starter-test / 清理顺序 | 低 | 待安排维护 | YoungerYang-Y | 2026-09-13 | 待安排维护 |

## 债务明细

<a id="td-013-rpc-mdc-scope"></a>

### TD-013：RPC MDC scope 兼容入口

- **现状与风险**：`MdcRpcTracerBridge.extract()` 的非 scope 入口不回滚 MDC；自定义 Bridge 只实现 `extract` 时仍可能泄漏上下文。
- **已缓解范围**：框架内部调用已迁移到调用级 scope，旧入口仅为兼容保留（证据：`bf493b6`、`2dd1b83`、`4e428e6`）。
- **处置与验收**：完成旧入口迁移或废弃，并验证自定义 Bridge 异常、嵌套调用和异步调用后 MDC 均被恢复。

<a id="td-016-字段加密-aad"></a>

### TD-016：字段加密 AAD 绑定

- **现状与风险**：字段加密没有字段或记录级 AAD 完整性绑定；在相同密钥和应用 context 下，密文可能被跨列或跨行互换。
- **已缓解范围**：v2.2.1 仅提供应用级 context 绑定，不能消除此风险（证据：`c6006b2`、`4e428e6`）。
- **处置与验收**：设计可迁移的字段/记录级 AAD，并验证跨字段、跨记录和历史密文兼容场景。

<a id="td-023-rpc-hook-legacy-api"></a>

### TD-023：RPC Hook 旧 API 兼容

- **现状与风险**：`RpcHookChain` 废弃的 `before`、`after`、`onError`、`cleanup` 直调 API 仍可使用，绕开调用级 invocation 的生命周期约束。
- **已缓解范围**：框架内部已改用调用级 invocation，旧直调入口仅为兼容保留（证据：`bf493b6`、`2dd1b83`、`4e428e6`）。
- **处置与验收**：提供迁移路径和废弃周期；删除或隔离旧入口后，验证正常、异常和清理回调顺序。

<a id="td-036-parent-flatten-属性覆盖"></a>

### TD-036：发布 Parent 属性覆盖

- **现状与风险**：发布 Parent 的 flatten 配置在发布时解析 `pluginManagement` 属性；下游覆盖 `java.version`、编译插件版本或 JaCoCo 门槛不会影响插件实际配置。
- **处置与验收**：保留可继承的延迟解析语义，并以真实发布消费者验证上述属性覆盖均生效。

<a id="td-037-okhttp-jvm-制品"></a>

### TD-037：OkHttp JVM 制品坐标

- **现状与风险**：BOM 管理的 `com.squareup.okhttp3:okhttp:5.5.0` 不提供 Java 直接使用的 `okhttp3` 类；消费者仅导入 BOM 并声明该依赖时，编译 `OkHttpClient` 失败。
- **处置与验收**：确认 JVM 制品坐标与版本组合，并添加只导入发布 BOM 的 API 编译和调用验证。

<a id="td-038-日志-json-脱敏"></a>

### TD-038：日志 JSON 脱敏

- **现状与风险**：启用 `api_key`、`account` 等预置规则后，普通 `key=value` 能脱敏，带引号的 JSON 字段不匹配，敏感值可能写入日志。
- **处置与验收**：统一字段型规则的引号感知处理，并覆盖 JSON、转义字符和普通赋值格式。

<a id="td-039-nacos-解密覆盖层"></a>

### TD-039：Nacos 解密覆盖层清理

- **现状与风险**：删除 Nacos 加密配置前缀时，刷新监听器不处理删除事件，也不移除旧 `decryptedProperties:*` 覆盖层；历史明文可继续覆盖底层配置。
- **处置与验收**：在前缀消失时清理覆盖层，并验证删除、明文切换、重新加密和重新绑定场景。

<a id="td-040-mongodb-驱动族"></a>

### TD-040：MongoDB 驱动族兼容

- **现状与风险**：BOM 将 `mongodb-driver-sync` 固定为 `4.11.5`，但 `mongodb-driver-core`、`bson` 仍由 Spring Boot 管理为 `5.0.1`；`MongoClients.create(...)` 在连接前抛出 `NoClassDefFoundError: com/mongodb/connection/StreamFactory`。
- **处置与验收**：统一 MongoDB 驱动族版本并验证 Spring Data 兼容性；增加只导入发布 BOM 的客户端初始化测试。

<a id="td-041-springdoc-boot-兼容性"></a>

### TD-041：Springdoc 与 Boot 基线兼容

- **现状与风险**：托管的 `springdoc-openapi-starter-webmvc-ui:2.9.1` 引用当前 `spring-webmvc:6.1.21` 不存在的 `LiteWebJarsResourceResolver`；加载 `SwaggerResourceResolver` 时抛出 `NoClassDefFoundError`。
- **处置与验收**：选择与 Spring Boot 3.3 基线兼容的版本，并覆盖消费者启动、`/v3/api-docs` 与 Swagger UI 资源访问；框架基线升级仍需 RFC。

<a id="td-042-分页参数校验"></a>

### TD-042：分页参数校验边界

- **现状与风险**：`PageRequest` 的无参构造和 Lombok setter 不校正输入；Jackson 绑定后，直接 getter 与 `PageQuery.toPageRequest()` 可返回负页码和超大页大小，和 Common README 的自动校验描述不一致。
- **已缓解范围**：`PageConverters.toMybatisPage()` 已显式校正，经过该转换器的路径不受影响。
- **处置与验收**：统一绑定、构造与转换边界的校验契约，并对负页码、零页大小和超大页大小建立回归测试。

<a id="td-043-jacoco-集成测试覆盖率"></a>

### TD-043：JaCoCo 集成测试覆盖率

- **历史问题**：JaCoCo `report` 曾绑定到 `test`，早于 Failsafe 集成测试，导致 XML 遗漏后续覆盖数据；历史重生成 Web 报告时，指令覆盖率从 `75.45%` 升至 `95.85%`，该数值不作为当前验收证据。
- **当前状态**：`mimir-boot-parent/pom.xml` 已将 `report` 绑定到 `verify`；仍需以集成测试专属覆盖场景和最终报告证据完成验收。
- **处置与验收**：在集成测试结束后生成最终报告，明确执行数据合并方式，并验证上传的 XML 包含 Failsafe 覆盖数据。

<a id="td-044-spotless-子模块门禁"></a>

### TD-044：Spotless 子模块格式门禁

- **历史问题**：根 POM 的 Spotless 活动配置 `__NO_SOURCES__` 曾被子模块继承，覆盖 Parent 的源码范围，造成检查未跳过但未扫描 Java 源码。
- **当前状态**：根 `pom.xml` 已设置该配置 `inherited=false`；仍需以子模块 effective POM 和错误格式负向 fixture 完成验收。
- **处置与验收**：限制根无源配置的继承范围或显式覆盖活动插件配置，并通过子模块负向格式 fixture 验证门禁会失败。

<a id="td-045-日志断言索引边界"></a>

### TD-045：日志断言索引边界

- **现状与风险**：`LogTestUtils.validateAndGetEvent()` 只检查索引上界；`getLogEvent(appender, -1)` 会抛出 `IndexOutOfBoundsException`，不符合非法索引应抛 `AssertionError` 的契约。
- **处置与验收**：同时校验负索引，并覆盖越界场景的统一诊断信息。

<a id="td-046-测试清理顺序"></a>

### TD-046：测试清理顺序

- **现状与风险**：`BaseUnitTest.tearDownBase()` 先清理环境，再调用可重写的 `tearDown()`；后者写入 MDC 时，状态可在测试结束后残留。
- **影响范围**：下一用例若也继承该基类，会在 `setUp` 再次清理，因此不视为必然污染所有后续测试。
- **处置与验收**：通过 `try/finally` 在自定义清理后执行统一清理，并覆盖 `tearDown()` 写 MDC 与抛异常两条路径。

## 历史说明

TD-001 至 TD-012、TD-014 至 TD-015、TD-017 至 TD-022、TD-024 至 TD-029 的有效部分已在 v2.2.1 完成并从活跃清单移除。TD-030 至 TD-035 的主体修复与终审补丁已分阶段提交，并通过 [v2.2.1 底座质量强化计划](./v2.2.1/foundation-quality-hardening/plan.md) 的本地 Final Gate。DG-1 保留旧枚举 fallback 的误判风险；DG-3 接受写入 v2 后不能回退到 v1-only 二进制。
