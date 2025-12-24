package com.tranthanhsang.example304.payload.response;

public class EmployeeSalesDTO {
    private Long employeeId;
    private String employeeName;
    private String employeeImageUrl; // Thêm đường dẫn ảnh
    private Long totalOrders; // Tổng số đơn hàng đã hoàn tất

    // Constructor dùng cho JPQL Constructor Expression
    public EmployeeSalesDTO(Long employeeId, String employeeName, String employeeImageUrl, Long totalOrders) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeImageUrl = employeeImageUrl;
        this.totalOrders = totalOrders;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getEmployeeImageUrl() {
        return employeeImageUrl;
    }

    public Long getTotalOrders() {
        return totalOrders;
    }

    // ==========================================================
    // SETTERS
    // ==========================================================

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public void setEmployeeImageUrl(String employeeImageUrl) {
        this.employeeImageUrl = employeeImageUrl;
    }

    public void setTotalOrders(Long totalOrders) {
        this.totalOrders = totalOrders;
    }
}
