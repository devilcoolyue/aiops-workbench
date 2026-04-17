package com.staryea.aiops.controller;

import com.staryea.aiops.model.FocusEvent;
import com.staryea.aiops.model.Result;
import com.staryea.aiops.model.TodoTask;
import com.staryea.aiops.model.TodoSummary;
import com.staryea.aiops.service.WorkbenchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    // ========== 待办任务 ==========

    @PostMapping("/todo-tasks")
    public Result<TodoTask> createTodoTask(@RequestBody TodoTask task) {
        return Result.success(workbenchService.createTodoTask(task));
    }

    @GetMapping("/todo-tasks")
    public Result<List<TodoTask>> listTodoTasks(@RequestParam(defaultValue = "default") String userId,
                                                @RequestParam(required = false) String systemCode) {
        return Result.success(workbenchService.listTodoTasks(userId, systemCode));
    }

    @GetMapping("/todo-tasks/{id}")
    public Result<TodoTask> getTodoTask(@PathVariable Long id) {
        return Result.success(workbenchService.getTodoTask(id));
    }

    @PutMapping("/todo-tasks/{id}")
    public Result<Void> updateTodoTask(@PathVariable Long id, @RequestBody TodoTask task) {
        task.setId(id);
        workbenchService.updateTodoTask(task);
        return Result.success(null);
    }

    @DeleteMapping("/todo-tasks/{id}")
    public Result<Void> deleteTodoTask(@PathVariable Long id) {
        workbenchService.deleteTodoTask(id);
        return Result.success(null);
    }

    // ========== 待办总结 ==========

    @GetMapping("/todo-summaries")
    public Result<TodoSummary> getTodoSummary(@RequestParam(defaultValue = "default") String userId) {
        return Result.success(workbenchService.getTodoSummary(userId));
    }

    @PostMapping("/todo-summaries")
    public Result<TodoSummary> upsertTodoSummary(@RequestBody TodoSummary summary) {
        return Result.success(workbenchService.upsertTodoSummary(summary));
    }

    // ========== 重点关注事项 ==========

    @PostMapping("/focus-events")
    public Result<FocusEvent> createFocusEvent(@RequestBody FocusEvent event) {
        return Result.success(workbenchService.createFocusEvent(event));
    }

    @GetMapping("/focus-events")
    public Result<List<FocusEvent>> listFocusEvents(@RequestParam(defaultValue = "default") String userId,
                                                    @RequestParam(required = false) String systemCode) {
        return Result.success(workbenchService.listFocusEvents(userId, systemCode));
    }

    @GetMapping("/focus-events/{id}")
    public Result<FocusEvent> getFocusEvent(@PathVariable Long id) {
        return Result.success(workbenchService.getFocusEvent(id));
    }

    @PutMapping("/focus-events/{id}")
    public Result<Void> updateFocusEvent(@PathVariable Long id, @RequestBody FocusEvent event) {
        event.setId(id);
        workbenchService.updateFocusEvent(event);
        return Result.success(null);
    }

    @DeleteMapping("/focus-events/{id}")
    public Result<Void> deleteFocusEvent(@PathVariable Long id) {
        workbenchService.deleteFocusEvent(id);
        return Result.success(null);
    }
}
