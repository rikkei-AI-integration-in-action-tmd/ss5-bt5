package com.rikkei.crm.service;

import com.rikkei.crm.dto.ApplyVoucherRequest;
import com.rikkei.crm.dto.ApplyVoucherResponse;
import com.rikkei.crm.dto.CustomerIdRequest;
import com.rikkei.crm.dto.CustomerVouchersResponse;
import com.rikkei.crm.dto.VoucherDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CrmBookingToolsService {

    @Tool(description = "Tra cứu danh sách các mã giảm giá (voucher) còn hạn sử dụng của khách hàng từ hệ thống CRM dựa trên mã khách hàng (Customer ID, ví dụ: KH888) hoặc số điện thoại.")
    public CustomerVouchersResponse getCustomerVouchers(CustomerIdRequest request) {
        if (request == null || request.customerId() == null || request.customerId().trim().isEmpty()) {
            return CustomerVouchersResponse.error("Mã khách hàng hoặc số điện thoại không được để trống. Vui lòng hỏi khách hàng thông tin định danh.");
        }

        String customerId = request.customerId().trim().toUpperCase(Locale.ROOT);

        if (customerId.contains("UNKNOWN") || customerId.length() < 3) {
            return CustomerVouchersResponse.error("Không tìm thấy hồ sơ khách hàng với mã: " + customerId + ". Vui lòng kiểm tra lại số điện thoại hoặc mã khách hàng.");
        }

        List<VoucherDto> vouchers = List.of(
                new VoucherDto("VIP20", "Giảm 20% cho khách hàng thân thiết", 20.0, 500000.0, "2026-12-31"),
                new VoucherDto("WELCOME10", "Giảm 10% cho khách hàng mới", 10.0, 200000.0, "2026-12-31")
        );

        return CustomerVouchersResponse.success(customerId, vouchers, "Tìm thấy " + vouchers.size() + " voucher còn hiệu lực.");
    }

    @Tool(description = "Áp dụng mã giảm giá (voucherCode) vào hóa đơn hoặc đơn đặt phòng (invoiceId) trong hệ thống quản lý lữ hành. Chỉ gọi công cụ này sau khi đã xác định được mã giảm giá tốt nhất.")
    public ApplyVoucherResponse applyVoucherToInvoice(ApplyVoucherRequest request) {
        if (request == null) {
            return ApplyVoucherResponse.error(null, null, "Dữ liệu yêu cầu áp dụng voucher không hợp lệ (null).");
        }

        if (request.invoiceId() == null || request.invoiceId().trim().isEmpty()) {
            return ApplyVoucherResponse.error(null, request.voucherCode(), "Thiếu mã hóa đơn/đơn đặt phòng (invoiceId).");
        }

        if (request.voucherCode() == null || request.voucherCode().trim().isEmpty()) {
            return ApplyVoucherResponse.error(request.invoiceId(), null, "Thiếu mã giảm giá (voucherCode) cần áp dụng.");
        }

        String invoiceId = request.invoiceId().trim().toUpperCase(Locale.ROOT);
        String voucherCode = request.voucherCode().trim().toUpperCase(Locale.ROOT);

        if ("HD888".equals(invoiceId) || invoiceId.contains("PAID")) {
            return ApplyVoucherResponse.error(invoiceId, voucherCode, "Hóa đơn " + invoiceId + " đã được thanh toán trước đó, không thể áp dụng thêm mã giảm giá.");
        }

        if (!"HD999".equals(invoiceId) && !invoiceId.startsWith("HD")) {
            return ApplyVoucherResponse.error(invoiceId, voucherCode, "Mã hóa đơn " + invoiceId + " không tồn tại trên hệ thống lữ hành.");
        }

        double originalAmount = 2500000.0;
        double discountRate = switch (voucherCode) {
            case "VIP20" -> 0.20;
            case "WELCOME10" -> 0.10;
            default -> -1.0;
        };

        if (discountRate < 0) {
            return ApplyVoucherResponse.error(invoiceId, voucherCode, "Mã voucher " + voucherCode + " không hợp lệ hoặc đã hết hạn.");
        }

        double discountAmount = originalAmount * discountRate;
        double finalAmount = originalAmount - discountAmount;

        return ApplyVoucherResponse.success(
                invoiceId,
                voucherCode,
                originalAmount,
                discountAmount,
                finalAmount,
                "Áp dụng thành công voucher " + voucherCode + " vào hóa đơn " + invoiceId + ". Giảm: " + String.format("%,.0f", discountAmount) + " VNĐ."
        );
    }
}
