---
version: v2.4.0
status: draft
created: 2026-10-29
updated: 2026-10-31
---

# MyBatis Processor 兼容设计

推荐方案，未实施。[规格](./spec.md) · [计划](./plan.md)

## 背景、目标与非目标

现有 AutoMybatisProcessor 固定声明 RELEASE_17；源码及既有测试已静态核对。目标是 MP1 消除较新编译器上的过时支持声明，并通过 MP2 纠正 README 冲突示例。不改命名、包结构、生成类型或错误处理策略。

## 架构及接口契约

编译器通过既有 services 文件发现处理器 → 查询支持源版本 → 沿用处理器生成路径 → Filer 写入三类源文件。新增覆盖签名 `public SourceVersion getSupportedSourceVersion()`，返回 `SourceVersion.latestSupported()`，移除 `@SupportedSourceVersion(SourceVersion.RELEASE_17)` 及无用 import。无参数、无状态，不依赖 processingEnv 初始化；正常返回当前编译器可支持的最新源级别，不修改本次任务的 release。

选择 latestSupported 而非 processingEnv.getSourceVersion：初始化前也可安全查询，符合跟随编译器的 D-71；改为固定 RELEASE_21 既不能持续跟随，也破坏 Java 17 运行边界。

审查补充：`latestSupported()` 反映的是实际运行 javac 的 JVM，而非 Maven 自身 JVM 或 `release` 目标。Compiler 插件 `fork=true`、`executable` 或 Maven Toolchains 指定其他 JDK 时，以被调用的 javac 进程为准；验收记录应写明实际 javac 版本。

## 文档契约与错误处理

README“问题4”将“User、Order 相同后缀导致冲突”的解释替换为“最终生成包 + 去除 DO 后缀后的类名 + 配置后缀相同才冲突”。使用同包 UserDO/User 作为冲突例；分别调整 Mapper、Service、ServiceImpl 的包或后缀为避让例。

Filer 的 IOException 仍转为编译 ERROR；本轮不覆盖、不自动重命名、不忽略重复。处理器自身 POM 的 proc=none 保留；服务注册路径不变。支持源版本变宽只是声明，不作为所有语法验证证据。

审查补充（推荐）：JDK 23 起 javac 在未显式给出 `-proc`/`-processor`/`-processorpath` 时默认不再运行从 classpath 发现的处理器。Parent P-02 的 `proc=full` 只覆盖继承 Parent 的消费者；B-01 支持仅导入 BOM 的接入方式，此类消费者在 JDK≥23 上会静默得不到生成代码（引用生成类型时才以编译错误暴露）。README 须在“问题1”及接入说明中补充：非 Parent 消费者在 JDK≥23 设置 `<maven.compiler.proc>full</maven.compiler.proc>` 或在 compiler 插件 `compilerArgs` 中加 `-proc:full`；JDK 21/22 仍运行处理器但会给出隐式处理提示，设置 full 亦可消除该提示。不修改服务发现文件、BOM 或处理器 POM。

审查补充（推荐）：README 不建议无条件写死 `proc=full`。`-proc:full` 是 JDK 21 引入的取值，较旧的 JDK 17 构建可能不识别并以无效参数失败；消费者若仍用 JDK 17 构建，应把该设置放入按 JDK 激活的 profile，例如 `<activation><jdk>[23,)</jdk></activation>`（如需同时消除 JDK 21/22 的隐式处理提示可用 `[21,)`），在其中设置 `maven.compiler.proc` 或 `compilerArgs`。`maven.compiler.proc` 属性依赖 maven-compiler-plugin 的 `proc` 参数接受 `full`：本地 `~/.m2` 中 3.13.0、3.14.1、3.15.0、3.16.0 的插件描述符均声明 `proc` 参数绑定 `${maven.compiler.proc}` 且允许值含 `full`（Parent 当前使用 3.16.0）；更早版本未在本地核对，其最低可用版本待验证，使用旧插件的消费者应改用 `compilerArgs` 传 `-proc:full`。

审查补充（推荐）：`<activation><jdk>` 按运行 Maven 的 JVM（`java.version`）判断，而不是 Toolchains 或 `fork=true`/`executable` 实际调用的 javac，这与上文“以实际 javac 为准”存在张力：Maven 跑在 JDK 17、javac 经 toolchain 为 JDK 25 时 profile 不激活而处理器被静默跳过；反之 Maven 跑在 JDK 25、javac 为 JDK 17 时 profile 激活却会因不识别 `-proc:full` 失败。因此使用 maven-toolchains 或 fork 指定不同 javac 的项目不应依赖 jdk profile，应按实际 javac 版本直接在 compiler 插件配置 `compilerArgs`（`-proc:full`）或 `maven.compiler.proc`（javac ≥21 时）；README 示例须注明该限制。

## 验证策略

| 契约 | 方法 | 通过标准 |
|---|---|---|
| MP1 生成与版本 | 扩展既有编译测试；JDK17/21/25消费者 | 三产物实际编译，原UserDO引用不变，无支持版本落后告警 |
| MP1 自动发现 | 发布 JAR 以 provided 进入 classpath；clean编译 | 未显式传入 processor 实例仍能生成；关闭处理则不生成 |
| MP1 仅导入 BOM + JDK 25 | 不继承 Parent 的消费者，JDK 25 clean编译 | 未设 proc 时无生成文件（记录为预期行为）；按 README 设置 `proc=full` 后三产物生成 |
| MP2 冲突及避让 | 编译三个README样例 | User/Order通过；UserDO/User冲突失败；全面避让通过 |

推荐在既有 AutoMybatisProcessorTest 中补有行为价值的编译案例，不只断言方法返回值。本轮未运行以上测试。
