package com.rikkei.crm.dto;

public record VoucherDto(
        String voucherCode,
        String description,
        double discountPercentage,
        double maxDiscountAmount,
        String expiryDate
) {
}
