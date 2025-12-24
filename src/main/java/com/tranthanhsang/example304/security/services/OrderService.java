package com.tranthanhsang.example304.security.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.tranthanhsang.example304.entity.Order;
import com.tranthanhsang.example304.entity.OrderItem;
import com.tranthanhsang.example304.entity.Product;
import com.tranthanhsang.example304.entity.Promotion;
import com.tranthanhsang.example304.entity.enums.OrderStatus;
import com.tranthanhsang.example304.model.User;
import com.tranthanhsang.example304.payload.response.EmployeeSalesDTO;
import com.tranthanhsang.example304.payload.response.OrderDTO;
import com.tranthanhsang.example304.payload.response.OrderItemDTO;
import com.tranthanhsang.example304.payload.response.ProductSalesDTO;
import com.tranthanhsang.example304.payload.response.RevenueCountDTO;
import com.tranthanhsang.example304.repository.OrderRepository;
import com.tranthanhsang.example304.repository.PromotionRepository;
import com.tranthanhsang.example304.repository.TableRepository;
import com.tranthanhsang.example304.repository.UserRepository;
import com.tranthanhsang.example304.repository.ProductRepository;
import com.tranthanhsang.example304.entity.TableEntity;

import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import com.tranthanhsang.example304.entity.enums.Status;
import org.springframework.data.domain.Pageable;
import java.util.Set;

import java.util.Map;

@Service
public class OrderService {
    @Autowired
    private OrderRepository orderRepo;
    @Autowired
    private TableRepository tableRepo;
    @Autowired
    private PromotionRepository promoRepo;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ProductRepository productRepo;
    @Autowired
    private UserRepository userRepository;

    // Lấy tất cả đơn hàng
    public Page<OrderDTO> getAll(int page) {
        // 1. Tạo Pageable, 10 đơn hàng mỗi trang, sắp xếp theo ID giảm dần
        Pageable pageable = PageRequest.of(page, 12, Sort.by("id").descending());

        // 2. Lấy dữ liệu đã phân trang từ repository
        Page<Order> orderPage = orderRepo.findAll(pageable);

        // 3. Chuyển đổi Page<Order> thành Page<OrderDTO> (dùng hàm convertToDTO của
        // bạn)
        return orderPage.map(this::convertToDTO);
    }

    // Trong file OrderService.java

    // ... các hàm và @Autowired khác ...

