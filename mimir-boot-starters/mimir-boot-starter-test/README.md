# Mimir Boot Starter Test

Mimir Boot 测试 Starter，提供统一的测试依赖管理、测试工具类和测试基类。

引入该 Starter 后，下游测试模块除了可以使用 `BaseUnitTest`、`BaseIntegrationTest`、`BaseWebTest`
和 `@MimirBootTest` 等抽象外，也可以直接使用 `spring-boot-starter-test` 提供的常用测试能力，
例如 JUnit 5、Mockito、AssertJ、Spring Test 与 MockMvc 等，无需再额外补充基础测试依赖。

## 📦 功能特性

- ✅ **统一测试依赖管理**：集中管理所有测试相关依赖，版本由 BOM 统一控制
- ✅ **测试工具类**：提供测试数据生成、MDC 管理、断言增强等工具方法
- ✅ **测试基类**：提供单元测试、集成测试、Web 测试基类，减少样板代码
- ✅ **测试配置**：自动配置测试环境，提供测试专用的配置和注解

## 🚀 快速开始

### 1. 添加依赖

在项目的 `pom.xml` 中添加：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.yggdrasil-labs</groupId>
        <artifactId>mimir-boot-starter-test</artifactId>
        <version>YOUR_RELEASE_VERSION</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 2. 使用测试基类

#### 单元测试

