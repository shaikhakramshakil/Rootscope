package com.rootscope;

import com.rootscope.service.KafkaProps;
import com.rootscope.service.ScoringProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ScoringProperties.class, KafkaProps.class})
public class AppConfig {
}
