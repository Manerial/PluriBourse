package org.pluribourse.shared;

import jakarta.validation.*;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.*;
import org.pluribourse.shared.exception.*;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone test for RFC 7807 Problem Details format (AC3).
 * No Spring context needed — uses MockMvcBuilders.standaloneSetup().
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    record SampleRequest(@NotBlank String name) {
    }

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

        @PostMapping("/test/validation-error")
        void triggerValidation(@RequestBody @Valid SampleRequest body) {
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Unprocessable Content"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/test/constraint-violation"));
    }

    @Test
    void constraintViolationWithNullMessageReturnsSafeDetail() throws Exception {
        mockMvc.perform(get("/test/constraint-violation-null"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Unprocessable Content"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void methodArgumentNotValidReturnsBadRequestWithProblemDetail() throws Exception {
        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://pluribourse/errors/validation-failed"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/test/validation-error"));
    }
}
