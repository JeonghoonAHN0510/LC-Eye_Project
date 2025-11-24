package lceye.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lceye.service.ExcelService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelService excelService;

    /**
     *  [Excel-01] 엑셀 다운로드
     * @param request session에서 token 확인
     * @param pjno 프로젝트 번호
     * @param response excel 다운로드를 위한 응답
     * @author OngTK
     */
    @GetMapping("/download")
    public ResponseEntity<?> downloadExcel(HttpServletRequest request, @RequestParam int pjno, HttpServletResponse response){
        // [1] Sesion에서 토큰 추출
        HttpSession session = request.getSession(false);
        String token = null;
        if (session != null){
            token = (String) session.getAttribute("loginMember");
        }
        // [2] service에 엑셀 출력 요청
        boolean result = excelService.downloadExcel(token, pjno, response);
        
        // [3] 결과 반환
        if(!result) return ResponseEntity.status(403).body("잘못된 요청입니다.");
        return ResponseEntity.ok(true);
    } // func end

} // class end
