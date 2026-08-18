package com.rikkei.crm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CustomerIdRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã định danh khách hàng (Customer ID, ví dụ: KH888) hoặc Số điện thoại khách hàng. Bắt buộc.")
        String customerId
) {
}
