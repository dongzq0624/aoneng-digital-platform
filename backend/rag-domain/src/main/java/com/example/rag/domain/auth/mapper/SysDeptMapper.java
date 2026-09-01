package com.example.rag.domain.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.domain.auth.po.SysDeptPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysDeptMapper extends BaseMapper<SysDeptPO> {

    /**
     * Department rows enriched with the user count and child count, alias matching the
     * legacy repository output.
     */
    List<Map<String, Object>> selectDepartmentOverviews();

    String selectAncestors(@Param("id") long id);

    /**
     * Whether {@code candidate} is a descendant of {@code ancestor}.
     */
    int isDescendant(@Param("candidate") long candidate, @Param("ancestor") long ancestor);

    int insertReturningId(SysDeptPO dept);

    int updateDept(@Param("id") long id,
                   @Param("name") String name,
                   @Param("parentId") long parentId,
                   @Param("ancestors") String ancestors,
                   @Param("sort") Integer sort,
                   @Param("status") Integer status);

    int hardDelete(@Param("id") long id);

    int countActiveById(@Param("id") long id);
}
