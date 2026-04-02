package com.staryea.aiops.mapper;

import com.staryea.aiops.model.TodoTask;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TodoTaskMapper {

    int insert(TodoTask task);

    TodoTask getById(@Param("id") Long id);

    List<TodoTask> listByUserId(@Param("userId") String userId);

    List<TodoTask> listByUserIdAndSystemCode(@Param("userId") String userId, @Param("systemCode") String systemCode);

    int update(TodoTask task);

    int deleteById(@Param("id") Long id);
}
