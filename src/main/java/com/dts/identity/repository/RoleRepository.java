package com.dts.identity.repository;

import com.dts.identity.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT r FROM Role r WHERE r.deletedAt IS NULL")
    List<Role> findAllActive();

    @Query("SELECT r FROM Role r WHERE r.name = :name AND r.deletedAt IS NULL")
    Optional<Role> findByNameActive(@Param("name") String name);

    @Query("SELECT r FROM Role r WHERE r.id = :id AND r.deletedAt IS NULL")
    Optional<Role> findByIdActive(@Param("id") UUID id);
}
