package com.example.warehouse.controller;

import com.example.warehouse.model.OnboardRequest;
import com.example.warehouse.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@Tag(name = "Onboarding", description = "MySQL-to-Hive table onboarding")
public class OnboardingController {
    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/onboarding")
    @Operation(summary = "Onboarding form page")
    public String onboarding(Model model) {
        OnboardRequest request = new OnboardRequest();
        request.setDatabaseName("basiccomment");
        request.setTableName("avatar_commentbatchsource");
        request.setDbaMetadataPath("metadata/dba/basiccomment.avatar_commentbatchsource.json");
        request.setPrimaryKeys("id");
        request.setVersionColumn("ver");
        request.setPartitionColumn("ctime");
        model.addAttribute("request", request);
        model.addAttribute("plan", onboardingService.buildPlan(request));
        return "onboarding";
    }

    @PostMapping("/onboarding")
    @Operation(summary = "Execute onboarding (MySQL boot + Hive DDL + scheduler pub)")
    public String execute(@ModelAttribute OnboardRequest request,
                          Model model,
                          Principal principal,
                          HttpServletResponse response) {
        model.addAttribute("request", request);
        model.addAttribute("plan", onboardingService.buildPlan(request));
        try {
            model.addAttribute("execution", onboardingService.execute(request, operator(principal)));
            response.setStatus(HttpServletResponse.SC_ACCEPTED);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch (IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
        return "onboarding";
    }

    private String operator(Principal principal) {
        return principal == null ? "system" : principal.getName();
    }
}
