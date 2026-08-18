package com.rikkei.crm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ApplyVoucherRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã đơn đặt phòng hoặc mã hóa đơn cần áp dụng mã giảm giá, ví dụ: HD999. Bắt buộc.")
        String invoiceId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã voucher giảm giá tốt nhất muốn áp dụng, ví dụ: VIP20. Bắt buộc.")
        String voucherCode
) {
}
