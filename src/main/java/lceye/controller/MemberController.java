package lceye.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lceye.model.dto.MemberDto;
import lceye.service.MemberService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {
    private final MemberService memberService;

    /**
     * [MB-01] 로그인(login)
     * <p>
     * [아이디, 비밀번호]를 받아서 DB에 일치하는 회원이 존재한다면, Redis와 Cookie에 로그인 정보가 담긴 JWT 토큰을 저장한다.
     * <p>
     * 테스트 : {"mid":"admin", "mpwd":"1234"}
     * @param memberDto 아이디, 비밀번호가 담긴 Dto
     * @param response 요청한 회원의 HTTP 정보
     * @return 로그인을 성공한 회원의 Dto
     * @author AhnJH
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody MemberDto memberDto, HttpServletResponse response){
        // 1. 입력받은 값을 Service에 전달하여 로그인 진행
        MemberDto result = memberService.login(memberDto);
        // 2. 로그인을 성공했다면
        if (result != null){
            // 3. 성공한 회원의 토큰을 쿠키에 저장 : loginMember
            Cookie cookie = new Cookie("loginMember", result.getToken());
            // 4. 쿠키 노출 및 탈취 방지
            cookie.setHttpOnly(true);
            cookie.setSecure(false);        // https에서만 true를 사용할 수 있어서 false로 설정
            cookie.setPath("/");
            cookie.setMaxAge(3600);         // 토큰 및 Redis의 유효시간이 1시간
            // 5. 생성한 쿠키를 클라이언트에게 반환
            response.addCookie(cookie);
            result.setToken(null);
        } // if end
        // 6. 최종적으로 결과 반환
        return ResponseEntity.ok(result);
    } // func end

    /**
     * [MB-02] 로그아웃(logout)
     * <p>
     * 요청한 회원의 로그인 정보를 Redis와 Cookie에서 제거한다.
     * @param token 요청한 회원의 token 정보
     * @param response 요청한 회원의 HTTP 정보
     * @return 로그아웃 성공 여부 - boolean
     * @author AhnJH
     */
    @GetMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "loginMember", required = false) String token,
                                    HttpServletResponse response){
        // 1. 삭제 기능을 할 임시 쿠키 생성
        Cookie cookie = new Cookie("loginMember", null);
        // 2. 즉시 삭제 쿠키 설정
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        // 3. 임시 쿠키를 클라이언트에게 반환
        response.addCookie(cookie);
        // 4. Redis에 저장된 쿠키 삭제 진행 후 반환
        return ResponseEntity.ok(memberService.logout(token));
    } // func end

    /**
     * [MB-03] 로그인 정보 확인(getInfo)
     * <p>
     * 요청한 회원의 [로그인 여부, 권한, 회원명, 회사명]을 반환한다.
     * @param token 요청한 회원의 token 정보
     * @return 요청한 회원의 정보
     * @author AhnJH
     */
    @GetMapping("/getinfo")
    public ResponseEntity<?> getInfo(@CookieValue(value = "loginMember", required = false) String token){
        // 1. 쿠키 내 토큰이 존재하고
        if (token != null){
            Map<String, Object> infoByToken = memberService.getInfo(token);
            // 2. 해당 토큰이 유효하여 정보 추출에 성공했다면
            if (infoByToken != null){
                // 3. 로그인여부를 표시하고
                infoByToken.put("isAuth", true);
                // 4. HTTP 200으로 반환
                return ResponseEntity.status(200).body(infoByToken);
            } // if end
        } // if end
        // 5. 비로그인 상태라면
        Map<String, Object> result = new HashMap<>();          // infoByToken이 null일 수 있기에, 재정의 해주기
        // 6. 비로그인을 표시하고
        result.put("isAuth", false);
        // 7. HTTP 403으로 반환
        return ResponseEntity.status(403).body(result);
    } // func end
} // class end