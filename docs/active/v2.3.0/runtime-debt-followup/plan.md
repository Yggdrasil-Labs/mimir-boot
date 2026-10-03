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
- 验证摘要写入本计划或版本 release；临时日志在 `/tmp`，不新增 tools 工具、一次性脚本或修改质量门槛。

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
