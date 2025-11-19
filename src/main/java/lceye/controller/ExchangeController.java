package lceye.controller;

import jakarta.servlet.http.HttpSession;
import lceye.model.dto.MemberDto;
import lceye.service.ExchangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/inout")
@RequiredArgsConstructor
public class ExchangeController {

    /**
     * 서비스 불러오기
     */
    private final ExchangeService exchangeService;

    /**
     * 투입물·산출물 저장/수정
     *
     * @param map 투입물·산출물 정보
     * @return boolean
     * @author 민성호
     */
    @PostMapping
    public ResponseEntity<?> saveIOInfo(@RequestBody Map<String,Object> map,
                                        HttpSession session){
        // 1. 세션에서 로그인 정보 꺼내기
        MemberDto memberDto = (MemberDto) session.getAttribute("loginMember");

        return ResponseEntity.ok(exchangeService.saveInfo(map,memberDto));
    }// func end

    /**
     * 투입물·산출물 프로세스와 자동매칭
     *
     * @param token 로그인한 회원의 토큰
     * @param inputList 클라이언트가 입력한 투입물·산출물
     * @return Map<String,Object>
     * @author 민성호
     */
    @PostMapping("/auto")
    public ResponseEntity<?> matchIO(HttpSession session,
                                     @RequestBody List<String> inputList){
        // 1. 세션에서 로그인 정보 꺼내기
        MemberDto memberDto = (MemberDto) session.getAttribute("loginMember");

        Map<String,Object> pjnoMap = exchangeService.autoMatchPjno(inputList,memberDto);
        Map<String,Object> cnoMap = exchangeService.autoMatchCno(inputList,memberDto);
        if (pjnoMap != null && !pjnoMap.isEmpty()){
            return ResponseEntity.ok(pjnoMap);
        } else if (cnoMap != null && !cnoMap.isEmpty()) {
            return ResponseEntity.ok(cnoMap);
        }else {
            return ResponseEntity.ok(exchangeService.similarity(inputList));
        }// if end
    }// func end

    /**
     * 프로젝트 초기화
     *
     * @param pjno 삭제할 프로젝트번호
     * @return boolean
     * @author 민성호
     */
    @DeleteMapping
    public ResponseEntity<?> clearIOInfo(HttpSession session,
                                         @RequestParam int pjno){
        // 1. 세션에서 로그인 정보 꺼내기
        MemberDto memberDto = (MemberDto) session.getAttribute("loginMember");

        return ResponseEntity.ok(exchangeService.clearIOInfo(memberDto,pjno));
    }// func end

}// class end
