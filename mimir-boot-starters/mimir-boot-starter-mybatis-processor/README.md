# Mimir Boot Starter MyBatis Processor

基于注解处理器的编译时代码生成工具，自动生成 MyBatis-Plus Mapper、Service 和 ServiceImpl。

## 用途

通过编译期注解处理器，减少样板代码：

- ✅ **自动生成 Mapper 接口**（继承 `BaseMapper<T>`）
- ✅ **自动生成 Service 接口**（继承 `IService<T>`）
- ✅ **自动生成 ServiceImpl 实现类**（继承 `ServiceImpl<M, T>`）
- ✅ **编译期生成**，零运行时开销
- ✅ **可配置包路径和命名规则**

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-mybatis-processor</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

**重要**：必须使用 `provided` 作用域，因为这是编译期工具，不需要打包到运行时。

### 使用示例

#### 1. 标注实体类

```java
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@AutoMybatis
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    
    // getters and setters...
}
```

#### 2. 编译项目

执行 Maven 编译：

```bash
mvn clean compile
```

#### 3. 生成的代码

编译后会自动生成以下文件：

**生成的 Mapper 接口**：
```java
// 位置：com/example/mapper/UserMapper.java
package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.User;

public interface UserMapper extends BaseMapper<User> {
}
```

**生成的 Service 接口**：
```java
// 位置：com/example/service/UserService.java
package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.User;

public interface UserService extends IService<User> {
}
```

**生成的 ServiceImpl 实现类**：
```java
// 位置：com/example/service/impl/UserServiceImpl.java
package com.example.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.mapper.UserMapper;
import com.example.User;
import com.example.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
```

#### 4. 使用生成的代码

```java
import com.example.service.UserService;
import com.example.User;

@Service
public class UserController {
    @Autowired
    private UserService userService;  // 注入生成的 Service
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);  // 直接使用 MyBatis-Plus 提供的方法
    }
    
    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        userService.save(user);
        return user;
    }
}
```

## 配置选项

### 自定义包路径和命名规则

```java
@AutoMybatis(
    mapperPackage = "dao",           // Mapper 包名，默认为 "mapper"
    servicePackage = "service",        // Service 包名，默认为 "service"
    serviceImplPackage = "service.impl", // ServiceImpl 包名，默认为 "service.impl"
    mapperSuffix = "Mapper",          // Mapper 后缀，默认为 "Mapper"
    serviceSuffix = "Service",        // Service 后缀，默认为 "Service"
    serviceImplSuffix = "ServiceImpl" // ServiceImpl 后缀，默认为 "ServiceImpl"
)
@TableName("sys_user")
public class User {
    // ...
}
```

### 配置示例

#### 示例1：使用默认配置

```java
@AutoMybatis  // 使用所有默认值
@TableName("sys_user")
public class User {
    // 生成的类：
    // - com.example.mapper.UserMapper
    // - com.example.service.UserService
    // - com.example.service.impl.UserServiceImpl
}
```

#### 示例2：自定义包路径

```java
@AutoMybatis(
    mapperPackage = "repository",
    servicePackage = "business",
    serviceImplPackage = "business.impl"
)
@TableName("sys_user")
public class User {
    // 生成的类：
    // - com.example.repository.UserMapper
    // - com.example.business.UserService
    // - com.example.business.impl.UserServiceImpl
}
```

#### 示例3：自定义命名后缀

```java
@AutoMybatis(
    mapperSuffix = "Dao",
    serviceSuffix = "Facade",
    serviceImplSuffix = "FacadeImpl"
)
@TableName("sys_user")
public class User {
    // 生成的类：
    // - com.example.mapper.UserDao
    // - com.example.service.UserFacade
    // - com.example.service.impl.UserFacadeImpl
}
```

## 工作原理

### 编译期处理流程

1. **编译时扫描**：注解处理器扫描所有标注了 `@AutoMybatis` 的类
2. **代码生成**：使用 JavaPoet 生成对应的 Mapper、Service、ServiceImpl
3. **写入文件**：将生成的代码写入 `target/generated-sources/annotations` 目录
4. **参与编译**：生成的代码会参与后续的编译过程

### 生成规则

- **Mapper 包路径**：`实体类包名 + mapperPackage`
- **Service 包路径**：`实体类包名 + servicePackage`
- **ServiceImpl 包路径**：`实体类包名 + serviceImplPackage`
- **类名**：`实体类名 + 对应后缀`

### 示例说明

假设实体类为：
```java
package com.example.entity;

@AutoMybatis
public class User {
    // ...
}
```

生成的代码位置：
- `com.example.entity.mapper.UserMapper`
- `com.example.entity.service.UserService`
- `com.example.entity.service.impl.UserServiceImpl`

## 最佳实践

### 1. 目录结构建议

推荐的项目结构：

```
src/main/java/com/example/
├── entity/          # 实体类（标注 @AutoMybatis）
│   ├── User.java
│   └── Order.java
├── mapper/          # Mapper 接口（自动生成）
│   ├── UserMapper.java
│   └── OrderMapper.java
├── service/         # Service 接口（自动生成）
│   ├── UserService.java
│   └── OrderService.java
└── service/impl/    # ServiceImpl 实现（自动生成）
    ├── UserServiceImpl.java
    └── OrderServiceImpl.java
```

