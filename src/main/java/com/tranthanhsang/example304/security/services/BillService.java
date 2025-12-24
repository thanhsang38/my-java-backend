package com.tranthanhsang.example304.security.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.tranthanhsang.example304.entity.Bill;
import com.tranthanhsang.example304.payload.response.BillDTO;
import com.tranthanhsang.example304.payload.response.OrderDTO;
import com.tranthanhsang.example304.payload.response.OrderItemDTO;
import com.tranthanhsang.example304.repository.BillRepository;
import com.tranthanhsang.example304.repository.OrderRepository;
import com.tranthanhsang.example304.entity.Order;
import com.tranthanhsang.example304.entity.OrderItem;
import com.tranthanhsang.example304.entity.TableEntity;
import com.tranthanhsang.example304.entity.enums.Status;
import com.tranthanhsang.example304.entity.enums.OrderStatus;
import com.tranthanhsang.example304.entity.enums.PaymentMethod;
import com.tranthanhsang.example304.entity.enums.PaymentStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import java.io.ByteArrayOutputStream;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import java.util.stream.Stream;

import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Element;

@Service
public class BillService {
    @Autowired
    private BillRepository billRepo;
    @Autowired
    private OrderRepository orderRepo;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private TableService tableService;

    @Autowired
    private OrderService orderService;
    @Autowired
    private VnPayService vnPayService;

    // Lấy tất cả hóa đơn
    public Page<BillDTO> getAll(int page) {
        // 1. Tạo Pageable, 10 hóa đơn mỗi trang, sắp xếp theo ID giảm dần (mới nhất lên
        // đầu)
        Pageable pageable = PageRequest.of(page, 12, Sort.by("id").descending());

        // 2. Lấy dữ liệu đã phân trang từ repository
        Page<Bill> billPage = billRepo.findAll(pageable);

        // 3. Chuyển đổi Page<Bill> thành Page<BillDTO> (dùng hàm convertToDTO của bạn)
        return billPage.map(this::convertToDTO);
    }

    // Tạo mới hóa đơn
    @Transactional
    public BillDTO create(Bill bill) {
        bill.setCreatedAt(LocalDateTime.now());
        bill.setUpdatedAt(LocalDateTime.now());
        bill.setIssuedAt(LocalDateTime.now());

        Order order;

        // ✅ 1. Kiểm tra và lấy order
        if (bill.getOrder() != null && bill.getOrder().getId() != null) {
            order = orderRepo.findById(bill.getOrder().getId())
                    .orElseThrow(() -> new RuntimeException("❌ Không tìm thấy đơn hàng!"));
            bill.setOrder(order);
        } else {
            throw new RuntimeException("❌ Thiếu thông tin orderId trong hóa đơn!");
        }

        // ✅ 2. Lưu bill vào DB
        Bill savedBill = billRepo.save(bill);
        System.out.println("✅ Bill đã lưu vào DB: Bill #" + savedBill.getId());

        // ✅ 3. Convert sang DTO
        BillDTO billDTO = convertToDTO(savedBill);

        // ✅ 4. Kiểm tra bàn để gửi WebSocket cho đúng khách
        if (order.getTable() != null && order.getTable().getId() != null) {
            Long tableId = order.getTable().getId();
            try {
                messagingTemplate.convertAndSend("/topic/customer-bill/" + tableId, billDTO);
                System.out.println("📤 Đã push bill DTO cho khách hàng: /topic/customer-bill/" + tableId);
            } catch (Exception e) {
                System.err.println("⚠️ Gửi WebSocket thất bại: " + e.getMessage());
            }
        } else {
            System.err.println("⚠️ Order không gắn bàn — không thể gửi bill cho khách hàng.");
        }

        // ✅ 5. Trả về DTO cho nhân viên
        return billDTO;
    }

    // Cập nhật hóa đơn
    public Bill update(Long id, Bill bill) {
        System.out.println("📥 Yêu cầu cập nhật bill #" + id);
        System.out.println("➡️ Trạng thái mới: " + bill.getPaymentStatus());
        System.out.println("➡️ Phương thức mới: " + bill.getPaymentMethod());
        System.out.println("➡️ Tổng tiền mới: " + bill.getTotalAmount());

        Bill existing = billRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("❌ Không tìm thấy bill #" + id));

