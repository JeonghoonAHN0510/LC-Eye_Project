package lceye.model.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProjectMapper {

    /**
     * 회사번호로 프로젝트파일명 조회
     *
     * @param cno 회사번호
     * @return json 파일명 리스트
     * @author 민성호
     */
    @Select("select pjfilename from project p inner join member m on p.mno = m.mno where m.cno = #{cno}")
    List<String> findByCno(int cno);

}// interface end
