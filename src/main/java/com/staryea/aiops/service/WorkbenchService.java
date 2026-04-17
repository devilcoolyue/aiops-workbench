package com.staryea.aiops.service;

import com.staryea.aiops.mapper.FocusEventMapper;
import com.staryea.aiops.mapper.TodoTaskMapper;
import com.staryea.aiops.mapper.TodoSummaryMapper;
import com.staryea.aiops.model.FocusEvent;
import com.staryea.aiops.model.TodoTask;
import com.staryea.aiops.model.TodoSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WorkbenchService {

    private final TodoTaskMapper todoTaskMapper;
    private final FocusEventMapper focusEventMapper;
    private final TodoSummaryMapper todoSummaryMapper;

    public WorkbenchService(TodoTaskMapper todoTaskMapper,
                            FocusEventMapper focusEventMapper,
                            TodoSummaryMapper todoSummaryMapper) {
        this.todoTaskMapper = todoTaskMapper;
        this.focusEventMapper = focusEventMapper;
        this.todoSummaryMapper = todoSummaryMapper;
    }

    // ========== 待办任务 ==========

    public TodoTask createTodoTask(TodoTask task) {
        todoTaskMapper.insert(task);
        return task;
    }

    public TodoTask getTodoTask(Long id) {
        return todoTaskMapper.getById(id);
    }

    public List<TodoTask> listTodoTasks(String userId) {
        return todoTaskMapper.listByUserId(userId);
    }

    public List<TodoTask> listTodoTasks(String userId, String systemCode) {
        if (systemCode == null || systemCode.isEmpty()) {
            return todoTaskMapper.listByUserId(userId);
        }
        return todoTaskMapper.listByUserIdAndSystemCode(userId, systemCode);
    }

    public void updateTodoTask(TodoTask task) {
        todoTaskMapper.update(task);
    }

    public void deleteTodoTask(Long id) {
        todoTaskMapper.deleteById(id);
    }

    // ========== 待办总结 ==========

    public TodoSummary getTodoSummary(String userId) {
        return todoSummaryMapper.getByUserId(userId);
    }

    public TodoSummary upsertTodoSummary(TodoSummary summary) {
        todoSummaryMapper.upsert(summary);
        return todoSummaryMapper.getByUserId(summary.getUserId());
    }

    // ========== 重点关注事项 ==========

    public FocusEvent createFocusEvent(FocusEvent event) {
        focusEventMapper.insert(event);
        return event;
    }

    public FocusEvent getFocusEvent(Long id) {
        return focusEventMapper.getById(id);
    }

    public List<FocusEvent> listFocusEvents(String userId) {
        return focusEventMapper.listByUserId(userId);
    }

    public List<FocusEvent> listFocusEvents(String userId, String systemCode) {
        if (systemCode == null || systemCode.isEmpty()) {
            return focusEventMapper.listByUserId(userId);
        }
        return focusEventMapper.listByUserIdAndSystemCode(userId, systemCode);
    }

    public void updateFocusEvent(FocusEvent event) {
        focusEventMapper.update(event);
    }

    public void deleteFocusEvent(Long id) {
        focusEventMapper.deleteById(id);
    }
}
