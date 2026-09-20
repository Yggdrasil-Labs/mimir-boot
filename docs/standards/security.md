---
updated: 2026-09-19
---

# 安全约束

本文档定义 Mimir Boot 必须遵守的安全约束。接入、配置与日常验证步骤见[工程规程](../engineering/development.md)；各 Starter 的具体配置以模块 README 为准。

## 默认原则

- 不静默扩大攻击面，不随意改变默认安全语义。
- 敏感配置、密钥和安全能力必须有清晰、可验证的说明。
- 不在仓库或示例中提交真实密钥、token、密码或可复用凭证。
- 安全相关的公开配置与默认行为变更属于高风险改动，必须先说明影响范围和回退方式。

## 当前能力与边界

| 能力 | 权威模块 | 长期边界 |
|---|---|---|
| 日志脱敏 | `mimir-boot-starter-log` | 仅覆盖已登记字段和编码形式；具体规则与已知限制见模块 README 和活跃技术债。 |
| Web 安全默认 | `mimir-boot-starter-web` | 不提供通用 XSS 防护或强制请求大小限制；应用须按输出上下文编码并配置 CSP、网关与 Spring Boot 限制。 |
| Nacos 配置加密 | `mimir-boot-starter-nacos` | 仅在新旧兼容前缀绑定时启用；遗留 AES/ECB API 只供离线迁移，不能用于应用配置。 |
| MyBatis 字段加密 | `mimir-boot-starter-mybatis` | v2 写入默认关闭；启用前须完成全实例可读和列容量预检，应用级 AAD 不等同字段或记录级完整性。 |

外部 `X-Trace-Id` 仅接受最长 64 位、以字母或数字开头的 ASCII `[A-Za-z0-9._-]`；无效值必须生成新的 Trace ID。

## 凭证与发布材料

Maven Central 凭证、GitHub Actions Secrets、GPG 私钥和 passphrase 都属于安全边界。修改其使用方式时，必须同步核对本页、[可靠性约束](./reliability.md)和[发布规程](../engineering/release.md)。

## 变更要求

下列改动默认先出计划并明确影响与回退：日志脱敏规则、请求大小限制、配置解密语义、认证/签名/token 传播方案，以及发布凭证流程。安全语义文档至少说明解决的问题、默认行为、调整方式和已知边界。

当前已知兼容或覆盖边界以[技术债台账](../active/tech-debt-tracker.md)为准；台账不是本约束的替代来源。
