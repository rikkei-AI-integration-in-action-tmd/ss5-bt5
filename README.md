BÀI 5: SÁNG TẠO NÂNG CAO - THIẾT KẾ TRỢ LÝ ẢO TRA CỨU CRM & ÁP DỤNG VOUCHER TỰ ĐỘNG

1. MÔ TẢ BỐI CẢNH & PHÂN TÍCH GIẢI PHÁP CHỊU LỖI (FAULT TOLERANCE)

1.1. Bối cảnh nghiệp vụ CRM Support Agent
Hệ thống khách sạn R-Hotels triển khai trợ lý ảo thông minh có khả năng tự động tra cứu ưu đãi cá nhân hóa của từng khách hàng từ hệ thống CRM và thực thi áp dụng voucher tốt nhất vào hóa đơn đặt phòng theo yêu cầu tự nhiên của khách hàng (ví dụ: "Áp dụng giúp tôi mã giảm giá tốt nhất của tôi vào đơn đặt phòng mã HD999 nhé").

1.2. Các nguy cơ lỗi nghiệp vụ và giải pháp lập trình phòng thủ
- Khách hàng chưa cung cấp thông tin danh tính (Customer ID / Phone):
  + Nguy cơ: AI phỏng đoán mã khách hàng hoặc gọi Tool với tham số rỗng.
  + Giải pháp: Prompt huấn luyện AI kiểm tra ChatMemory; nếu chưa có danh tính thì dừng lại và chủ động hỏi khách hàng trước khi gọi Tool. Đồng thời Tool 1 kiểm tra request.customerId(), nếu rỗng sẽ trả về CustomerVouchersResponse.error(...).
- Hóa đơn đã được thanh toán từ trước (Invoice Already Paid):
  + Nguy cơ: Áp dụng đè voucher làm sai lệch doanh thu kế toán hoặc ném Exception sập API.
  + Giải pháp: Tool 2 kiểm tra trạng thái hóa đơn; nếu đã thanh toán thì trả về ApplyVoucherResponse.error(..., "Hóa đơn HD888 đã được thanh toán trước đó, không thể áp dụng thêm mã giảm giá.").
- Mã voucher không hợp lệ hoặc đã hết hạn (Invalid / Expired Voucher):
  + Nguy cơ: Tính toán sai tổng tiền hoặc làm hỏng dữ liệu đơn đặt phòng.
  + Giải pháp: Tool 2 xác thực danh mục mã hợp lệ và trả về isSuccess = false kèm thông điệp chi tiết.
- Nguyên tắc không ném Unhandled Exception:
  + Cả 2 Tool @Tool đều không ném Runtime Exception mà đóng gói kết quả trong Java Record Response để Spring AI duy trì chu trình hội thoại và để AI giải thích tự nhiên cho người dùng.


2. SƠ ĐỒ LUỒNG XỬ LÝ DỮ LIỆU (ASCII FLOW DIAGRAM)

+---------------------------------------------------------------------------------------------------+
|                                 NGƯỜI DÙNG (USER / CLIENT)                                         |
+---------------------------------------------------------------------------------------------------+
  | "Áp dụng giúp tôi mã giảm giá tốt nhất vào đơn đặt phòng HD999"
  v
+---------------------------------------------------------------------------------------------------+
|                                  SPRING AI CHATCLIENT & ADVISORS                                  |
|  - Nạp lịch sử từ ChatMemory -> Kiểm tra thông tin định danh (Customer ID / Phone)                |
+---------------------------------------------------------------------------------------------------+
  |
  +--- [Trường hợp 1: Chưa có Customer ID] ---> AI hỏi lại người dùng: "Vui lòng cung cấp SĐT/Mã KH"
  |
  +--- [Trường hợp 2: Đã có Customer ID (ví dụ: KH888)]
        |
        v
+---------------------------------------------------------------------------------------------------+
|                        BƯỚC 1: GỌI TOOL 1 (getCustomerVouchers)                                    |
|  - Input: CustomerIdRequest(customerId = "KH888")                                                 |
|  - Tầng Java: CRM Service tra cứu danh sách voucher còn hạn trong CRM                             |
|  - Output JSON: {isSuccess: true, vouchers: [{code: "VIP20", 20%}, {code: "WELCOME10", 10%}]}     |
+---------------------------------------------------------------------------------------------------+
        |
        v
+---------------------------------------------------------------------------------------------------+
|                        BƯỚC 2: AI SUY LUẬN & CHỌN VOUCHER TỐI ƯU                                  |
|  - LLM phân tích danh sách: 20% > 10% -> Quyết định chọn voucher tốt nhất là: "VIP20"             |
+---------------------------------------------------------------------------------------------------+
        |
        v
