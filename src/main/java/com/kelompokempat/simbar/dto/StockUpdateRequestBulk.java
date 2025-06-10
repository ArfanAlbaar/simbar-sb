
 package com.kelompokempat.simbar.dto;
 import com.kelompokempat.simbar.entity.TypeEnum;
 import lombok.AllArgsConstructor;
 import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;

 @Getter
 @Setter
 @AllArgsConstructor
 @NoArgsConstructor
 public class StockUpdateRequestBulk {

     private String itemName;
     private Integer quantity;
     private TypeEnum type;
     private String description;
 }