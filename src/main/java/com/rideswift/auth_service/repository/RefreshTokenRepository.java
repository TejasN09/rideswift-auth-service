package com.rideswift.auth_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.rideswift.auth_service.model.RefreshToken;
import com.rideswift.auth_service.model.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByTokenId(UUID tokenId);

    List<RefreshToken> findByUserAndRevokedFalse(User user);
}