+---------------------------------------------------------------------------------------------------+
|                        BƯỚC 3: GỌI TOOL 2 (applyVoucherToInvoice)                                 |
|  - Input: ApplyVoucherRequest(invoiceId = "HD999", voucherCode = "VIP20")                         |
|  - Tầng Java: Kiểm tra trạng thái hóa đơn -> Tính toán giảm trừ -> Cập nhật Database              |
|  - Output JSON: {isSuccess: true, original: 2.500.000, discount: 500.000, final: 2.000.000}       |
+---------------------------------------------------------------------------------------------------+
        |
        v
+---------------------------------------------------------------------------------------------------+
|                        BƯỚC 4: AI TỔNG HỢP & PHẢN HỒI KHÁCH HÀNG                                  |
|  - "Em đã áp dụng thành công mã VIP20 (giảm 20%) cho đơn HD999. Tổng thanh toán là 2.000.000 VNĐ"|
+---------------------------------------------------------------------------------------------------+


3. MÃ NGUỒN JAVA TRIỂN KHAI HOÀN CHỈNH

3.1. DTOs cho Tool 1 (Tra cứu CRM)
- CustomerIdRequest.java:
```java
package com.rikkei.crm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CustomerIdRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Mã định danh khách hàng (Customer ID, ví dụ: KH888) hoặc Số điện thoại khách hàng. Bắt buộc.")
        String customerId
) {
}
```

- VoucherDto.java:
```java
package com.rikkei.crm.dto;

public record VoucherDto(
        String voucherCode,
        String description,
        double discountPercentage,
        double maxDiscountAmount,
        String expiryDate
) {
}
```

- CustomerVouchersResponse.java:
```java
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
```

3.2. DTOs cho Tool 2 (Áp dụng Voucher)
- ApplyVoucherRequest.java:
```java
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
```

- ApplyVoucherResponse.java:
```java
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
```

3.3. DTOs cho REST Controller
- ChatRequest.java:
```java
package com.rikkei.crm.dto;

public record ChatRequest(
        String conversationId,
        String message
) {
}
```

- ChatResponse.java:
```java
package com.rikkei.crm.dto;

import java.time.LocalDateTime;

public record ChatResponse(
        String conversationId,
        String answer,
        LocalDateTime timestamp
) {
    public static ChatResponse of(String conversationId, String answer) {
        return new ChatResponse(conversationId, answer, LocalDateTime.now());
    }
}
```

3.4. Service Tools CrmBookingToolsService.java
```java
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
```

3.5. Cấu hình CrmAgentConfig.java
```java
package com.rikkei.crm.config;

import com.rikkei.crm.service.CrmBookingToolsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CrmAgentConfig {

    private static final String CRM_SYSTEM_PROMPT = """
            === VAI TRÒ VÀ MỤC TIÊU ===
            Bạn là CRM Support Agent (Trợ lý chăm sóc khách hàng và quản lý ưu đãi thông minh) của chuỗi khách sạn R-Hotels.
            Nhiệm vụ của bạn là hỗ trợ khách hàng kiểm tra mã giảm giá cá nhân trong hệ thống CRM và tự động áp dụng mã giảm giá tốt nhất vào hóa đơn đặt phòng.

            === NGUYÊN TẮC VÀ QUY TRÌNH THỰC THI (WORKFLOW) ===
            1. KIỂM TRA ĐỊNH DANH KHÁCH HÀNG (CUSTOMER IDENTITY):
               - Trước khi tra cứu voucher, hãy kiểm tra lịch sử trò chuyện xem khách hàng đã cung cấp thông tin định danh chưa (Mã khách hàng, ví dụ: KH888, hoặc Số điện thoại).
               - Nếu CHƯA CÓ thông tin định danh, bạn KHÔNG ĐƯỢC tự ý gọi Tool tra cứu. Hãy lịch sự hỏi khách hàng cung cấp Mã khách hàng hoặc Số điện thoại đã đăng ký.
            2. TRA CỨU VOUCHER (TOOL 1: getCustomerVouchers):
               - Khi đã có mã khách hàng / số điện thoại, gọi ngay công cụ getCustomerVouchers để lấy danh sách voucher còn hạn.
            3. PHÂN TÍCH VÀ CHỌN VOUCHER TỐI ƯU (VOUCHER OPTIMIZATION):
               - Đọc danh sách các voucher trả về, so sánh phần trăm giảm giá (discountPercentage) hoặc số tiền giảm tối đa để chọn ra DUY NHẤT 01 mã voucher tốt nhất (ví dụ: VIP20 giảm 20% tốt hơn WELCOME10 giảm 10%).
            4. ÁP DỤNG VOUCHER VÀO HÓA ĐƠN (TOOL 2: applyVoucherToInvoice):
               - Tự động gọi tiếp công cụ applyVoucherToInvoice với mã hóa đơn (invoiceId) và mã voucher tốt nhất vừa chọn.
            5. PHÒNG THỦ & XỬ LÝ LỖI NGHIỆP VỤ:
               - Nếu bất kỳ công cụ nào trả về 'isSuccess = false', hãy đọc thông điệp 'message' và giải thích rõ ràng, ân cần cho khách hàng, không báo lỗi kỹ thuật hệ thống chung chung.
            6. TỔNG HỢP KẾT QUẢ:
               - Khi hoàn tất, tổng hợp và thông báo: Tên voucher áp dụng, số tiền ban đầu, số tiền được giảm, và tổng tiền thanh toán cuối cùng.
            """;

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    public ChatClient crmChatClient(ChatClient.Builder builder, ChatMemory chatMemory, CrmBookingToolsService toolsService) {
        return builder
                .defaultSystem(CRM_SYSTEM_PROMPT)
                .defaultTools(toolsService)
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }
}
```

