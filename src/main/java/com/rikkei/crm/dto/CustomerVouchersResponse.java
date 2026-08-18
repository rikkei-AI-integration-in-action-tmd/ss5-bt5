package com.rikkei.crm.dto;

import java.util.List;

public record CustomerVouchersResponse(
        boolean isSuccess,
        String customerId,
        List<VoucherDto> vouchers,
        String message
) {
    public static CustomerVouchersResponse error(String message) {
        return new CustomerVouchersResponse(false, null, List.of(), message);
    }

    public static CustomerVouchersResponse success(String customerId, List<VoucherDto> vouchers, String message) {
        return new CustomerVouchersResponse(true, customerId, vouchers, message);
    }
}
