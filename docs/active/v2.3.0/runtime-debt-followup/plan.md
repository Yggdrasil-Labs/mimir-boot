---
id: runtime-debt-followup-plan
version: v2.3.0
status: completed
owner: YoungerYang-Y
created: 2026-10-03
updated: 2026-10-03
---

# 运行时技术债后续修复

## 范围与边界

基于 `dbb6a23622a7e3bc13d1ec37a522a5081e55f41e`，复用 `.worktrees/technical-debt-next`、`fix/technical-debt-next`。本轮继续修复剩余债务调查中确认的兼容缺口；既有 main、前批验收和其他工作树保留。根版本仍为 `2.2.2-SNAPSHOT`，文档目录不承诺发布。

- TD-013：默认 `extractScope` 对旧 extract-only Bridge 返回 noop，正常返回和提取中途异常均可能污染 MDC。补齐提取所修改键的作用域回滚，保留旧入口及非 MDC 上下文的迁移要求。
- 字段加密注册范围：Spring 暴露的三个加密 Handler 被 MyBatis 泛型推断注册为普通 String/Long/Integer 默认 Handler，与显式字段加密契约冲突。隔离自动注册键，保留显式 Handler 类到原 Spring 实例的映射；不重定义 v1/v2 或降低读取兼容。
- TD-016：当前 Handler 只收到值与 JDBC 参数/列序号，没有稳定字段或记录身份。字段/记录 AAD 需要独立协议、显式身份和历史迁移策略，本轮不从列 alias 或序号推断身份，不宣告此债务关闭。
- TD-023：旧 Hook 的正序清理、独立调用和异常行为是现存兼容契约；直接转新 Invocation 会改变行为。保留旧入口及迁移要求，不以搬移代码关闭债务。
- TD-039：重绑定原 Bean 不会清空缺失属性，需明确可重建 Bean 或显式清空策略的接入边界；删除属性不等于任意 Bean 可以安全重建。本轮调查与已确定修复分开记录。

## 实施顺序

1. RPC：先用真实旧 Bridge 的 MDC 回滚测试复现，再让默认适配记录新增、删除、修改键并幂等恢复；提取失败先回滚再传播原异常。覆盖空 MDC、嵌套 LIFO、重复关闭、不同线程独立 scope、误跨线程关闭以及 Dubbo 实际异步完成路径。不替换内置 Bridge 的两个键管理方式。
2. MyBatis：先用真实 Registry 复现默认 Handler 被覆盖；三个具体加密类使用专用自身类型注册键，避免泛型推断替换普通类型。通过真实 Spring/MyBatis-Plus/H2 Mapper 验证普通参数/结果、显式加密字段、数据库实际密文、默认 v1 和显式 v2 配置及历史读取；保留应用自定义普通 Handler。H2 仅作该模块 test 依赖，版本沿用 Boot BOM 管理，不加入运行时依赖。
3. 收尾：同步模块 README、债务剩余范围和版本记录；仅在对应断言和模块 `clean verify` 通过后记录已修复范围。RPC 变更连同 Dubbo/Feign 适配层验证，MyBatis 变更连同测试依赖验证；再运行文档 full、自检与 diff 检查。

## 通过标准

- 新回归先确认失败源于上述真实缺口，再确认修复后的断言通过；不把探针命令存在写成已验证。
- 默认适配只恢复旧 extract 净修改的 MDC 键，保留后续无关键变更；同一 scope 在创建线程关闭，失败线程不消耗原线程关闭权。
- 普通 String/Long/Integer 的默认和应用自定义映射保留；显式加密参数与结果使用配置中的密钥、context 和写入版本，原公开构造器保留。
- 对已有历史 v1/v2 密文、旧 extract、旧 Hook 和普通可变配置 Bean 的剩余风险如实记录；不关闭尚未满足完整迁移条件的债务。
- 验证摘要写入本计划或版本 release；临时日志在 `/tmp`。前三阶段不新增 tools 工具；第四阶段仅补长期 Java 门禁的必要支持，不新增专项脚本或降低质量门槛。

