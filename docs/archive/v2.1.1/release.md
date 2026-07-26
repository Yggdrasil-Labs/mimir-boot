---
version: v2.1.1
released: 2026-07-26
retain_until: 2027-07-26
---

# v2.1.1 归档说明

本版本于 2026-07-26 发布，归档内容保留至 2027-07-26，供兼容性与历史决策追溯。

## 包含需求

### capability-review-2026-07（能力复审）

对已交付 Starter 的独立复审，完成 R-001 至 R-014 全部修复项：

- **R-001**：Nacos 启动期解密重写为 `EnvironmentPostProcessor`，支持 Bean 创建前解密和动态刷新。
- **R-002**：CORS 默认关闭，携带凭证时拒绝通配 Origin。
- **R-003/R-004**：Nacos 加密策略统一为 `mimir.boot.nacos.encrypt` 前缀，升级为 AES-GCM。
- **R-005/R-006/R-007**：MyBatis 字段加密强制稳定密钥、SQL 参数脱敏、访问日志保护。
- **R-008**：Dubbo 双向 Trace 传播，按 Consumer/Provider 角色分别执行注入与提取。
- **R-009**：Feign 保留 delegate 链路，限制敏感请求头传播。
- **R-010 至 R-014**：文档漂移修复、测试覆盖补全、安全默认值迁移说明。

### quality-refinement（工程质量优化）

- 收紧测试覆盖率门禁，统一 JaCoCo 配置。
- 固化发布插件版本与 CI 流水线行为。
- 完善文档治理流程与质量纪律。

## 归档边界

- 本目录是已发布实现的历史记录，不应再作为新的实施计划执行。
- 后续相关改进应创建新的活跃需求，不回写本归档。
