# Mimir Boot 智能体导航

本文件只做入口导航，不承载完整说明书。目标是让智能体先知道边界和阅读路径，再按需进入更具体的文档。

## 1. 基本边界

- 用户明确指令优先。
- 默认行为必须安全、保守、可回退。
- 多文件变更、新功能、重构、架构/性能/安全相关调整，先给计划再实施。
- 小范围缺陷修复、单文件局部调整、纯文档小修可直接执行。
- 所有回复、计划、说明、代码注释使用简体中文，代码标识符和专有名词除外。

## 2. 项目事实

- 这是一个 Maven 多模块仓库：`mimir-boot-parent`、`mimir-boot-bom`、`mimir-boot-common`、`mimir-boot-starters`。
- 默认运行环境是 Java 17。
- 在 WSL 中如需 Node 运行时，先 `source ~/.nvm/nvm.sh`。
- 默认保持向后兼容，不要静默修改公共配置语义、发布结构、公开接口或依赖体系。

## 3. 阅读顺序

第一次进入仓库，按这个顺序看：

1. [`ARCHITECTURE.md`](./ARCHITECTURE.md)
2. [`docs/index.md`](./docs/index.md)
3. [`docs/design-docs/index.md`](./docs/design-docs/index.md)
4. [`docs/product-specs/index.md`](./docs/product-specs/index.md)
5. [`docs/PLANS.md`](./docs/PLANS.md)

按主题继续深读：

- 设计原则：[`docs/design-docs/core-beliefs.md`](./docs/design-docs/core-beliefs.md)
- 模块边界：[`docs/design-docs/module-boundaries.md`](./docs/design-docs/module-boundaries.md)
- 产品定位：[`docs/PRODUCT_SENSE.md`](./docs/PRODUCT_SENSE.md)
- 可靠性：[`docs/RELIABILITY.md`](./docs/RELIABILITY.md)
- 安全：[`docs/SECURITY.md`](./docs/SECURITY.md)
- 执行计划与技术债：[`docs/exec-plans/`](./docs/exec-plans)

## 4. 维护规则

- 新增重要模块、能力或约束时，同时更新对应索引文档。
- 不要把长篇规则重新堆回本文件；细节应下沉到 `docs/`。
- 如果文档与代码不一致，优先修正文档漂移，或明确指出差异。
