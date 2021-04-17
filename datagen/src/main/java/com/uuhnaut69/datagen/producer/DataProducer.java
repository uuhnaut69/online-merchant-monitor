package com.uuhnaut69.datagen.producer;

import com.uuhnaut69.datagen.config.OrderStreamConfig;
import com.uuhnaut69.datagen.event.Dish;
import com.uuhnaut69.datagen.event.Order;
import com.uuhnaut69.datagen.event.OrderLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;

import static com.uuhnaut69.datagen.event.DishType.*;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(OrderStreamConfig.class)
public class DataProducer {

  private static final Float LAT_PREFIX = 16F;

  private static final Float LONG_PREFIX = 108F;

  private static final List<Dish> DISHES =
      List.of(
          new Dish("Cơm thố bò", BigDecimal.valueOf(69000), RICE),
          new Dish("Cơm gà xối mỡ", BigDecimal.valueOf(65000), RICE),
          new Dish("Cơm thố vịt quay", BigDecimal.valueOf(69000), RICE),
          new Dish("Cơm thố gà", BigDecimal.valueOf(69000), RICE),
          new Dish("Cơm thố ếch", BigDecimal.valueOf(69000), RICE),
          new Dish("Cơm chiên dương châu", BigDecimal.valueOf(75000), RICE),
          new Dish("Cơm chiên xá xíu", BigDecimal.valueOf(65000), RICE),
          new Dish("Cơm thố hải sản", BigDecimal.valueOf(89000), RICE),
          new Dish("Cơm thố nai", BigDecimal.valueOf(89000), RICE),
          new Dish("Mì hoành thánh xá xíu", BigDecimal.valueOf(49000), NOODLE),
          new Dish("Mì xíu", BigDecimal.valueOf(49000), NOODLE),
          new Dish("Mỳ vịt tiềm", BigDecimal.valueOf(69000), NOODLE),
          new Dish("Sủi cảo", BigDecimal.valueOf(49000), DIMSUM),
          new Dish("Hoành thánh", BigDecimal.valueOf(49000), DIMSUM),
          new Dish("Hoành thánh chiên", BigDecimal.valueOf(49000), DIMSUM),
          new Dish("Mì xào hải sản", BigDecimal.valueOf(69000), NOODLE),
          new Dish("Mì xào bò", BigDecimal.valueOf(69000), NOODLE),
          new Dish("Mì gà da giòn", BigDecimal.valueOf(69000), NOODLE),
          new Dish("Vịt quay 1/2 con", BigDecimal.valueOf(180000), ROASTED),
          new Dish("Vịt quay phần nhỏ", BigDecimal.valueOf(69000), ROASTED),
          new Dish("Vịt quay nguyên con", BigDecimal.valueOf(360000), ROASTED),
          new Dish("Gà quay phần nhỏ", BigDecimal.valueOf(80000), ROASTED),
          new Dish("Gà quay 1/2 con", BigDecimal.valueOf(190000), ROASTED),
          new Dish("Gà quay nguyên con", BigDecimal.valueOf(380000), ROASTED),
          new Dish("Canh rong biển", BigDecimal.valueOf(15000), SOUP),
          new Dish("Canh khổ qua", BigDecimal.valueOf(15000), SOUP),
          new Dish("Canh chua", BigDecimal.valueOf(15000), SOUP));

  private static final List<String> RESTAURANTS =
      List.of("RESTAURANT_A", "RESTAURANT_B", "RESTAURANT_C", "RESTAURANT_D");

  private final KafkaTemplate<String, Order> kafkaTemplate;

  private final OrderStreamConfig orderStreamConfig;

  @Scheduled(fixedDelay = 5000)
  public void producer() {
    var order = new Order();
    var rand = new Random();

    var orderSize = rand.nextInt(30);

    IntStream.range(0, orderSize)
        .forEach(
            value1 -> {
              var restaurant = RESTAURANTS.get(rand.nextInt(RESTAURANTS.size()));
              order.setRestaurantId(restaurant);
              order.setOrderId(UUID.randomUUID());

              var orderLineSize = rand.nextInt(5 - 1) + 1;
              var orderLines = new HashSet<OrderLine>();

              IntStream.range(0, orderLineSize)
                  .forEach(
                      value2 -> {
                        var unit = rand.nextInt(5 - 1) + 1;
                        var item = DISHES.get(rand.nextInt(DISHES.size()));
                        orderLines.add(new OrderLine(item, unit));
                      });
              order.setOrderLines(orderLines);

              order.setLat(LAT_PREFIX + rand.nextFloat());
              order.setLon(LONG_PREFIX + rand.nextFloat());
              order.setOrderedDate(new Date());

              this.kafkaTemplate.send(
                  orderStreamConfig.getOutputTopic(), order.getOrderId().toString(), order);
            });
  }
}
