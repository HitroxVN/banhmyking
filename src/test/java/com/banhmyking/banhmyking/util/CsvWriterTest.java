package com.banhmyking.banhmyking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    @DisplayName("File CSV mở đầu bằng BOM UTF-8 để Excel nhận đúng tiếng Việt")
    void toBytes_startsWithUtf8Bom() {
        byte[] bytes = CsvWriter.toBytes(List.<String[]>of(new String[] {"Món"}));

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    @DisplayName("Ô nối bằng dấu phẩy, dòng kết thúc bằng CRLF")
    void toBytes_joinsCellsWithCommaAndRowsWithCrlf() {
        byte[] bytes = CsvWriter.toBytes(List.<String[]>of(
                new String[] {"Món", "Số lượng"},
                new String[] {"Bánh mì", "2"}));

        assertThat(body(bytes)).isEqualTo("Món,Số lượng\r\nBánh mì,2\r\n");
    }

    @Test
    @DisplayName("Ô chứa dấu phẩy / nháy kép được bọc nháy kép và nhân đôi nháy bên trong")
    void toBytes_escapesCommaAndQuotes() {
        byte[] bytes = CsvWriter.toBytes(List.<String[]>of(
                new String[] {"Bánh mì, \"đặc biệt\""}));

        assertThat(body(bytes)).isEqualTo("\"Bánh mì, \"\"đặc biệt\"\"\"\r\n");
    }

    @Test
    @DisplayName("Ô chứa xuống dòng vẫn nằm gọn trong một ô")
    void toBytes_escapesNewline() {
        byte[] bytes = CsvWriter.toBytes(List.<String[]>of(
                new String[] {"Ghi chú", "dòng 1\ndòng 2"}));

        assertThat(body(bytes)).isEqualTo("Ghi chú,\"dòng 1\ndòng 2\"\r\n");
    }

    @Test
    @DisplayName("Giá trị null thành ô rỗng thay vì chữ \"null\"")
    void toBytes_nullBecomesEmptyCell() {
        byte[] bytes = CsvWriter.toBytes(List.<String[]>of(
                new String[] {"Tài xế", null}));

        assertThat(body(bytes)).isEqualTo("Tài xế,\r\n");
    }

    /** Bỏ 3 byte BOM để so phần nội dung. */
    private String body(byte[] bytes) {
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }
}
