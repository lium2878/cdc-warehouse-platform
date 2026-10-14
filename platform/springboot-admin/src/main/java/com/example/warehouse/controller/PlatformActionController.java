package com.example.warehouse.controller;

import com.example.warehouse.model.ActionAudit;
import com.example.warehouse.model.ActionRequest;
import com.example.warehouse.model.CommandResult;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.ActionAuditRepository;
import com.example.warehouse.service.PlatformActionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actions")
@Tag(name = "Platform Actions", description = "Run local warehouse operation actions")
public class PlatformActionController {
    private final PlatformActionService platformActionService;
    private final ActionAuditRepository actionAuditRepository;
    private final ObjectMapper objectMapper;

    public PlatformActionController(PlatformActionService platformActionService,
                                    ActionAuditRepository actionAuditRepository,
                                    ObjectMapper objectMapper) {
        this.platformActionService = platformActionService;
        this.actionAuditRepository = actionAuditRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{action}")
    @Operation(summary = "Run a local warehouse operation")
    public ResponseEntity<?> run(@PathVariable String action,
                                 @RequestBody(required = false) ActionRequest request,
                                 HttpServletRequest httpRequest,
                                 Principal principal) {
        long startedAt = System.currentTimeMillis();
        try {
            Object result = platformActionService.run(action, request, operator(principal));
            long durationMs = System.currentTimeMillis() - startedAt;
            if (result instanceof TaskExecution) {
                TaskExecution execution = (TaskExecution) result;
                actionAuditRepository.save(action, operator(principal), clientIp(httpRequest), toJson(request),
                        0, "submitted executionId=" + execution.getId() + " status=" + execution.getStatus(), durationMs);
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(execution);
            }
            CommandResult commandResult = (CommandResult) result;
            actionAuditRepository.save(action, operator(principal), clientIp(httpRequest), toJson(request),
                    commandResult.getExitCode(), commandResult.getOutput(), durationMs);
            return ResponseEntity.ok(commandResult);
        } catch (IllegalArgumentException ex) {
            auditFailure(action, request, httpRequest, principal, startedAt, ex);
            return ResponseEntity.badRequest().body(error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            auditFailure(action, request, httpRequest, principal, startedAt, ex);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ex.getMessage()));
        } catch (RuntimeException ex) {
            auditFailure(action, request, httpRequest, principal, startedAt, ex);
            throw ex;
        }
    }

    @GetMapping("/audits")
    @Operation(summary = "List latest operation audit records")
    public List<ActionAudit> audits() {
        return actionAuditRepository.findLatest(50);
    }

    private void auditFailure(String action, ActionRequest request, HttpServletRequest httpRequest,
                              Principal principal, long startedAt, RuntimeException ex) {
        actionAuditRepository.save(action, operator(principal), clientIp(httpRequest), toJson(request),
                -1, ex.getMessage(), System.currentTimeMillis() - startedAt);
    }

    private String operator(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            return "anonymous";
        }
        return principal.getName();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String toJson(ActionRequest request) {
        if (request == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private Map<String, String> error(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }
}