## 本地实施与验收（2026-10-03，未发布）

本轮两项确定修复已完成，TD-013 仍为兼容保留，TD-016、TD-023、TD-039 继续活跃。Nacos 普通 Bean 的清空或重建策略尚未确定，本轮没有修改其运行时实现；不以等待策略作为其他修复的前置条件。

- RPC Red：修复前 `LegacyRpcTracerBridgeCompatibilityTest` 的 MDC 恢复断言失败，预期 `previous`、实际 `current`；2 项测试、1 failure、0 errors。
- MyBatis Red：修复前 `CryptoTypeHandlerRegistrationTest` 同时发现 String、Long、Integer 默认 Handler 被 Crypto Handler 覆盖；1 项测试、1 failure、0 errors。首次格式检查失败不计作行为复现；诊断用 `-Dspotless.check.skip=true` 的回归仅用于确认根因，最终验收无跳过参数。
- RPC Green：11 项兼容测试及 4 项真实 Dubbo injvm 测试通过，包括旧 Bridge 的异步调用线程、完成线程恢复。首次沙箱运行的 socket 探测警告未造成断言失败；最终完整验收在允许本地 socket 的环境执行。
- MyBatis Green：3 项 Registry 回归和 2 项真实 Spring/MyBatis-Plus/H2 Mapper IT 通过。普通列与条件参数、显式加密列、Spring Handler 实例映射、默认 v1、显式 v2 及历史 v1 读取均有对应断言。
- 完整模块验收：以下命令 exit 0，2026-10-03 09:39:42 +08:00 完成，10 个 reactor 模块均 SUCCESS，794 项单元测试及 37 项 IT，失败、错误、跳过均为 0；Spotless、Enforcer 和适用模块的 JaCoCo 检查通过。日志 `/tmp/mimir-runtime-clean-verify.log`。

```bash
./mvnw -o -Pci -pl mimir-boot-starters/mimir-boot-starter-mybatis,mimir-boot-starters/mimir-boot-starter-rpc-core,mimir-boot-starters/mimir-boot-starter-dubbo,mimir-boot-starters/mimir-boot-starter-feign -am clean verify
```

验收对象为基线 `dbb6a23` 之上的本工作区 Java、POM 和测试变更；Java 17.0.19、MyBatis 3.5.19、MyBatis-Plus 3.5.17、H2 2.2.224。MyBatis POM SHA-256 为 `6e6f03fb849828df9a772f9be965a6f55ab4f3fbd67656089d6b41de2f6990c7`，新 RPC 兼容 scope 为 `fd3a45b2977161c3539b8b5d0c1fa03728728460a6fb31b3ccbbe1104201205c`。后续仅补验收文档，不改变上述构建输入。

以上为本地模块验收，不代表全仓库 full、远程 CI、制品发布或发布后消费者验证。升级时还需检查是否有应用依赖旧版意外的全局加密注册；字段/记录 AAD 与旧接口迁移不能据此宣告完成。

## 第二阶段：Nacos 显式重建接入

用户要求继续实施并分阶段提交后，前三项成果分别提交为 `35f8979`（RPC）、`d0f961d`（MyBatis）、`d45f847`（验收文档）。第二阶段限定为现有 Spring Cloud 显式刷新作用域的消费方接入契约，不自动重建普通 Bean，不新增通用字段重置机制。

1. 使用实际 `ConfigDataContextRefresher.refresh()` 和自动配置先复现普通 Bean 删除属性后的旧值残留，再在工厂方法显式增加 `@RefreshScope`；不直接调用 `refreshAll()` 代替真实刷新流程。
2. 验证缺失属性恢复 null/初始化默认值，重复删除与重新加密、下层回退、稳定代理与目标重建，以及失败刷新保留已有目标；单独发布环境事件不能承诺刷新作用域重建。
3. 将可运行的 `@Bean`、`@RefreshScope`、`@ConfigurationProperties` 示例及生命周期限制写入 Nacos README；完成模块 `clean verify`、独立复核和文档检查后分两次提交模块测试/说明与验收记录。

