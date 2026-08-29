---
version: "v2.2.1"
date: "2026-08-29"
status: "in-progress"
branch: "feature/technical-debt-remediation"
---

# Release — v2.2.1

## Changelog

### Features

- 公共枚举新增 `CommonStatus.fromCodeOrNull(Integer)`、`DeleteFlag.fromCodeOrNull(Integer)` 和 `ErrorCode.fromCodeOrNull(String)`；既有 `fromCode` fallback 保持兼容。
- 日志脱敏覆盖 JSON、已登记的 URL 编码字段名（例如 `%70assword`）及 private/secret/access key，并以不可变快照原子刷新规则。
- MyBatis 增加应用级 `cryptoContext` AAD 读取和默认关闭的 `cryptoV2WriteEnabled` 渐进写入能力。

### Fixes

- 修复分页 null/非法边界、异常消息清洗、RPC 异步上下文与 Feign 元数据边界。
- 修复 Nacos 解密误触发和遗留 AES/ECB 迁移告警；无绑定加密前缀时普通 `ENC(...)` 不触发密钥校验。
- 修复测试 Starter 的数据库副作用与固定应用名默认值，构建门禁、BOM 坐标、报告隔离和 GPG 签名校验收敛。

## 接口变更

| 类型 | 路径 | 说明 |
|------|------|------|
| 兼容新增 | `mimir-boot-common` 枚举 | 使用三个 `fromCodeOrNull` 处理未知值；旧 `fromCode` 不变。 |
| 兼容保留 | `mimir-boot-starter-rpc-core` | 旧 `RpcTracerBridge.extract` 与 `RpcHookChain` 直调入口继续保留；新代码使用 `extractScope`、`open`/`openAsync`。 |
| 默认值调整 | `mimir-boot-starter-test` | 不再通过类路径资源注入数据库策略、`show-sql` 或固定应用名；接入方需显式配置测试环境。 |

## 数据变更

| 类型 | 表/字段 | 说明 |
|------|---------|------|
| 渐进迁移 | MyBatis 加密字段 | 先让所有实例具备 v2 读取能力并继续写 v1；完成列容量预检后再统一开启 v2 写入。应用级 AAD 不提供字段/记录级完整性。 |
| 回退下限 | MyBatis 加密字段 | 已写入 v2 后，只允许回退到支持 v2 且使用相同 `cryptoContext` 的版本；不得回退到 v1-only 版本。 |

## 依赖变更

| 操作 | 依赖 | 版本 |
|------|------|------|
| 修正坐标 | `org.apache.rocketmq:rocketmq-spring-boot-starter` | `2.3.6` |
| 修正坐标 | `co.elastic.clients:elasticsearch-java` | `8.11.0` |
| 构建门禁 | google-java-format | `1.23.0` |

## 配置变更

| 环境 | Key | 说明 |
|------|-----|------|
| 所有环境 | `mimir.boot.nacos.encrypt`（兼容 `mimir.nacos.encrypt`） | 只有显式绑定前缀时才处理配置解密；遗留 AES/ECB API 仅供离线迁移并产生告警。 |
| 所有环境 | `mimir.boot.mybatis.crypto-context` | v2 密文使用的应用级稳定 context；所有实例必须一致。 |
| 所有环境 | `mimir.boot.mybatis.crypto-v2-write-enabled` | 默认 `false`；全量可读升级和列容量预检完成后才允许统一开启。 |
| 所有环境 | `mimir.boot.log.mask.replacement` | 脱敏默认替换值为 `****`，可按接入方需要显式配置。 |

## 迁移与回退矩阵

| 能力 | 迁移顺序 | 回退下限 |
|------|----------|----------|
| 测试 Starter | 删除对 Starter 隐式 `application-test.yml` 默认值的依赖，在接入方测试资源中显式设置数据库、日志和应用名。 | 回退到旧版本前保留显式配置，避免恢复危险隐式默认值。 |
| 枚举 API | 新代码按需改用三个 `fromCodeOrNull`；既有调用方可继续使用 `fromCode`。 | 任意支持 v2.2.1 API 的版本均可保留旧方法。 |
| RPC SPI | 新扩展实现 `extractScope` 和调用级 `RpcHookInvocation`；旧 `extract`/直调 API 仅作为兼容入口。 | 不删除旧 SPI；若自定义 Bridge 只有 `extract`，需接受 noop scope 不提供未知上下文回滚。 |
| Nacos legacy ECB | 应用配置仅使用 AES-GCM；遗留 AES/ECB 仅在离线迁移路径显式调用，并关注每次调用一次的迁移告警。 | 不把遗留 API 作为新敏感数据方案；自动解密始终不得回退到 ECB。 |
| MyBatis v2 密文 | 所有实例先部署可读 v2 的版本，统一 `cryptoContext`，继续写 v1；完成全量升级与列长度预检后统一打开 v2 写入。 | v2 已写入后只能回退到支持 v2 且 context 相同的版本；应用级 AAD 的字段/记录级完整性缺口仍需后续设计。 |

## 脱敏性能证据

固定 1 KiB 消息、JDK 17、同一 JVM 下，baseline 为无规则路径，candidate 为 3 个敏感字段路径；每次均预热 100000 次、测量 1000000 次，不剔除离群值。单次 delta = candidate − baseline：

| 运行 | baseline ns/op | candidate ns/op | 有符号 delta ns/op | 本次 3 样本平均 delta ns/op | 本次最大 delta ns/op |
|------|---------------:|----------------:|-------------------:|----------------------------:|---------------------:|
| 1 | 4.43 / 3.00 / 3.01 | 2359.23 / 2290.49 / 2281.87 | +2354.80 / +2287.49 / +2278.86 | +2307.05 | +2354.80 |
| 2 | 4.56 / 3.01 / 2.99 | 2332.74 / 2269.09 / 2290.98 | +2328.18 / +2266.08 / +2287.99 | +2294.08 | +2328.18 |
| 3 | 4.65 / 3.08 / 3.06 | 2412.65 / 2366.23 / 2332.89 | +2408.00 / +2363.16 / +2329.82 | +2366.99 | +2408.00 |

三次运行平均 delta 为 **+2322.71 ns/op（2.323 µs）**，三次运行中的最大 delta 为 **+2408.00 ns/op（2.408 µs）**，低于 20 µs 门槛。原始日志位于 `/tmp/mimir-boot-benchmark-1.log`、`/tmp/mimir-boot-benchmark-2.log` 和 `/tmp/mimir-boot-benchmark-3.log`。

## 验证证据

- T1–T7 主提交及补充提交见技术债实施计划；T7 的 consumer 与签名 fixture 通过隔离仓库验证。
- T9 在当前 HEAD 连续运行三次脱敏基准：三次运行平均 delta +2322.71 ns/op，运行最大 delta +2408.00 ns/op，均值低于 20 µs 门槛。
- `test-suite-consumer.sh` 已完成 13 个发布前模块构建及独立 file repository consumer 1/1；签名门禁已验证 46 个制品和 `.asc`，失败 GPG fixture 正确阻断部署。签名预热模式曾因远端 TLS handshake 中断，使用完整种子 Maven 缓存重跑通过；该网络波动不改变代码门禁结论。
- 本文档不宣称 Maven Central、GitHub Packages 或生产实例已发布；正式发布仍需既有 workflow、凭证和 GPG 门禁。
