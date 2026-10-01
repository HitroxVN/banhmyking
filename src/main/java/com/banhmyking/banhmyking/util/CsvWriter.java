package com.banhmyking.banhmyking.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sinh CSV cho báo cáo quản trị. Có BOM + CRLF để Excel mở đúng tiếng Việt thay vì lỗi font.
 */
public final class CsvWriter {

    /** BOM UTF-8 (EF BB BF) — Excel cần nó mới nhận đúng bảng mã. */
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String NEWLINE = "\r\n";

    private CsvWriter() {}

    /**
     * @param rows dòng đầu là tiêu đề; ô chứa dấu phẩy / nháy kép / xuống dòng sẽ tự được bọc nháy kép.
     */
    public static byte[] toBytes(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(escape(row[i]));
            }
            sb.append(NEWLINE);
        }

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        // Chặn CSV/formula injection: tên món, tên shipper... bắt đầu bằng = + - @ sẽ bị Excel chạy như
        // công thức. Số âm (vd "-5000") vẫn giữ nguyên để cột tiền còn là số.
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0
                && !value.matches("-\\d+(\\.\\d+)?")) {
            value = "'" + value;
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
