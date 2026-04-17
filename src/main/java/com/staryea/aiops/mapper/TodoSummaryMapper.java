package com.staryea.aiops.mapper;

import com.staryea.aiops.model.TodoSummary;
import org.apache.ibatis.annotations.Param;

public interface TodoSummaryMapper {

    TodoSummary getByUserId(@Param("userId") String userId);

    int upsert(TodoSummary summary);
}
