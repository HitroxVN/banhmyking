package com.banhmyking.banhmyking.util;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import java.util.regex.Pattern;

/**
 * Chuẩn hoá + kiểm các trường liên hệ của form công khai (hồ sơ ứng tuyển, phản hồi).
 * SĐT theo đúng quy tắc ô đăng ký ở frontend: 0 hoặc +84, đầu 3/5/7/8/9, thêm 8 số.
 */
public final class ContactFields {

    private static final Pattern VN_PHONE = Pattern.compile("^(0|\\+84)(3|5|7|8|9)[0-9]{8}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s.-]");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int EMAIL_MAX = 150;

    private ContactFields() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String requireText(String value, int max, String requiredMessage, String tooLongMessage) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw invalid(requiredMessage);
        }
        if (trimmed.length() > max) {
            throw invalid(tooLongMessage);
        }
        return trimmed;
    }

    public static String optionalText(String value, int max, String tooLongMessage) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            throw invalid(tooLongMessage);
        }
        return trimmed;
    }

    /** null nếu bỏ trống; bỏ khoảng trắng, dấu chấm, gạch nối trước khi kiểm. */
    public static String phone(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        String normalised = PHONE_SEPARATORS.matcher(trimmed).replaceAll("");
        if (!VN_PHONE.matcher(normalised).matches()) {
            throw invalid("Số điện thoại không đúng định dạng Việt Nam");
        }
        return normalised;
    }

    public static String email(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > EMAIL_MAX) {
            throw invalid("Email tối đa 150 ký tự");
        }
        if (!EMAIL.matcher(trimmed).matches()) {
            throw invalid("Email không đúng định dạng");
        }
        return trimmed;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
