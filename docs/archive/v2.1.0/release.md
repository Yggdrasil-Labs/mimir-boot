---
version: v2.1.0
released: 2026-06-28
retain_until: 2027-06-28
---

# v2.1.0 归档说明

本版本于 2026-06-28 发布，归档内容保留至 2027-06-28，供兼容性与历史决策追溯。

## 包含需求

### documentation-system-rebuild（文档体系重建）

- 重写根 `AGENTS.md` 收敛为入口地图。
- 新增 `ARCHITECTURE.md` 作为顶层架构文档。
- 创建 `docs/` 目录下的设计、产品、计划、生成、参考五类分层结构。

### exception-handler-adapter（异常处理适配器）

- 提供 `ExceptionResponseFactory` 和默认 `R<T>` 响应适配实现。
- 使用 `MimirExceptionHandler` 统一处理异常，并允许接入方替换响应工厂。
- 自动配置支持自定义工厂覆盖默认工厂，并有单元与 Web 自动配置集成测试。

## 归档边界

- 本目录是已发布实现的历史记录，不应再作为新的实施计划执行。
- 后续相关改进应创建新的活跃需求，不回写本归档。
