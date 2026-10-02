package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackRequest;
import com.banhmyking.banhmyking.dto.feedback.FeedbackResponse;
import com.banhmyking.banhmyking.dto.feedback.UpdateFeedbackRequest;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.service.FeedbackService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    private static final String BODY = """
            {"type":"COMPLAINT","orderCode":"BMK-1","fullName":"Bình","phone":"0912345678",
             "subject":"Bánh nguội","content":"Đơn giao chậm, bánh nguội."}
            """;

    @Mock private FeedbackService feedbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FeedbackController(feedbackService, new ClientIpResolver(false)),
                        new AdminFeedbackController(feedbackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousSubmissionPassesNullSenderAndClientIp() throws Exception {
        when(feedbackService.submit(isNull(), any(FeedbackRequest.class), eq("10.2.3.4"))).thenReturn(true);

        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(request -> {
                            request.setRemoteAddr("10.2.3.4");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cảm ơn bạn! Phản hồi đã được gửi tới cửa hàng."))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(feedbackService).submit(isNull(), captor.capture(), eq("10.2.3.4"));
        assertThat(captor.getValue().type()).isEqualTo(FeedbackType.COMPLAINT);
        assertThat(captor.getValue().orderCode()).isEqualTo("BMK-1");
    }

    @Test
    void bearerHeaderWithoutAuthenticatedPrincipalIsRejectedWith401() throws Exception {
        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Authorization", "Bearer expired.token.value"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(feedbackService);
    }

    @Test
    void loggedInSubmissionPassesUserIdAndErrorsMap() throws Exception {
        login("5", "ROLE_CUSTOMER");
        when(feedbackService.submit(eq(5L), any(FeedbackRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng"))
                .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau"));

        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));
        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void inboxEndpointsPassActorId() throws Exception {
        login("50", "ROLE_MANAGER");
        when(feedbackService.search(50L, FeedbackType.COMPLAINT, null, FeedbackStatus.NEW, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true));
        when(feedbackService.countNew(50L)).thenReturn(2L);
        when(feedbackService.get(50L, 3L)).thenReturn(response());
        when(feedbackService.update(eq(50L), eq(3L), any(UpdateFeedbackRequest.class))).thenReturn(response());

        mockMvc.perform(get("/api/v1/admin/feedbacks").param("type", "COMPLAINT").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderCode").value("BMK-1"));
        mockMvc.perform(get("/api/v1/admin/feedbacks/count-new")).andExpect(jsonPath("$.data").value(2));
        mockMvc.perform(get("/api/v1/admin/feedbacks/3")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/feedbacks/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"resolutionNote\":\"Đang gọi khách\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã cập nhật phản hồi"));

        ArgumentCaptor<UpdateFeedbackRequest> captor = ArgumentCaptor.forClass(UpdateFeedbackRequest.class);
        verify(feedbackService).update(eq(50L), eq(3L), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(FeedbackStatus.IN_PROGRESS);
    }

    private static void login(String userId, String authority) {
        UserDetails principal = User.withUsername(userId).password("x").authorities(authority).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static FeedbackResponse response() {
        return new FeedbackResponse(3L, FeedbackType.COMPLAINT, 1L, "Cơ sở 1", "BMK-1", 5L, "Bình", "0912345678",
                null, "Bánh nguội", "Đơn giao chậm, bánh nguội.", FeedbackStatus.NEW, null, null, null,
                LocalDateTime.of(2026, 10, 2, 9, 0));
    }
}
