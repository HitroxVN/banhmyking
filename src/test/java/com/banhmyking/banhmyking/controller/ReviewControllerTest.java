package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.ReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: POST /api/v1/reviews phải lấy danh tính từ JWT, KHÔNG được tin
 * header X-User-Id do người gọi tự khai (xem markdown/report.md — F1).
 */
@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock ReviewService reviewService;
    @InjectMocks ReviewController reviewController;

    MockMvc mockMvc;

    /** Principal giả — username là userId (JWT subject), user 5. */
    private static final UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("5")
                    .password("x")
                    .authorities("ROLE_CUSTOMER")
                    .build();

    private static final String BODY = "{\"orderItemId\":7,\"rating\":5,\"comment\":\"Ngon\"}";

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(reviewController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReview_usesJwtIdentity_notXUserIdHeader() throws Exception {
        when(reviewService.createReview(eq(5L), any(CreateReviewRequest.class))).thenReturn(null);

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "1")
                        .content(BODY))
                .andExpect(status().isCreated());

        verify(reviewService).createReview(eq(5L), any(CreateReviewRequest.class));
        verify(reviewService, never()).createReview(eq(1L), any(CreateReviewRequest.class));
    }

    @Test
    void createReview_withoutAuthentication_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Id", "1")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(reviewService, never()).createReview(any(), any(CreateReviewRequest.class));
    }
}
