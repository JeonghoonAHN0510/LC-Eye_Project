package lceye.service;

import com.deepl.api.DeepLClient;
import com.deepl.api.TextResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service @Transactional
public class TranslationService {

    @Value("${service.deepl.api-key}")
    private String deeplApiKey;

    /**
     * DeepL API 호출하여 클라이언트가 입력한 문자열을
     * 영어로 번역 요청 로직
     *
     * @param text 번역요청 문자열
     * @return String 번역된 문자열
     * @author 민성호
     */
    public String Translate(String text){
        DeepLClient client = new DeepLClient(deeplApiKey);
        try{
            TextResult result = client.translateText(text,"ko","en-US");
            return result.getText();
        }catch (Exception e){
            e.printStackTrace();
            return "API 호출 중 오류 발생: " + e.getMessage();
        }// try end
    }// func end

    /**
     * 클라이언트가 입력한 투입물·산출물 번역해서 반환
     *
     * @param clientInput 클라이언트가 입력한 투입물·산출물
     * @return List<String>
     * @author 민성호
     */
    public List<String> TransInput(List<String> clientInput){
        List<String> transInput = clientInput.stream().map(this::Translate).toList();
        return transInput;
    }// func end

} // class end