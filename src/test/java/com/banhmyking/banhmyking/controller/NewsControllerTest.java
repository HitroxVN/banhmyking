package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.service.NewsService;
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

/** Standalone MockMvc — phân quyền URL kiểm ở NewsCareersSecurityTest (Task 9). */
@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    private static final UserDetails ADMIN = User.withUsername("7").password("x").authorities("ROLE_ADMIN").build();

    @Mock private NewsService newsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService), new AdminNewsController(newsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ADMIN, null, ADMIN.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicListUsesDefaultPaging() throws Exception {
        when(newsService.listPublished(0, 9)).thenReturn(new PageResponse<>(List.of(summary()), 0, 9, 1, 1, true));

        mockMvc.perform(get("/api/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].slug").value("ra-mat"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void latestAndDetail() throws Exception {
        when(newsService.latest(3)).thenReturn(List.of(summary()));
        when(newsService.getPublished("ra-mat")).thenReturn(new NewsDetailResponse(1L, "Ra mắt", "ra-mat", null,
                null, "# Nội dung", LocalDateTime.of(2026, 10, 1, 8, 0), List.of()));

        mockMvc.perform(get("/api/v1/news/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Ra mắt"));
        mockMvc.perform(get("/api/v1/news/ra-mat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# Nội dung"));
    }

    @Test
    void hiddenPostIs404() throws Exception {
        when(newsService.getPublished("nhap")).thenThrow(new ResourceNotFoundException("Không tìm thấy bài viết"));

        mockMvc.perform(get("/api/v1/news/nhap"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy bài viết"));
    }

    @Test
    void adminCreatePassesActorAndParsesVietnamLocalTime() throws Exception {
        when(newsService.create(eq(7L), any(NewsRequest.class))).thenReturn(adminResponse());

        mockMvc.perform(post("/api/v1/admin/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ra mắt","content":"# Nội dung","status":"PUBLISHED",
                                 "publishedAt":"2026-10-05T08:30","pinned":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));

        ArgumentCaptor<NewsRequest> captor = ArgumentCaptor.forClass(NewsRequest.class);
        verify(newsService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().publishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 8, 30));
        assertThat(captor.getValue().status()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(captor.getValue().pinned()).isTrue();
    }

    @Test
    void adminListGetUpdateDelete() throws Exception {
        when(newsService.searchAdmin(NewsDisplayState.SCHEDULED, "km", 0, 20))
                .thenReturn(new PageResponse<>(List.of(adminResponse()), 0, 20, 1, 1, true));
        when(newsService.getAdmin(1L)).thenReturn(adminResponse());
        when(newsService.update(eq(1L), any(NewsRequest.class))).thenReturn(adminResponse());

        mockMvc.perform(get("/api/v1/admin/news").param("status", "SCHEDULED").param("keyword", "km"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].displayState").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/admin/news/1")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/news/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ra mắt\",\"content\":\"x\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/news/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xoá bài viết"));
        verify(newsService).delete(1L);
    }

    private static NewsSummaryResponse summary() {
        return new NewsSummaryResponse(1L, "Ra mắt", "ra-mat", null, "Tóm tắt",
                LocalDateTime.of(2026, 10, 1, 8, 0), false);
    }

    private static AdminNewsResponse adminResponse() {
        return new AdminNewsResponse(1L, "Ra mắt", "ra-mat", null, null, "# Nội dung", NewsStatus.PUBLISHED,
                NewsDisplayState.PUBLISHED, LocalDateTime.of(2026, 10, 1, 8, 0), false, "Quản trị",
                LocalDateTime.of(2026, 10, 1, 7, 0), null);
    }
}
