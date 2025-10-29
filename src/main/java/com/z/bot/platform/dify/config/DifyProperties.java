package com.z.bot.platform.dify.config;

import com.z.bot.adapter.model.AppConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Component
@ConditionalOnProperty(name = "llm.type", havingValue = "dify")
@ConfigurationProperties(prefix = "llm")
@Data
public class DifyProperties extends AppConfig {

    private String chatflowApiKey;

    private String workflowApiKey;

    private String datasetApiKey;
}
