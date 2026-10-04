package com.example.warehouse.controller;

import com.example.warehouse.model.ReplayRequest;
import com.example.warehouse.model.ReplayRecord;
import com.example.warehouse.service.ReplayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Tag(name = "Replay", description = "Maxwell bootstrap / replay operations")
public class ReplayController {
    private final ReplayService replayService;

    public ReplayController(ReplayService replayService) {
        this.replayService = replayService;
    }

    @GetMapping("/replay")
    @Operation(summary = "Replay form page")
    public String replay(Model model) {
        ReplayRequest request = new ReplayRequest();
        request.setDatabaseName("basiccomment");
        request.setTableName("avatar_commentbatchsource");
        request.setStartTime(LocalDate.now().minusDays(1) + " 00:00:00");
        request.setEndTime(LocalDate.now() + " 00:00:00");
        model.addAttribute("request", request);
        model.addAttribute("records", replayService.latest());
        return "replay";
    }

    @PostMapping("/replay")
    @Operation(summary = "Execute a full MySQL snapshot replay")
    public String submit(@ModelAttribute ReplayRequest request, Model model, Principal principal) {
        model.addAttribute("request", request);
        try {
            model.addAttribute("execution", replayService.execute(request, operator(principal)));
            model.addAttribute("command", replayService.buildBootstrapCommand(request));
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        model.addAttribute("records", replayService.latest());
        return "replay";
    }

    @GetMapping("/api/replay/records")
    @ResponseBody
    @Operation(summary = "List recent replay executions")
    public List<ReplayRecord> records() {
        return replayService.latest();
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
