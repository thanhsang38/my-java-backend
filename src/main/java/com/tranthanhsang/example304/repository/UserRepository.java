package com.tranthanhsang.example304.repository;

import com.tranthanhsang.example304.model.ERole;
import com.tranthanhsang.example304.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    // THAY ĐỔI: Thêm Pageable và trả về Page<User>
    Page<User> findByRole(@Param("roleName") ERole roleName, Pageable pageable);

    Optional<User> findByPhone(String phone);

    // Tìm người dùng theo số điện thoại VÀ ID KHÁC (cho hàm update)
    Optional<User> findByPhoneAndIdNot(String phone, Long id);
}