        boolean wasPending = existing.getPaymentStatus() != PaymentStatus.COMPLETED
                && bill.getPaymentStatus() == PaymentStatus.COMPLETED;

        existing.setOrder(bill.getOrder());
        existing.setTotalAmount(bill.getTotalAmount());
        existing.setPaymentMethod(bill.getPaymentMethod());
        existing.setPaymentStatus(bill.getPaymentStatus());
        existing.setUpdatedAt(LocalDateTime.now());

        Bill updated = billRepo.save(existing);
        System.out.println("✅ Bill đã cập nhật: #" + updated.getId());

        // ✅ Nếu trạng thái chuyển từ PENDING → PAID → push cho khách để ẩn QR
        if (wasPending) {
            // MỚI: Chuyển đổi sang DTO trước khi gửi
            BillDTO billDTO = convertToDTO(updated);

            // SỬA: Gửi đi đối tượng DTO
            Long tableId = updated.getOrder().getTable().getId();
            messagingTemplate.convertAndSend("/topic/customer-bill/" + tableId, billDTO);
            System.out.println("📤 Đã push bill PAID DTO cho khách: /topic/customer-bill/" + tableId);
        }

        return updated;
    }

    // Xóa hóa đơn
    public void delete(Long id) {
        billRepo.deleteById(id);
    }

    // Chuyển đổi Bill entity sang BillDTO
    public BillDTO convertToDTO(Bill bill) {
        Order order = bill.getOrder();

        BillDTO dto = new BillDTO();
        dto.setId(bill.getId());
        dto.setTotalAmount(bill.getTotalAmount());
        dto.setPaymentMethod(bill.getPaymentMethod().name());
        dto.setPaymentStatus(bill.getPaymentStatus().name());
        dto.setIssuedAt(bill.getIssuedAt());

        // Đơn hàng
        dto.setOrderId(order.getId());
        dto.setOrderStatus(order.getStatus().name());
        dto.setOrderTotal(order.getTotalAmount());
        dto.setOrderNotes(order.getNotes());
        dto.setTotalQuantity(order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum());

        // Bàn
        if (order.getTable() != null) {
            dto.setTableId(order.getTable().getId());
            dto.setTableNumber(order.getTable().getNumber());
        }

        // Nhân viên
        if (order.getEmployee() != null) {
            dto.setEmployeeId(order.getEmployee().getId());
            dto.setEmployeeName(order.getEmployee().getFullName());
        }

        // ✅ Khuyến mãi (có thể null)
        if (order.getPromotion() != null) {
            dto.setPromotionId(order.getPromotion().getId());
            dto.setPromotionCode(order.getPromotion().getName());
        } else {
            dto.setPromotionId(null);
            dto.setPromotionCode(null);
        }

        // Món ăn
        List<OrderItemDTO> itemDTOs = order.getItems().stream().map(item -> {
            OrderItemDTO itemDTO = new OrderItemDTO();
            itemDTO.setProductId(item.getProduct().getId());
            itemDTO.setProductName(item.getProduct().getName());
            itemDTO.setQuantity(item.getQuantity());
            itemDTO.setPrice(item.getPrice());
            itemDTO.setSubtotal(item.getSubtotal());
            itemDTO.setOrderId(order.getId());
            return itemDTO;
        }).toList();

        dto.setItems(itemDTOs);

        // ✅ Tính originalAmount
        BigDecimal originalAmount = order.getItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setOriginalAmount(originalAmount);

        // ✅ Tính discountAmount
        BigDecimal discountAmount = originalAmount.subtract(order.getTotalAmount());
        dto.setDiscountAmount(discountAmount.compareTo(BigDecimal.ZERO) > 0 ? discountAmount : BigDecimal.ZERO);

        return dto;
    }

    // Lấy hóa đơn theo ID bàn
    public List<BillDTO> getBillsByTable(Long tableId) {
        List<Bill> bills = billRepo.findByTableId(tableId);

        return bills.stream().map(bill -> {
            Order order = bill.getOrder();

            BillDTO dto = new BillDTO();
            dto.setId(bill.getId());
            dto.setTotalAmount(bill.getTotalAmount());
            dto.setPaymentMethod(bill.getPaymentMethod().name());
            dto.setPaymentStatus(bill.getPaymentStatus().name());
            dto.setIssuedAt(bill.getIssuedAt());

            // Đơn hàng
            dto.setOrderId(order.getId());
            dto.setOrderStatus(order.getStatus().name());
            dto.setOrderTotal(order.getTotalAmount());
            dto.setOrderNotes(order.getNotes());
            dto.setTotalQuantity(order.getItems().stream()
                    .mapToInt(OrderItem::getQuantity)
                    .sum());

            // Bàn
            if (order.getTable() != null) {
                dto.setTableId(order.getTable().getId());
                dto.setTableNumber(order.getTable().getNumber());
            }

            // Nhân viên
            if (order.getEmployee() != null) {
                dto.setEmployeeId(order.getEmployee().getId());
                dto.setEmployeeName(order.getEmployee().getFullName());
            }

            // ✅ Khuyến mãi (có thể null)
            if (order.getPromotion() != null) {
                dto.setPromotionId(order.getPromotion().getId());
                dto.setPromotionCode(order.getPromotion().getName());
            } else {
                dto.setPromotionId(null);
                dto.setPromotionCode(null);
            }

            // Món ăn
            List<OrderItemDTO> itemDTOs = order.getItems().stream().map(item -> {
                OrderItemDTO itemDTO = new OrderItemDTO();
                itemDTO.setProductId(item.getProduct().getId());
                itemDTO.setProductName(item.getProduct().getName());
                itemDTO.setQuantity(item.getQuantity());
                itemDTO.setPrice(item.getPrice());
                itemDTO.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                itemDTO.setOrderId(order.getId());
                return itemDTO;
            }).toList();

            dto.setItems(itemDTOs);

            // ✅ Tính originalAmount
            BigDecimal originalAmount = order.getItems().stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setOriginalAmount(originalAmount);

            // ✅ Tính discountAmount
            BigDecimal discountAmount = originalAmount.subtract(order.getTotalAmount());
            dto.setDiscountAmount(discountAmount.compareTo(BigDecimal.ZERO) > 0 ? discountAmount : BigDecimal.ZERO);

            return dto;
        }).toList();
    }

    // Lấy hóa đơn theo ID bàn và trạng thái thanh toán
    public List<BillDTO> getBillsByTableAndPaymentStatus(Long tableId, PaymentStatus paymentStatus) {
        List<Bill> bills = billRepo.findByTableIdAndPaymentStatus(tableId, paymentStatus);

        return bills.stream().map(bill -> {
            Order order = bill.getOrder();
            BillDTO dto = new BillDTO();

            dto.setId(bill.getId());
            dto.setTotalAmount(bill.getTotalAmount());
            dto.setPaymentMethod(bill.getPaymentMethod().name());
            dto.setPaymentStatus(bill.getPaymentStatus().name());
            dto.setIssuedAt(bill.getIssuedAt());

            // Đơn hàng
            dto.setOrderId(order.getId());
            dto.setOrderStatus(order.getStatus().name());
            dto.setOrderTotal(order.getTotalAmount());
            dto.setOrderNotes(order.getNotes());
            dto.setTotalQuantity(order.getItems().stream()
                    .mapToInt(OrderItem::getQuantity)
                    .sum());

            // Bàn
            if (order.getTable() != null) {
                dto.setTableId(order.getTable().getId());
                dto.setTableNumber(order.getTable().getNumber());
            }

            // Nhân viên
            if (order.getEmployee() != null) {
                dto.setEmployeeId(order.getEmployee().getId());
                dto.setEmployeeName(order.getEmployee().getFullName());
            }

            // ✅ Khuyến mãi (có thể null)
            if (order.getPromotion() != null) {
                dto.setPromotionId(order.getPromotion().getId());
                dto.setPromotionCode(order.getPromotion().getName());
            } else {
                dto.setPromotionId(null);
                dto.setPromotionCode(null);
            }

            // Món ăn
            List<OrderItemDTO> itemDTOs = order.getItems().stream().map(item -> {
                OrderItemDTO itemDTO = new OrderItemDTO();
                itemDTO.setProductId(item.getProduct().getId());
                itemDTO.setProductName(item.getProduct().getName());
                itemDTO.setQuantity(item.getQuantity());
                itemDTO.setPrice(item.getPrice());
                itemDTO.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                itemDTO.setOrderId(order.getId());
                return itemDTO;
            }).toList();

            dto.setItems(itemDTOs);

            // ✅ Tính originalAmount
            BigDecimal originalAmount = order.getItems().stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setOriginalAmount(originalAmount);

            // ✅ Tính discountAmount
            BigDecimal discountAmount = originalAmount.subtract(order.getTotalAmount());
            dto.setDiscountAmount(discountAmount.compareTo(BigDecimal.ZERO) > 0 ? discountAmount : BigDecimal.ZERO);

            return dto;
        }).toList();
    }

    // Lấy hóa đơn theo trạng thái thanh toán
    public List<BillDTO> getBillsByPaymentStatus(PaymentStatus paymentStatus) {
        List<Bill> bills = billRepo.findByPaymentStatus(paymentStatus);

        return bills.stream().map(bill -> {
            Order order = bill.getOrder();
            BillDTO dto = new BillDTO();

            dto.setId(bill.getId());
            dto.setTotalAmount(bill.getTotalAmount());
            dto.setPaymentMethod(bill.getPaymentMethod().name());
            dto.setPaymentStatus(bill.getPaymentStatus().name());
            dto.setIssuedAt(bill.getIssuedAt());

            // Đơn hàng
            dto.setOrderId(order.getId());
            dto.setOrderStatus(order.getStatus().name());
            dto.setOrderTotal(order.getTotalAmount());
            dto.setOrderNotes(order.getNotes());
            dto.setTotalQuantity(order.getItems().stream()
                    .mapToInt(OrderItem::getQuantity)
                    .sum());

            // Bàn
            if (order.getTable() != null) {
                dto.setTableId(order.getTable().getId());
                dto.setTableNumber(order.getTable().getNumber());
            }

            // Nhân viên
            if (order.getEmployee() != null) {
                dto.setEmployeeId(order.getEmployee().getId());
                dto.setEmployeeName(order.getEmployee().getFullName());
            }

            // ✅ Khuyến mãi (có thể null)
            if (order.getPromotion() != null) {
                dto.setPromotionId(order.getPromotion().getId());
                dto.setPromotionCode(order.getPromotion().getName());
            } else {
                dto.setPromotionId(null);
                dto.setPromotionCode(null);
            }

            // Món ăn
            List<OrderItemDTO> itemDTOs = order.getItems().stream().map(item -> {
                OrderItemDTO itemDTO = new OrderItemDTO();
                itemDTO.setProductId(item.getProduct().getId());
                itemDTO.setProductName(item.getProduct().getName());
                itemDTO.setQuantity(item.getQuantity());
                itemDTO.setPrice(item.getPrice());
                itemDTO.setSubtotal(item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                itemDTO.setOrderId(order.getId());
                return itemDTO;
            }).toList();

            dto.setItems(itemDTOs);

            // ✅ Tính originalAmount
            BigDecimal originalAmount = order.getItems().stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setOriginalAmount(originalAmount);

            // ✅ Tính discountAmount
            BigDecimal discountAmount = originalAmount.subtract(order.getTotalAmount());
            dto.setDiscountAmount(discountAmount.compareTo(BigDecimal.ZERO) > 0 ? discountAmount : BigDecimal.ZERO);

            return dto;
        }).toList();
    }

    // Lấy hóa đơn theo ID
    public BillDTO getById(Long id) {
        Bill bill = billRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với ID: " + id));

        return convertToDTO(bill);
    }

    public Bill updatePaymentStatus(Long id, PaymentStatus newStatus) {
        System.out.println("📥 Yêu cầu cập nhật trạng thái bill #" + id + " thành " + newStatus);

        // Tìm bill hiện có trong DB
        Bill existing = billRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("❌ Không tìm thấy bill #" + id));

        // Kiểm tra xem trạng thái có thực sự thay đổi từ chưa hoàn thành -> hoàn thành
        // không
        boolean wasPending = existing.getPaymentStatus() != PaymentStatus.COMPLETED
                && newStatus == PaymentStatus.COMPLETED;

        // Chỉ cập nhật trạng thái và thời gian
        existing.setPaymentStatus(newStatus);
        existing.setUpdatedAt(LocalDateTime.now());

        Bill updated = billRepo.save(existing);
        System.out.println("✅ Đã cập nhật trạng thái bill #" + updated.getId());

        // Nếu bill vừa được thanh toán, push thông báo cho khách hàng
        if (wasPending) {
            Order order = updated.getOrder();
            TableEntity table = order.getTable();

            // 1. Cập nhật trạng thái Order → PAID
            order.setStatus(OrderStatus.PAID);
            orderRepo.save(order);

            // 2. Cập nhật trạng thái Bàn → FREE
            if (table != null) {
                table.setStatus(Status.FREE);
                tableService.update(table.getId(), table);
            }

            // 3. Push cho khách để ẩn QR (hoặc thông báo)
            BillDTO billDTO = convertToDTO(updated);
            Long tableId = updated.getOrder().getTable().getId();
            messagingTemplate.convertAndSend("/topic/customer-bill/" + tableId, billDTO);
            System.out.println("📤 Đã push bill PAID DTO cho khách: /topic/customer-bill/" + tableId);
            if (tableId != null) {
                try {
                    OrderDTO orderDTO = orderService.convertToDTO(order);
                    // Gửi OrderDTO đã đóng (PAID) lên kênh cập nhật Order của khách
                    messagingTemplate.convertAndSend("/topic/order-updates/" + tableId, orderDTO);
                    System.out.println("📤 Đã push cập nhật đơn hàng (PAID/CASH) cho khách hàng bàn: " + tableId);
                } catch (Exception e) {
                    System.err.println("⚠️ Gửi WebSocket Order update thất bại (CASH): " + e.getMessage());
                }
            }
        }

        return updated;
    }

    public byte[] generateBillPdf(Long billId) {
        Bill bill = billRepo.findById(billId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn với ID: " + billId));

        BillDTO dto = convertToDTO(bill);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // --- 🔤 NẠP FONT HỖ TRỢ TIẾNG VIỆT ---
            String fontPath = "C:/Windows/Fonts/times.ttf";
            BaseFont baseFont = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);

            Font titleFont = new Font(baseFont, 20, Font.BOLD, BaseColor.BLACK);
            Font textFont = new Font(baseFont, 12, Font.NORMAL, BaseColor.BLACK);
            Font boldFont = new Font(baseFont, 12, Font.BOLD, BaseColor.BLACK);

            // --- Tiêu đề ---

            Paragraph title = new Paragraph("COFFEE THANH XANN", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("HÓA ĐƠN THANH TOÁN #" + dto.getId(), boldFont));
            document.add(new Paragraph(
                    "Ngày xuất: " + dto.getIssuedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    textFont));
            document.add(new Paragraph(" "));

            // --- Thông tin bàn / nhân viên ---
            document.add(new Paragraph("Bàn: " + dto.getTableNumber(), textFont));
            document.add(new Paragraph("Nhân viên: " +
                    (dto.getEmployeeName() != null ? dto.getEmployeeName() : "N/A"), textFont));
            document.add(new Paragraph("Phương thức thanh toán: " + dto.getPaymentMethod(), textFont));
            document.add(new Paragraph("Trạng thái: " + dto.getPaymentStatus(), textFont));
            document.add(new Paragraph(" "));

            // --- Bảng món ---
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 4, 1, 2, 2 });

            Stream.of("Tên món", "SL", "Đơn giá", "Thành tiền").forEach(header -> {
                PdfPCell cell = new PdfPCell(new Paragraph(header, boldFont));
                cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
                table.addCell(cell);
            });

            for (OrderItemDTO item : dto.getItems()) {
                table.addCell(new Paragraph(item.getProductName(), textFont));
                table.addCell(new Paragraph(String.valueOf(item.getQuantity()), textFont));
                table.addCell(new Paragraph(String.format("%,.0f₫", item.getPrice()), textFont));
                table.addCell(new Paragraph(String.format("%,.0f₫", item.getSubtotal()), textFont));
            }

            document.add(table);
            document.add(new Paragraph(" "));

            // --- Tổng kết ---
            Paragraph subTotal = new Paragraph("Tạm tính: " +
                    String.format("%,.0f₫", dto.getOriginalAmount()), textFont);
            subTotal.setAlignment(Element.ALIGN_RIGHT);
            document.add(subTotal);

            Paragraph discount = new Paragraph("Giảm giá: -" +
                    String.format("%,.0f₫", dto.getDiscountAmount()), textFont);
            discount.setAlignment(Element.ALIGN_RIGHT);
            document.add(discount);

            Paragraph total = new Paragraph("Tổng cộng: " +
                    String.format("%,.0f₫", dto.getTotalAmount()), boldFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(new Paragraph(" "));

            // --- QR VietQR ---
            String qrUrl = String.format(
                    "https://img.vietqr.io/image/970422-0398617329-compact.png?amount=%s&addInfo=DonHang%s&accountName=%s",
                    dto.getTotalAmount().intValue(), dto.getOrderId(), "TRAN%20THANH%20SANG");

            try {
                Image qrImage = Image.getInstance(qrUrl);
                qrImage.scaleAbsolute(150, 150);
                qrImage.setAlignment(Element.ALIGN_CENTER);
                document.add(qrImage);

                Paragraph qrNote = new Paragraph(
                        "Quét mã để thanh toán: " + String.format("%,.0f₫", dto.getTotalAmount()),
                        textFont);
                qrNote.setAlignment(Element.ALIGN_CENTER);
                document.add(qrNote);

            } catch (Exception e) {
                document.add(new Paragraph("(Không thể tải mã QR)", textFont));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo PDF: " + e.getMessage(), e);
        }
    }

    @Transactional
    public BillDTO createBillFromOrder(Long orderId, String paymentMethodStr) {
        // 1. Lấy Order (Đơn hàng) và đảm bảo nó đang PENDING
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new RuntimeException("Đơn hàng phải ở trạng thái PENDING để thanh toán.");
        }

        // 2. Tạo Bill (Hóa đơn)
        Bill newBill = new Bill();
        PaymentMethod paymentMethod = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
        newBill.setOrder(order);
        newBill.setPaymentMethod(paymentMethod);
        newBill.setTotalAmount(order.getTotalAmount());
        newBill.setIssuedAt(LocalDateTime.now());
        newBill.setPaymentStatus(PaymentStatus.PENDING); // Bắt đầu ở PENDING

        Bill savedBill = billRepo.save(newBill);
        order.setStatus(OrderStatus.PAID); // Đổi Order thành PAID
        orderRepo.save(order); // Lưu Order đã đổi trạng thái

        // 4. Đổi trạng thái Bàn thành FREE
        TableEntity table = order.getTable();
        if (table != null) {
            table.setStatus(Status.FREE);
            tableService.update(table.getId(), table); // Cập nhật Table
        }
        BillDTO billDTO = convertToDTO(savedBill);
        Long tableId = (order.getTable() != null) ? order.getTable().getId() : null;

        if (paymentMethod != PaymentMethod.CASH) {
            try {

                if (tableId != null) {
                    messagingTemplate.convertAndSend("/topic/customer-bill/" + tableId, billDTO);
                    System.out.println("📤 Đã push Bill có URL VNPAY cho khách hàng bàn: " + tableId);
                }
                System.out.println("📤 Đã push Bill có QR VNPAY cho khách hàng bàn: " + tableId);
            } catch (Exception e) {
                System.err.println("⚠️ Gửi WebSocket thất bại: " + e.getMessage());
            }
        }

        // 5. Trả về DTO
        return convertToDTO(savedBill);
    }

}
