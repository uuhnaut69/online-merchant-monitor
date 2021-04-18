package com.uuhnaut69.datagen.producer;

import com.uuhnaut69.datagen.config.OrderStreamConfig;
import com.uuhnaut69.datagen.event.Order;
import com.uuhnaut69.datagen.event.OrderLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(OrderStreamConfig.class)
public class DataProducer {

  private static final Float LAT_PREFIX = 16F;

  private static final Float LONG_PREFIX = 108F;

  private final OrderStreamConfig orderStreamConfig;

  private final KafkaTemplate<String, Order> kafkaTemplate;

  @Scheduled(fixedDelay = 5000)
  public void producer() {
    var order = new Order();
    var rand = new Random();

    var orderSize = rand.nextInt(15);

    IntStream.range(0, orderSize)
        .forEach(
            value1 -> {
              order.setRestaurantId(1L);
              order.setOrderId(UUID.randomUUID());

              var orderLineSize = rand.nextInt(5 - 1) + 1;
              var orderLines = new HashSet<OrderLine>();

              IntStream.range(0, orderLineSize)
                  .forEach(
                      value2 -> {
                        var unit = rand.nextInt(5 - 1) + 1;

                        // Pre-created dishes have id in range 1 -> 27
                        var dishId = rand.nextInt(27 - 1) + 1;
                        orderLines.add(new OrderLine((long) dishId, unit));
                      });
              order.setOrderLines(orderLines);

              order.setLat(LAT_PREFIX + rand.nextFloat());
              order.setLon(LONG_PREFIX + rand.nextFloat());
              order.setCreatedAt(new Date());

              this.kafkaTemplate.send(
                  orderStreamConfig.getOutputTopic(), order.getOrderId().toString(), order);
            });
  }
}
