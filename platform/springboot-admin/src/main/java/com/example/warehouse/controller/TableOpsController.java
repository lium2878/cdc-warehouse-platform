package com.example.warehouse.controller;

import com.example.warehouse.model.TableOpsRequest;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.service.TableOpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Collections;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "Table Ops", description = "Table-level backfill, checks, and verification")
public class TableOpsController {
    private final TableOpsService tableOpsService;

    public TableOpsController(TableOpsService tableOpsService) {
        this.tableOpsService = tableOpsService;
    }

    @GetMapping("/table-ops")
    @Operation(summary = "Table operations page")
    public String page(Model model) {
        TableOpsRequest request = new TableOpsRequest();
        request.setDatabaseName("basiccomment");
        request.setTableName("avatar_commentbatchsource");
        request.setBizDt("2026-07-07");
        request.setStartDt("2026-07-07");
        request.setEndDt("2026-07-07");
        request.setDryRun(true);
        model.addAttribute("request", request);
        model.addAttribute("tables", tableOpsService.listTables());
        return "table_ops";
    }

    @PostMapping("/api/table-ops/backfill")
    @ResponseBody
    public ResponseEntity<?> backfill(@ModelAttribute TableOpsRequest request, Principal principal) {
        return submit(() -> tableOpsService.backfill(request, operator(principal)));
    }

    @PostMapping("/api/table-ops/check-lineage")
    @ResponseBody
    public ResponseEntity<?> checkLineage(@ModelAttribute TableOpsRequest request, Principal principal) {
        return submit(() -> tableOpsService.checkLineage(request, operator(principal)));
    }

    @PostMapping("/api/table-ops/consistency")
    @ResponseBody
    public ResponseEntity<?> consistency(@ModelAttribute TableOpsRequest request, Principal principal) {
        return submit(() -> tableOpsService.consistency(request, operator(principal)));
    }

    @PostMapping("/api/table-ops/onboarding-verify")
    @ResponseBody
    public ResponseEntity<?> onboardingVerify(@ModelAttribute TableOpsRequest request, Principal principal) {
        return submit(() -> tableOpsService.onboardingVerify(request, operator(principal)));
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }

    private ResponseEntity<?> response(Object result) {
        if (result instanceof TaskExecution) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
        }
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<?> submit(TaskOperation operation) {
        try {
            return response(operation.run());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Collections.singletonMap("error", ex.getMessage()));
        }
    }

    @FunctionalInterface
    private interface TaskOperation {
        Object run();
    }
}
