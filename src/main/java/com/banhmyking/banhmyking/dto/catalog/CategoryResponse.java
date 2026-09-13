package com.banhmyking.banhmyking.dto.catalog;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Integer sortOrder;
}
