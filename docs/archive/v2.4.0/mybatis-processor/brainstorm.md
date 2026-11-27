---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis Processor 兼容小修方案大纲

## 本轮推荐与 SDD 产物

2026-10-29 用户要求停止逐项提问，并补齐每项需求的 SDD。本文保留前期讨论过程；未逐项确认的细节已由本轮推荐方案收敛到 [spec](./spec.md)、[design](./design.md)、[plan](./plan.md)，推荐不追记为历史用户决定。以下“候选”“待讨论”“后续”等措辞描述此前状态，当前推荐以 SDD 为准；文档不代表实现或验收完成。

本稿承接[版本级 brainstorm](../brainstorm.md)的 D-71，只讨论处理器支持源版本与生成冲突说明。它不扩展生成能力，不代表实现或验收已完成。

## 需求与边界

- 支持的源版本跟随实际编译器，避免较高编译目标下固定声明 Java 17 产生过时支持版本告警。
- 保持现有 Mapper、Service、ServiceImpl 的命名、包路径和生成规则。
- 纠正 README“问题4”：不同实体采用相同后缀配置并不必然冲突，应解释最终生成全限定名相同的情况。
- 保留 classpath 服务发现与消费者 `provided` 接入方式，不新增处理器隔离、选择生成或覆盖手写类能力。
- 不把消除告警等同于承诺所有未来 JDK 和语言特性都已验证。

## 已核对的代码事实

本轮静态阅读了 [Processor 实现](../../../../mimir-boot-starters/mimir-boot-starter-mybatis-processor/src/main/java/com/yggdrasil/labs/mybatis/processor/AutoMybatisProcessor.java)、[现有编译测试](../../../../mimir-boot-starters/mimir-boot-starter-mybatis-processor/src/test/java/com/yggdrasil/labs/mybatis/processor/AutoMybatisProcessorTest.java)、[模块 POM](../../../../mimir-boot-starters/mimir-boot-starter-mybatis-processor/pom.xml)及 [README](../../../../mimir-boot-starters/mimir-boot-starter-mybatis-processor/README.md)。

- 实现类使用 `@SupportedSourceVersion(SourceVersion.RELEASE_17)`，尚未重写支持源版本查询方法。
- 通过 `META-INF/services/javax.annotation.processing.Processor` 注册处理器。
- 模块自身编译显式设置 `proc=none`，用于构建处理器本身；这不等于消费者应关闭注解处理。
- 实体名以 `DO` 结尾且长度大于 2 时会去除该后缀，再拼接 Mapper、Service、ServiceImpl 后缀。
- 生成包由实体所在包与配置中的相对包拼接；最终全限定名由生成包与生成类名共同决定。
- 写文件遇到 `IOException` 时输出编译错误；当前没有覆盖或自动改名策略。
- 已有测试通过显式注入处理器验证代码生成，并覆盖 `UserDO` 去后缀行为；这不证明发布 JAR 的 classpath 自动发现可用。
- README 当前拿 `User`、`Order` 配相同生成规则作为冲突症状的背景，未解释名称归一化后的真正冲突。

本轮未运行编译测试，也未重新检查版本级记录中的三个内部消费者；其 24 个实体使用情况仍以版本级记录的历史核对范围为限。

## 候选方案一：支持版本跟随编译器

建议移除固定版本声明，以查询方法返回编译器可支持的源版本。具体方法实现与行为断言在后续详细设计中确定。

| 方向 | 优点 | 需要注意 |
|---|---|---|
| 跟随编译器所支持的最新源版本 | 不必每次 JDK 升级都改固定枚举，与 D-71 方向一致 | 声明范围变宽，仍需说明实际验证过的 JDK |
| 跟随本次编译任务的目标源版本 | 反映本次任务配置，可能更保守 | 与处理器初始化时序耦合，需要明确初始化前查询行为 |

当前倾向第一种；两种方式都不修改消费者的 `java.version` 或编译目标，也不提高处理器制品自身的最低运行版本。

固定改成 Java 21 或更高枚举不作为推荐方向：它既不能持续跟随编译器，也可能破坏处理器继续在 Java 17 上运行的要求。

## 候选方案二：用真实冲突解释命名规则

建议 README 用同一实体包中的 `UserDO` 与 `User` 展示冲突，两者采用相同生成包与后缀时，都会尝试生成相同的 `UserMapper` 等类型。

可比较两种避让方式：

| 方向 | 好处 | 代价 |
|---|---|---|
| 为其中一个实体调整生成包 | 可保留短类名，类的归属更清楚 | 需要调整引用或扫描配置，三个生成类型的包都要检查 |
| 为其中一个实体调整生成后缀 | 可保持已有包布局 | 类名较长，需检查 Mapper、Service、ServiceImpl 是否全部避让 |

示例应同时说明 `User` 与 `Order` 仅使用相同后缀不会冲突；不同实体包也要比较最终生成包，不能仅按实体简单类名判断。

不建议只改一个 Mapper 后缀却声称“已消除全部冲突”，因为 Service 与 ServiceImpl 仍可能重名。文档必须覆盖三类产物。

## 关联需求

- [Parent 构建需求](../parent-build/brainstorm.md)的 P-02 决定较新 JDK 是否默认执行注解处理；本需求只处理处理器自己的支持版本声明。
- 本模块 `proc=none` 必须在继承 Parent 新配置后继续生效；消费者则应按自己的开关决定是否生成。
- [BOM 配套依赖](../bom-dependencies/brainstorm.md)与本需求可分别讨论；不因 Processor 小修添加 Lombok、MapStruct 或其他运行依赖。
- 验收需要最终发布形态的 Processor JAR，以及 MyBatis-Plus、Spring 注解等生成代码实际所需类型。

## 验收方向

| 验证面 | 应观察的结果 | 不能替代的证据 |
|---|---|---|
| 支持版本声明 | 在所测编译器及目标组合下不出现处理器支持版本落后告警 | 不能替代生成代码编译 |
| 原有生成规则 | 三类产物存在、引用正确，`UserDO` 仍引用原实体类型 | 不能仅检查支持版本方法返回值 |
| 名称冲突 | `UserDO` 与 `User` 相同配置产生可解释的编译错误 | 不要求本轮新增自动处理策略 |
| 文档避让示例 | 调整后的三个生成全限定名均不同，能够一起编译 | 不能只比较 Mapper 名称 |
| 独立消费者 | 以 `provided` 引入发布 JAR，依靠 classpath 发现生成代码 | 显式传入处理器的现有单元测试不足以证明此项 |

JDK 17、21、25 与 Parent 联合选择验证组合：同一较新 JDK 下分别检查目标 17 与受支持的较高目标，区分“使用新编译器”和“提高目标版本”。消费者显式关闭处理时，不应残留上次构建的生成结果来误判成功。

## 下一轮优先讨论

首先确认支持版本查询采用哪条候选路径，并约定文档中实际支持与已验证 JDK 的表述。随后收敛冲突示例与最小回归范围；不为这一小修增加独立构建工具或新模块。
