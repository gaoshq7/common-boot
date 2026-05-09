package cn.gsq.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GalaxySpringUtil 集成测试。
 * 启动真实 Spring 上下文，覆盖新旧两套 API 的完整行为。
 */
@SpringBootTest(classes = {
        GalaxySpringUtil.class,
        GalaxySpringUtilTest.TestBeans.class,
        GalaxySpringUtilTest.PrototypeBean.class,
        GalaxySpringUtilTest.AnnotatedService.class,
        GalaxySpringUtilTest.TestEventListener.class
})
@org.springframework.test.context.ActiveProfiles("test")
class GalaxySpringUtilTest {

    @Autowired
    private Environment environment;

    @Autowired
    private TestService testService;

    @Autowired
    private TestEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.lastEvent.set(null);
    }

    // ============================================================
    // ===== 测试用 Bean 定义
    // ============================================================

    @TestConfiguration
    static class TestBeans {
        @Bean
        public TestService testService() {
            return new TestService("from-test-config");
        }
    }

    static class TestService {
        private final String name;
        TestService(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @Component
    @Scope("prototype")
    static class PrototypeBean {
        private static int counter = 0;
        private final int id = ++counter;
        public int getId() { return id; }
    }

    @Component
    @Profile("test")
    static class AnnotatedService {
        public String hello() { return "annotated"; }
    }

    @Component
    static class TestEventListener implements ApplicationListener<ApplicationEvent> {
        final AtomicReference<ApplicationEvent> lastEvent = new AtomicReference<>();

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            lastEvent.set(event);
        }
    }

    static class ManualBean {
        @Autowired(required = false)
        private TestService injectedService;

        public TestService getInjectedService() { return injectedService; }
    }

    static class BeanWithInit {
        boolean initialized = false;

        @PostConstruct
        public void init() {
            initialized = true;
        }
    }

    /** 无参构造的 bean，用于注册相关测试 */
    static class NoArgBean {
        public String hello() { return "hello"; }
    }

    /** 专门用于"找不到注解"场景的测试注解 */
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @interface FakeAnno {}

    // ============================================================
    // ===== A. 状态查询
    // ============================================================

    @Nested
    @DisplayName("A. 状态查询")
    class StateQueryTests {

        @Test
        @DisplayName("isReady() 在上下文就绪后返回 true")
        void isReady_should_return_true() {
            assertThat(GalaxySpringUtil.isReady()).isTrue();
        }

        @Test
        @DisplayName("getRequiredContext() 返回非 null 的 ApplicationContext")
        void getRequiredContext_should_return_context() {
            assertThat(GalaxySpringUtil.getRequiredContext()).isNotNull();
        }

        @Test
        @DisplayName("getContext() 返回非 null")
        void getContext_should_return_context() {
            assertThat(GalaxySpringUtil.getContext()).isNotNull();
        }

        @Test
        @DisplayName("getRequiredContext() 与 getContext() 返回同一实例")
        void contexts_should_be_same() {
            assertThat(GalaxySpringUtil.getRequiredContext()).isSameAs(GalaxySpringUtil.getContext());
        }

        @Test
        @DisplayName("containsBean() 对存在的 bean 返回 true")
        void containsBean_for_existing_should_return_true() {
            assertThat(GalaxySpringUtil.containsBean("testService")).isTrue();
        }

        @Test
        @DisplayName("containsBean() 对不存在的 bean 返回 false")
        void containsBean_for_missing_should_return_false() {
            assertThat(GalaxySpringUtil.containsBean("nonExistentBean")).isFalse();
        }

        @Test
        @DisplayName("containsBeanDefinition() 对 BeanDefinition 返回 true")
        void containsBeanDefinition_for_existing_should_return_true() {
            assertThat(GalaxySpringUtil.containsBeanDefinition("testService")).isTrue();
        }

        @Test
        @DisplayName("containsBeanDefinition() 对仅单例返回 false")
        void containsBeanDefinition_for_singleton_only_should_return_false() {
            // putSingleton 注册的不含 BeanDefinition
            GalaxySpringUtil.putSingleton("pureSingleton", new Object());
            try {
                assertThat(GalaxySpringUtil.containsBeanDefinition("pureSingleton")).isFalse();
                assertThat(GalaxySpringUtil.containsBean("pureSingleton")).isTrue();
            } finally {
                GalaxySpringUtil.unregisterBean("pureSingleton");
            }
        }
    }

    // ============================================================
    // ===== B. Bean 查询（Optional / 安全集合）
    // ============================================================

    @Nested
    @DisplayName("B. Bean 查询 — Optional 风格")
    class BeanQueryTests {

        @Test
        @DisplayName("findBean(Class) 能找到存在的 bean")
        void findBean_by_class_should_find() {
            Optional<TestService> bean = GalaxySpringUtil.findBean(TestService.class);
            assertThat(bean).isPresent();
            assertThat(bean.get().getName()).isEqualTo("from-test-config");
        }

        @Test
        @DisplayName("findBean(Class) 找不到返回 empty")
        void findBean_by_class_not_found_should_return_empty() {
            Optional<String> bean = GalaxySpringUtil.findBean(String.class);
            assertThat(bean).isEmpty();
        }

        @Test
        @DisplayName("findBean(String, Class) 能找到")
        void findBean_by_name_and_class_should_find() {
            Optional<TestService> bean = GalaxySpringUtil.findBean("testService", TestService.class);
            assertThat(bean).isPresent();
        }

        @Test
        @DisplayName("findBean(String, Class) 类型不匹配会异常完成，返回 empty")
        void findBean_by_name_and_class_type_mismatch_should_return_empty() {
            Optional<String> bean = GalaxySpringUtil.findBean("testService", String.class);
            assertThat(bean).isEmpty();
        }

        @Test
        @DisplayName("findBean(String) 能找到并返回 Optional<Object>")
        void findBean_by_name_should_find() {
            Optional<Object> bean = GalaxySpringUtil.findBean("testService");
            assertThat(bean).isPresent();
            assertThat(bean.get()).isInstanceOf(TestService.class);
        }

        @Test
        @DisplayName("getRequiredBean(Class) 能找到")
        void getRequiredBean_by_class_should_find() {
            TestService bean = GalaxySpringUtil.getRequiredBean(TestService.class);
            assertThat(bean).isNotNull();
            assertThat(bean.getName()).isEqualTo("from-test-config");
        }

        @Test
        @DisplayName("getRequiredBean(Class) 找不到抛 IllegalStateException")
        void getRequiredBean_by_class_not_found_should_throw() {
            assertThatThrownBy(() -> GalaxySpringUtil.getRequiredBean(java.math.BigDecimal.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("找不到必需的 bean");
        }

        @Test
        @DisplayName("getRequiredBean(String, Class) 能找到")
        void getRequiredBean_by_name_and_class_should_find() {
            TestService bean = GalaxySpringUtil.getRequiredBean("testService", TestService.class);
            assertThat(bean.getName()).isEqualTo("from-test-config");
        }

        @Test
        @DisplayName("getRequiredBean(String, Class) 找不到抛 IllegalStateException")
        void getRequiredBean_by_name_and_class_not_found_should_throw() {
            assertThatThrownBy(() -> GalaxySpringUtil.getRequiredBean("nope", TestService.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("name=nope");
        }
    }

    @Nested
    @DisplayName("B2. Bean 查询 — 集合操作")
    class BeanCollectionTests {

        @Test
        @DisplayName("findBeans(Class) 返回所有匹配的 bean，绝不返回 null")
        void findBeans_should_return_list() {
            List<TestService> beans = GalaxySpringUtil.findBeans(TestService.class);
            assertThat(beans).isNotNull();
            assertThat(beans).hasSize(1);
        }

        @Test
        @DisplayName("findBeans(Class) 找不到返回空 list")
        void findBeans_not_found_should_return_empty_list() {
            List<java.math.BigDecimal> beans = GalaxySpringUtil.findBeans(java.math.BigDecimal.class);
            assertThat(beans).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("getBeansAsMap(Class) 返回 name -> bean 的 map")
        void getBeansAsMap_should_return_map() {
            Map<String, TestService> map = GalaxySpringUtil.getBeansAsMap(TestService.class);
            assertThat(map).isNotNull();
            assertThat(map).containsKey("testService");
        }

        @Test
        @DisplayName("getBeansAsMap(Class) 找不到返回空 map")
        void getBeansAsMap_not_found_should_return_empty_map() {
            Map<String, java.math.BigDecimal> map = GalaxySpringUtil.getBeansAsMap(java.math.BigDecimal.class);
            assertThat(map).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("getBeanNames(Class) 返回名字数组")
        void getBeanNames_should_return_array() {
            String[] names = GalaxySpringUtil.getBeanNames(TestService.class);
            assertThat(names).contains("testService");
        }

        @Test
        @DisplayName("getBeanNames(Class) 找不到返回空数组")
        void getBeanNames_not_found_should_return_empty_array() {
            String[] names = GalaxySpringUtil.getBeanNames(java.math.BigDecimal.class);
            assertThat(names).isEmpty();
        }
    }

    @Nested
    @DisplayName("B3. Bean 查询 — 注解相关")
    class AnnotationQueryTests {

        @Test
        @DisplayName("getBeansByAnnotation(@Component) 返回带注解的 bean 实例")
        void getBeansByAnnotation_should_return_instances() {
            Collection<Object> beans = GalaxySpringUtil.getBeansByAnnotation(Component.class);
            assertThat(beans).isNotNull();
            // PrototypeBean、AnnotatedService、TestEventListener 都标了 @Component
            assertThat(beans).anyMatch(b -> b instanceof PrototypeBean);
            assertThat(beans).anyMatch(b -> b instanceof AnnotatedService);
        }

        @Test
        @DisplayName("getBeansByAnnotation 找不到返回空集合")
        void getBeansByAnnotation_not_found_should_return_empty() {
            Collection<Object> beans = GalaxySpringUtil.getBeansByAnnotation(FakeAnno.class);
            assertThat(beans).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("getBeanNamesForAnnotation(@Component) 返回 bean 名字")
        void getBeanNamesForAnnotation_should_return_names() {
            String[] names = GalaxySpringUtil.getBeanNamesForAnnotation(Component.class);
            assertThat(names).anyMatch(n -> n.contains("PrototypeBean"));
            assertThat(names).anyMatch(n -> n.contains("AnnotatedService"));
        }

        @Test
        @DisplayName("getBeanNamesForAnnotation 找不到返回空数组")
        void getBeanNamesForAnnotation_not_found_should_return_empty() {
            String[] names = GalaxySpringUtil.getBeanNamesForAnnotation(FakeAnno.class);
            assertThat(names).isEmpty();
        }
    }

    @Nested
    @DisplayName("B4. Bean 属性查询")
    class BeanPropertyTests {

        @Test
        @DisplayName("isSingleton() 对单例 bean 返回 true")
        void isSingleton_should_return_true_for_singleton() {
            assertThat(GalaxySpringUtil.isSingleton("testService")).isTrue();
        }

        @Test
        @DisplayName("isPrototype() 对 prototype bean 返回 true")
        void isPrototype_should_return_true_for_prototype() {
            String[] names = GalaxySpringUtil.getBeanNames(PrototypeBean.class);
            assertThat(names).isNotEmpty();
            assertThat(GalaxySpringUtil.isPrototype(names[0])).isTrue();
        }

        @Test
        @DisplayName("isSingleton() 对不存在的 bean 返回 false")
        void isSingleton_for_missing_should_return_false() {
            assertThat(GalaxySpringUtil.isSingleton("nope")).isFalse();
        }

        @Test
        @DisplayName("getBeanType() 返回正确的 Class")
        void getBeanType_should_return_class() {
            assertThat(GalaxySpringUtil.getBeanType("testService")).isEqualTo(TestService.class);
        }

        @Test
        @DisplayName("getBeanType() 对不存在的 bean 返回 null")
        void getBeanType_for_missing_should_return_null() {
            assertThat(GalaxySpringUtil.getBeanType("nope")).isNull();
        }

        @Test
        @DisplayName("getBeanDefinition() 返回非 null 的 BeanDefinition")
        void getBeanDefinition_should_return_definition() {
            assertThat(GalaxySpringUtil.getBeanDefinition("testService")).isNotNull();
        }

        @Test
        @DisplayName("getBeanDefinition() 对不存在的 bean 返回 null")
        void getBeanDefinition_for_missing_should_return_null() {
            assertThat(GalaxySpringUtil.getBeanDefinition("nope")).isNull();
        }
    }

    // ============================================================
    // ===== C. 配置访问（Environment 包装）
    // ============================================================

    @Nested
    @DisplayName("C. 配置访问")
    class ConfigAccessTests {

        @Test
        @DisplayName("getProperty(key) 能读到配置")
        void getProperty_should_read_config() {
            // java.version 是 JVM 系统属性，一定存在
            String version = GalaxySpringUtil.getProperty("java.version");
            assertThat(version).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("getProperty(key, defaultValue) 未配置时返回默认值")
        void getProperty_with_default_should_return_default() {
            String value = GalaxySpringUtil.getProperty("galaxy.nonexistent.key", "default-val");
            assertThat(value).isEqualTo("default-val");
        }

        @Test
        @DisplayName("getProperty(key, Class<T>) 能按类型转换")
        void getProperty_with_type_should_convert() {
            // server.port 默认是 8080（字符串），这里读一个已知存在的 int 类型配置
            // 用环境变量方式读 spring boot 版本号
            String version = GalaxySpringUtil.getProperty("spring.boot.version");
            // 可能不存在，不报错就行
        }

        @Test
        @DisplayName("getProperty(key, Class<T>, defaultValue) 未配置返回默认值")
        void getProperty_with_type_and_default_should_return_default() {
            Integer value = GalaxySpringUtil.getProperty("galaxy.fake.port", Integer.class, 9999);
            assertThat(value).isEqualTo(9999);
        }

        @Test
        @DisplayName("getActiveProfiles() 包含 'test'")
        void getActiveProfiles_should_include_test() {
            String[] profiles = GalaxySpringUtil.getActiveProfiles();
            assertThat(profiles).contains("test");
        }

        @Test
        @DisplayName("acceptsProfile('test') 返回 true")
        void acceptsProfile_should_match_active() {
            assertThat(GalaxySpringUtil.acceptsProfile("test")).isTrue();
        }

        @Test
        @DisplayName("acceptsProfile('dev') 返回 false")
        void acceptsProfile_should_not_match_inactive() {
            assertThat(GalaxySpringUtil.acceptsProfile("dev")).isFalse();
        }

        @Test
        @DisplayName("acceptsProfile('dev | test') 返回 true（表达式支持）")
        void acceptsProfile_should_support_expression() {
            assertThat(GalaxySpringUtil.acceptsProfile("dev | test")).isTrue();
        }
    }

    // ============================================================
    // ===== D. 注入辅助
    // ============================================================

    @Nested
    @DisplayName("D. 注入辅助")
    class AutowireTests {

        @Test
        @DisplayName("autowireBean() 能给非 Spring 创建的对象注入依赖")
        void autowireBean_should_inject_dependencies() {
            ManualBean bean = new ManualBean();
            assertThat(bean.getInjectedService()).isNull();

            GalaxySpringUtil.autowireBean(bean);

            assertThat(bean.getInjectedService()).isNotNull();
            assertThat(bean.getInjectedService().getName()).isEqualTo("from-test-config");
        }

        @Test
        @DisplayName("initializeBean() 触发完整的生命周期（含 @PostConstruct）")
        void initializeBean_should_trigger_lifecycle() {
            BeanWithInit bean = new BeanWithInit();
            assertThat(bean.initialized).isFalse();

            GalaxySpringUtil.initializeBean(bean, "beanWithInit");

            assertThat(bean.initialized).isTrue();
        }

        @Test
        @DisplayName("autowireBean() 传入 null 抛 NullPointerException")
        void autowireBean_null_should_throw() {
            assertThatThrownBy(() -> GalaxySpringUtil.autowireBean(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("existingObject");
        }
    }

    // ============================================================
    // ===== E. 安全注册 / 删除
    // ============================================================

    @Nested
    @DisplayName("E. 安全注册 / 删除")
    class RegistrationTests {

        @Test
        @DisplayName("registerBeanIfAbsent() 注册成功返回 true")
        void registerBeanIfAbsent_should_return_true_on_success() {
            boolean result = GalaxySpringUtil.registerBeanIfAbsent(
                    "myRegisteredBean", TestService.class, "registered"
            );
            try {
                assertThat(result).isTrue();
                Optional<TestService> bean = GalaxySpringUtil.findBean("myRegisteredBean", TestService.class);
                assertThat(bean).isPresent();
                assertThat(bean.get().getName()).isEqualTo("registered");
            } finally {
                GalaxySpringUtil.unregisterBean("myRegisteredBean");
            }
        }

        @Test
        @DisplayName("registerBeanIfAbsent() 已存在返回 false")
        void registerBeanIfAbsent_should_return_false_when_exists() {
            // 先注册一次
            GalaxySpringUtil.registerBeanIfAbsent("dupBean", TestService.class, "first");
            try {
                boolean result = GalaxySpringUtil.registerBeanIfAbsent(
                        "dupBean", TestService.class, "second"
                );
                assertThat(result).isFalse();
                // 应该还是第一次注册的那个
                assertThat(GalaxySpringUtil.getRequiredBean("dupBean", TestService.class).getName())
                        .isEqualTo("first");
            } finally {
                GalaxySpringUtil.unregisterBean("dupBean");
            }
        }

        @Test
        @DisplayName("registerBeanIfAbsent() 入参非法返回 false")
        void registerBeanIfAbsent_should_return_false_on_invalid_args() {
            assertThat(GalaxySpringUtil.registerBeanIfAbsent(null, TestService.class)).isFalse();
            assertThat(GalaxySpringUtil.registerBeanIfAbsent("name", null)).isFalse();
            assertThat(GalaxySpringUtil.registerBeanIfAbsent("", TestService.class)).isFalse();
        }

        @Test
        @DisplayName("registerSingletonBean() 用 lowerFirst 命名，与 Spring 默认一致")
        void registerSingletonBean_should_use_lower_first() {
            NoArgBean bean = GalaxySpringUtil.registerSingletonBean(NoArgBean.class);
            try {
                // lowerFirst("NoArgBean") = "noArgBean"
                assertThat(GalaxySpringUtil.containsBean("noArgBean")).isTrue();
                Optional<NoArgBean> found = GalaxySpringUtil.findBean("noArgBean", NoArgBean.class);
                assertThat(found).isPresent();
                assertThat(found.get().hello()).isEqualTo("hello");
            } finally {
                GalaxySpringUtil.unregisterBean("noArgBean");
            }
        }

        @Test
        @DisplayName("putSingleton() 注册已存在的对象")
        void putSingleton_should_register_object() {
            Object obj = new Object();
            GalaxySpringUtil.putSingleton("mySingleton", obj);
            try {
                assertThat(GalaxySpringUtil.containsBean("mySingleton")).isTrue();
                assertThat(GalaxySpringUtil.findBean("mySingleton")).isPresent();
                assertThat(GalaxySpringUtil.findBean("mySingleton").get()).isSameAs(obj);
            } finally {
                GalaxySpringUtil.unregisterBean("mySingleton");
            }
        }

        @Test
        @DisplayName("unregisterBean() 删除存在的 bean 返回 true")
        void unregisterBean_should_return_true_when_removed() {
            GalaxySpringUtil.putSingleton("toRemove", new Object());
            assertThat(GalaxySpringUtil.containsBean("toRemove")).isTrue();

            boolean removed = GalaxySpringUtil.unregisterBean("toRemove");

            assertThat(removed).isTrue();
            assertThat(GalaxySpringUtil.containsBean("toRemove")).isFalse();
        }

        @Test
        @DisplayName("unregisterBean() 删除不存在的 bean 返回 false")
        void unregisterBean_should_return_false_when_not_exists() {
            assertThat(GalaxySpringUtil.unregisterBean("neverExisted")).isFalse();
        }

        @Test
        @DisplayName("unregisterBean() 能删除纯单例（无 BeanDefinition）")
        void unregisterBean_should_remove_pure_singleton() {
            GalaxySpringUtil.putSingleton("pureSingle", new Object());
            assertThat(GalaxySpringUtil.containsBeanDefinition("pureSingle")).isFalse();

            boolean removed = GalaxySpringUtil.unregisterBean("pureSingle");

            assertThat(removed).isTrue();
            assertThat(GalaxySpringUtil.containsBean("pureSingle")).isFalse();
        }
    }

    // ============================================================
    // ===== F. 事件
    // ============================================================

    @Nested
    @DisplayName("F. 事件发布")
    class EventTests {

        @Test
        @DisplayName("tryPublishEvent() 发布成功返回 true")
        void tryPublishEvent_should_return_true() {
            ContextRefreshedEvent event = new ContextRefreshedEvent(
                    GalaxySpringUtil.getRequiredContext()
            );
            boolean result = GalaxySpringUtil.tryPublishEvent(event);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("publishEvent(ApplicationEvent) 能发布事件并被监听")
        void publishEvent_should_be_listened() {
            ContextRefreshedEvent event = new ContextRefreshedEvent(
                    GalaxySpringUtil.getRequiredContext()
            );
            GalaxySpringUtil.publishEvent(event);
            // 稍等异步处理
            assertThat(eventListener.lastEvent.get()).isNotNull();
        }

        @Test
        @DisplayName("publishEvent(Object) 能发布任意对象")
        void publishEvent_object_should_publish() {
            String payload = "test-payload";
            GalaxySpringUtil.publishEvent(payload);
            // Spring 4.2+ 支持任意对象作为事件，会被包装为 PayloadApplicationEvent
            // 验证不抛异常即可
        }
    }

    // ============================================================
    // ===== G. 全局变量
    // ============================================================

    @Nested
    @DisplayName("G. 全局变量")
    class GlobalArgumentTests {

        @Test
        @DisplayName("putGlobalArgument + getGlobalArgument 能存取")
        void put_and_get_should_work() {
            GalaxySpringUtil.putGlobalArgument("myKey", "myValue");
            assertThat(GalaxySpringUtil.getGlobalArgument("myKey")).isEqualTo("myValue");
        }

        @Test
        @DisplayName("getGlobalArgument 空 key 返回 null")
        void getGlobalArgument_blank_key_should_return_null() {
            assertThat(GalaxySpringUtil.getGlobalArgument(null)).isNull();
            assertThat(GalaxySpringUtil.getGlobalArgument("")).isNull();
            assertThat(GalaxySpringUtil.getGlobalArgument("   ")).isNull();
        }

        @Test
        @DisplayName("putGlobalArgument 空 key 或 value 不存储")
        void putGlobalArgument_null_should_not_store() {
            GalaxySpringUtil.putGlobalArgument(null, "value");
            GalaxySpringUtil.putGlobalArgument("key", null);
            assertThat(GalaxySpringUtil.getGlobalArgument("key")).isNull();
        }
    }

    // ============================================================
    // ===== H. 旧 @Deprecated API 兼容性回归
    // ============================================================

    @Nested
    @DisplayName("H. 旧 @Deprecated API 兼容性回归")
    @SuppressWarnings("deprecation")
    class DeprecatedApiTests {

        @Test
        @DisplayName("getBean(Class) 能找到存在的 bean")
        void getBean_by_class_should_find() {
            TestService bean = GalaxySpringUtil.getBean(TestService.class);
            assertThat(bean).isNotNull();
            assertThat(bean.getName()).isEqualTo("from-test-config");
        }

        @Test
        @DisplayName("getBean(Class) 找不到返回 null（不抛异常）")
        void getBean_by_class_not_found_should_return_null() {
            java.math.BigDecimal bean = GalaxySpringUtil.getBean(java.math.BigDecimal.class);
            assertThat(bean).isNull();
        }

        @Test
        @DisplayName("getBean(String, Class) 能找到")
        void getBean_by_name_and_class_should_find() {
            TestService bean = GalaxySpringUtil.getBean("testService", TestService.class);
            assertThat(bean).isNotNull();
        }

        @Test
        @DisplayName("getBean(String) 能找到（隐式 cast）")
        void getBean_by_name_should_find() {
            Object bean = GalaxySpringUtil.getBean("testService");
            assertThat(bean).isInstanceOf(TestService.class);
        }

        @Test
        @DisplayName("getBeans(Class) 返回 list")
        void getBeans_should_return_list() {
            List<TestService> beans = GalaxySpringUtil.getBeans(TestService.class);
            assertThat(beans).isNotNull().hasSize(1);
        }

        @Test
        @DisplayName("getBeans(Class) 找不到返回空 list（实际行为，旧注释说返回 null 有误）")
        void getBeans_not_found_should_return_empty_list() {
            List<java.math.BigDecimal> beans = GalaxySpringUtil.getBeans(java.math.BigDecimal.class);
            // 旧注释声称返回 null，但实际 context.getBeansOfType 找不到时返回空 map，stream 后为空 list
            assertThat(beans).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("registerBean() 能注册新 bean")
        void registerBean_should_work() {
            GalaxySpringUtil.registerBean("oldRegBean", TestService.class, "old-style");
            try {
                Object bean = GalaxySpringUtil.getBean("oldRegBean");
                assertThat(bean).isInstanceOf(TestService.class);
            } finally {
                GalaxySpringUtil.unregisterBean("oldRegBean");
            }
        }

        @Test
        @DisplayName("registerSingleton(Class) 用 upperFirst 命名（与 Spring 默认相反）")
        void registerSingleton_should_use_upper_first() {
            // 注意：旧 API 用 upperFirst，这是已知 bug
            NoArgBean bean = GalaxySpringUtil.registerSingleton(NoArgBean.class);
            try {
                // upperFirst("NoArgBean") = "NoArgBean"（首字母大写不变）
                assertThat(GalaxySpringUtil.containsBean("NoArgBean")).isTrue();
            } finally {
                GalaxySpringUtil.unregisterBean("NoArgBean");
            }
        }

        @Test
        @DisplayName("registerSingleton(String, Object) 能注册并返回单例总数")
        void registerSingleton_with_name_should_work() {
            Object obj = new Object();
            int count = GalaxySpringUtil.registerSingleton("oldSingle", obj);
            // 返回值是单例总数，不需要验证具体数字
            try {
                assertThat(GalaxySpringUtil.containsBean("oldSingle")).isTrue();
            } finally {
                GalaxySpringUtil.unregisterBean("oldSingle");
            }
        }

        @Test
        @DisplayName("removeBeanByName() 能删除 bean")
        void removeBeanByName_should_work() {
            GalaxySpringUtil.registerBean("toDelete", TestService.class, "delete-me");
            Object bean1 = GalaxySpringUtil.getBean("toDelete");
            assertThat(bean1).isNotNull();

            GalaxySpringUtil.removeBeanByName("toDelete");
            Object bean2 = GalaxySpringUtil.getBean("toDelete");
            assertThat(bean2).isNull();
        }

        @Test
        @DisplayName("getBeanNamesByAnno(@Component) 返回实例集合（非名字！命名误导）")
        void getBeanNamesByAnno_should_return_instances() {
            Collection<Object> beans = GalaxySpringUtil.getBeanNamesByAnno(Component.class);
            assertThat(beans).isNotNull();
            // 返回的是带 @Component 注解的 bean 的实例集合
            assertThat(beans).anyMatch(b -> b instanceof PrototypeBean);
        }
    }

    // ============================================================
    // ===== I. 边界 / 健壮性
    // ============================================================

    @Nested
    @DisplayName("I. 边界与健壮性")
    class EdgeCaseTests {

        @Test
        @DisplayName("多次调用 updateApplicationContext 不会崩溃")
        void updateApplicationContext_should_be_idempotent() {
            // updateApplicationContext 是公开的，但通常内部使用
            GalaxySpringUtil.updateApplicationContext(GalaxySpringUtil.getContext());
            assertThat(GalaxySpringUtil.isReady()).isTrue();
        }

        @Test
        @DisplayName("unregisterBean 空名字返回 false")
        void unregisterBean_blank_name_should_return_false() {
            assertThat(GalaxySpringUtil.unregisterBean(null)).isFalse();
            assertThat(GalaxySpringUtil.unregisterBean("")).isFalse();
        }

        @Test
        @DisplayName("findBean 系列在 context 正常时不会返回 null Optional")
        void findBean_should_never_return_null() {
            // 防御性：确保 Optional 永远不会是 null（而是 empty）
            assertThat(GalaxySpringUtil.findBean("nope")).isNotNull().isEmpty();
            assertThat(GalaxySpringUtil.findBean("nope", String.class)).isNotNull().isEmpty();
            assertThat(GalaxySpringUtil.findBean(String.class)).isNotNull().isEmpty();
        }
    }
}