计划提交：`test(nacos): 验证显式刷新作用域的配置删除语义`，仅 Nacos 模块 IT 与 README；`docs(debt): 记录配置重建接入与剩余迁移边界`，仅本计划、活跃索引、债务台账和 release。TD-039 对未接入刷新作用域的普通 Bean 仍保持部分修复；此阶段不以测试已存在机制为由关闭全部债务。TD-016 的密文协议与 TD-023 的公共旧 API 迁移仍需独立设计与迁移约定。

### 第二阶段本地验收（2026-10-03，未发布）

- Red：普通工厂 Bean 经真实 `ConfigDataContextRefresher.refresh()` 后，Environment 已无 `app.secret`，Bean 仍为 `old-secret`；1 项测试、1 failure、0 errors。日志 `/tmp/mimir-nacos-scope-red.log`。
- Green：仅在消费方工厂方法显式增加 `@RefreshScope` 后，原回归恢复 null、数字初始值和空集合；随后新增目标身份、普通 Bean 控制、下层回退、恢复加密、仅删除业务属性、失败后保留旧目标与再次刷新、单独环境事件对照，最终 4 项新 IT 通过。
- 完整验收：`./mvnw -o -Pci -pl mimir-boot-starters/mimir-boot-starter-nacos -am clean verify` 于 2026-10-03 11:23:27 +08:00 exit 0，5 个 reactor 模块均 SUCCESS；293 项单元测试、10 项 IT，失败、错误、跳过均为 0，Spotless、Enforcer、适用 JaCoCo 检查通过。日志 `/tmp/mimir-nacos-scope-verify.log`。
- 状态：基线 `d45f847` 之上的新 IT 与 README；IT SHA-256 为 `ab6b8b57468ed608de8eca22284390454a4b3747a7ad1db9750381fef7c10d6e`。使用 Java 17.0.19、Spring Cloud Context 4.1.6、Alibaba 2023.0.3.4；运行时源码与 POM 未改。
- 边界：测试使用真实 Spring Cloud 刷新实现及本地可变属性源，没有连接 Nacos 服务端；不能替代远程 CI、真实服务端删除/推送或生产消费方验证。业务需选择可重建 JavaBean 并持续访问注入代理，裸目标、历史字段快照及普通 Bean 的旧值不会由该方式统一清除。

两阶段确定范围均已完成本地实施与验收；TD-013、TD-016、TD-023、TD-039 不据此整体关闭。后续协议或旧接口迁移应先明确身份、迁移周期和兼容验收，再按独立方案实施。

## 第三阶段：TD-039 事件桥接与消费方生命周期

1. 用临时配置文件及真实 Boot ConfigData 重载替换原地修改 Map 的场景，注册实际 Alibaba `NacosConfigRefreshEventListener`；从 `NacosConfigRefreshEvent` 进入 Spring Cloud 完整刷新流程，不直接调用刷新器。
2. 构造注入业务消费者，验证稳定代理、删除后默认值、销毁与重新初始化、恢复配置及解密失败后的恢复。观察真实环境变更事件，区分属性源重载与仅修改已安装属性源。
3. 完成 Nacos 模块验收并同步 README、债务台账和版本记录；保持普通 Bean 及真实 Nacos 服务端验收的剩余边界。本地 Docker daemon 当前不可连接，未提供测试服务前不宣称完成服务端删除/推送验证。

此阶段只修改 Nacos 模块测试与对应文档，不修改运行时源码、POM 或 `tools/`。临时配置文件由 JUnit 管理；本地文件加载器不冒充 Nacos ConfigData 加载器。

### 第三阶段本地验收（2026-10-03，未发布）

