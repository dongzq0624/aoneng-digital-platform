package com.aoneng.rag.domain.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aoneng.rag.domain.kb.po.KbBasePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface KbBaseMapper extends BaseMapper<KbBasePO> {

    /**
     * Knowledge base overview list with the active document count.
     */
    List<Map<String, Object>> selectBaseOverviews();

    Map<String, Object> selectBaseOverview(@Param("id") long id);

    int insertReturningId(KbBasePO base);

    int updateBase(@Param("id") long id,
                   @Param("name") String name,
                   @Param("description") String description,
                   @Param("visibility") String visibility,
                   @Param("deptId") Long deptId);

    int softDelete(@Param("id") long id);

    List<Long> selectAllowedDeptIds(@Param("kbId") long kbId);

    int countActiveDepts(@Param("deptIds") List<Long> deptIds);

    int deleteAllowedDepts(@Param("kbId") long kbId);
    int deleteAllowedDeptsByDeptIds(@Param("deptIds") List<Long> deptIds);

    int insertAllowedDept(@Param("kbId") long kbId, @Param("deptId") long deptId);
}
