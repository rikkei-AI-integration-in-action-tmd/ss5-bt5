package com.rikkei.crm.dto;

public record ApplyVoucherResponse(
        boolean isSuccess,
        String invoiceId,
        String voucherCode,
        double originalAmount,
        double discountAmount,
        double finalAmount,
        String message
) {
    public static ApplyVoucherResponse error(String invoiceId, String voucherCode, String message) {
        return new ApplyVoucherResponse(false, invoiceId, voucherCode, 0.0, 0.0, 0.0, message);
    }

    public static ApplyVoucherResponse success(String invoiceId, String voucherCode, double originalAmount, double discountAmount, double finalAmount, String message) {
        return new ApplyVoucherResponse(true, invoiceId, voucherCode, originalAmount, discountAmount, finalAmount, message);
    }
}
