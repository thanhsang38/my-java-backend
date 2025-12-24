package com.tranthanhsang.example304.repository;

import com.tranthanhsang.example304.entity.Order;
import com.tranthanhsang.example304.entity.enums.OrderStatus;
import com.tranthanhsang.example304.payload.response.EmployeeSalesDTO;
import com.tranthanhsang.example304.payload.response.ProductSalesDTO;
import com.tranthanhsang.example304.payload.response.RevenueCountDTO;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
        List<Order> findByStatus(OrderStatus status);

        @Query("SELECT o FROM Order o WHERE o.table.id = :tableId")
        List<Order> findByTableId(@Param("tableId") Long tableId);

        Optional<Order> findFirstByTableIdAndStatus(Long tableId, OrderStatus status);

        @Query("SELECT NEW com.tranthanhsang.example304.payload.response.ProductSalesDTO(oi.product.id, oi.product.name, SUM(oi.quantity)) "
                        +
                        "FROM OrderItem oi " +
                        "WHERE oi.order.status = 'PAID' " + // Chỉ tính món đã được thanh toán
                        "GROUP BY oi.product.id, oi.product.name " +
                        "ORDER BY SUM(oi.quantity) DESC")
        List<ProductSalesDTO> findTopSellingProducts(Pageable pageable);

        @Query(value = "SELECT " +
        // Dùng DATE_FORMAT để buộc kết quả trả về là STRING, ví dụ: '2025-10-30'
                        "DATE_FORMAT(created_at, '%Y-%m-%d') AS period, " +
                        "COUNT(id) AS orderCount, " +
                        "SUM(total_amount) AS totalRevenue " +
                        "FROM orders " +
                        "WHERE status = 'PAID' AND created_at >= :startDate " +
                        "GROUP BY period " +
                        "ORDER BY period ASC", nativeQuery = true)
        List<RevenueCountDTO> findDailyPaidOrderStats(@Param("startDate") LocalDateTime startDate);

        @Query("SELECT NEW com.tranthanhsang.example304.payload.response.ProductSalesDTO(" +
        // Cần kiểm tra lại: Truy vấn này có thể ném lỗi nếu tên Entity không khớp.
                        "c.id, c.name, SUM(oi.subtotal)) " +
                        "FROM OrderItem oi JOIN oi.product p JOIN p.category c " +
                        "WHERE oi.order.status = 'PAID' " +
                        "GROUP BY c.id, c.name " +
                        "ORDER BY SUM(oi.subtotal) DESC")
        List<ProductSalesDTO> findRevenueByCategoryStats();

        @Query("SELECT NEW com.tranthanhsang.example304.payload.response.EmployeeSalesDTO(" +
                        "u.id, u.fullName, u.imageUrl, COUNT(o.id)) " +
                        "FROM Order o JOIN o.employee u " + // Join từ Order sang Employee (User)
                        "WHERE o.status = com.tranthanhsang.example304.entity.enums.OrderStatus.PAID " +
                        "  AND o.createdAt >= :startDate " + // Lọc theo ngày
                        "GROUP BY u.id, u.fullName, u.imageUrl " +
                        "ORDER BY COUNT(o.id) DESC")
        List<EmployeeSalesDTO> findTopSellingEmployees(@Param("startDate") LocalDateTime startDate);
}