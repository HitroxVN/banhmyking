package com.banhmyking.banhmyking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class PageableFactoryTest {

    @Test
    @DisplayName("page âm → clamp về 0, không nổ PageRequest")
    void negativePage_clampedToZero() {
        Pageable p = PageableFactory.of(-5, 10);
        assertThat(p.getPageNumber()).isZero();
    }

    @Test
    @DisplayName("size 0/âm → clamp về 1")
    void nonPositiveSize_clampedToOne() {
        assertThat(PageableFactory.of(0, 0).getPageSize()).isEqualTo(1);
        assertThat(PageableFactory.of(0, -3).getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("size quá lớn → cap MAX_SIZE (chặn kéo sập DB)")
    void oversizedSize_capped() {
        assertThat(PageableFactory.of(0, 100_000).getPageSize()).isEqualTo(PageableFactory.MAX_SIZE);
    }

    @Test
    @DisplayName("giá trị hợp lệ giữ nguyên")
    void validValues_passThrough() {
        Pageable p = PageableFactory.of(2, 20);
        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(20);
    }
}
