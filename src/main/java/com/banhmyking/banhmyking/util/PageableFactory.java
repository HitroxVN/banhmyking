package com.banhmyking.banhmyking.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validate page/size tập trung — hết clamp tay mỗi nơi một kiểu
 * (Math.max ở OrderServiceImpl, không clamp ở UserServiceImpl → page âm nổ 500).
 */
public final class PageableFactory {

    /** Size tối đa mỗi trang — chặn client gọi size=100000 kéo sập DB. */
    public static final int MAX_SIZE = 50;

    private PageableFactory() {}

    public static Pageable of(int page, int size) {
        return PageRequest.of(safePage(page), safeSize(size));
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(safePage(page), safeSize(size), sort);
    }

    private static int safePage(int page) {
        return Math.max(0, page);
    }

    private static int safeSize(int size) {
        return Math.min(Math.max(1, size), MAX_SIZE);
    }
}
