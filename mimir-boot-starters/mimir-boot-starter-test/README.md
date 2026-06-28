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
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 2. 使用测试基类

#### 单元测试

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

### 测试基类

#### BaseUnitTest

单元测试基类，提供：

- 自动初始化 Mockito
- 自动清理测试环境
- 可重写的 `setUp()` 和 `tearDown()` 方法

#### BaseIntegrationTest

集成测试基类，提供：

- 自动配置 Spring Boot 测试环境
- 使用 `test` profile
- 自动清理测试环境

#### BaseWebTest

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

测试环境会自动加载 `application-test.yml` 配置文件，你可以在项目中创建此文件来自定义测试配置。

### 依赖管理

所有测试依赖的版本由 `mimir-boot-bom` 统一管理，无需手动指定版本。

当前 `mimir-boot-starter-test` 会向下游暴露完整的 Spring Boot 测试基础栈，因此在大多数场景下，
只需要引入该 Starter 即可开始编写基于 JUnit 5、Mockito、AssertJ 和 Spring Test 的测试代码。

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
