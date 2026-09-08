package com.cjv.sistemacjv.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String LLAVE_SECRETA =
            "CjvSistemaFotografiaEscolar2026LlaveSecretaMuyLargaParaFirmarTokens";

    private static final long DURACION_TOKEN_MS = 8 * 60 * 60 * 1000; // 8 horas

    private SecretKey obtenerLlave() {
        return Keys.hmacShaKeyFor(LLAVE_SECRETA.getBytes());
    }

    public String generarToken(String nombreUsuario, String rol) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + DURACION_TOKEN_MS);

        return Jwts.builder()
                .subject(nombreUsuario)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(obtenerLlave())
                .compact();
    }

    public String obtenerNombreUsuario(String token) {
        return extraerClaims(token).getSubject();
    }

    public String obtenerRol(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    public boolean esTokenValido(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(obtenerLlave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}