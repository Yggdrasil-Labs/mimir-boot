# Mimir Boot

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.13-brightgreen.svg)
![Maven](https://img.shields.io/badge/Maven-3.9.9-blue.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

> Yggdrasil-Labs 的 Java 企业级基础框架  
> 统一依赖版本、公共组件、自定义 Starter 与编译期代码生成工具。

[快速开始](#-快速开始) • [模块说明](#-模块说明) • [技术栈](#-技术栈) • [特性展示](#-特性展示)

</div>

## ✨ 特性

- 🎯 **企业级基座**: 统一技术栈，减少重复配置
- 📦 **依赖管理**: Parent POM + BOM 模式，版本统一管理
- 🛠️ **公共组件**: 常量、枚举、异常处理、分页等
- 🪵 **智能日志**: 自动脱敏、Trace追踪、访问日志
- 🔐 **配置安全**: Nacos 配置加密脱敏，支持 ENC() 格式
- 🔧 **开箱即用**: 多个 Starter 模块，快速接入
- 📋 **质量保证**: Checkstyle、单元测试、JaCoCo 覆盖率

## 📦 模块说明

| 模块 | 描述 | 状态 |
|------|------|------|
| `mimir-boot-parent` | 父 POM，提供插件版本和构建配置 | ✅ 已完成 |
| `mimir-boot-bom` | 依赖版本统一管理（BOM） | ✅ 已完成 |
| `mimir-boot-common` | 公共模型与工具类 | ✅ 已完成 |
| `mimir-boot-starter-log` | 日志启动器（Logback + 脱敏 + 访问日志） | ✅ 已完成 |
| `mimir-boot-starter-exception` | 异常处理启动器（全局异常处理、统一响应） | ✅ 已完成 |
| `mimir-boot-starter-web` | Web 层启动器（CORS、Trace、响应增强） | ✅ 已完成 |
| `mimir-boot-starter-mybatis` | MyBatis 启动器（分页、审计、加密字段） | ✅ 已完成 |
| `mimir-boot-starter-mybatis-processor` | MyBatis 编译期处理器（自动 Mapper 扫描） | ✅ 已完成 |
| `mimir-boot-starter-nacos` | Nacos 配置加密启动器（ENC() 格式解密） | ✅ 已完成 |
| `examples` | 示例项目集合 | ✅ 进行中 |

## 🚀 快速开始

### 1. 继承 Parent POM 并引入 BOM

```xml
<parent>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-parent</artifactId>
  <version>1.0-SNAPSHOT</version>
</parent>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.yggdrasil.labs</groupId>
      <artifactId>mimir-boot-bom</artifactId>
      <version>1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### 2. 添加依赖

```xml
<dependencies>
  <!-- Spring Boot Web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>

  <!-- Mimir Boot Log Starter - 日志模块 -->
  <dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-log</artifactId>
  </dependency>

  <!-- Mimir Boot Common - 公共组件 -->
  <dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-common</artifactId>
  </dependency>
</dependencies>
```

### 3. 配置应用名称（可选）

在 `application.yml` 中配置应用名：

```yaml
spring:
  application:
    name: your-app-name

logging:
  level:
    root: INFO
```

日志文件将保存在 `logs/your-app-name/` 目录下。

### 4. 运行示例项目

```bash
# 进入示例项目目录
cd examples/example-general-web-project

# 运行
mvn spring-boot:run
```

访问 `http://localhost:8080/hello` 查看效果。

## 🔧 常用命令

```bash
# 构建整个项目
mvn clean install

# 构建特定模块
mvn clean install -pl mimir-boot-starter-log -am

# 跳过测试
mvn clean package -DskipTests

# 代码质量检查
mvn checkstyle:check -Pquality

# 使用 Maven Wrapper
./mvnw clean install
```

## 📋 技术栈

| 类别 | 主要技术 |
|------|---------|
| **运行环境** | Java 17 (LTS) |
| **应用框架** | Spring Boot 3.3.13 |
| **微服务** | Spring Cloud 2023.0.6 (Leyton) |
| **配置中心** | Spring Cloud Alibaba Nacos 2023.0.3.4 |
| **数据库** | MyBatis-Plus 3.5.14, MySQL 8.4, PostgreSQL 42.7 |
| **工具类** | Hutool 5.8.41, Lombok 1.18.42, MapStruct 1.6.3 |
| **日志** | Logback, SLF4J 2.0.17 |
| **测试** | JUnit 5, Mockito, Testcontainers |
| **监控** | Micrometer, Prometheus, JaCoCo |

完整技术栈列表请参考 [mimir-boot-bom/pom.xml](mimir-boot-bom/pom.xml)

## 💡 特性展示

### 📦 智能依赖管理

- **统一 BOM 管理**: 所有第三方依赖版本由 BOM 统一管理
- **自动版本同步**: 避免版本冲突，确保依赖兼容性
- **与 Spring Boot 兼容**: 依赖版本与 Spring Boot BOM 保持一致

### 🪵 企业级日志方案

引入 `mimir-boot-starter-log` 后自动提供：

#### ✨ 自动敏感信息脱敏
```java
log.info("用户登录，用户名: {}, 密码: {}", "admin", "123456");
// 输出: 用户登录，用户名: admin, 密码: ******
```

#### 🔍 TraceId & SpanId 支持
```log
2025-01-28 10:23:45.123 [http-nio-8080-exec-1] INFO  [abc-123] [span-456] com.yggdrasil.labs.example.GeneralWebProject - Hello World
```

#### 📊 访问日志
- 自动记录每个 HTTP 请求
- 自动识别慢接口（超过阈值自动 WARN）
- 文件：`logs/{app-name}/access.log`

#### 🌈 多环境配置
- **开发环境**: 彩色控制台输出 + 文件输出
- **测试环境**: 控制台 + 文件
- **生产环境**: 仅文件输出，配置优化

详细文档请参考 [mimir-boot-starter-log/README.md](mimir-boot-starters/mimir-boot-starter-log/README.md)

### 🔐 配置加密脱敏

引入 `mimir-boot-starter-nacos` 后提供配置加密功能：

#### ✨ 自动解密配置
在 Nacos 配置中心使用 `ENC(encrypted_value)` 格式：
```yaml
# Nacos 配置
database:
  password: ENC(encrypted_base64_string)
  
# 应用运行时自动解密
# database.password = "decrypted_plaintext"
```

#### 🔄 动态刷新支持
配置刷新时自动重新解密：
```java
@RefreshScope
@Component
public class ConfigBean {
    @Value("${database.password}")
    private String password;  // 始终是解密后的值
}
```

#### 🛠️ 工具类支持
```java
// 生成密钥
String key = NacosEncryptUtil.generateKey();

// 加密配置值
String encrypted = NacosEncryptUtil.encrypt("secret", key);
String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);
// 输出: ENC(encrypted_value)
```

详细文档请参考 [mimir-boot-starter-nacos/README.md](mimir-boot-starters/mimir-boot-starter-nacos/README.md)

### 🔧 Web 层增强

引入 `mimir-boot-starter-web` 后自动提供：

#### 🎯 统一响应格式
```java
@GetMapping("/api/user/{id}")
public R<UserVO> getUser(@PathVariable Long id) {
    return R.success(userService.getById(id));
    // 自动填充 traceId
}
```

#### 🔍 Trace 追踪
- 自动生成或获取 traceId
- 设置到 MDC 和响应头
- 支持分布式追踪

详细文档请参考 [mimir-boot-starter-web/README.md](mimir-boot-starters/mimir-boot-starter-web/README.md)

### 💾 持久层增强

引入 `mimir-boot-starter-mybatis` 后自动提供：

#### ✨ 自动配置拦截器
- 分页拦截器（自动识别 Page 对象）
- 乐观锁拦截器（自动版本控制）
- 审计字段自动填充（createdBy、updatedTime 等）

#### 🔐 字段加解密
```java
@TableField(typeHandler = StringCryptoTypeHandler.class)
private String phoneNumber;  // 自动加密存储、解密读取
```

详细文档请参考 [mimir-boot-starter-mybatis/README.md](mimir-boot-starters/mimir-boot-starter-mybatis/README.md)

## 📚 模块文档

- [mimir-boot-common](mimir-boot-common/README.md) - 公共组件说明
- [mimir-boot-starter-log](mimir-boot-starters/mimir-boot-starter-log/README.md) - 日志启动器文档
- [mimir-boot-starter-exception](mimir-boot-starters/mimir-boot-starter-exception/README.md) - 异常处理启动器文档
- [mimir-boot-starter-web](mimir-boot-starters/mimir-boot-starter-web/README.md) - Web 层启动器文档
- [mimir-boot-starter-mybatis](mimir-boot-starters/mimir-boot-starter-mybatis/README.md) - MyBatis 启动器文档
- [mimir-boot-starter-mybatis-processor](mimir-boot-starters/mimir-boot-starter-mybatis-processor/README.md) - MyBatis 编译期处理器文档
- [mimir-boot-starter-nacos](mimir-boot-starters/mimir-boot-starter-nacos/README.md) - Nacos 配置加密启动器文档

## 🏗️ 项目结构

```
mimir-boot/
├── mimir-boot-parent/                    # 父 POM，插件和构建配置
├── mimir-boot-bom/                        # 依赖版本管理（BOM）
├── mimir-boot-common/                     # 公共组件
├── mimir-boot-starters/                   # Starter 集合
│   ├── mimir-boot-starter-log/            # 日志 Starter
│   ├── mimir-boot-starter-exception/      # 异常处理 Starter
│   ├── mimir-boot-starter-web/            # Web 层 Starter
│   ├── mimir-boot-starter-mybatis/        # MyBatis Starter
│   ├── mimir-boot-starter-mybatis-processor/  # MyBatis 编译期处理器
│   └── mimir-boot-starter-nacos/          # Nacos 配置加密 Starter
├── examples/                              # 示例项目
│   └── example-general-web-project/
└── README.md                              # 本文件
```

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 📞 联系我们

- **GitHub**: https://github.com/Yggdrasil-Labs/mimir-boot
- **组织**: Yggdrasil Labs
