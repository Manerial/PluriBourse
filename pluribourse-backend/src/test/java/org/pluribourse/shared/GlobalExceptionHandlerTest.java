package org.pluribourse.shared;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pluribourse.shared.exception.BusinessException;
import org.pluribourse.shared.exception.GlobalExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone test for RFC 7807 Problem Details format (AC3).
 * No Spring context needed — uses MockMvcBuilders.standaloneSetup().
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @RestController
    static class StubController {
        @GetMapping("/test/business-error")
        void throwBusiness() {
            throw new BusinessException(HttpStatus.CONFLICT, "item-already-sold", "Item was already sold.");
        }

        @GetMapping("/test/constraint-violation")
        void throwConstraintViolation() {
            throw new ConstraintViolationException("Validation failed", Set.of());
        }

        @GetMapping("/test/constraint-violation-null")
        void throwConstraintViolationNullMessage() {
            throw new ConstraintViolationException(null, Set.of());
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessExceptionReturnsProblemDetailFormat() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/item-already-sold"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Item was already sold."))
                .andExpect(jsonPath("$.instance").value("/test/business-error"));
    }

    @Test
    void constraintViolationReturnsUnprocessableEntityWithFallbackMessage() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/test/constraint-violation"));
    }

    @Test
    void constraintViolationWithNullMessageReturnsSafeDetail() throws Exception {
        mockMvc.perform(get("/test/constraint-violation-null"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.detail").exists());
    }
}
