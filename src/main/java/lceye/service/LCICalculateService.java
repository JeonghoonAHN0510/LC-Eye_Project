package lceye.service;

import lceye.model.dto.CalculateResultDto;
import lceye.model.entity.ProjectEntity;
import lceye.model.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class LCICalculateService {

    private final ProjectRepository projectRepository;
    private final FileService fileService;

    /**
     * [LCI-01] LCI 계산하기
     */
    public boolean calcLCI(int pjno){
        // todo OngTK calcLCI 구현중
        // [1] pjno를 기반으로 projectExchange json 파일명을 찾음
        ProjectEntity projectEntity = projectRepository.getReferenceById(pjno);
        String projectExchangeFileName = projectEntity.getPjfilename();
        // [2] projectExchangeFileName 의 json 파일을 찾음 + 읽음 // json 파일 읽힘 확인
        // todo OngTK 테스트 종료후 파일명의 _test 삭제 필요
        Map<String,Object> readFileData = fileService.readFile("exchange",projectExchangeFileName+"_test");
        // [3] processExchanges 를 리스트로 꺼냄
        List<Map<String,Object>> processExchanges = (List<Map<String, Object>>) readFileData.get("exchanges");
        double pjamount = (double) readFileData.get("pjamount");

        // [4] flow 결과를 누적할 Map (key: fno_isInput_uno)
        Map<String, CalculateResultDto> resultMap = new LinkedHashMap<>();
        // [4.1] 프로세스 JSON 캐시
        Map<String, Map<String, Object>> processCache = new HashMap<>();

        // [5] exchange 를 1개씩 꺼내서 처리
        for(Map<String, Object> processExchange: processExchanges){
            System.out.println(processExchange);
            if(processExchange.get("puuid") == null){ // 생산 제품
                // TODO OngTK 생산 제품에 대한 result 계산 처리 필요
                continue;
            } else{ // exchange = 프로세스
                // [6.1] exchange의 사용한 프로세스 정보인 puuid 를 추출
                String searchPuuid = String.valueOf(processExchange.get("puuid"));
                double pjeAmount = (double) processExchange.get("pjeamount");
                System.out.println(pjeAmount + "[6.1] 실행");
                // [6.2] puuid 로 JSON 읽어오기
                Map<String, Object> process = getProcessJsonFromCache(processCache, searchPuuid);
                System.out.println("[6.2] 실행");
                // [6.3] process 내부에서 exchanges(=flow 리스트) 가져오기
                List<Map<String, Object>> flowExchanges =
                        (List<Map<String, Object>>) process.get("exchanges");
                System.out.println("[6.3] 실행");
                // [6.4] 각 flow에 대해 pjeAmount × amount(flow) 계산 후, resultMap에 누적
                for (Map<String, Object> flow : flowExchanges) {
                    accumulateFlow(resultMap, flow, pjeAmount);
                }
                System.out.println("[6.4] 실행");
            }
        }
        // [7] resultMap -> List<CalculateResultDto> 변환
        List<CalculateResultDto> results = new ArrayList<>(resultMap.values());

        // [8] 결과 JSON 구조 만들기 & 파일 저장
        Map<String, Object> resultJson = buildResultJson(projectEntity, readFileData, results);
        String resultFileName = makeResultFileName(projectEntity); // cno_pjno_result_yyyyMMdd_HHmm.json
        fileService.writeFile("result", resultFileName, resultJson);
        return true;
    } // func end

    /**
     * [LCI-02] LCI 결과 개별 조회
     */
    public List<CalculateResultDto> readLCI(int pjno){
      // todo OngTK readLCI 구현 필요
        return null;
    } // func end

    /**
     * LCI 결과 시 매번 process 정보를 가져오는 것은 시간적으로, 물리적으로 비효율적
     * <p>
     * 한 번 읽은 process는 캐시화하여 재조회 시 속도를 증가
     * @author OngTK
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getProcessJsonFromCache(
            Map<String, Map<String, Object>> processCache,
            String puuid
    ) {
        if (processCache.containsKey(puuid)) {
            return processCache.get(puuid);
        }
        Map<String, Object> process = fileService.searchProcessJson(puuid);
        processCache.put(puuid, process);
        return process;
    } // func end

    /**
     * 각 flow에 대해 pjeAmount × amount(flow) 계산 후, resultMap에 누적
     * @author OngTK
     */
    // todo 확인
    private void accumulateFlow(
            Map<String, CalculateResultDto> resultMap,
            Map<String, Object> flow,
            double pjeAmount
    ) {
        // [1] flow 기본 정보 꺼내기
        int fno      = ((Number) flow.get("fno")).intValue();
        String fuuid = (String) flow.get("fuuid");
        String fname = (String) flow.get("fname");

        double amountFlow = ((Number) flow.get("amount")).doubleValue();
        String uname = (String) flow.get("uname");
        int uno      = ((Number) flow.get("uno")).intValue();
        boolean isInput = (boolean) flow.get("isInput");

        // [2] 프로젝트 기준 amount = pjeAmount × amount(flow)
        double scaledAmount = pjeAmount * amountFlow;

        // 3) 합산 key (fno + isInput + 단위)
        //    - 보통은 fno만 해도 되지만, 단위(uno)가 다를 수 있으니 포함하는 편이 안전
        String key = fno + "_" + isInput + "_" + uno;

        // 4) 이미 resultMap에 같은 key가 있으면 amount만 합산
        CalculateResultDto dto = resultMap.get(key);
        if (dto == null) {
            dto = new CalculateResultDto();
            dto.setFno(fno);
            dto.setFuuid(fuuid);
            dto.setFname(fname);
            dto.setUname(uname);
            dto.setUno(uno);
            dto.setInput(isInput);
            dto.setAmount(scaledAmount);  // 최초 값
            resultMap.put(key, dto);
        } else {
            // 있는 경우: 양만 더해주기
            dto.setAmount(dto.getAmount() + scaledAmount);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResultJson(
            ProjectEntity project,
            Map<String, Object> projectExchangeJson,
            List<CalculateResultDto> results
    ) {
        Map<String, Object> root = new LinkedHashMap<>();

        // 프로젝트 기본정보: projectEntity + projectExchangeJson (필요한 만큼만)
        root.put("pjno", project.getPjno());
        root.put("pjname", project.getPjname());
        root.put("mno", project.getMemberEntity().getMno());

        // pjamount, uno, uname, pjdesc 등은 projectExchange JSON에서 꺼내도 됨
        root.put("pjamount", ((Number) projectExchangeJson.get("pjamount")).doubleValue());
        root.put("uno", ((Number) projectExchangeJson.get("uno")).intValue());
        root.put("uname", (String) projectExchangeJson.get("uname"));
        root.put("pjdesc", (String) projectExchangeJson.get("pjdesc"));

        root.put("createdate", project.getCreatedate());
        root.put("updatedate", project.getUpdatedate());

        // results -> List<Map<String,Object>> 로 변환
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (CalculateResultDto dto : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fno", dto.getFno());
            item.put("fuuid", dto.getFuuid());
            item.put("fname", dto.getFname());
            item.put("amount", dto.getAmount());
            item.put("uname", dto.getUname());
            item.put("uno", dto.getUno());
            item.put("isInput", dto.isInput());
            resultList.add(item);
        }

        root.put("results", resultList);

        return root;
    }

    private String makeResultFileName(ProjectEntity project) {
        int cno = project.getMemberEntity().getCompanyEntity().getCno();
        int pjno = project.getPjno();

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return cno + "_" + pjno + "_result_" + now + ".json";
    }

} // class end
