package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Một món lẻ trong combo (spec combo-sale §2.2). Khoá ghép (combo, món) nên mỗi món tối đa một dòng.
 * Combo bị xoá mềm vẫn giữ dòng này để phục vụ lịch sử.
 */
@Getter
@Setter
@Entity
@Table(name = "combo_items")
public class ComboItem {

    @EmbeddedId
    private ComboItemId id;

    @MapsId("comboId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Product combo;

    @MapsId("componentId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id")
    private Product component;

    /** Số phần của món trong MỘT combo (1–20). */
    @Column(nullable = false)
    private int quantity;
}
