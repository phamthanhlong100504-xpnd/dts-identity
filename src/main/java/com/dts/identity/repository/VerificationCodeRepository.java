package com.dts.identity.repository;

import com.dts.identity.entity.VerificationCode;
import com.dts.identity.entity.VerificationCode.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    @Query("SELECT v FROM VerificationCode v WHERE v.userId = :userId AND v.type = :type AND v.usedAt IS NULL ORDER BY v.createdAt DESC LIMIT 1")
    Optional<VerificationCode> findLatestByUserIdAndType(@Param("userId") UUID userId, @Param("type") VerificationType type);

    @Modifying
    @Query("UPDATE VerificationCode v SET v.usedAt = CURRENT_TIMESTAMP WHERE v.userId = :userId AND v.type = :type AND v.usedAt IS NULL")
    int markAllUsedByUserIdAndType(@Param("userId") UUID userId, @Param("type") VerificationType type);
}
