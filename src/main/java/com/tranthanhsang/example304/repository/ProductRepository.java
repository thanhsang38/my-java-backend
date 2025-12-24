package com.tranthanhsang.example304.repository;

import com.tranthanhsang.example304.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    List<Product> findByNameContainingIgnoreCase(String keyword);

    Page<Product> findByCategory_NameIgnoreCase(String name, Pageable pageable);

    Optional<Product> findByNameIgnoreCase(String name);

    // ✅ 2. Tìm sản phẩm theo tên VÀ ID KHÁC (dùng cho hàm update)
    Optional<Product> findByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCategoryId(Long categoryId);
}
