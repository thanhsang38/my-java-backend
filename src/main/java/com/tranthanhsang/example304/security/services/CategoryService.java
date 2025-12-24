package com.tranthanhsang.example304.security.services;

import com.tranthanhsang.example304.entity.Category;
import com.tranthanhsang.example304.payload.response.CategoryDTO;
import com.tranthanhsang.example304.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.tranthanhsang.example304.repository.ProductRepository;

import java.time.LocalDateTime;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private FileUploadService fileUploadService;
    @Autowired
    private ProductRepository productRepository;

    public Page<CategoryDTO> getAllCategories(Pageable pageable) {
        // 1. Gọi repository để lấy dữ liệu dạng Page<Category>
        Page<Category> categoryPage = categoryRepository.findAll(pageable);

        // 2. Dùng hàm map của Page để chuyển đổi từng Category thành CategoryDTO
        return categoryPage.map(c -> new CategoryDTO(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getImageUrl(),
                c.getParentCategory() != null ? c.getParentCategory().getId() : null,
                c.getParentCategory() != null ? c.getParentCategory().getName() : null));
    }

    // Tạo mới danh mục
    public Category create(Category category) {
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new RuntimeException("❌ Lỗi: Tên danh mục không được để trống.");
        }
        categoryRepository.findByNameIgnoreCase(category.getName())
                .ifPresent(c -> {
                    throw new RuntimeException("❌ Lỗi: Tên danh mục '" + category.getName() + "' đã tồn tại.");
                });
        category.setName(category.getName().trim());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    // Cập nhật danh mục
    public Category update(Long id, Category category) {
        Category existing = categoryRepository.findById(id).orElseThrow();
        if (category.getName() == null || category.getName().trim().isEmpty()) {
            throw new RuntimeException("❌ Lỗi: Tên danh mục không được để trống.");
        }
        categoryRepository.findByNameIgnoreCaseAndIdNot(category.getName(), id)
                .ifPresent(c -> {
                    throw new RuntimeException(
                            "❌ Lỗi: Tên danh mục '" + category.getName() + "' đã được sử dụng bởi danh mục khác.");
                });
        String newImageUrl = category.getImageUrl();
        String oldImageUrl = existing.getImageUrl();

        // Nếu có ảnh mới, có ảnh cũ, và chúng khác nhau -> xóa ảnh cũ
        if (newImageUrl != null && oldImageUrl != null && !newImageUrl.equals(oldImageUrl)) {
            fileUploadService.deleteImage(oldImageUrl);
        }
        existing.setName(category.getName().trim());
        existing.setDescription(category.getDescription());
        existing.setImageUrl(category.getImageUrl());

        existing.setUpdatedAt(LocalDateTime.now());

        if (category.getParentCategory() != null && category.getParentCategory().getId() != null) {
            Category parent = categoryRepository.findById(category.getParentCategory().getId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found"));
            existing.setParentCategory(parent);
        } else {
            existing.setParentCategory(null);
        }

        return categoryRepository.save(existing);
    }

    // Xóa danh mục
    public void delete(Long id) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục có id: " + id));
        if (productRepository.existsByCategoryId(id)) {
            throw new RuntimeException("❌ Lỗi: Không thể xóa danh mục này. Vẫn còn sản phẩm đang sử dụng danh mục.");
        }
        // === THÊM LOGIC XÓA ẢNH ===
        if (existing.getImageUrl() != null && !existing.getImageUrl().isBlank()) {
            fileUploadService.deleteImage(existing.getImageUrl());
        }
        // =========================

        // Sau khi xóa ảnh mới xóa danh mục
        categoryRepository.delete(existing);
    }

    // Lấy danh mục theo ID
    public CategoryDTO getById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setImageUrl(category.getImageUrl());

        if (category.getParentCategory() != null) {
            dto.setParentId(category.getParentCategory().getId());
            dto.setParentName(category.getParentCategory().getName());
        }

        return dto;
    }

    // Lấy danh sách danh mục con theo ID danh mục cha
    public List<CategoryDTO> getByParentId(Long parentId) {
        List<Category> categories = categoryRepository.findByParentCategory_Id(parentId);

        return categories.stream().map(category -> {
            CategoryDTO dto = new CategoryDTO();
            dto.setId(category.getId());
            dto.setName(category.getName());
            dto.setDescription(category.getDescription());
            dto.setImageUrl(category.getImageUrl());

            if (category.getParentCategory() != null) {
                dto.setParentId(category.getParentCategory().getId());
                dto.setParentName(category.getParentCategory().getName());
            }

            return dto;
        }).toList();
    }
}
