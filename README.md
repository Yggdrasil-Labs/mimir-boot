# Mimir Boot

<div align="center">

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.13-brightgreen.svg)
![Maven](https://img.shields.io/badge/Maven-3.9.9-blue.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

> Yggdrasil-Labs 的 Java 企业级基础框架  
> 统一依赖版本、公共组件、自定义 Starter 与编译期代码生成工具。

[快速开始](#-快速开始) • [模块说明](#-模块说明) • [技术栈](#-技术栈)

</div>

## ✨ 特性

- 🎯 **企业级基座**: 统一技术栈，减少重复配置
- 📦 **依赖管理**: Parent POM + BOM 模式，版本统一管理
- 🛠️ **公共组件**: 常量、枚举、异常处理、分页等
- 📋 **质量保证**: Checkstyle、测试框架集成

## 📦 模块说明

| 模块 | 描述 | 状态 |
|------|------|------|
| `mimir-boot-parent` | 父 POM，提供插件版本和构建配置 | ✅ 已完成 |
| `mimir-boot-bom` | 依赖版本统一管理 | ✅ 已完成 |
| `mimir-boot-common` | 公共模型与工具类 | ✅ 已完成 |
| `mimir-boot-web` | Web Starter | 🚧 计划中 |
| `mimir-boot-db` | MyBatis-Plus Starter | 🚧 计划中 |
| `mimir-boot-samples` | 示例项目 | 🚧 计划中 |

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

### 2. 添加需要的依赖

```xml
<dependencies>
  <!-- Mimir Boot Common - 公共定义类 -->
  <dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-common</artifactId>
  </dependency>

  <!-- Spring Boot Web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  
  <!-- 其他依赖版本由 BOM 统一管理 -->
</dependencies>
```

### 3. mimir-boot-common 模块

提供公共定义类，包括常量、枚举、异常处理、分页等。详细说明请参考 [mimir-boot-common/README.md](mimir-boot-common/README.md)

## 🔧 常用命令

```bash
# 构建项目
mvn clean install

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
| **数据库** | MyBatis-Plus, MySQL, PostgreSQL |
| **工具类** | Hutool, Lombok, MapStruct |
| **测试** | JUnit 5, Mockito |
| **监控** | Micrometer, Prometheus |

完整技术栈列表请参考 [mimir-boot-bom/pom.xml](mimir-boot-bom/pom.xml)

完整的 POM 配置示例请查看各模块的 pom.xml 文件。

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 📞 联系我们

- **GitHub**: https://github.com/Yggdrasil-Labs/mimir-boot
- **组织**: Yggdrasil Labs