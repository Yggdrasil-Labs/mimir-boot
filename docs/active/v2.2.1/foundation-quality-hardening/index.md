---
id: foundation-quality-hardening
version: v2.2.1
status: design
owner: YoungerYang-Y
branch: main
created: 2026-08-30
updated: 2026-08-30
---

# 底座质量强化

本需求承接代码质量复核后决定采纳的 6 组改进，作为 v2.2.1 的独立需求与验收单元。它不回填已经完成的 `technical-debt-remediation`，也不改变本版本已确认的公共响应、密文和旧 RPC SPI，也不改变分页已有的 null/范围/排序纠正规则，仅新增不可表示 offset 的明确失败语义。

| 文档 | 状态 | 说明 |
|------|------|------|
| [spec.md](./spec.md) | 草拟 | 8 个 Behavior、36 个 Scenario，定义底座使用方可观察的质量契约 |
| [design.md](./design.md) | 草拟 | 跨 common、MyBatis、log、web、RPC Core、Feign、Dubbo 的完整设计 |
| [plan.md](./plan.md) | 待确认 | 9 个可独立提交的 TDD Task，覆盖 IC-1 至 IC-9 与最终验收门禁 |

## 纳入范围

- 可执行 JAR 中的 Mapper 包自动发现。
- Servlet 异步请求的最终访问日志与 MDC 生命周期。
- 转义引号、异常链和 Feign URL 的敏感信息保护。
- RPC Core 开关与 Feign/Dubbo 适配器的组合语义。
- 分页 offset 与 totalPages 的 Long 溢出保护。
- `Loggable` 弃用和 3.0 移除路线。

## 明确排除

- 不在本版本改变校验异常响应的 wire format。
- 不移除 `R<T extends Serializable>` 或其他公开类型约束。
- 不在本版本重构 Dubbo Holder、日志脱敏器的多 ApplicationContext 静态状态。
- 不为 `Loggable` 增加 AOP 运行时行为。
