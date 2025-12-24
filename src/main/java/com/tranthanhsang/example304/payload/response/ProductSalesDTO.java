package com.tranthanhsang.example304.payload.response;

import java.math.BigDecimal;

public class ProductSalesDTO {
    private Long productId;
    private String productName; // Hoặc Category Name
    private Long totalQuantity; // Dùng cho số lượng bán
    private BigDecimal totalRevenue; // Dùng cho doanh thu

    // Constructor 1: Dùng cho JPQL Thống kê SỐ LƯỢNG (Top Selling)
    // Sẽ gán totalQuantity
    public ProductSalesDTO(Long productId, String productName, Long totalQuantity) {
        this.productId = productId;
        this.productName = productName;
        this.totalQuantity = totalQuantity;
        this.totalRevenue = BigDecimal.ZERO; // Khởi tạo Zero cho doanh thu
    }

    // ✅ CONSTRUCTOR 2 MỚI: Dùng cho JPQL Thống kê DOANH THU theo Danh mục
    // Sẽ gán totalRevenue (BigDecimal)
    public ProductSalesDTO(Long categoryId, String categoryName, BigDecimal totalRevenue) {
        this.productId = categoryId; // Dùng trường này để lưu Category ID
        this.productName = categoryName; // Dùng trường này để lưu Category Name
        this.totalRevenue = totalRevenue;
        this.totalQuantity = 0L; // Khởi tạo Zero cho số lượng
    }

    // --- Getters và Setters Đã Cập Nhật ---

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Long totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    // ✅ THÊM GETTER/SETTER CHO totalRevenue
    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }
}