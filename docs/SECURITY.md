---
updated: 2026-08-29
---

# 安全要求

本文档定义本仓库默认遵循的安全边界，重点在“基础框架仓库应避免什么、必须说明什么”。

## 1. 默认原则

- 不静默扩大攻击面
- 不随意改变默认安全语义
- 不把敏感配置或密钥处理逻辑藏在隐式行为里
- 安全相关能力必须有清晰文档说明

## 2. 当前已知安全相关能力

- `mimir-boot-starter-log`：日志脱敏
- `mimir-boot-starter-web`：安全默认的 CORS 开关与白名单、外部 Trace ID 格式和长度限制
- `mimir-boot-starter-nacos`：配置加解密
- 规划中的 `starter-security`：尚未正式落地

v2.2.1 已验证以下边界：

- 日志脱敏支持 JSON、已登记的 URL 编码字段名（例如 `%70assword`）以及 private/secret/access key；公钥字段保持可见。规则编译完成后以不可变快照整体刷新，避免一次日志输出混用新旧配置。
- Nacos 解密处理器只有在新前缀 `mimir.boot.nacos.encrypt` 或兼容旧前缀 `mimir.nacos.encrypt` 被绑定时才运行；遗留 AES/ECB 三参数 API 仅供离线迁移，不能用于应用配置。
- MyBatis v2 密文使用相同应用 `cryptoContext` 生成的应用级 AAD，并保留 v1 读取；`mimir.boot.mybatis.crypto-v2-write-enabled` 默认关闭。应用级 AAD 不提供字段或记录级完整性，启用写入前必须完成全实例可读和列容量预检。

`mimir-boot-starter-web` **不提供**通用 XSS 防护，也不强制请求或上传大小限制。已弃用的
`mimir.boot.web.security` 仅为保持 2.x 配置绑定和 Java API 兼容而保留，不产生运行时效果：

- 上传大小通过 Spring Boot 的 `spring.servlet.multipart.max-file-size` 和
  `spring.servlet.multipart.max-request-size` 配置，并按部署环境在网关层设置请求体上限。
- XSS 防护由应用根据 HTML、JavaScript、URL 等输出上下文编码，并为浏览器页面配置 CSP。
- 外部 `X-Trace-Id` 仅接受最长 64 位、以字母或数字开头的 ASCII
  `[A-Za-z0-9._-]`；请求头存在但无效时生成新的 Trace ID。

此外，发布凭证与签名材料也属于安全边界的一部分：

- Maven Central 凭证
- GitHub Actions Secrets
- GPG 私钥与 passphrase

## 3. 高风险改动

以下改动应视为高风险：

- 更改日志脱敏规则
- 新增或更改请求大小限制及其默认值
- 更改配置解密语义
- 引入新的认证、签名、token 传播方案
- 修改发布凭证相关流程

这些改动默认应先出计划，并在实施前明确影响范围与回退方式。

## 4. 文档要求

凡涉及安全语义的能力，应至少说明：

- 解决什么问题
- 默认行为是什么
- 如何关闭或调整
- 已知边界是什么

## 5. 凭证与签名材料要求

- 不在仓库中提交真实密钥、token、密码
- 不在示例文档中给出可直接复用的真实凭证格式
- 修改发布凭证使用方式时，必须同步更新可靠性与安全文档
- 正式版发布所需的签名要求应保持清晰、稳定、可验证

## 6. 不应做的事

- 在文档中写入真实密钥、token、凭证
- 用示例配置误导接入方在生产中关闭关键保护
- 在无明确授权时改造发布和凭证管理链路
