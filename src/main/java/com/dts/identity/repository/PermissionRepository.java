package com.dts.identity.repository;

import com.dts.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    @Query("SELECT p FROM Permission p WHERE p.name = :name AND p.deletedAt IS NULL")
    Optional<Permission> findByName(@Param("name") String name);

    @Query("SELECT p FROM Permission p WHERE p.deletedAt IS NULL")
    List<Permission> findAllActive();

    @Query("SELECT p FROM Permission p WHERE p.resource = :resource AND p.deletedAt IS NULL")
    List<Permission> findByResource(@Param("resource") String resource);
}
