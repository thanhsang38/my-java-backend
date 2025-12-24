package com.tranthanhsang.example304.repository;

import com.tranthanhsang.example304.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findById(Long id);

    List<Category> findByParentCategory_Id(Long parentId);

    Optional<Category> findByNameIgnoreCase(String name);

    // ✅ 2. Tìm danh mục theo tên VÀ ID KHÁC (dùng cho hàm update)
    Optional<Category> findByNameIgnoreCaseAndIdNot(String name, Long id);
}
