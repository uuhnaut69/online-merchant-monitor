package com.uuhnaut69.datagen.event;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Order {

  private Long restaurantId;

  private UUID orderId;

  private Set<OrderLine> orderLines;

  private Float lat;

  private Float lon;

  private Date createdAt;
}