### 2. 实体类设计

```java
import com.baomidou.mybatisplus.annotation.*;
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;

@AutoMybatis
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    private String email;
    
    // 审计字段
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    
    // 逻辑删除
    @TableLogic
    private Integer deleted;
    
    // 乐观锁
    @Version
    private Integer version;
    
    // getters and setters...
}
```

### 3. 扩展生成的 Service

生成的 ServiceImpl 可以添加自定义业务方法：

```java
// 方式1：直接在生成的类上添加方法（需要确保不会在重新生成时被覆盖）
// 注意：如果修改了实体类并重新编译，生成的文件会重新生成，自定义方法会丢失

// 方式2：创建扩展类（推荐）
@Service
public class UserServiceExt extends UserServiceImpl {
    
    public User findByUsername(String username) {
        return lambdaQuery()
            .eq(User::getUsername, username)
            .one();
    }
    
    public List<User> findActiveUsers() {
        return lambdaQuery()
            .eq(User::getStatus, "ACTIVE")
            .list();
    }
}
```

### 4. 自定义 Mapper 方法

如果需要自定义 SQL，可以创建对应的 XML 文件或在生成的 Mapper 接口中添加方法：

```java
// 方式1：在生成的 Mapper 接口中添加方法（注意重新编译会被覆盖）
// 不推荐，建议使用方式2

// 方式2：创建自定义 Mapper 接口（推荐）
@Mapper
public interface UserMapperCustom {
    List<User> findUsersByCondition(@Param("keyword") String keyword);
}
```

对应的 XML：
```xml
<!-- src/main/resources/mapper/UserMapper.xml -->
<mapper namespace="com.example.mapper.UserMapperCustom">
    <select id="findUsersByCondition" resultType="com.example.entity.User">
        SELECT * FROM sys_user
        <where>
            <if test="keyword != null and keyword != ''">
                AND (username LIKE CONCAT('%', #{keyword}, '%')
                OR email LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
    </select>
</mapper>
```

## 常见问题

### 问题1：编译后没有生成代码

**症状**：标注了 `@AutoMybatis`，但编译后没有生成对应的 Mapper、Service

**可能原因**：
1. 依赖未正确配置（缺少 `provided` 作用域）
2. IDE 未启用注解处理器
3. Maven 编译插件配置禁用了注解处理

**解决方案**：

1. **检查依赖配置**：
```xml
<dependency>
    <groupId>com.yggdrasil.labs</groupId>
    <artifactId>mimir-boot-starter-mybatis-processor</artifactId>
    <scope>provided</scope>  <!-- 必须使用 provided -->
</dependency>
```

2. **检查 Maven 编译插件配置**：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <source>17</source>
        <target>17</target>
        <!-- 不要设置 proc:none，这会禁用注解处理 -->
    </configuration>
</plugin>
```

3. **IDE 配置（IntelliJ IDEA）**：
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - 勾选 "Enable annotation processing"

4. **手动触发编译**：
```bash
mvn clean compile
```

### 问题2：生成的代码位置不正确

**症状**：生成的代码不在预期的包路径下

**解决方案**：
1. 检查 `@AutoMybatis` 注解的配置
2. 确认实体类的包路径
3. 生成的代码位置为：`实体类包名 + 配置的 package`

### 问题3：重新编译后自定义代码丢失

**症状**：在生成的类中添加了自定义方法，重新编译后丢失

**原因**：生成的代码会在每次编译时重新生成，覆盖之前的修改

**解决方案**：
1. **不要直接修改生成的类**
2. **创建扩展类**（推荐）：
```java
// 不要修改生成的 UserServiceImpl
// 而是创建扩展类

@Service
public class UserServiceExt extends UserServiceImpl {
    // 添加自定义方法
    public User findByUsername(String username) {
        return lambdaQuery()
            .eq(User::getUsername, username)
            .one();
    }
}
```

### 问题4：多个实体类生成冲突

**症状**：多个实体类使用相同的配置，生成的类名冲突

**解决方案**：
1. 为不同的实体类使用不同的包路径配置
2. 使用不同的命名后缀

```java
// Entity 1
@AutoMybatis(mapperPackage = "mapper", servicePackage = "service")
public class User { }

// Entity 2
@AutoMybatis(mapperPackage = "dao", servicePackage = "facade")
public class Order { }
```

## 与手动编写代码的对比

### 手动编写方式

```java
// 需要手动编写 3 个类，每个实体类都需要重复类似代码

// 1. UserMapper.java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

// 2. UserService.java
public interface UserService extends IService<User> {
}

// 3. UserServiceImpl.java
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
```

### 使用 Processor 方式

```java
// 只需要在实体类上标注注解
@AutoMybatis
public class User {
    // ...
}

// 编译后自动生成上述 3 个类
```

**优势**：
- ✅ 减少样板代码
- ✅ 统一代码风格
- ✅ 降低出错概率
- ✅ 提高开发效率

## 技术栈

- **JavaPoet**: 1.13.0 - 代码生成库
- **MyBatis-Plus**: 3.5.14+ - ORM 框架
- **Java Annotation Processing**: Java 17+
- **Maven**: 3.6+

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs

