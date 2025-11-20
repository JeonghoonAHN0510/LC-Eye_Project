package lceye.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lceye.model.dto.MemberDto;

@Service
public class JwtService {
    private final String secret = "LCEyeSecretKeyIsVeryDifficultSecretKey";
    // 비밀키를 기반으로 SHA-256 알고리즘 적용
    private final Key secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

    /**
     * 해당 토큰이 유효한지 확인하는 메소드
     * @param token 유효성을 확인할 토큰
     * @return 유효(true)/무효(false)
     * @author AhnJH
     */
    public boolean validateToken(String token){
        try {
            Jwts.parser()
                    // 1. 검증을 위한 비밀키 대입
                    .setSigningKey(secretKey)
                    .build()
                    // 2. 검증할 토큰을 대입하여 검증 실행
                    .parseClaimsJws(token);
            // 3. 예외가 발생하지 않으면, 유효
            return true;
        } catch (JwtException e) {
            // 4. 예외가 발생하면, 무효
            return false;
        } // try-catch end
    } // func end

    /**
     * 특정한 토큰의 Claims를 추출하기 위한 메소드
     * @param token Claims를 추출할 토큰
     * @return 추출한 Claims
     * @author AhnJH
     */
    public Claims getClaimsFromToken(String token){
        return Jwts.parser()
                // 1. 검증을 위한 비밀키 대입
                .setSigningKey(secretKey)
                .build()
                // 2. 검증에 성공한 토큰의 Claims 반환
                .parseClaimsJws(token).getBody();
    } // func end

    // Claims의 특정값 추출
    public int getMnoFromClaims(String token){
        return getClaimsFromToken(token).get("loginMno", Integer.class);
    } // func end
    public String getRoleFromClaims(String token){
        return getClaimsFromToken(token).get("loginRole", String.class);
    } // func end
    public int getCnoFromClaims(String token){
        return getClaimsFromToken(token).get("loginCno", Integer.class);
    } // func end
} // class end