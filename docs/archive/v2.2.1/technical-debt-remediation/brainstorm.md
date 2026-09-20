---
id: technical-debt-remediation
version: v2.2.1
status: confirmed
created: 2026-08-16
updated: 2026-08-25
---

# 技术债修复决策

## Decisions

| ID | 决策点 | 选择 | 理由 |
|----|--------|------|------|
| D-01 | 分页非法值 | PageRequest 容错、PageResult 严格校验 | 保持外部请求兼容，同时消除内部 NPE 与错误页数 |
| D-02 | 校验错误响应 | 保持两类异常既有错误项文本格式 | v2.2.1 不静默改变公共响应契约，只恢复中文消息 |
| D-03 | 字段密文 | 可选应用级 cryptoContext AAD 绑定 | 新密文仅拒绝跨应用上下文搬移；旧密文与未配置场景保持兼容，跨列/跨行绑定留作后续安全债 |
| D-04 | 遗留 ECB API | 保留并每次 WARN | 支持迁移，同时使误用可观测 |
| D-05 | 未知枚举编码 | 保留既有 fallback，新增 `fromCodeOrNull` | 补丁版本保持兼容，同时为需要区分未知值的调用方提供明确 API。 |
| D-06 | GPG 签名 | 默认跳过，发布/显式 profile 签名 | 本地 verify 可复现，发布仍强制签名 |
| D-07 | 测试 profile | 移除危险类路径默认配置 | 基础测试 starter 不得隐式改变下游数据库 |
| D-08 | RpcExecutionTemplate | 保留为手工调用扩展点 | 保护下游注入兼容性，以文档与测试约束语义 |
| D-09 | 旧 extract SPI | v2.2.1 修复内置 Bridge，保留旧 SPI 和 TD-013 | 默认 extractScope 委托旧 extract 并返回 noop，只能保持加载兼容，无法通用恢复自定义 Bridge 管理的未知上下文。 |
| D-10 | 旧 RpcHookChain 直调 API | 保留弃用方法并禁止框架内部使用，继续保留 TD-023 残余项 | 四个分阶段公开方法没有调用句柄，无法在补丁版本中无状态地恢复完整调用级生命周期；强行维护全局 context 映射会引入泄漏和并发歧义。 |
| D-11 | Dubbo Holder | volatile 与原子快照，单上下文边界 | 修复可见性，不承诺不可靠的多上下文隔离 |
| D-12 | Dubbo 异步 MDC | 仅在完成回调重建 scope | 保护框架回调可观测性，不接管业务线程池 |
| D-13 | Nacos 解密 | 仅在当前或旧解密配置前缀已绑定时启用 | starter 自身已携带 Nacos 类，类路径无法充当有效门控；配置前缀门控可保持 key-only 使用方兼容，并避免无解密配置的应用误触发 |
| D-14 | 非 Logback | 跳过转换器注册并 WARN | 日志 starter 不得阻断下游启动 |
| D-15 | MdcUtil | 保留替换/忽略语义并文档化 | 避免静默改变线程上下文行为 |
| D-16 | Feign 元数据 | host 回退，多值非敏感头拼接 | 提高 Hook 观测完整性，不改变请求执行 |
| D-17 | TestAutoConfiguration | 先弃用，主版本后删除 | 保护手工 Import 的源码兼容 |
| D-18 | getFinalMapperPackages | 先弃用，提供准确查询方法 | 避免下游得到与实际扫描不一致的结果 |
| D-19 | 审计人降级 | 保持 system 并 WARN | 不阻断写入，同时暴露身份链路故障 |
| D-20 | AAD 多实例发布 | 独立控制 v2 读取 context 与 v2 写入开关 | 全实例先读 v2/写 v1，再统一开启 v2 写入，支持零停机滚动升级。 |
| D-21 | Handler 构造语义 | 单参只读写 v1，双参读 v2/写 v1，三参显式 true 才写 v2 | 所有入口都遵循独立写开关；手工构造不会仅因提供 context 而提前产生 v2 密文。 |
| D-22 | 构建与 BOM 固定值 | formatter 1.23.0、RocketMQ 2.3.6、consumer 动态读取 revision | 消除执行者二次选型，避免再次产生格式版本漂移、无效坐标和硬编码版本债务。 |

## Decision Closure

兼容性复核打开的三个决策门已由用户于 2026-08-24 全部选择 A，选项与影响记录在 [design.md 的 Decision Log](./design.md#decision-log)。
终审补充的旧 Hook API 边界、Handler 构造语义和构建固定值属于上述决策的兼容性收敛或机械化实施约束，已于 2026-08-25 固化，不新增架构方向或发布副作用。

| 编号 | 最终决定 | 状态 |
|------|----------|------|
| DG-1 | 保留既有 `fromCode` fallback，新增 nullable API | 已确认（A） |
| DG-2 | 只修复内置 Bridge，旧自定义 Bridge 风险继续记录为 TD-013 | 已确认（A） |
| DG-3 | 采用读写开关分离的零停机滚动方案 | 已确认（A） |
