package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.entity.Store;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StoreHoursTest {

    private final Store store = new Store(); // mặc định 06:30–22:00

    @Test
    void openInsideHours() {
        assertThat(StoreHours.isOpen(store, LocalTime.of(6, 30))).isTrue();
        assertThat(StoreHours.isOpen(store, LocalTime.of(21, 59))).isTrue();
    }

    @Test
    void closedAtAndAfterCloseTimeAndBeforeOpen() {
        assertThat(StoreHours.isOpen(store, LocalTime.of(22, 0))).isFalse();
        assertThat(StoreHours.isOpen(store, LocalTime.of(6, 29))).isFalse();
    }
}
