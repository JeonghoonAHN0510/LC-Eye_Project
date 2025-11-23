package lceye.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lceye.service.ExchangeService;
import lceye.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController @RequestMapping("/api/inout")
@RequiredArgsConstructor
public class ExchangeController {

    /**
     * 서비스 불러오기
     */
    private final TranslationService translationService;
    private final ExchangeService exchangeService;

    //@PostMapping("/auto") // localhost:8080/api/inout/auto
    //public ResponseEntity<?> test(){
    //    String text = "경유";
    //    return ResponseEntity.ok(translationService.Translate(text));
    //}// func end

    /**
     * 투입물·산출물 저장/수정
     *
     * @param request 요청한 회원의 HTTP 요청 정보
     * @param map 투입물·산출물 정보
     * @return boolean
     * @author 민성호
     */
    @PostMapping
    public ResponseEntity<?> saveIOInfo(HttpServletRequest request,
                                        @RequestBody Map<String,Object> map){
        HttpSession session = request.getSession(false);
        String token = null;
        if (session != null){
            token = (String) session.getAttribute("loginMember");
        } // if end
        System.out.println("map : " + map);
        if (token != null){
            return ResponseEntity.ok(exchangeService.saveInfo(map,token));
        } else {
            return ResponseEntity.status(401).body(null);
        } // if end
    }// func end

    /**
     * 투입물·산출물 프로세스와 자동매칭
     *
     * @param request 요청한 회원의 HTTP 요청 정보
     * @param inputList 클라이언트가 입력한 투입물·산출물
     * @return Map<String,Object>
     * @author 민성호
     */
    @PostMapping("/auto")
    public ResponseEntity<?> matchIO(HttpServletRequest request,
                                     @RequestBody List<String> inputList){
        HttpSession session = request.getSession(false);
        String token = null;
        if (session != null){
            token = (String) session.getAttribute("loginMember");
        } // if end
        if (token != null){
            Map<String, Set<String>> pjnoMap = exchangeService.autoMatchPjno(inputList,token);
            if (pjnoMap != null && !pjnoMap.isEmpty()){
                return ResponseEntity.ok(pjnoMap);
            } else {
                return ResponseEntity.status(404).body(null);
            } // if end
        } else {
            return ResponseEntity.status(404).body(null);
        } // if end
    }// func end

    /**
     * 프로젝트 초기화
     *
     * @param request 요청한 회원의 HTTP 요청 정보
     * @param pjno 삭제할 프로젝트번호
     * @return boolean
     * @author 민성호
     */
    @DeleteMapping
    public ResponseEntity<?> clearIOInfo(HttpServletRequest request,
                                         @RequestParam int pjno){
        HttpSession session = request.getSession(false);
        String token = null;
        if (session != null){
            token = (String) session.getAttribute("loginMember");
        } // if end
        if (token != null){
            return ResponseEntity.ok(exchangeService.clearIOInfo(token,pjno));
        } else {
            return ResponseEntity.status(404).body(null);
        } // if end
    }// func end

    /**
     * [IO-03] 투입물·산출물 정보 조회
     * <p>
     * 프로젝트 번호를 매개변수로 받아 투입물·산출물 json파일에서 exchanges 정보를 반환한다.
     * @param pjno - 조회할 프로젝트 번호
     * @return List<Map<Strig,Object>>
     * @author OngTK
     */
    @GetMapping
    public ResponseEntity<?> readIOInfo(@RequestParam int pjno){
        Map<String, Object> result = exchangeService.readIOInfo(pjno);
        if(result == null){
            return ResponseEntity.status(403).body("잘못된 요청입니다.");
        }
        return ResponseEntity.ok(result);
    } // func end
} // class end