# 文档总览

这是 `docs/` 目录的总入口。

## 核心入口

- 设计体系：[`DESIGN.md`](./DESIGN.md)
- 计划体系：[`PLANS.md`](./PLANS.md)
- 产品视角：[`PRODUCT_SENSE.md`](./PRODUCT_SENSE.md)
- 质量观察：[`QUALITY_SCORE.md`](./QUALITY_SCORE.md)
- 可靠性要求：[`RELIABILITY.md`](./RELIABILITY.md)
- 安全要求：[`SECURITY.md`](./SECURITY.md)
- 前端边界：[`FRONTEND.md`](./FRONTEND.md)

## 目录分区

- 设计文档：[`design-docs/`](./design-docs)
- 执行计划：[`exec-plans/`](./exec-plans)
- 生成类文档：[`generated/`](./generated)
- 产品规格：[`product-specs/`](./product-specs)
- 参考资料：[`references/`](./references)

## 历史专题文档处理说明

以下历史专题文档不再是主入口，后续删除前应逐项核对剩余内容：

- Maven Central 发布的原则性约束已合并到 [`RELIABILITY.md`](./RELIABILITY.md) 与 [`SECURITY.md`](./SECURITY.md)，但操作细节仍需继续迁移
- Parent / BOM / Starter 边界已合并到 [`../ARCHITECTURE.md`](../ARCHITECTURE.md) 与 [`design-docs/module-boundaries.md`](./design-docs/module-boundaries.md)，删除旧文档前仍应核对是否有遗漏

第一次进入仓库，先看 [`../ARCHITECTURE.md`](../ARCHITECTURE.md) 和 [`DESIGN.md`](./DESIGN.md)。
