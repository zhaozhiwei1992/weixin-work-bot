package com.z.bot.platform.bailian.config;

import com.z.bot.adapter.model.AppConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Component
@ConditionalOnProperty(name = "llm.type", havingValue = "bailian")
@ConfigurationProperties(prefix = "llm")
@Data
public class BaiLianProperties extends AppConfig {
    private String apiKey;
    private String appId;
}
