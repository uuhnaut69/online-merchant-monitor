package com.uuhnaut69.datagen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "orders")
public class OrderStreamConfig {

  private String outputTopic;
}
