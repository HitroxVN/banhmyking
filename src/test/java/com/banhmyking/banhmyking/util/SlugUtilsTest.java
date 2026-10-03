package com.banhmyking.banhmyking.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SlugUtilsTest {

    @Test
    void removesVietnameseDiacriticsIncludingDStroke() {
        assertThat(SlugUtils.slugify("Khuyến mãi tháng 10 — Đặc biệt!")).isEqualTo("khuyen-mai-thang-10-dac-biet");
        assertThat(SlugUtils.slugify("ĐƯỜNG ĐUA Bánh Mì Ơi")).isEqualTo("duong-dua-banh-mi-oi");
        assertThat(SlugUtils.slugify("Phụ bếp ca tối (22–25k/giờ)")).isEqualTo("phu-bep-ca-toi-22-25k-gio");
    }

    @Test
    void collapsesSeparatorsAndTrimsDashes() {
        assertThat(SlugUtils.slugify("  --Hello__World--  ")).isEqualTo("hello-world");
        assertThat(SlugUtils.slugify("a---b")).isEqualTo("a-b");
    }

    @Test
    void emptyWhenNothingUsable() {
        assertThat(SlugUtils.slugify("!!! ???")).isEmpty();
        assertThat(SlugUtils.slugify(null)).isEmpty();
    }

    @Test
    void truncatesLongInputWithoutTrailingDash() {
        String slug = SlugUtils.slugify("ab ".repeat(150));
        assertThat(slug.length()).isLessThanOrEqualTo(SlugUtils.MAX_BASE_LENGTH);
        assertThat(slug).doesNotEndWith("-").startsWith("ab-ab");
    }

    @Test
    void uniqueSlugAddsNumericSuffix() {
        Set<String> taken = Set.of("tin-moi", "tin-moi-2");
        assertThat(SlugUtils.uniqueSlug("tin-moi", taken::contains)).isEqualTo("tin-moi-3");
        assertThat(SlugUtils.uniqueSlug("tin-khac", taken::contains)).isEqualTo("tin-khac");
    }
}
