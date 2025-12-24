package com.tranthanhsang.example304.controllers;

import com.tranthanhsang.example304.entity.Product;
import com.tranthanhsang.example304.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.tranthanhsang.example304.security.services.ProductService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    // ✅ Trả về danh sách Product
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<Page<Product>> getAll(@RequestParam(defaultValue = "0") int page) {
        Page<Product> products = productService.getAllPaged(page); // 👈 gọi service xử lý phân trang
        return ResponseEntity.ok(products);
    }

    // Thêm sản phẩm
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> create(@RequestBody Product product) {
        try {
            Product createdProduct = productService.create(product);
            return ResponseEntity.ok(createdProduct);
        } catch (RuntimeException e) {
            // ✅ SỬA: Bắt RuntimeException (lỗi ràng buộc: tên trùng, thiếu ảnh)
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage()); // Gửi thông báo lỗi cụ thể

            // Trả về mã lỗi 409 CONFLICT (Lỗi nghiệp vụ)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    // Cập nhật sản phẩm
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Product product) {
        try {
            Product updatedProduct = productService.update(id, product);
            return ResponseEntity.ok(updatedProduct);
        } catch (RuntimeException e) {
            // ✅ SỬA: Bắt RuntimeException (lỗi ràng buộc: tên trùng)
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());

            // Trả về mã lỗi 409 CONFLICT
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    // Xóa sản phẩm
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            productService.delete(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            // ✅ SỬA: Bắt RuntimeException (lỗi khóa ngoại/không tìm thấy)
            System.err.println("❌ Lỗi xóa sản phẩm: " + e.getMessage());

            // Trả về mã lỗi 409 CONFLICT (Lỗi khóa ngoại)
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // Tìm kiếm sản phẩm theo từ khóa với các bộ lọc và sắp xếp
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN','ROLE_USER')")
    // THAY ĐỔI 1: Xóa sortBy, order và thêm Pageable
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) { // Spring Boot tự động tạo đối tượng này từ các tham số URL

        // THAY ĐỔI 2: Xóa bỏ việc kiểm tra keyword là bắt buộc
        // Giờ đây chúng ta có thể lọc mà không cần từ khóa

        // THAY ĐỔI 3: Gọi hàm service mới với pageable
        Page<Product> results = productService.searchWithFilter(keyword, categoryName, minPrice, maxPrice, pageable);

        return ResponseEntity.ok(results);
    }

    // Lấy sản phẩm theo ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN')")
    public Product getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    // Lấy sản phẩm theo danh mục
    @GetMapping("/category")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN','ROLE_USER')")
    public ResponseEntity<Page<Product>> getByCategoryName(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productService.getByCategoryName(name, pageable);
        return ResponseEntity.ok(products);
    }

    // Lọc sản phẩm theo nhiều tiêu chí
    @GetMapping("/filter")
    @PreAuthorize("hasAnyRole('ROLE_EMPLOYEE', 'ROLE_ADMIN','ROLE_USER')")
    public List<Product> filterProducts(
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "price") String sortBy,
            @RequestParam(defaultValue = "asc") String order) {
        return productService.filterProducts(categoryName, minPrice, maxPrice, sortBy, order);
    }
}
