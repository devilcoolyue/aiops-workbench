package com.staryea.aiops.mapper;

import com.staryea.aiops.model.FocusEvent;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FocusEventMapper {

    int insert(FocusEvent event);

    FocusEvent getById(@Param("id") Long id);

    List<FocusEvent> listByUserId(@Param("userId") String userId);

    List<FocusEvent> listByUserIdAndSystemCode(@Param("userId") String userId, @Param("systemCode") String systemCode);

    int update(FocusEvent event);

    int deleteById(@Param("id") Long id);
}
