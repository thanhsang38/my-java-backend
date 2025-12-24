package com.tranthanhsang.example304.controllers;

import com.tranthanhsang.example304.entity.Bill;
import com.tranthanhsang.example304.entity.enums.PaymentStatus;
import com.tranthanhsang.example304.payload.response.BillDTO;
import com.tranthanhsang.example304.security.services.BillService;
import com.tranthanhsang.example304.security.services.VnPayService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/bills")
public class BillController {
    @Autowired
    private BillService billService;
    @Autowired
    private VnPayService vnPayService;

    // ✅ Trả về danh sách BillDTO
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
    public ResponseEntity<Page<BillDTO>> getAllBills(
            // Nhận số trang, mặc định là trang 0 (giống hệt ProductController)
            @RequestParam(defaultValue = "0") int page) {
        Page<BillDTO> bills = billService.getAll(page);
        return ResponseEntity.ok(bills);
    }

    // Thêm hóa đơn
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
    public ResponseEntity<BillDTO> createBill(@RequestBody Bill bill) {
        BillDTO createdBillDTO = billService.create(bill);
        return ResponseEntity.ok(createdBillDTO);
    }

    // Cập nhật hóa đơn
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public Bill update(@PathVariable Long id, @RequestBody Bill bill) {
        return billService.update(id, bill);
    }

    // Xóa hóa đơn
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public void delete(@PathVariable Long id) {
        billService.delete(id);
    }

    // Lấy hóa đơn theo ID bàn
    @GetMapping("/tables/{tableId}")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
    public ResponseEntity<List<BillDTO>> getBillsByTable(@PathVariable Long tableId) {
        return ResponseEntity.ok(billService.getBillsByTable(tableId));
    }

    // Lấy hóa đơn theo ID bàn và trạng thái thanh toán
    @GetMapping("/tables/{tableId}/status/{paymentStatus}")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
    public ResponseEntity<List<BillDTO>> getBillsByTableAndPaymentStatus(
            @PathVariable Long tableId,
            @PathVariable PaymentStatus paymentStatus) {

        List<BillDTO> bills = billService.getBillsByTableAndPaymentStatus(tableId, paymentStatus);
        return ResponseEntity.ok(bills);
    }

    // Lấy hóa đơn theo trạng thái thanh toán
    @GetMapping("/payment_status/{paymentStatus}")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
    public ResponseEntity<List<BillDTO>> getBillsByPaymentStatus(@PathVariable PaymentStatus paymentStatus) {
        return ResponseEntity.ok(billService.getBillsByPaymentStatus(paymentStatus));
    }

    // Lấy hóa đơn theo ID
    @GetMapping("/{id}")
    public ResponseEntity<BillDTO> getBillById(@PathVariable Long id) {
        BillDTO dto = billService.getById(id);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Bill> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        // Lấy trạng thái mới từ body request
        String statusStr = body.get("paymentStatus");
        PaymentStatus newStatus = PaymentStatus.valueOf(statusStr); // Chuyển String thành Enum

        Bill updatedBill = billService.updatePaymentStatus(id, newStatus);
        return ResponseEntity.ok(updatedBill);
    }

    @GetMapping("/{id}/pdf")
    // Giả sử bạn có PreAuthorize ở đây
    public ResponseEntity<byte[]> exportBillPdf(@PathVariable Long id) {
        byte[] pdfData = billService.generateBillPdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);

        // SỬA ĐỔI: Đổi "attachment" thành "inline" để PDF mở trong trình duyệt
        headers.setContentDispositionFormData("inline", "HoaDon_" + id + ".pdf");

        return new ResponseEntity<>(pdfData, headers, HttpStatus.OK);
    }

    @PostMapping("/from-order/{orderId}")
    public ResponseEntity<BillDTO> createBillFromOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        try {
            // 1. Lấy phương thức thanh toán từ body
            String paymentMethodStr = body.get("paymentMethod");

            // 2. Gọi hàm service để tạo Bill, đóng Order và giải phóng Table
            BillDTO createdBillDTO = billService.createBillFromOrder(orderId, paymentMethodStr);

            // 3. Trả về BillDTO đã được tạo
            return ResponseEntity.ok(createdBillDTO);

        } catch (RuntimeException e) {
            // Xử lý lỗi nếu Order không tìm thấy hoặc đã được thanh toán
            return ResponseEntity.badRequest().body(new BillDTO()); // Trả về 400 Bad Request
        }
    }

    // @GetMapping("/vnpay-return")
    // public ResponseEntity<String> vnpayReturn(@RequestParam Map<String, String>
    // params) {
    // System.out.println("📥 VNPAY callback nhận được: " + params);

    // if (!vnPayService.validateHash(params)) {
    // System.err.println("❌ VNPAY callback thất bại: Hash không hợp lệ.");
    // return
    // ResponseEntity.status(HttpStatus.BAD_REQUEST).body("INVALID_SIGNATURE");
    // }

    // try {
    // Long orderId = Long.parseLong(params.get("vnp_TxnRef"));
    // String responseCode = params.get("vnp_ResponseCode");

    // PaymentStatus status = "00".equals(responseCode)
    // ? PaymentStatus.COMPLETED
    // : PaymentStatus.FAILED;

    // billService.updatePaymentStatusByOrderId(orderId, status);
    // System.out.println("✅ VNPAY callback: orderId=" + orderId + ", status=" +
    // status);

    // return ResponseEntity.ok("OK");
    // } catch (Exception e) {
    // e.printStackTrace();
    // return ResponseEntity.badRequest().body("Error processing VNPAY return");
    // }
    // }

}