package com.tranthanhsang.example304.controllers;

import com.tranthanhsang.example304.entity.Category;
import com.tranthanhsang.example304.payload.response.CategoryDTO;
import com.tranthanhsang.example304.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.tranthanhsang.example304.security.services.CategoryService;

import java.util.HashMap;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")

public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    // ✅ Trả về danh sách
    @GetMapping
    // Phương pháp chính xác nhất, sử dụng nhiều tham số cho hasAnyRole:
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_EMPLOYEE')")
    public ResponseEntity<Page<CategoryDTO>> getCategories(
            // THAY ĐỔI 2: Thêm Pageable và thiết lập sắp xếp mặc định
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        // THAY ĐỔI 3: Gọi service với tham số pageable
        Page<CategoryDTO> categoryPage = categoryService.getAllCategories(pageable);
        return ResponseEntity.ok(categoryPage);
    }

    // thêm danh mục
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> create(@RequestBody Category category) {
        try {
            Category createdCategory = categoryService.create(category);
            return ResponseEntity.ok(createdCategory);
        } catch (RuntimeException e) {
            // ✅ SỬA: ĐÓNG GÓI LỖI SERVICE VÀ TRẢ VỀ JSON
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());

            // Trả về mã lỗi 409 CONFLICT (cho lỗi nghiệp vụ: tên trùng, tên rỗng)
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    // cập nhật danh mục
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Category category) {
        try {
            Category updatedCategory = categoryService.update(id, category);
            return ResponseEntity.ok(updatedCategory);
        } catch (RuntimeException e) {
            // ✅ SỬA: ĐÓNG GÓI LỖI SERVICE VÀ TRẢ VỀ JSON
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());

            // Trả về mã lỗi 409 CONFLICT
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    // xóa danh mục
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            categoryService.delete(id);
            // ✅ Trả về 200 OK (Không có body)
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            // ✅ Bắt RuntimeException (Lỗi: Vẫn còn sản phẩm sử dụng danh mục)
            System.err.println("❌ Lỗi xóa danh mục: " + e.getMessage());

            // Trả về 409 CONFLICT: Vì đây là lỗi ràng buộc nghiệp vụ (Khóa ngoại)
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // lấy danh mục theo ID
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<CategoryDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getById(id));
    }

    // lấy danh mục theo Parent ID
    @GetMapping("/parent/{parentId}")
    public ResponseEntity<List<CategoryDTO>> getByParentId(@PathVariable Long parentId) {
        return ResponseEntity.ok(categoryService.getByParentId(parentId));
    }
}
