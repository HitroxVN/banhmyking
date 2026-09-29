package com.banhmyking.banhmyking.dto.catalog;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OptionGroupResponse {
    private Long id;
    private String name;
    private boolean required;
    private int maxChoices;
    private int sortOrder;
    private List<ProductOptionResponse> options;
}
