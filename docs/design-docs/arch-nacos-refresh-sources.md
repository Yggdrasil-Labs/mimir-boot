---
id: nacos-refresh-sources
status: draft
owner: YoungerYang-Y
created: 2026-10-09
---

# Nacos 配置刷新中的属性源归属与删除

## 背景与目标

Cloud Context 4.1.6 只新增、替换重载后的属性源，不删除消失的旧源；环境变更键又在解密监听器清理之前计算。Nacos 空配置、DataId 删除和 YAML 文档减少因此可能保留旧值或漏报删除键。

目标是在本 Starter 默认 ConfigData 接入中，成功刷新后同步标准枚举 Nacos 原始源、解密层和有效值变更键，保留原 import 参数、格式、优先级与完整 RefreshScope 流程。普通 Bean 的重置由消费者显式选择；不清理应用自定义源，不修补所有本地文件源消失的行为。

方案依据为实际 Boot 3.3.13、Cloud Context 4.1.6、Alibaba 2023.0.3.4、Nacos Client 2.4.3 的本地 API 与字节码，已完成本地实现和完整质量验收，真实服务验收待认证凭据。

## 架构方案

### ConfigData 委托与明确归属

公共 SPI 中增加委托 resolver、独立 resource 与 loader。resolver 顺序 -2，优先于原 Alibaba 的 -1，继续处理 `nacos:`；同时委托 resolve 与 resolveProfileSpecific，将原资源包装成独立 ConfigDataResource，避免原 loader 重复匹配。

loader 委托原 Nacos loader，不重写内容、地址或格式解析。成功结果包装为带归属的枚举属性源，保留源名、值、已有 OriginLookup 和逐源 ConfigData options。归属使用服务地址、namespace、group、DataId 的稳定标识，不依赖资源对象引用、全局 repository 或凭据。

成功空 ConfigData 生成无配置键的占位源，标记该资源已经成功加载为空；保留原 options。optional 异常返回 null，不生成成功标记；非 optional 异常照常传播。SDK 自身 snapshot/failover 行为沿用上游，不承诺服务端线性一致读取。

上游已吞掉的 optional 异常不使完整刷新失败，该资源旧源保留，其他成功结果继续应用，RefreshScope 仍按完整刷新流程失效。只有传播出的加载、同步或解密异常才恢复旧源列表并保留现有作用域目标。

### 在差异计算前同步

仅当默认 ConfigData 模式、刷新启用、没有自定义 ContextRefresher 时，使用原 ConfigDataContextRefresher 子类，继续其 EnvironmentPostProcessor 和全环境重载流程。捕获独立重载环境和刷新前源列表。

Nacos 自动配置在 Cloud 刷新自动配置之后参与。ImportBeanDefinitionRegistrar 在后续自动配置条件评估前，只定制 Cloud 原 `configDataContextRefresher` 工厂定义的实例供应器与子类目标类型，保留原 Bean 名称、工厂元数据和完整初始化生命周期。模块内工厂 Bean 只持有应用上下文，刷新器正常实例化时才取得 RefreshScope 与刷新属性；不提前创建刷新依赖。上游被注解或配置项排除时，没有默认定义可定制，应用正常退让。用户自定义工厂（包括同名 Bean）或已有实例供应器不替换，多刷新器定义也不参与定制。

副本携带不提供配置键的私有重载状态，记录刷新前源的对象引用；解密处理器先区分新加载对象与原样复制的 retain 源，去除已被同资源新结果替代的旧文档，再移除私有状态。仅复制的旧文档不能授权清理，避免 optional 失败或移除 import 时误删其他文档。

原合并返回后、差异计算前，只移除本次成功加载资源下未再出现的旧受管理源，包括 YAML 文档收缩。optional 失败、移除 import、非 Nacos 源及应用自行替换的源均不提供删除授权。随后按合并后的完整环境重建解密覆盖层，保留应用附加源中的加密配置。

若发布环境事件前重载、同步或解密失败，恢复刷新前的源引用及顺序并抛异常；不发布成功环境事件，不销毁旧作用域目标。此恢复不提供跨任意并发 Environment 写入的事务隔离，也不回滚 SDK 内部快照；原合并到恢复之间的并发直接读取可能观察到中间状态，不宣称失败前实时环境完全不动。

之后继续原 Cloud 差异计算、一次 EnvironmentChangeEvent、RefreshScope 失效。单独环境事件仍不重建目标；新目标在下次代理访问时创建，初始化、绑定、校验失败是独立边界。

事件仍按有效值差异报告：删除导致缺失或回退为不同值时包含该键，有效值不变时不承诺通知每次底层修改。自定义非枚举 parser 源保留原对象/options，不提供标准枚举源的自动删除契约。

## 兼容与取舍

保留 nacos:/optional:、格式、preference、refreshEnabled、多文档和生命周期。自定义 ContextRefresher 与 legacy bootstrap 不替换，需应用自行满足删除契约。Origin 只保留上游已有能力，不补回 parser 已丢失的位置。普通 Bean、裸目标和历史字段快照不自动清空，不保证擦除 JVM 所有明文引用。

仅事件监听器清理不能修复漏报；按源名猜测会误删且混淆 optional 失败；重写 Nacos 事件刷新会改变全环境重载与格式处理。因此选择 SPI 委托与默认刷新器扩展。代价是新增加载适配类，框架升级时需重验路由、options 与事件顺序。

## 决策与验证

用户于 2026-10-09 授权启动指定本地 Nacos 容器并继续修复 TD-039；本 RFC 是该范围内的实施方案，独立复核后继续，不将草案写成已验证。真实服务只写本轮唯一测试 group/DataId，动态测试密钥与认证凭据不提交。

验收覆盖委托路由、空与失败区分、源归属隔离、多文档收缩、删除键、解密失败恢复、自定义刷新器退让及真实推送和代理消费。后合并回滚另以未被副本重载的应用附加 ENC 源验证：副本新密钥解密成功，合并后旧密钥附加源失败，原源引用/顺序、旧值和现有目标应恢复，成功事件为零；修正后恢复刷新。真实推送的失败验收只接受本轮预期配置键对应、cause 链含认证失败的解密异常，不能用历史异常或加载失败替代。[实施及验收计划](../active/v2.3.0/runtime-debt-followup/plan.md#第六阶段td-039-运行时同步与真实-nacos-验收)负责具体证据，[复审修复](../active/v2.3.0/runtime-debt-followup/plan.md#第七阶段td-039-复审问题修复)补充装配排除与异常归因；[台账](../active/tech-debt-tracker.md#td-039-nacos-解密覆盖层)保留未迁移消费者。
