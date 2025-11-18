package com.yggdrasil.labs.test.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MockDataBuilder 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MockDataBuilderTest {

    /**
     * 测试用的简单类
     */
    static class TestUser {
        private String name;
        private Integer age;
        private String email;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    /**
     * 测试用的类（无参构造函数）
     */
    static class SimpleClass {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    /**
     * 测试用的类（有父类）
     */
    static class ParentClass {
        protected String parentField;

        public String getParentField() {
            return parentField;
        }

        public void setParentField(String parentField) {
            this.parentField = parentField;
        }
    }

    static class ChildClass extends ParentClass {
        private String childField;

        public String getChildField() {
            return childField;
        }

        public void setChildField(String childField) {
            this.childField = childField;
        }
    }

    // ========== 基本构建测试 ==========

    @Test
    void testOf() {
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class);
        assertNotNull(builder, "构建器不应为 null");
    }

    @Test
    void testWith() {
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("张三"))
                .with(u -> u.setAge(25))
                .with(u -> u.setEmail("zhangsan@example.com"));

        TestUser user = builder.build();
        assertEquals("张三", user.getName());
        assertEquals(25, user.getAge());
        assertEquals("zhangsan@example.com", user.getEmail());
    }

    @Test
    void testBuild() {
        TestUser user = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("李四"))
                .build();

        assertNotNull(user, "构建的对象不应为 null");
        assertEquals("李四", user.getName());
    }

    @Test
    void testGetInstance() {
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("王五"));

        TestUser instance1 = builder.getInstance();
        TestUser instance2 = builder.build();

        assertSame(instance1, instance2, "getInstance() 和 build() 应返回同一个实例");
        assertEquals("王五", instance1.getName());
    }

    // ========== buildList 测试 ==========

    @Test
    void testBuildList_WithoutCustomizer() {
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("测试用户"))
                .with(u -> u.setAge(30))
                .with(u -> u.setEmail("test@example.com"));

        List<TestUser> users = builder.buildList(3);

        assertNotNull(users, "列表不应为 null");
        assertEquals(3, users.size(), "列表大小应为 3");

        // 验证所有实例都复制了配置
        for (TestUser user : users) {
            assertEquals("测试用户", user.getName(), "所有实例应具有相同的 name");
            assertEquals(30, user.getAge(), "所有实例应具有相同的 age");
            assertEquals("test@example.com", user.getEmail(), "所有实例应具有相同的 email");
        }

        // 验证是不同的实例
        assertNotSame(users.get(0), users.get(1), "应该是不同的实例");
        assertNotSame(users.get(0), users.get(2), "应该是不同的实例");
        assertNotSame(users.get(1), users.get(2), "应该是不同的实例");
    }

    @Test
    void testBuildList_WithCustomizer() {
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("基础名称"));

        List<TestUser> users = builder.buildList(3, u -> {
            u.setAge(20);
            u.setEmail("custom@example.com");
        });

        assertNotNull(users);
        assertEquals(3, users.size());

        // 验证自定义配置器被应用
        for (TestUser user : users) {
            assertEquals(20, user.getAge());
            assertEquals("custom@example.com", user.getEmail());
            // 注意：自定义配置器会覆盖基础配置，所以 name 可能为 null 或基础值
        }
    }

    @Test
    void testBuildList_WithIndexBasedCustomizer() {
        List<TestUser> users = MockDataBuilder.of(TestUser.class)
                .buildList(3, u -> u.setName("用户"));

        assertNotNull(users);
        assertEquals(3, users.size());

        // 可以进一步自定义每个实例
        for (int i = 0; i < users.size(); i++) {
            users.get(i).setAge(20 + i);
        }

        assertEquals(20, users.get(0).getAge());
        assertEquals(21, users.get(1).getAge());
        assertEquals(22, users.get(2).getAge());
    }

    @Test
    void testBuildList_EmptyList() {
        List<TestUser> users = MockDataBuilder.of(TestUser.class)
                .buildList(0);

        assertNotNull(users);
        assertTrue(users.isEmpty(), "空列表应为空");
    }

    @Test
    void testBuildList_WithInheritance() {
        MockDataBuilder<ChildClass> builder = MockDataBuilder.of(ChildClass.class)
                .with(c -> c.setParentField("父类字段"))
                .with(c -> c.setChildField("子类字段"));

        List<ChildClass> children = builder.buildList(2);

        assertEquals(2, children.size());
        for (ChildClass child : children) {
            assertEquals("父类字段", child.getParentField(), "应复制父类字段");
            assertEquals("子类字段", child.getChildField(), "应复制子类字段");
        }
    }

    /**
     * 测试用的类（只有带参构造函数，没有无参构造函数）
     */
    static class ClassWithoutNoArgConstructor {
        private final String value;

        public ClassWithoutNoArgConstructor(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 测试用的类（只有私有构造函数）
     */
    static class ClassWithPrivateConstructor {
        private ClassWithPrivateConstructor() {
            // 私有构造函数
        }
    }

    // ========== 异常测试 ==========

    @Test
    void testOf_ClassWithoutNoArgConstructor() {
        // 测试没有无参构造函数的类
        assertThrows(com.yggdrasil.labs.common.exception.SystemException.class, () -> {
            MockDataBuilder.of(ClassWithoutNoArgConstructor.class);
        }, "没有无参构造函数的类应抛出异常");
    }

    @Test
    void testOf_ClassWithPrivateConstructor() {
        // 测试只有私有构造函数的类（虽然有无参构造函数，但无法访问）
        assertThrows(com.yggdrasil.labs.common.exception.SystemException.class, () -> {
            MockDataBuilder.of(ClassWithPrivateConstructor.class);
        }, "只有私有构造函数的类应抛出异常");
    }

    @Test
    void testBuildList_ClassWithoutNoArgConstructor() {
        // TestUser 有无参构造函数，所以这里不会失败
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class);
        assertDoesNotThrow(() -> builder.buildList(1), "TestUser 有无参构造函数，应该成功");
        
        // 测试没有无参构造函数的类
        assertThrows(com.yggdrasil.labs.common.exception.SystemException.class, () -> {
            MockDataBuilder<ClassWithoutNoArgConstructor> badBuilder = 
                    MockDataBuilder.of(ClassWithoutNoArgConstructor.class);
            badBuilder.buildList(1);
        }, "没有无参构造函数的类在 buildList 时应抛出异常");
    }

    // ========== 静态方法测试 ==========

    @Test
    void testTestTimestamp() {
        LocalDateTime timestamp1 = MockDataBuilder.testTimestamp();
        LocalDateTime timestamp2 = MockDataBuilder.testTimestamp();

        assertNotNull(timestamp1, "时间戳不应为 null");
        assertNotNull(timestamp2, "时间戳不应为 null");
        assertTrue(timestamp1.isBefore(timestamp2) || timestamp1.isEqual(timestamp2),
                "时间戳应合理");
    }

    @Test
    void testTestTimestamp_WithOffset() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime future = MockDataBuilder.testTimestamp(5);
        LocalDateTime past = MockDataBuilder.testTimestamp(-5);

        assertNotNull(future, "未来时间戳不应为 null");
        assertNotNull(past, "过去时间戳不应为 null");
        assertTrue(future.isAfter(now), "未来时间戳应在当前时间之后");
        assertTrue(past.isBefore(now), "过去时间戳应在当前时间之前");
    }

    @Test
    void testTestTimestamp_ZeroOffset() {
        LocalDateTime timestamp1 = MockDataBuilder.testTimestamp(0);
        LocalDateTime timestamp2 = MockDataBuilder.testTimestamp();

        // 两个时间戳应该非常接近（在几毫秒内）
        long diff = Math.abs(java.time.Duration.between(timestamp1, timestamp2).toMillis());
        assertTrue(diff < 100, "零偏移的时间戳应与当前时间接近");
    }

    // ========== 链式调用测试 ==========

    @Test
    void testChainedCalls() {
        TestUser user = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("链式调用"))
                .with(u -> u.setAge(18))
                .with(u -> u.setEmail("chain@example.com"))
                .build();

        assertEquals("链式调用", user.getName());
        assertEquals(18, user.getAge());
        assertEquals("chain@example.com", user.getEmail());
    }

    @Test
    void testMultipleWithCalls() {
        MockDataBuilder<TestUser> builder = MockDataBuilder.of(TestUser.class)
                .with(u -> u.setName("多次调用1"))
                .with(u -> u.setName("多次调用2"))
                .with(u -> u.setName("多次调用3"));

        TestUser user = builder.build();
        assertEquals("多次调用3", user.getName(), "最后一次调用应覆盖前面的值");
    }
}

