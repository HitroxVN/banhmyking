package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class StoreProductId implements Serializable {

    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "product_id")
    private Long productId;
}