    // Tạo mới đơn hàng (đã sửa lại theo cấu trúc của hàm update)
    public OrderDTO create(Order order) {

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // ✅ Gán khuyến mãi nếu có
        Promotion promo = null;
        if (order.getPromotion() != null && order.getPromotion().getId() != null) {
            promo = promoRepo.findByIdWithProducts(order.getPromotion().getId()).orElse(null);
            order.setPromotion(promo);
        } else {
            order.setPromotion(null);
        }

        // ✅ Xử lý danh sách món ăn (items) theo cấu trúc giống hàm update
        if (order.getItems() != null) {
            List<OrderItem> processedItems = new ArrayList<>();

            for (OrderItem incomingItem : order.getItems()) {
                // 1. Kiểm tra xem item có thông tin product không
                if (incomingItem.getProduct() == null || incomingItem.getProduct().getId() == null) {
                    throw new RuntimeException("❌ Thiếu product trong OrderItem");
                }

                // 2. Lấy thông tin Product đầy đủ từ DB
                Product product = productRepo.findById(incomingItem.getProduct().getId())
                        .orElseThrow(() -> new RuntimeException(
                                "Không tìm thấy sản phẩm với ID: " + incomingItem.getProduct().getId()));

                // 3. Thiết lập các thuộc tính cho item
                incomingItem.setProduct(product);
                incomingItem.setOrder(order); // Gán item vào đơn hàng chính
                incomingItem
                        .setSubtotal(incomingItem.getPrice().multiply(BigDecimal.valueOf(incomingItem.getQuantity())));
                incomingItem.setCreatedAt(LocalDateTime.now());
                incomingItem.setUpdatedAt(LocalDateTime.now());

                processedItems.add(incomingItem);
            }

            // 4. Cập nhật lại danh sách items của đơn hàng
            order.getItems().clear();
            order.getItems().addAll(processedItems);
        }
        if (order.getTable() != null && order.getTable().getId() != null) {
            TableEntity table = tableRepo.findById(order.getTable().getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy bàn"));

            // Cập nhật trạng thái bàn thành Đang dùng
            table.setStatus(Status.OCCUPIED);
            tableRepo.save(table);
        }
        // ✅ Tính tổng tiền gốc
        BigDecimal total = BigDecimal.ZERO;
        if (order.getItems() != null) {
            total = order.getItems().stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // ✅ Áp dụng khuyến mãi nếu hợp lệ và có món phù hợp
        // (Toàn bộ logic này được giữ lại từ code gốc của bạn)
        final Promotion finalPromo = promo;
        if (finalPromo != null && Boolean.TRUE.equals(finalPromo.getIsActive())) {
            LocalDate today = LocalDate.now();
            boolean isValidDate = (finalPromo.getStartDate() == null || !today.isBefore(finalPromo.getStartDate())) &&
                    (finalPromo.getEndDate() == null || !today.isAfter(finalPromo.getEndDate()));

            if (isValidDate) {
                BigDecimal discountBase = order.getItems().stream()
                        .filter(item -> item.getProduct() != null &&
                                finalPromo.getProducts().stream()
                                        .anyMatch(p -> p.getId().equals(item.getProduct().getId())))
                        .map(OrderItem::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal discountedTotal = total;

                if (discountBase.compareTo(BigDecimal.ZERO) > 0) {
                    if (finalPromo.getDiscountPercentage() != null &&
                            finalPromo.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal discountValue = discountBase
                                .multiply(finalPromo.getDiscountPercentage().divide(new BigDecimal("100")));
                        discountedTotal = total.subtract(discountValue);
                    } else if (finalPromo.getDiscountAmount() != null &&
                            finalPromo.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                        discountedTotal = total.subtract(finalPromo.getDiscountAmount());
                    }
                }

                if (discountedTotal.compareTo(BigDecimal.ZERO) < 0) {
                    discountedTotal = BigDecimal.ZERO;
                }
                order.setTotalAmount(discountedTotal);

            } else {
                order.setTotalAmount(total); // Khuyến mãi hết hạn
            }
        } else {
            order.setTotalAmount(total); // Không có khuyến mãi
        }

        // ✅ Lưu đơn hàng và trả về DTO
        Order savedOrder = orderRepo.save(order);
        OrderDTO dto = convertToDTO(savedOrder);

        // ✅ Sau khi lưu, gửi thông báo realtime cho khách hàng tại bàn đó
        if (dto.getTableId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/order-updates/" + dto.getTableId(),
                    dto);
        }

        return dto;
    }

    public OrderDTO update(Long id, Order order) {
        Order existing = orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        existing.setTable(order.getTable());

        if (order.getEmployee() != null && order.getEmployee().getId() != null) {
            User employee = userRepository.findById(order.getEmployee().getId())
                    .orElseThrow(() -> new RuntimeException("❌ Không tìm thấy nhân viên"));
            existing.setEmployee(employee);
        } else {
            existing.setEmployee(null);
        }

        existing.setStatus(order.getStatus());
        existing.setNotes(order.getNotes());
        existing.setUpdatedAt(LocalDateTime.now());

        // ✅ Gán khuyến mãi nếu có
        Promotion promo = null;
        if (order.getPromotion() != null && order.getPromotion().getId() != null) {
            promo = promoRepo.findById(order.getPromotion().getId()).orElse(null);
            existing.setPromotion(promo);
        } else {
            existing.setPromotion(null);
        }

        // ✅ Xử lý danh sách món ăn
        if (order.getItems() != null) {

            // Map các items CŨ theo ID
            Map<Long, OrderItem> oldItemsById = existing.getItems().stream()
                    .filter(i -> i.getId() != null)
                    .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

            Set<Long> incomingItemIds = order.getItems().stream()
                    .map(OrderItem::getId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            // A. XÓA CÁC ITEMS CŨ ĐÃ BỊ LOẠI BỎ KHỎI FORM
            // Dùng removeIf để xóa an toàn
            existing.getItems().removeIf(item -> item.getId() != null && !incomingItemIds.contains(item.getId()));

            // B. DUYỆT VÀ GỘP ITEMS MỚI/CẬP NHẬT
            for (OrderItem incoming : order.getItems()) {
                if (incoming.getProduct() == null || incoming.getProduct().getId() == null) {
                    throw new RuntimeException("❌ Thiếu product trong OrderItem");
                }
                Product product = productRepo.findById(incoming.getProduct().getId())
                        .orElseThrow(() -> new RuntimeException(
                                "Không tìm thấy sản phẩm với ID: " + incoming.getProduct().getId()));

                if (incoming.getId() != null && oldItemsById.containsKey(incoming.getId())) {
                    // Trường hợp 1: ITEM ĐÃ CÓ (ID tồn tại) -> CẬP NHẬT
                    OrderItem itemToUpdate = oldItemsById.get(incoming.getId());

                    // === LÔ GIC CẬP NHẬT BẢO TỒN ENTITY ===
                    itemToUpdate.setQuantity(incoming.getQuantity());
                    itemToUpdate.setPrice(incoming.getPrice());
                    itemToUpdate.setSubtotal(incoming.getPrice().multiply(BigDecimal.valueOf(incoming.getQuantity())));
                    itemToUpdate.setUpdatedAt(LocalDateTime.now());

                } else {
                    // Trường hợp 2: ITEM HOÀN TOÀN MỚI -> THÊM VÀO EXISTING
                    OrderItem newItem = incoming;
                    newItem.setProduct(product);
                    newItem.setOrder(existing);
                    newItem.setSubtotal(incoming.getPrice().multiply(BigDecimal.valueOf(incoming.getQuantity())));
                    newItem.setCreatedAt(LocalDateTime.now());
                    newItem.setUpdatedAt(LocalDateTime.now());

                    existing.getItems().add(newItem);
                }
            }
        }

        // ✅ Tính tổng tiền gốc
        BigDecimal total = existing.getItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ✅ Áp dụng khuyến mãi nếu hợp lệ và có món phù hợp
        final Promotion finalPromo = promo;
        if (finalPromo != null && Boolean.TRUE.equals(finalPromo.getIsActive())) {
            LocalDate today = LocalDate.now();
            boolean isValidDate = (finalPromo.getStartDate() == null || !today.isBefore(finalPromo.getStartDate())) &&
                    (finalPromo.getEndDate() == null || !today.isAfter(finalPromo.getEndDate()));

            if (isValidDate) {
                BigDecimal discountBase = existing.getItems().stream()
                        .filter(item -> item.getProduct() != null &&
                                finalPromo.getProducts().stream()
                                        .anyMatch(p -> p.getId().equals(item.getProduct().getId())))
                        .map(OrderItem::getSubtotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal discountedTotal = total;

                if (discountBase.compareTo(BigDecimal.ZERO) > 0) {
                    if (finalPromo.getDiscountPercentage() != null &&
                            finalPromo.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal discountValue = discountBase
                                .multiply(finalPromo.getDiscountPercentage())
                                .divide(new BigDecimal("100"));
                        discountedTotal = total.subtract(discountValue);
                    } else if (finalPromo.getDiscountAmount() != null &&
                            finalPromo.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                        discountedTotal = total.subtract(finalPromo.getDiscountAmount());
                    }

                    if (discountedTotal.compareTo(BigDecimal.ZERO) < 0) {
                        discountedTotal = BigDecimal.ZERO;
                    }

                    existing.setTotalAmount(discountedTotal);
                } else {
                    existing.setTotalAmount(total); // ❌ Không có món nào phù hợp → không giảm
                }
            } else {
                existing.setTotalAmount(total); // ❌ Khuyến mãi hết hạn
            }
        } else {
            existing.setTotalAmount(total); // ❌ Không có khuyến mãi
        }

        Order savedOrder = orderRepo.save(existing);
        OrderDTO dto = convertToDTO(savedOrder);

        // ✅ Sau khi cập nhật, cũng gửi realtime cho khách hàng tại bàn đó
        if (dto.getTableId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/order-updates/" + dto.getTableId(),
                    dto);
        }

        return dto;
    }

    // Xóa đơn hàng
    public void delete(Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));

        // Xóa đơn hàng, JPA sẽ tự động xóa luôn các OrderItem nhờ cascade
        orderRepo.delete(order);
    }

    // Chuyển đổi Order entity sang OrderDTO
    public OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setStatus(order.getStatus().name());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setNotes(order.getNotes());
        dto.setCreatedAt(order.getCreatedAt());

        dto.setTotalQuantity(order.getItems().stream()
                .mapToInt(OrderItem::getQuantity)
                .sum());

        // Bàn
        if (order.getTable() != null) {
            dto.setTableId(order.getTable().getId());

            // ✅ Fetch lại từ DB để lấy số bàn đầy đủ
            TableEntity fullTable = tableRepo.findById(order.getTable().getId()).orElse(null);
            if (fullTable != null) {
                dto.setTableNumber(fullTable.getNumber());
            }
        }

        // Nhân viên
        if (order.getEmployee() != null) {
            dto.setEmployeeId(order.getEmployee().getId());
            dto.setEmployeeName(order.getEmployee().getFullName());
        }

        // Khuyến mãi (có thể null)
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

            // ✅ Fetch lại từ DB để lấy tên sản phẩm đầy đủ
            Product fullProduct = productRepo.findById(item.getProduct().getId()).orElse(null);
            if (fullProduct != null) {
                itemDTO.setProductName(fullProduct.getName());
            }
            itemDTO.setId(item.getId());
            itemDTO.setProductId(item.getProduct().getId());
            itemDTO.setImageUrl(fullProduct.getImageUrl()); // ✅ gán ảnh vào DTO
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

    // Lấy đơn hàng theo trạng thái
    public List<OrderDTO> getOrdersByStatus(OrderStatus status) {
        List<Order> orders = orderRepo.findByStatus(status);
        return orders.stream().map(this::convertToDTO).toList();
    }

    // Lấy đơn hàng theo ID bàn
    public List<OrderDTO> getOrdersByTable(Long tableId) {
        List<Order> orders = orderRepo.findByTableId(tableId);

        return orders.stream().map(order -> {
            OrderDTO dto = new OrderDTO();
            dto.setId(order.getId());
            dto.setStatus(order.getStatus().name());
            dto.setTotalAmount(order.getTotalAmount());
            dto.setNotes(order.getNotes());
            dto.setTotalQuantity(order.getItems().stream()
                    .mapToInt(OrderItem::getQuantity)
                    .sum());

            // Bàn
            dto.setTableId(order.getTable().getId());
            dto.setTableNumber(order.getTable().getNumber());

            // Nhân viên
            if (order.getEmployee() != null) {
                dto.setEmployeeId(order.getEmployee().getId());
                dto.setEmployeeName(order.getEmployee().getFullName());
            }

            // Khuyến mãi
            if (order.getPromotion() != null) {
                dto.setPromotionId(order.getPromotion().getId());
                dto.setPromotionCode(order.getPromotion().getName());
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

    // Lấy đơn hàng theo ID
    public OrderDTO getById(Long id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng với ID: " + id));

        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setStatus(order.getStatus().name());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setNotes(order.getNotes());
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

        // Khuyến mãi
        if (order.getPromotion() != null) {
            dto.setPromotionId(order.getPromotion().getId());
            dto.setPromotionCode(order.getPromotion().getName());
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
    }

    public OrderDTO getActiveOrderForEdit(Long tableId) {
        // 1. Tìm đơn hàng đầu tiên có trạng thái PENDING tại bàn này
        // (Sử dụng hàm mới khai báo trong OrderRepository)
        Order order = orderRepo.findFirstByTableIdAndStatus(tableId, OrderStatus.PENDING)
                .orElseThrow(
                        () -> new RuntimeException("Không tìm thấy đơn hàng ĐANG MỞ (PENDING) tại bàn " + tableId));

        // 2. Chuyển đổi sang DTO và trả về
        return convertToDTO(order);
    }

    public List<ProductSalesDTO> getTopSellingProducts(int limit) {
        // 1. Tạo Pageable với giới hạn (Limit)
        // Pageable ở đây chỉ dùng để giới hạn số lượng trả về (Limit)
        Pageable pageable = PageRequest.of(0, limit);

        // 2. Gọi hàm Repository với truy vấn JPQL (đã có @Query)
        return orderRepo.findTopSellingProducts(pageable);
    }

    public List<RevenueCountDTO> getDailyRevenueAndOrderCount(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return orderRepo.findDailyPaidOrderStats(startDate);
    }

    public List<ProductSalesDTO> getRevenueByCategoryStats() {
        // Gọi hàm Repository mới với truy vấn JPQL
        return orderRepo.findRevenueByCategoryStats();
    }

    public List<EmployeeSalesDTO> getTopSellingEmployees(int days) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        return orderRepo.findTopSellingEmployees(startDate);
    }
}
