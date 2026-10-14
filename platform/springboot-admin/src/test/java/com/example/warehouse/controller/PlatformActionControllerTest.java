package com.example.warehouse.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.warehouse.model.CommandResult;
import com.example.warehouse.model.TaskExecution;
import com.example.warehouse.repository.ActionAuditRepository;
import com.example.warehouse.service.PlatformActionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class PlatformActionControllerTest {
    @Test
    void returnsAcceptedForAsyncAction() {
        PlatformActionService service = mock(PlatformActionService.class);
        TaskExecution execution = new TaskExecution();
        execution.setId(101L);
        execution.setStatus("PENDING");
        when(service.run(eq("daily-merge"), eq(null), eq("alice"))).thenReturn(execution);
        PlatformActionController controller = controller(service);

        ResponseEntity<?> response = controller.run(
                "daily-merge", null, new MockHttpServletRequest(), principal("alice"));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertSame(execution, response.getBody());
    }

    @Test
    void returnsOkForSynchronousAction() {
        PlatformActionService service = mock(PlatformActionService.class);
        CommandResult result = new CommandResult(0, "refreshed");
        when(service.run(eq("refresh-ops"), eq(null), eq("alice"))).thenReturn(result);
        PlatformActionController controller = controller(service);

        ResponseEntity<?> response = controller.run(
                "refresh-ops", null, new MockHttpServletRequest(), principal("alice"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(result, response.getBody());
    }

    private PlatformActionController controller(PlatformActionService service) {
        return new PlatformActionController(service, mock(ActionAuditRepository.class), new ObjectMapper());
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
