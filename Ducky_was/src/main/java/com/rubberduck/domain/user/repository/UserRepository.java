package com.rubberduck.domain.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rubberduck.domain.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByLoginId(String loginId);

    Optional<User> findByEmail(String email);

    Optional<User> findByLoginId(String loginId);
}
