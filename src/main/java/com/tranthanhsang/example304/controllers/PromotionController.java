package com.tranthanhsang.example304.controllers;

import com.tranthanhsang.example304.entity.Promotion;
import com.tranthanhsang.example304.entity.Product;
import com.tranthanhsang.example304.repository.PromotionRepository;
import com.tranthanhsang.example304.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.tranthanhsang.example304.security.services.PromotionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/promotions")

public class PromotionController {

    @Autowired
    private PromotionService promotionService;

    // ✅ Trả về danh sách Promotion
    @GetMapping
    // Phương pháp chính xác nhất, sử dụng nhiều tham số cho hasAnyRole:
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE')")
    public ResponseEntity<Page<Promotion>> getAll(
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Promotion> promotions = promotionService.getAll(pageable);
        return ResponseEntity.ok(promotions);
    }

    // Thêm khuyến mãi
    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ResponseEntity<?> create(@RequestBody Promotion promotion) {
        try {
            return ResponseEntity.ok(promotionService.create(promotion));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating promotion: " + e.getMessage());
        }
    }

    // Cập nhật khuyến mãi
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Promotion promotion) {
        try {
            return ResponseEntity.ok(promotionService.update(id, promotion));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error updating promotion: " + e.getMessage());
        }
    }

    // Xóa khuyến mãi
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            promotionService.delete(id);
            return ResponseEntity.ok("Promotion deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting promotion: " + e.getMessage());
        }
    }

    // Lấy khuyến mãi đang hoạt động
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE')")
    public ResponseEntity<List<Promotion>> getActivePromotions() {
        return ResponseEntity.ok(promotionService.getActivePromotions());
    }
}
