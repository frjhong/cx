package com.cx.airouter.support;

import com.cx.airouter.config.AgentRoutingProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** 直接綁定實際的 application.yml（+ 指定 profile 檔），讓單元測試驗證的是真實設定。 */
public final class TestProperties {

    private TestProperties() {
    }

    public static AgentRoutingProperties load(String profile, Map<String, Object> overrides) {
        try {
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().addFirst(new MapPropertySource("overrides", Map.of("server.port", 8080)));
            if (!overrides.isEmpty()) {
                env.getPropertySources().addFirst(new MapPropertySource("test-overrides", overrides));
            }
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            if (profile != null) {
                for (PropertySource<?> ps : loader.load(profile, new ClassPathResource("application-" + profile + ".yml"))) {
                    env.getPropertySources().addBefore("systemEnvironment", ps);
                }
            }
            List<PropertySource<?>> base = loader.load("base", new ClassPathResource("application.yml"));
            base.forEach(ps -> env.getPropertySources().addLast(ps));
            return Binder.get(env).bind("ai-router", AgentRoutingProperties.class).get();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static AgentRoutingProperties mock() {
        return load("mock", Map.of());
    }
}
