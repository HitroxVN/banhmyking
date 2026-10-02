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
public class ComboItemId implements Serializable {

    @Column(name = "combo_id")
    private Long comboId;

    @Column(name = "component_id")
    private Long componentId;
}