- 新增 `NacosRefreshEventIT` 三项契约回归，使用实际 Alibaba `NacosConfigRefreshEventListener`、真实 SpringApplication readiness 和 Boot 文件 ConfigData 加载器，从 Nacos 配置事件进入 Spring Cloud 完整刷新。测试没有直接调用刷新器，没有在刷新前修改已安装属性源，也未连接 Nacos 服务端。
- 覆盖配置删除后构造注入消费者恢复 null/默认值、消费者与代理身份保持、旧目标销毁及下次访问重新初始化、重新加密恢复、保留加密配置时下层回退，以及文件重载解密失败保留 live 配置和旧目标、修复后再次刷新。新增测试补验现有机制，不宣称新增运行时修复；第二阶段的普通 Bean Red 证据继续保留。
- 聚焦三项 IT 通过，日志 `/tmp/mimir-nacos-event-focused.log`。完整命令 `./mvnw -o -Pci -pl mimir-boot-starters/mimir-boot-starter-nacos -am clean verify` 于 2026-10-03 13:09:35 +08:00 exit 0，5 个 reactor 模块均 SUCCESS；293 项单元测试及 13 项 IT，失败、错误、跳过均为 0，Spotless、Enforcer 和适用 JaCoCo 检查通过。日志 `/tmp/mimir-nacos-event-verify.log`。首次沙箱运行因 Mockito JVM agent attach 限制失败，最终在允许 attach 的环境重跑通过，没有跳过测试或降低门槛。
- 状态指纹：基线 `e994d423256420526b249b513ccd65e07bfce779`；新 IT SHA-256 为 `e821cec45bb0ebae4e734e27fa1e9c600c6155970affc631548096b02bb4f9ee`；Java 17.0.19、Spring Boot 3.3.13、Cloud Context 4.1.6、Alibaba 2023.0.3.4。运行时源码与 POM 未改。
- 已观察到的剩余问题：删除密文时，环境差异计算仍可能读取旧解密覆盖层，实际事件键集合漏报 `app.secret`；随后监听器清理覆盖层，最终 Environment 与完整刷新后的目标正确。当前测试不把漏报固化为正确契约；此问题与按键选择性更新、普通 Bean 及缓存消费者的处置一起保留在 TD-039，尚未修复。
- 外部边界：本地 Docker daemon 不可连接，当前没有可用 Nacos 测试服务；未验证服务端删除/推送、Nacos 网络 ConfigData 加载、实际业务应用迁移、远程 CI 或发布后行为。第三阶段的本地范围完成，TD-039 仍为部分修复。
- 独立复核：未发现本阶段范围内的 P0-P3 问题，各原始维度均为 4；按公共 review policy 加权为 4.0，结论 PASS。`bash scripts/engineering.sh docs --root "$PWD" --mode full --self-test` exit 0，79 个 Markdown 文件、0 issues；`git diff --check` exit 0。该结果仅适用于本地阶段交付，不解除上述剩余债务。

## 第四阶段：整体复查问题修复

整体复查发现三项 P1 和一项 P2，本阶段按用户 `fixall` 指令修复：

1. 测试报告完整性：从有效选择器匹配的源码及本次测试类路径发现实际可运行测试类，逐类核对 XML 执行证据；不能仅检查已有报告或比较文件数量。辅助类、抽象类、继承和嵌套测试需按实际测试引擎处理。
2. 构建模型：明确拒绝无法可靠建模的动态源码根，防止扫描时空目录误获覆盖率豁免；兼容 JaCoCo 百分比阈值并保留原最低门槛。
3. Nacos：将删除保证收紧为实时 Environment 真正删键的场景；补齐整源消失仍保留旧源的负向回归，不承诺已修复 Spring Cloud 的整源删除行为。
4. 验证与提交：运行聚焦反例、全仓 Java 门禁、文档检查和独立复核，按工程门禁、Nacos 契约和验收文档分阶段提交。

