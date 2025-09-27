package com.rideswift.auth_service.service;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;

@Component
public class JwtService {

    private final RSAKey rsaKey;

    public JwtService(RSAKey rsaKey) {
        this.rsaKey = rsaKey;
    }

    public String generateAccessToken(UUID userId, List<String> roles) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(15, ChronoUnit.MINUTES)))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJWT.sign(new RSASSASigner(rsaKey.toPrivateKey()));
        return signedJWT.serialize();
    }

    public JWTClaimsSet validateToken(String token) throws ParseException, JOSEException, BadJWTException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        RSASSAVerifier verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
        if (!signedJWT.verify(verifier))
            throw new BadJWTException("Invalid signature");
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        if (claims.getExpirationTime().before(new Date()))
            throw new BadJWTException("Token expired");
        return claims;
    }

    public String extractJti(String token) throws ParseException, JOSEException, BadJWTException {
        JWTClaimsSet claims = validateToken(token);
        return claims.getJWTID();
    }

    public long getExpirySeconds(String token) throws ParseException, JOSEException, BadJWTException {
        JWTClaimsSet claims = validateToken(token);
        long exp = claims.getExpirationTime().getTime();
        long now = System.currentTimeMillis();
        return Math.max(0, (exp - now) / 1000);
    }
}