3.6. REST Controller CrmSupportController.java
```java
package com.rikkei.crm.controller;

import com.rikkei.crm.dto.ChatRequest;
import com.rikkei.crm.dto.ChatResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm-support")
public class CrmSupportController {

    private final ChatClient chatClient;

    public CrmSupportController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String conversationId = (request != null && request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId().trim()
                : UUID.randomUUID().toString();

        String userMessage = (request != null && request.message() != null)
                ? request.message()
                : "";

        String responseContent = this.chatClient.prompt()
                .user(userMessage)
                .advisors(advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .call()
                .content();

        return ChatResponse.of(conversationId, responseContent);
    }
}
```


4. PHÂN TÍCH KỸ THUẬT CHUYÊN SÂU VỀ LUỒNG GỌI TOOL LIÊN TIẾP (TOOL CHAINING)

4.1. Vòng lặp suy luận ReAct (Reasoning + Acting) trong Spring AI
Spring AI thực hiện cơ chế Tool Calling thông qua một vòng lặp đa lượt (Multi-turn Execution Loop):
- Lượt 1 (Request -> Tool 1):
  + Người dùng gửi: "Áp dụng voucher tốt nhất cho đơn HD999, tôi là KH888".
  + LLM nhận diện cần lấy danh sách voucher -> Phản hồi cờ tool_calls yêu cầu thực thi getCustomerVouchers(customerId="KH888").
  + Spring AI bắt sự kiện này, tìm bean CrmBookingToolsService, gọi phương thức tương ứng và thu về kết quả CustomerVouchersResponse dạng JSON.
- Lượt 2 (Context Injection & Tool 2 Trigger):
  + Spring AI tự động đóng gói kết quả JSON của Tool 1 thành một message loại TOOL và gửi ngược lại cho LLM cùng toàn bộ lịch sử trò chuyện.
  + LLM đọc kết quả: Thấy có VIP20 (20%) và WELCOME10 (10%).
  + Khả năng suy luận ngữ nghĩa của LLM so sánh 20% > 10% và kết luận VIP20 là voucher tối ưu nhất.
  + LLM tiếp tục phát sinh cờ tool_calls thứ hai: applyVoucherToInvoice(invoiceId="HD999", voucherCode="VIP20").
- Lượt 3 (Tổng hợp câu trả lời cuối cùng):
  + Spring AI thực thi Tool 2, cập nhật hóa đơn và trả về kết quả ApplyVoucherResponse cho LLM.
  + LLM nhận thấy không cần gọi thêm Tool nào nữa, tiến hành tổng hợp toàn bộ dữ liệu thành câu văn hoàn chỉnh, thân thiện trả về cho Controller.

4.2. Tại sao cơ chế này hoạt động an toàn và hoàn toàn tự động?
- Trích xuất động theo chuỗi (Contextual Data Propagation): Giá trị voucherCode="VIP20" dùng cho Tool 2 không phải do người dùng nhập trực tiếp, mà do LLM tự động trích xuất từ payload kết quả của Tool 1 trong cùng một phiên hội thoại.
- Không ngắt quãng luồng hội thoại: Nhờ áp dụng lập trình phòng thủ, các tình huống biên (hóa đơn đã thanh toán, voucher hết hạn) đều được trả về dưới dạng JSON Response với isSuccess = false, giúp LLM giải thích lỗi nhẹ nhàng thay vì làm crash hệ thống với HTTP 500.
