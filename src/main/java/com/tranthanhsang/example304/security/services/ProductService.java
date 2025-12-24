package com.tranthanhsang.example304.security.services;

import com.tranthanhsang.example304.entity.Product;
import com.tranthanhsang.example304.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private FileUploadService fileUploadService;

    // Lấy tất cả Product
    public Page<Product> getAllPaged(int page) {
        Pageable pageable = PageRequest.of(page, 12, Sort.by("id").descending()); // 👈 10 sản phẩm mỗi trang
        return productRepository.findAll(pageable);
    }

    private void validateProductFields(Product product, boolean isCreation) {
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new RuntimeException("❌ Lỗi: Tên sản phẩm không được để trống.");
        }
        if (product.getDescription() == null || product.getDescription().trim().isEmpty()) {
            throw new RuntimeException("❌ Lỗi: Mô tả sản phẩm không được để trống.");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("❌ Lỗi: Giá sản phẩm không hợp lệ.");
        }
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            throw new RuntimeException("❌ Lỗi: Phải chọn danh mục cho sản phẩm.");
        }
        if (isCreation && (product.getImageUrl() == null || product.getImageUrl().trim().isEmpty())) {
            // Ràng buộc ảnh chỉ áp dụng khi THÊM MỚI
            throw new RuntimeException("❌ Lỗi: Sản phẩm mới phải có ảnh đại diện.");
        }
    }

    // Tạo mới Product
    public Product create(Product product) {
        // ✅ BƯỚC 1: RÀNG BUỘC KHÔNG ĐƯỢC TRỐNG
        validateProductFields(product, true);

        // ✅ BƯỚC 2: RÀNG BUỘC TÊN DUY NHẤT
        productRepository.findByNameIgnoreCase(product.getName())
                .ifPresent(p -> {
                    throw new RuntimeException("❌ Lỗi: Tên sản phẩm '" + product.getName() + "' đã tồn tại.");
                });

        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    // Cập nhật Product
    public Product update(Long id, Product product) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm có id: " + id));

        // ✅ BƯỚC 1: RÀNG BUỘC KHÔNG ĐƯỢC TRỐNG
        validateProductFields(product, false); // Không kiểm tra ảnh (Image) ở đây

        // ✅ BƯỚC 2: RÀNG BUỘC TÊN DUY NHẤT (Bỏ qua ID hiện tại)
        productRepository.findByNameIgnoreCaseAndIdNot(product.getName(), id)
                .ifPresent(p -> {
                    throw new RuntimeException(
                            "❌ Lỗi: Tên sản phẩm '" + product.getName() + "' đã được sử dụng bởi sản phẩm khác.");
                });
        // ✅ Nếu ảnh mới khác ảnh cũ → xóa ảnh cũ
        if (product.getImageUrl() != null &&
                existing.getImageUrl() != null &&
                !product.getImageUrl().equals(existing.getImageUrl())) {
            fileUploadService.deleteImage(existing.getImageUrl());
        }

        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        existing.setCategory(product.getCategory());
        existing.setImageUrl(product.getImageUrl());
        existing.setStockQuantity(product.getStockQuantity());
        existing.setIsActive(product.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());

        return productRepository.save(existing);
    }

    // Xóa Product
    public void delete(Long id) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm có id: " + id));

        String imagePath = existing.getImageUrl();

        try {
            // 🟢 Thử xóa sản phẩm trong DB
            productRepository.deleteById(id);
            productRepository.flush(); // ép Hibernate thực thi SQL ngay

            // 🟢 Xóa thành công → giờ mới xóa ảnh
            if (imagePath != null) {
                fileUploadService.deleteImage(imagePath);
            }

        } catch (DataIntegrityViolationException ex) {
            // 🔴 Lỗi khóa ngoại → Không xóa được → KHÔNG được xóa ảnh
            throw new RuntimeException("Sản phẩm đang được sử dụng, không thể xóa.");
        }
    }

    // Lấy sản phẩm theo ID
    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
    }

    // Lấy sản phẩm theo danh mục
    public Page<Product> getByCategoryName(String name, Pageable pageable) {
        return productRepository.findByCategory_NameIgnoreCase(name, pageable);
    }

    // Lọc sản phẩm theo nhiều tiêu chí
    public List<Product> filterProducts(String categoryName, BigDecimal minPrice, BigDecimal maxPrice,
            String sortBy, String order) {
        Specification<Product> spec = Specification.where(null);

        // Lọc theo tên danh mục (không phân biệt hoa thường)
        if (categoryName != null && !categoryName.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("category").get("name")),
                    categoryName.toLowerCase()));
        }

        // Lọc theo giá tối thiểu
        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }

        // Lọc theo giá tối đa
        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }

        // Xử lý sắp xếp
        Sort sort;
        try {
            sort = order.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
        } catch (Exception e) {
            sort = Sort.by("id").ascending(); // fallback nếu sortBy sai
        }

        return productRepository.findAll(spec, sort);
    }

    public Page<Product> searchWithFilter(String keyword, String categoryName,
            BigDecimal minPrice, BigDecimal maxPrice,
            Pageable pageable) {

        Specification<Product> spec = Specification.where(null);

        // Phần logic Specification để lọc dữ liệu của bạn giữ nguyên, nó đã rất tốt.
        if (keyword != null && !keyword.isBlank()) {
            String likeKeyword = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), likeKeyword));
        }

        if (categoryName != null && !categoryName.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("category").get("name")),
                    categoryName.toLowerCase()));
        }

        if (minPrice != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice));
        }

        if (maxPrice != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice));
        }

        return productRepository.findAll(spec, pageable);
    }

}
