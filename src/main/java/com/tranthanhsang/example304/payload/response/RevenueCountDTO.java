package com.tranthanhsang.example304.payload.response;

import java.math.BigDecimal;

public class RevenueCountDTO {
    private String period; // Ngày, Tuần, Tháng
    private Long orderCount; // Số lượng đơn hàng
    private BigDecimal totalRevenue; // Tổng doanh thu

    public RevenueCountDTO(String period, Long orderCount, BigDecimal totalRevenue) {
        this.period = period;
        this.orderCount = orderCount;
        this.totalRevenue = totalRevenue;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public Long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

}
