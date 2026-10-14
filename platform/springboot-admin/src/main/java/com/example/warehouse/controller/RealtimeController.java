package com.example.warehouse.controller;

import com.example.warehouse.model.RealtimeSnapshot;
import com.example.warehouse.service.PlatformActionService;
import com.example.warehouse.service.RealtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "Realtime", description = "Kudu / Impala realtime warehouse")
public class RealtimeController {
    private final RealtimeService realtimeService;
    private final PlatformActionService platformActionService;

    public RealtimeController(RealtimeService realtimeService, PlatformActionService platformActionService) {
        this.realtimeService = realtimeService;
        this.platformActionService = platformActionService;
    }

    @GetMapping("/realtime")
    @Operation(summary = "Realtime warehouse page")
    public String realtime(Model model) {
        model.addAttribute("snapshot", realtimeService.snapshot());
        return "realtime";
    }

    @GetMapping("/api/realtime")
    @ResponseBody
    @Operation(summary = "Realtime warehouse snapshot")
    public RealtimeSnapshot snapshot() {
        return realtimeService.snapshot();
    }

    @PostMapping("/api/realtime/kafka-to-kudu")
    @ResponseBody
    @Operation(summary = "Run one Kafka-to-Kudu realtime batch")
    public ResponseEntity<?> kafkaToKudu(Principal principal) {
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                    platformActionService.run("realtime-kafka-kudu-once", null, operator(principal)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Collections.singletonMap("error", ex.getMessage()));
        }
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
