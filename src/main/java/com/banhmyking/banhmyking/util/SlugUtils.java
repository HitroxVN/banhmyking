package com.banhmyking.banhmyking.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Slug cho URL tin tức / tuyển dụng: bỏ dấu tiếng Việt (cả đ/Đ), chỉ còn [a-z0-9-] (spec D §3, §8). */
public final class SlugUtils {

    /** Chừa chỗ cho hậu tố "-N" trong cột VARCHAR(220). */
    public static final int MAX_BASE_LENGTH = 200;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private SlugUtils() {
    }

    /** Trả chuỗi rỗng nếu đầu vào không có ký tự chữ/số nào dùng được. */
    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String text = input.replace('đ', 'd').replace('Đ', 'D');
        text = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        text = NON_SLUG.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("-");
        text = trimDashes(text);
        if (text.length() > MAX_BASE_LENGTH) {
            text = trimDashes(text.substring(0, MAX_BASE_LENGTH));
        }
        return text;
    }

    /** base, base-2, base-3… — lấy giá trị đầu tiên chưa bị chiếm. */
    public static String uniqueSlug(String base, Predicate<String> taken) {
        if (!taken.test(base)) {
            return base;
        }
        int suffix = 2;
        while (taken.test(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private static String trimDashes(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }
}
