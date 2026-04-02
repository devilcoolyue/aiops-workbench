package com.staryea.aiops.service;

import com.staryea.aiops.mapper.FocusEventMapper;
import com.staryea.aiops.mapper.TodoTaskMapper;
import com.staryea.aiops.model.FocusEvent;
import com.staryea.aiops.model.TodoTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class WorkbenchService {

    private final TodoTaskMapper todoTaskMapper;
    private final FocusEventMapper focusEventMapper;

    public WorkbenchService(TodoTaskMapper todoTaskMapper, FocusEventMapper focusEventMapper) {
        this.todoTaskMapper = todoTaskMapper;
        this.focusEventMapper = focusEventMapper;
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
