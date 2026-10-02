package com.banhmyking.banhmyking.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.exception.BusinessException;
import org.junit.jupiter.api.Test;

class ContactFieldsTest {

    @Test
    void phoneIsNormalisedAndValidatedAsVietnamese() {
        assertThat(ContactFields.phone(" 090 123.45-67 ")).isEqualTo("0901234567");
        assertThat(ContactFields.phone("+84901234567")).isEqualTo("+84901234567");
        assertThat(ContactFields.phone("   ")).isNull();
        assertThat(ContactFields.phone(null)).isNull();
        assertThatThrownBy(() -> ContactFields.phone("0123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số điện thoại không đúng định dạng Việt Nam");
        assertThatThrownBy(() -> ContactFields.phone("0201234567"))
                .hasMessage("Số điện thoại không đúng định dạng Việt Nam");
    }

    @Test
    void emailIsOptionalButMustLookValid() {
        assertThat(ContactFields.email(" an@banhmy.vn ")).isEqualTo("an@banhmy.vn");
        assertThat(ContactFields.email("")).isNull();
        assertThatThrownBy(() -> ContactFields.email("an@"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email không đúng định dạng");
        assertThatThrownBy(() -> ContactFields.email("a".repeat(150) + "@x.vn"))
                .hasMessage("Email tối đa 150 ký tự");
    }

    @Test
    void requiredAndOptionalText() {
        assertThat(ContactFields.requireText("  Lan  ", 100, "Vui lòng nhập họ tên", "Họ tên tối đa 100 ký tự"))
                .isEqualTo("Lan");
        assertThatThrownBy(() -> ContactFields.requireText(" ", 100, "Vui lòng nhập họ tên", "x"))
                .hasMessage("Vui lòng nhập họ tên");
        assertThatThrownBy(() -> ContactFields.requireText("a".repeat(101), 100, "x", "Họ tên tối đa 100 ký tự"))
                .hasMessage("Họ tên tối đa 100 ký tự");
        assertThat(ContactFields.optionalText("   ", 2000, "x")).isNull();
        assertThatThrownBy(() -> ContactFields.optionalText("a".repeat(2001), 2000, "Lời nhắn tối đa 2000 ký tự"))
                .hasMessage("Lời nhắn tối đa 2000 ký tự");
        assertThat(ContactFields.trimToNull("  x ")).isEqualTo("x");
    }
}
