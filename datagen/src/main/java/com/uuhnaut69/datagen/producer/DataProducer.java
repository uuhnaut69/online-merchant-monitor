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

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(OrderStreamConfig.class)
public class DataProducer {

  private static final Double LAT_PREFIX = 16D;

  private static final Double LONG_PREFIX = 108D;

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
              order.setOrderId(UUID.randomUUID().toString());

              var orderLineSize = rand.nextInt(5 - 1) + 1;
              var orderLines = new ArrayList<OrderLine>();

              IntStream.range(0, orderLineSize)
                  .forEach(
                      value2 -> {
                        var unit = rand.nextInt(5 - 1) + 1;

                        // Pre-created dishes have id in range 1 -> 10
                        var dishId = rand.nextInt(10 - 1) + 1;
                        orderLines.add(new OrderLine((long) dishId, unit));
                      });
              order.setOrderLines(orderLines);

              order.setLat(LAT_PREFIX + rand.nextFloat());
              order.setLon(LONG_PREFIX + rand.nextFloat());
              order.setCreatedAt(new Date().getTime());

              this.kafkaTemplate.send(
                  orderStreamConfig.getOutputTopic(), order.getOrderId().toString(), order);
            });
  }
}
