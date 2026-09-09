---
updated: 2026-09-10
---

# 技术债务追踪

<!--!
  本文件是智能体了解代码库"已知问题"的入口。
  智能体在修改某个领域前，应先查看该领域是否有已知债务。
  智能体在完成任务后发现新的债务，应记录到此文件。

  本文件属于"长期维护清单"，永不归档；由 doc-gardening agent 持续维护。
-->

TD-001 至 TD-012、TD-014/015、TD-017/018/019/020/021/022、TD-024/025/026/027/028/029 的有效部分已在 v2.2.1 完成并有实现提交与测试证据；对应条目从活跃清单移除。TD-030 至 TD-035 的主体修复与终审代码补丁已分阶段提交，并已通过 [v2.2.1 底座质量强化计划](./v2.2.1/foundation-quality-hardening/plan.md) 的本地 Final Gate。DG-1 保留旧枚举 fallback 的误判风险，DG-3 接受写入 v2 后不得回退到 v1-only 二进制。以下七项是当前仍需后续设计、修复或兼容迁移的残余债务。

| 编号 | 领域 | 问题描述 | 优先级 | 记录日期 | Owner | 关联计划 |
|------|------|----------|--------|----------|-------|----------|
| TD-036 | 发布 Parent / 构建 | 发布 Parent 的 flatten 配置将 `pluginManagement` 解析为发布时的常量；下游覆盖 `java.version`、编译插件版本或 JaCoCo 门槛时，插件实际仍使用 Parent 发布时的值。需要保留可继承的延迟解析语义，并以真实发布消费者验证属性覆盖生效。 | 高 | 2026-09-10 | YoungerYang-Y | 待制定修复计划 |
| TD-037 | BOM / 依赖治理 | BOM 管理的 `com.squareup.okhttp3:okhttp:5.5.0` 不提供可供 Java 直接使用的 `okhttp3` 类；仅导入 BOM 并声明该依赖的消费者编译 `OkHttpClient` 失败。需要确认 JVM 制品坐标与版本组合，并增加真实 API 编译和调用门禁。 | 高 | 2026-09-10 | YoungerYang-Y | 待制定修复计划 |
| TD-038 | starter-log / 数据安全 | 显式启用 `api_key`、`account` 等预置规则后，普通 `key=value` 可脱敏，但 JSON 带引号字段不匹配，敏感值可能原样写入日志。需要统一字段型规则的引号感知处理，并覆盖 JSON、转义与普通赋值格式。 | 高 | 2026-09-10 | YoungerYang-Y | 待制定修复计划 |
| TD-039 | starter-nacos / 配置刷新 | 删除 Nacos 加密配置前缀时，刷新监听器跳过处理且未移除旧 `decryptedProperties:*` 覆盖层；旧解密明文可继续覆盖新的底层配置。需要在前缀消失时清理覆盖层，并验证删除、明文切换和重新绑定场景。 | 高 | 2026-09-10 | YoungerYang-Y | 待制定修复计划 |
| TD-013 | starter-rpc-core | `MdcRpcTracerBridge.extract()` 非 scope 入口不回滚 MDC；自定义 Bridge 仅实现 `extract` 时仍可能发生上下文泄漏。T3 已将框架内部路径迁移到调用级 scope，旧入口保留兼容（证据：`bf493b6`、`2dd1b83`、`4e428e6`）。 | 中 | 2026-08-29 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-016 | starter-mybatis | 字段加密仍无字段/记录级 AAD 完整性绑定，同密钥且同应用 context 下密文可能跨列/行互换；v2.2.1 仅提供应用级 context 绑定，不能关闭该风险（证据：`c6006b2`、`4e428e6`）。 | 中 | 2026-08-29 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |
| TD-023 | starter-rpc-core / feign | `RpcHookChain` 废弃的 before/after/onError/cleanup 直调 API 仍保留兼容；框架内部已改用调用级 invocation，旧直调入口的兼容风险继续存在（证据：`bf493b6`、`2dd1b83`、`4e428e6`）。 | 低 | 2026-08-29 | YoungerYang-Y | [技术债修复计划](./v2.2.1/technical-debt-remediation/plan.md) |

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