`tools/` 修改仅承担跨模块、跨版本持续运行的 Java 构建模型与报告完整性门禁，复用入口为 Java 子检查、完整 quality 和 CI。源码声明解析及 JUnit 发现支持文件属于该长期门禁；专项反例与临时日志保留在临时目录，不新增需求专用脚本。TD-039 继续保持部分修复，真实 Nacos 服务端和消费方迁移仍待验收。

### 第四阶段本地验收（2026-10-03，未发布）

- 报告完整性 Red：旧核验对构建登记前已缺失的 `CommonStatusTest` XML 返回通过；Green：正常 Common 报告通过，登记前临时移出该 XML 后按测试类身份判定缺失，退出失败，随后恢复产物。日志入口 `/tmp/mimir-report-completeness-probe.mjs`，不保留专项脚本到仓库。
- 源码清单：JDK 语法树一次读取全部测试源码，再按编译后顶层类名匹配选择器；补齐异名 `Benchmark.java` 内 `HiddenTest/HiddenIT`、多种顶层类型、package-info、空输入、语法错误拒绝与来源去重，4 项临时回归通过。25 项构建模型回归在最终代码上通过，包括动态源码拒绝、百分比/小数等价、越界/降低阈值拒绝、测试类目录及发现配置边界。日志 `/tmp/mimir-source-inventory-probe.log`、`/tmp/mimir-java-build-model-fix-probe.log`。
- JUnit 清单：继承、meta 注解、Nested、Factory、ParameterizedTest 和异名类仅输出实际可运行顶层类，辅助类与抽象类排除；主动抛错的测试方法没有执行。候选字节码缺失与参数依赖缺失均明确失败。证据 `/tmp/mimir-junit-inventory-fixture/summary.json`。此清单不证明类内方法或动态实例全量执行；未知源码生成扩展仍需先补模型支持。
- Nacos：新增整源消失回归通过；空文件经实际事件桥接刷新后仍保留旧原始源、密文及绑定值，显式清理夹具原始源和解密层后默认值恢复。README、台账和 release 已收紧删除保证。生产刷新器未修改，整源消失仍是 TD-039 的已知限制，不把测试中的显式移源作为业务清理方案。
- 完整验收：`RUN_SONAR=false bash scripts/engineering.sh quality --mode full --source worktree --report /tmp/mimir-fixall-final-full/quality-report.json` 于 2026-10-03 18:12:08 至 18:21:02 +08:00 完成，exit 0、0 findings，所有必需检查 passed。15 个模块、1086 项单元测试和 53 项 IT，失败、错误、跳过均为 0；包含新增的第四项 `NacosRefreshEventIT`。隔离消费者、48 个制品及附属制品的临时签名和失败 fixture 均通过。首轮全局 `MAVEN_ARGS=-o` 阻止临时本地 deploy，不能计作 full 通过；去掉此参数后完整重跑，没有跳过检查。
- 输入：基线 `7ed569d` 之上的本工作区修复，tree `worktree-821ac93b2a1d64ea5b34f3ad56652dca4eedbecd7aa88341bbd55e8a03d4ab60`，configurationHash `615ea8b2422692148de124a51836cf08888125950653287d62e639d552e58d86`；Java 17.0.19、Node 22.22.3。有效模型代码 SHA-256 `fe59389764c34a6f3ac4f27e517b94166f074a0d23e9777378ea7edd741216d5`，报告核验代码 `91db3f383e6d7a2fb4756861a8fd94521cfb19b4f284931d8ff17e11b0575b0f`，Nacos IT `537329cdba17ab176ae5229969d6ac5f1841609164105bf7d7276ac2a7a738fd`。随后只补验收文档，不改变验证过的代码或构建输入；最终文档单独复验。
- 独立复核：最新工程门禁、支持文件、Nacos IT 与说明未发现已确认的 P0-P3 问题。四项整体复查问题在约定本地范围内完成处置；本轮未启用 Sonar，未验证远程 CI、真实 Nacos 服务端、实际应用迁移或发布后行为，剩余债务继续保留。