自定义准备与清理钩子的执行顺序见 [BaseUnitTest](#baseunittest)。

```java
import com.yggdrasil.labs.test.base.BaseUnitTest;

class MyServiceTest extends BaseUnitTest {
    
    @Mock
    private MyRepository repository;
    
    @Test
    void testSomething() {
        // 测试代码
    }
}
```

#### 集成测试

```java
import com.yggdrasil.labs.test.base.BaseIntegrationTest;

class MyServiceIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private MyService myService;
    
    @Test
    void testSomething() {
        // 测试代码
    }
}
```

#### Web 测试

```java
import com.yggdrasil.labs.test.base.BaseWebTest;

class MyControllerTest extends BaseWebTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testEndpoint() throws Exception {
        mockMvc.perform(get("/api/test"))
            .andExpect(status().isOk());
    }
}
```

### 3. 使用测试工具类

#### 测试数据生成

```java
import com.yggdrasil.labs.test.util.TestUtils;

@Test
void testWithRandomData() {
    String traceId = TestUtils.randomTraceId();
    String userId = TestUtils.randomUserId();
    String ip = TestUtils.randomIp();
    
    // 使用生成的数据进行测试
}
```

#### MDC 上下文管理

```java
import com.yggdrasil.labs.test.util.TestUtils;

@Test
void testWithMdc() {
    // 设置测试 MDC
    TestUtils.setupMdc("trace-123", "user-456", "192.168.1.1");
    
    // 执行测试
    
    // 自动清理（基类会自动清理）
}
```

#### 断言增强

```java
import com.yggdrasil.labs.test.util.AssertUtils;

@Test
void testAssertions() {
    List<String> list = Arrays.asList("a", "b", "c");
    AssertUtils.assertContains(list, "a");
    
    Map<String, String> map = new HashMap<>();
    map.put("key", "value");
    AssertUtils.assertContainsKey(map, "key");
    
    AssertUtils.assertNotBlank("test");
}
```

#### Mock 数据构建器

```java
import com.yggdrasil.labs.test.util.MockDataBuilder;

@Test
void testWithMockData() {
    User user = MockDataBuilder.of(User.class)
        .with(u -> u.setName("test"))
        .with(u -> u.setEmail("test@example.com"))
        .build();
    
    // 使用构建的数据进行测试
}
```

### 4. 使用测试注解

```java
import com.yggdrasil.labs.test.annotation.MimirBootTest;

@MimirBootTest
class MyServiceTest {
    
    @Autowired
    private MyService myService;
    
    @Test
    void testSomething() {
        // 测试代码
    }
}
```

## 📚 API 文档

### 测试工具类

#### TestUtils

提供测试数据生成和 MDC 管理功能：

- `randomUuid()`: 生成随机 UUID
- `randomTraceId()`: 生成随机 traceId
- `randomRequestId()`: 生成随机 requestId
- `randomUserId()`: 生成随机 userId
- `randomIp()`: 生成随机 IP 地址
- `setupMdc(traceId, userId, ip)`: 设置测试 MDC
- `setupFullMdc(...)`: 设置完整 MDC
- `clearMdc()`: 清理 MDC
- `setupRandomMdc()`: 设置随机 MDC
- `cleanupTestEnvironment()`: 清理测试环境

#### AssertUtils

提供增强的断言方法：

- `assertContains(collection, element)`: 断言集合包含元素
- `assertContainsKey(map, key)`: 断言 Map 包含 key
- `assertNotBlank(str)`: 断言字符串不为空
- `assertEquals(expected, actual)`: 断言相等（处理 null）

#### MockDataBuilder

提供链式构建测试数据：

```java
MockDataBuilder.of(MyClass.class)
    .with(obj -> obj.setField1("value1"))
    .with(obj -> obj.setField2("value2"))
    .build();
```

#### LogTestUtils

提供基于 Logback `ListAppender` 的日志断言：`setupLogger`、`cleanupLogger`、`assertLogLevel`、`assertLogContains`、`assertLogStatus` 与 `getLogSize`。使用这些方法时，测试运行时需要具备 Logback；该依赖在 Starter 中标记为可选依赖。

#### Servlet Mock 构建器

`HttpServletRequestMockBuilder` 用于构造 `HttpServletRequest` Mock，`FilterChainMockBuilder` 用于构造可设置响应状态码的 `FilterChain` Mock。两者依赖 Jakarta Servlet API；使用它们的项目需在测试范围提供 Servlet API（Starter 中为可选依赖）。

```java
HttpServletRequest request = HttpServletRequestMockBuilder.create()
    .uri("/users/1")
    .method("GET")
    .remoteAddr("192.168.1.100")
    .defaultIpHeaders()
    .build();

FilterChain chain = FilterChainMockBuilder.create()
    .statusCode(204)
    .build();
```

### 测试基类

#### BaseUnitTest

单元测试基类，提供：

- 自动初始化 Mockito
- 自动清理测试环境
- 可重写的 `setUp()` 和 `tearDown()` 方法

每次测试开始前清理环境，再调用 `setUp()`；测试结束时先调用 `tearDown()`，随后在
`finally` 中清理 MDC 等测试环境。自定义清理抛出的异常继续传播，统一清理仍会执行。
`tearDown()` 可以读取测试留下的 MDC，钩子写入的 MDC 也会在结束后清空。

#### BaseIntegrationTest

集成测试基类，提供：

- 自动配置 Spring Boot 测试环境
- 使用 `test` profile
- 自动清理测试环境

#### BaseWebTest

使用方应已具备 Spring MVC 运行时，例如通过 `spring-boot-starter-web` 引入。

Web 测试基类，提供：

- 自动配置 Spring Boot 测试环境
- 自动配置 MockMvc
- 使用 `test` profile
- 自动清理测试环境

### 测试注解

#### @MimirBootTest

简化 Spring Boot 测试配置：

- 自动使用 `test` profile
- 自动配置 Spring Boot 测试环境
- 支持自定义配置

## 🔧 配置

### 测试环境配置

`BaseIntegrationTest`、`BaseWebTest` 和 `@MimirBootTest` 会激活 `test` profile。Starter 自身不再通过类路径资源注入数据库策略、`show-sql` 或固定应用名；接入方应在自己的 `application-test.yml` 中显式配置测试环境。

### 依赖管理

所有测试依赖的版本由 `mimir-boot-bom` 统一管理，无需手动指定版本。

当前 `mimir-boot-starter-test` 会向下游暴露完整的 Spring Boot 测试基础栈，因此在大多数场景下，
只需要引入该 Starter 即可开始编写基于 JUnit 5、Mockito、AssertJ 和 Spring Test 的测试代码。

### JUnit Suite

Starter 也会向下游测试类路径提供 JUnit Suite API 和 Engine。可以直接用 Suite 聚合不符合
Surefire 默认命名规则的测试类：

```java
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({OrderScenario.class, PaymentScenario.class})
class AcceptanceSuiteTest {}
```

Testcontainers 按业务模块的实际需要单独引入；Starter 不再内置未使用的容器测试依赖。

### 架构依赖规则

架构规则位于 `com.yggdrasil.labs.test.arch`，按需调用，不会自动执行。ArchUnit 是 Starter 的
optional 依赖；只依赖 Starter 的消费者不会获得 ArchUnit。要使用规则，继承 Parent 或导入
BOM 后显式添加核心库，以普通 JUnit Jupiter 测试调用 `rule.check(classes)`：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.yggdrasil-labs</groupId>
        <artifactId>mimir-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.tngtech.archunit</groupId>
        <artifactId>archunit</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

BOM 托管核心库 1.5.1，传递依赖只有 `slf4j-api`，其有效版本由 BOM 管理。不引入
`archunit-junit5`，避免额外引入与当前 Spring Boot 基线不同的 JUnit Platform 引擎。

所有包参数都传**完整包名**，包括 `LayerSpec.layer(name, packageNames)` 中的包名；例如
`com.acme.order.domain`。内部会包含它的全部子包，无需传 `..`、`*` 等 ArchUnit 包标识。
参数缺失或无效会抛出 `IllegalArgumentException`，消息包含参数名；导入为空会抛出
`IllegalStateException`。分层声明至少包含两层，执行时也必须实际导入至少两个有类的层。

#### COLA 预设与多模块导入

```java
import com.tngtech.archunit.core.domain.JavaClasses;
import com.yggdrasil.labs.test.arch.ColaArchRules;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    @Test
    void followsColaArchitecture() {
        JavaClasses classes = MimirArchClasses.importMain("com.acme.order");
        ColaArchRules.recommended("com.acme.order").check(classes);
    }
}
```

`recommended` 组合 `layered`、`domainIsFrameworkFree` 和 `MimirArchRules.noCycles`。
COLA 预设采用以下访问关系，未出现的层可选；各层之外的启动、装配代码不受分层规则约束。

| 被访问层 | 允许访问它的其他层 |
| --- | --- |
| `adapter` | 无 |
| `app` | `adapter` |
| `client` | `adapter`、`app` |
| `domain` | `app`、`infrastructure` |
| `infrastructure` | 无 |

领域层不得依赖 Spring、MyBatis、MyBatis-Plus、Servlet、JPA，覆盖 `jakarta` 与 `javax`
包。`domainIsFrameworkFree` 单独使用时允许领域层缺失；`layered` 与 `recommended`
仍要求实际出现至少两层。循环规则按根包的直接子包切片，不检查同一直接子包内部的循环。

多模块工程将上述测试放在依赖所有待检查模块的 `start` 模块，并使用 `importMain`。
它同时导入类路径上的生产输出目录与 jar，排除测试输出目录，适用于 reactor 的 `test`
和 `verify` 阶段；不要使用排除 jar 的导入选项。若多个模块含同名包且只想检查本模块，
使用 `MimirArchClasses.importDirectory(Path.of("target/classes"))`；目录不存在或没有类时会失败。

#### 自定义分层与模块白名单

```java
import com.tngtech.archunit.core.domain.JavaClasses;
import com.yggdrasil.labs.test.arch.LayerSpec;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModuleArchitectureTest {
    @Test
    void keepsDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        LayerSpec layers = LayerSpec.of("com.acme.order")
                .layer("controller")
                .layer("service", "com.acme.order.service", "com.acme.order.usecase")
                .layer("repository");
        MimirArchRules.layered(layers).check(classes);
        MimirArchRules.noCycles("com.acme.order").check(classes);
        MimirArchRules.noDependencyOn("com.acme.order.repository",
                "com.acme.order.controller").check(classes);
        MimirArchRules.onlyDependOnWithin("com.acme", "com.acme.order",
                "com.acme.common").check(classes);
    }
}
```

自定义层按声明顺序自上而下，下层不得依赖上层，层外的包不参与分层限制。
`onlyDependOnWithin` 限制命名空间内的依赖，自身包及允许包均含子包，命名空间外的库不受限制。
违规使用 ArchUnit 原生格式，包含源类、目标类的全限定名和源码位置。

#### 冻结存量违规

存量项目可直接使用 ArchUnit 的 `FreezingArchRule`，仅阻止新增违规：

```java
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;
import org.junit.jupiter.api.Test;

class FrozenArchitectureTest {
    @Test
    void introducesNoNewDependencies() {
        FreezingArchRule.freeze(MimirArchRules.noDependencyOn(
                "com.acme.order.domain", "com.acme.order.app"))
                .check(MimirArchClasses.importMain("com.acme.order"));
    }
}
```

首次建立基线，在测试资源 `src/test/resources/archunit.properties` 中配置：

```properties
freeze.store.default.path=archunit_store
freeze.store.default.allowStoreCreation=true
```

检查并保留生成的基线文件。后续运行及 CI 将 `allowStoreCreation` 改为 `false` 或移除该配置
（默认禁止创建）；已有存储仍可读取并随违规减少而更新。新增违规会报告失败，规则库没有
额外的冻结封装。

## 📝 最佳实践

1. **使用测试基类**：继承相应的测试基类，减少样板代码
2. **使用工具类**：利用 `TestUtils`、`AssertUtils` 等工具类简化测试代码
3. **清理测试环境**：测试基类会自动清理，但如有特殊需求可重写 `tearDown()` 方法
4. **使用测试配置**：在 `application-test.yml` 中配置测试环境专用的配置

## 📚 相关文档

- [Mimir Boot 项目主页](../../README.md) - 项目总体说明
- [Mimir Boot Common](../../mimir-boot-common/README.md) - 公共组件说明
- [Mimir Boot Parent](../../mimir-boot-parent/README.md) - 父 POM 说明
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing) - Spring Boot 测试文档
