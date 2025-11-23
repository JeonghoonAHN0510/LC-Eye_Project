package lceye.service;

import lceye.model.dto.CalculateResultDto;
import lceye.model.entity.ProjectEntity;
import lceye.model.entity.ProjectResultFileEntity;
import lceye.model.repository.ProjectRepository;
import lceye.model.repository.ProjectResultFileRepository;
import lombok.RequiredArgsConstructor;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class LCICalculateService {

    private final ProjectRepository projectRepository;
    private final RedissonClient redissonClient;
    private final JwtService jwtService;
    private final FileService fileService;
    private final ProjectResultFileRepository projectResultFileRepository;

    /**
     * [LCI-01] LCI 계산하기
     */
    public boolean calcLCI(int pjno, String token) {
        // [1] Lock Key 정의
        String lockKey = "lock:project:exchange:" + pjno;
        System.out.println("lockKey = " + lockKey);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // [2] 락 획득 시도 : 최대 10초 대기 + 5초 후 자동 해제
            boolean available = lock.tryLock(10, 5, TimeUnit.SECONDS);
            // [3] 획득에 실패했다면, 메소드 종료
            if (!available) return false;
            // [4] 락 획득에 성공했다면, 비지니스 로직 시작
            // [5] pjno를 기반으로 projectExchange json 파일명을 찾음
            ProjectEntity projectEntity = projectRepository.getReferenceById(pjno);
            String projectExchangeFileName = projectEntity.getPjfilename();
            // [6] projectExchangeFileName 의 json 파일을 찾음 + 읽음 // json 파일 읽힘 확인
            Map<String, Object> readFileData = fileService.readFile("exchange", projectExchangeFileName);
            // [7] processExchanges 를 리스트로 꺼냄
            List<Map<String, Object>> processExchanges = (List<Map<String, Object>>) readFileData.get("exchanges");
            double pjamount = (double) readFileData.get("pjamount");

            // [8] flow 결과를 누적할 Map (key: fno_isInput_uno)
            Map<String, CalculateResultDto> resultMap = new LinkedHashMap<>();
            // [8.1] 프로세스 JSON 캐시
            Map<String, Map<String, Object>> processCache = new HashMap<>();
            // [8.2] 산출물 exchange(완제품) 저장용 변수 (puuid == null && isInput == false)
            Map<String, Object> productExchange = new HashMap<>();

            // [9] exchange 를 1개씩 꺼내서 처리
            for (Map<String, Object> processExchange : processExchanges) {
                // [9.1] exchnage(process)의 uuid와 input 여부 꺼내기
                Object puuidObj = processExchange.get("puuid");
                Boolean isInput = (Boolean) processExchange.get("isInput");

                if (puuidObj != null) { // exchange = 프로세스
                    // [10.1] puuis String으로 파싱, 해당 프로세스의 투입/산출양 확인
                    String searchPuuid = String.valueOf(puuidObj);
                    // 타입 확인 후 처리
                    Object value = processExchange.get("pjeamount");
                    double pjeAmount = 0.0;
                    if (value instanceof Double) {
                        pjeAmount = (Double) value;
                    }
                    else if (value instanceof Number) {
                        // Integer, Long 등 Number 하위 타입 처리
                        pjeAmount = ((Number) value).doubleValue();
                    }
                    else if (value instanceof String) {
                        pjeAmount = Double.parseDouble((String) value);
                    }
                    else {
                        throw new IllegalArgumentException("pjeamount cannot be converted to Double: " + value);
                    }

                    // [10.2] puuid 로 JSON 읽어오기 (※ porcess 캐쉬 처리!!>> 처리 속도 증가)
                    Map<String, Object> process = getProcessJsonFromCache(processCache, searchPuuid);
                    // [10.3] process 내부에서 exchanges(=flow 리스트) 가져오기
                    List<Map<String, Object>> flowExchanges =
                            (List<Map<String, Object>>) process.get("exchanges");
                    // [10.4] 각 flow에 대해 pjeAmount × amount(flow) 계산 후, resultMap에 누적
                    for (Map<String, Object> flow : flowExchanges) {
                        accumulateFlow(resultMap, flow, pjeAmount);
                    }
                } else {
                    // puuid가 null 이고, output인 경우 >> 대상제품
                    if (Boolean.FALSE.equals(isInput)) {
                        productExchange = processExchange;
                    }
                }
            }
            // [11] resultMap -> List<CalculateResultDto> 변환
            List<CalculateResultDto> results = new ArrayList<>(resultMap.values());
            System.out.println(results);

            // [11.1] 산출물 DTO 한 줄 추가 (productExchange가 존재할 때만)
            if (productExchange != null) {
                CalculateResultDto productDto = buildProductResultDto(projectEntity, readFileData, productExchange, pjamount);
                results.add(productDto);
            }

            // [12] 산출물 1 단위(예: 1 kg) 기준으로 환산
            // 대상제품 생산량이 500 이라도 최종적으로 result는 생산량 1에 대한 flow 값들로 표시되어야 함
            // 즉, flow를 sum 한 것을 생산량에 대해서 나누기
            if (pjamount != 0) {
                for (CalculateResultDto dto : results) {
                    dto.setAmount(dto.getAmount() / pjamount);
                }
            }

            // [13] 위의 자료들로 JSON으로 바꾸기 위한 최종 Map 구성
            Map<String, Object> resultJson = buildResultJson(projectEntity, readFileData, results);
            // [14] 파일명 만들기
            int cno = jwtService.getCnoFromClaims(token);
            String resultFileName = makeResultFileName(projectEntity, cno); // cno_pjno_result_yyyyMMdd_HHmm.json
            // [15] 파일 저장하기 ( type, 파일명 ,JSON으로 변환할 데이터)
            fileService.writeFile("result", resultFileName, resultJson);

            // [16] resultfile 매핑 테이블 결과 저장
            ProjectResultFileEntity projectResultFileEntity = ProjectResultFileEntity.builder()
                    .prfname(resultFileName)
                    .projectEntity(projectEntity)
                    .build();
            projectResultFileRepository.save(projectResultFileEntity);
            // [17] 결과 반환
            if (projectResultFileEntity.getPrfno() > 0) {
                return true;
            } else {
                return false;
            } // if end
        } catch (InterruptedException e){
            throw new RuntimeException("Lock 획득 중 에러 발생");
        } finally {
            // [] 최종적으로 락 해제
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            } // if end
        } // try-catch-finally end
    } // func end

    /**
     * [clacLCI-6.2] 연계
     * <p>
     * LCI 결과 시 매번 process 정보를 가져오는 것은 시간적으로, 물리적으로 비효율적
     * <p>
     * 한 번 읽은 process는 캐시화하여 재조회 시 속도를 증가
     *
     * @param processCache process 처리 이력에 대한 캐쉬
     * @param puuid        프로세스uuid
     * @return 저장되어 있는 process 정보
     * @author OngTK
     */
    @SuppressWarnings("unchecked")
    // 컴파일러가 발생시키는 “unchecked 경고”를 숨기기(무시하기)
    // 즉, 제네릭 타입이 명확하지 않은 상황에서 발생하는 경고를 없애는 어노테이션
    private Map<String, Object> getProcessJsonFromCache(
            Map<String, Map<String, Object>> processCache,
            String puuid
    ) {
        // [1] 캐쉬에 uuid가 이미 존재하면
        if (processCache.containsKey(puuid)) {
            // 캐쉬에서 해당 uuid의 process를 반환
            return processCache.get(puuid);
        }
        // [2] 캐쉬에 존재하지 않는다면
        // puuid에 해당하는 프로세스 JSON 조회
        Map<String, Object> process = fileService.searchProcessJson(puuid);
        // 프로세스 캐쉬에 해댱 uuid와 프로세스 정보를 Map 형태로 저장
        processCache.put(puuid, process);
        // 해당 프로세스를 반환
        return process;
    } // func end

    /**
     * [clac_LCI-6.4] flow 누적 계산용 및 Map처리 함수
     * <p>
     * 각 flow에 대해 pjeAmount × amount(flow) 계산 후, resultMap에 누적
     *
     * @author OngTK
     */
    private void accumulateFlow(
            Map<String, CalculateResultDto> resultMap,
            Map<String, Object> flow,
            double pjeAmount
    ) {
        // [1] flow 기본 정보 꺼내기
        int fno = ((Number) flow.get("fno")).intValue();
        String fuuid = (String) flow.get("fuuid");
        String fname = (String) flow.get("fname");

        double amountFlow = ((Number) flow.get("amount")).doubleValue();
        String uname = (String) flow.get("uname");
        int uno = ((Number) flow.get("uno")).intValue();
        boolean isInput = (boolean) flow.get("isInput");

        // [2] 프로젝트 기준 amount = pjeAmount × amount(flow)
        double scaledAmount = pjeAmount * amountFlow;

        // [3] 합산 key (fno + isInput + 단위)
        // 보통은 fno만 해도 되지만, 단위(uno)까지 포함
        String key = fno + "_" + isInput + "_" + uno;

        // [4] 이미 resultMap에 같은 key가 있으면 amount만 합산
        CalculateResultDto dto = resultMap.get(key);
        // [4.1] 해당 dto(flow)가 없다면
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
            // [4.2] key로 검색되는 dto가 있는 경우: 양만 더해주기
            dto.setAmount(dto.getAmount() + scaledAmount);
        }
    } // func end

    /**
     * [clacLCI-7.1] 대상제품에 대한 resultMap처리
     * <p>
     * 대상제품의 경우 puuid가 없는 특수한 대상
     * <p>
     * 다만 dto에 기본타입을 적용하여서 int에 null 처리가 불가하여 별도의 func을 통해 일부 항목에 대한 대체 처리를 위한 함수
     *
     * @param project             프로젝트 정보를 담은 엔티티
     * @param projectExchangeJson 프로젝트 입출력 JSON 파일
     * @param productExchange     fuuid가 없고 isInput이 null인 대상 = 생산 제품
     * @param pjamount            프로젝트 산출량 = 제품 생산량
     * @author OngTK
     */
    @SuppressWarnings("unchecked")
    private CalculateResultDto buildProductResultDto(
            ProjectEntity project,
            Map<String, Object> projectExchangeJson,
            Map<String, Object> productExchange,
            double pjamount
    ) {
        // [1] 프로젝트의 단위 정보
        int uno = ((Number) projectExchangeJson.get("uno")).intValue();
        String uname = (String) projectExchangeJson.get("uname");

        // [2] 산출물 exchange에 입력된 양 (ex: 750kg)
        Object value = productExchange.get("pjeamount");
        double productAmount = 0.0;
        // 안전한 타입 변환
        if (value instanceof Double) {
            productAmount = (Double) value;
        }
        else if (value instanceof Number) {
            productAmount = ((Number) value).doubleValue();
        }
        else if (value instanceof String) {
            productAmount = Double.parseDouble((String) value);
        }
        else {
            throw new IllegalArgumentException("pjeamount cannot be converted to Double: " + value);
        }
        // [3] 결과 반환에 사용될 dto
        CalculateResultDto dto = new CalculateResultDto();

        // DTO가 int fno 이라 null 을 못 받으므로 일단 0을 사용 (권장: Integer로 바꿔서 null 허용)
        dto.setFno(0);
        dto.setFuuid(null);
        // 산출물 이름은 프로젝트명으로
        dto.setFname(project.getPjname());  // 또는 (String) projectExchangeJson.get("pjname")
        dto.setUno(uno);
        dto.setUname(uname);
        dto.setInput(false);

        // [calcLCI 8]에서 나누기 될 것이므로 총량을 넣음
        dto.setAmount(pjamount);

        return dto;
    } // func end

    /**
     * [calcLCI-9] 결과 JSON 만들기
     *
     * @param project             프로젝트 정보를 담는 엔티티
     * @param projectExchangeJson 프로젝트 투입·산출물 정보 JSON
     * @param results             LCI 계산 결과에 해당하는 flow 리스트
     * @return Map<String, Object> JSON으로 바꾸기 직전의 최종 형태
     * @author OngTK
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildResultJson(
            ProjectEntity project,
            Map<String, Object> projectExchangeJson,
            List<CalculateResultDto> results
    ) {
        // [1] 결과를 반환하기 위한 Map 객체 생성
        Map<String, Object> root = new LinkedHashMap<>();
        // [2] create/update Date 표시를 위한 LocalDateTime
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String now = LocalDateTime.now().format(fmt);

        // [3] 프로젝트 기본정보: projectEntity + projectExchangeJson (필요한 만큼만)
        root.put("pjno", project.getPjno());
        root.put("pjname", project.getPjname());
        root.put("mno", project.getMno());

        // [4] pjamount, uno, uname, pjdesc 등은 projectExchange JSON에서 꺼냄
        root.put("pjamount", ((Number) projectExchangeJson.get("pjamount")).doubleValue());
        root.put("uno", ((Number) projectExchangeJson.get("uno")).intValue());
        root.put("uname", (String) projectExchangeJson.get("uname"));
        root.put("pjdesc", (String) projectExchangeJson.get("pjdesc"));

        // [5] 파일 생성일 수정일 기재
        root.put("createdate", now);
        root.put("updatedate", now);

        // [6] LCI 계산결과인 flow관련 results -> List<Map<String,Object>> 로 변환
        List<Map<String, Object>> resultList = new ArrayList<>();
        // [6.1] List<dto> 에서 dto를 하나씩 꺼내서 Map형태로 변경
        for (CalculateResultDto dto : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fno", dto.getFno());
            item.put("fuuid", dto.getFuuid());
            item.put("fname", dto.getFname());
            item.put("amount", dto.getAmount());
            item.put("uname", dto.getUname());
            item.put("uno", dto.getUno());
            item.put("isInput", dto.isInput());
            // [6.2] List<Map>에 삽입
            resultList.add(item);
        }
        // [7] 최종 결과 Map에 삽입
        root.put("results", resultList);
        // [8] 최종 결과 반환
        return root;
    } // func end

    /**
     * [calcLCI-10] 파일명 만들기
     *
     * @param project 프로젝트 정보를 담는 엔티티
     * @return filename 확장자(json)을 제외한 파일명(cno_pjno_result_yyyyMMdd_HHmm.json)
     * @author OngTK
     */
    private String makeResultFileName(ProjectEntity project, int cno) {
        int pjno = project.getPjno();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return cno + "_" + pjno + "_result_" + now;
    } // func end

    /**
     * [LCI-02] LCI 결과 조회하기
     *
     * @author OngTK
     */
    public Map<String, Object> readLCI(int pjno) {
        // [1] pjno로 project_resultfile 테이블에서 가장 최신의 레코드를 찾고, 파일명을 확인
        String fileName = projectResultFileRepository.returnFilename(pjno);
        if (fileName == null) return null;
        // [2] 파일명으로 파일 찾아오기
        Map<String, Object> file = fileService.readFile("result", fileName);
        // [3] results 항목만 가져오기
        List<Map<String, Object>> results = (List<Map<String, Object>>) file.get("results");
        // [4] result에서 input과 output을 구별
        List<Map<String, Object>> inputList = new ArrayList<>();
        List<Map<String, Object>> outputList = new ArrayList<>();

        for (Map<String, Object> map : results) {
            if ((boolean) map.get("isInput")) {
                inputList.add(map);
            } else {
                outputList.add(map);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("inputList", inputList);
        result.put("outputList", outputList);

        return result;

    } // func end

    /**
     * [LCI-03] LCI 결과 존재 여부 확인
     *
     * @author OngTK
     */
    public String checkLCI(int pjno) {
        // [1] pjno로 project_resultfile 테이블에서 가장 최신의 레코드를 찾고, 파일명을 확인
        String fileName = projectResultFileRepository.returnFilename(pjno);
        // [2] 조회 되는게 없으면 null 반환
        if (fileName.isBlank()) return null;
        // [3] 조회된 String을 반환
        return fileName;
    } // func end

} // class end