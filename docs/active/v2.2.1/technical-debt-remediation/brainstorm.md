---
id: technical-debt-remediation
version: v2.2.1
status: confirmed
created: 2026-08-16
updated: 2026-08-19
---

# 技术债修复决策

## Decisions

| 决策点 | 选择 | 理由 |
|------|------|------|
| 分页非法值 | PageRequest 容错、PageResult 严格校验 | 保持外部请求兼容，同时消除内部 NPE 与错误页数 |
| 校验错误响应 | 保持两类异常既有错误项文本格式 | v2.2.1 不静默改变公共响应契约，只恢复中文消息 |
| 字段密文 | 可选应用级 cryptoContext AAD 绑定 | 新密文仅拒绝跨应用上下文搬移；旧密文与未配置场景保持兼容，跨列/跨行绑定留作后续安全债 |
| 遗留 ECB API | 保留并每次 WARN | 支持迁移，同时使误用可观测 |
| 未知枚举编码 | 返回 null | 不把未知数据伪装为有效业务状态 |
| GPG 签名 | 默认跳过，发布/显式 profile 签名 | 本地 verify 可复现，发布仍强制签名 |
| 测试 profile | 移除危险类路径默认配置 | 基础测试 starter 不得隐式改变下游数据库 |
| RpcExecutionTemplate | 保留为手工调用扩展点 | 保护下游注入兼容性，以文档与测试约束语义 |
| 旧 extract SPI | 保留并弃用，内部用 extractScope | 避免 MDC 泄漏且不破坏既有 Bridge |
| Dubbo Holder | volatile 与原子快照，单上下文边界 | 修复可见性，不承诺不可靠的多上下文隔离 |
| Dubbo 异步 MDC | 仅在完成回调重建 scope | 保护框架回调可观测性，不接管业务线程池 |
| Nacos 解密 | 仅在当前或旧解密配置前缀已绑定时启用 | starter 自身已携带 Nacos 类，类路径无法充当有效门控；配置前缀门控可保持 key-only 使用方兼容，并避免无解密配置的应用误触发 |
| 非 Logback | 跳过转换器注册并 WARN | 日志 starter 不得阻断下游启动 |
| MdcUtil | 保留替换/忽略语义并文档化 | 避免静默改变线程上下文行为 |
| Feign 元数据 | host 回退，多值非敏感头拼接 | 提高 Hook 观测完整性，不改变请求执行 |
| TestAutoConfiguration | 先弃用，主版本后删除 | 保护手工 Import 的源码兼容 |
| getFinalMapperPackages | 先弃用，提供准确查询方法 | 避免下游得到与实际扫描不一致的结果 |
| 审计人降级 | 保持 system 并 WARN | 不阻断写入，同时暴露身份链路故障 |
