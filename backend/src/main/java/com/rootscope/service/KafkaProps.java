package com.rootscope.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rootscope.kafka")
public class KafkaProps {
  private boolean enabled = false;
  private String topic = "rootscope-ingest";

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getTopic() { return topic; }
  public void setTopic(String topic) { this.topic = topic; }
}
