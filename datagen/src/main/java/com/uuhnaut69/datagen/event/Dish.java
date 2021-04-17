package com.uuhnaut69.datagen.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Dish {

  private String name;

  private BigDecimal price;

  private DishType type;
}
