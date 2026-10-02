package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.dto.job.StoreRef;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.service.JobPostingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock private JobPostingService jobPostingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JobController(jobPostingService), new AdminJobController(jobPostingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicListFiltersByOptionalStore() throws Exception {
        JobSummaryResponse job = new JobSummaryResponse(1L, "Phụ bếp", "phu-bep", EmploymentType.PART_TIME,
                "22–25k/giờ", 2, null, true, List.of(new StoreRef(3L, "CS03", "Cơ sở 3")), true);
        when(jobPostingService.listOpen(3L)).thenReturn(List.of(job));
        when(jobPostingService.listOpen(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs").param("storeId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("phu-bep"))
                .andExpect(jsonPath("$.data[0].stores[0].code").value("CS03"));
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void publicDetailAnd404() throws Exception {
        when(jobPostingService.getBySlug("phu-bep")).thenReturn(new JobDetailResponse(1L, "Phụ bếp", "phu-bep",
                EmploymentType.PART_TIME, null, null, null, "## Mô tả", true, List.of(), false));
        when(jobPostingService.getBySlug("khong-co"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tin tuyển dụng"));

        mockMvc.perform(get("/api/v1/jobs/phu-bep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptingApplications").value(false));
        mockMvc.perform(get("/api/v1/jobs/khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tin tuyển dụng"));
    }

    @Test
    void adminCreateParsesDeadlineAndStores() throws Exception {
        when(jobPostingService.create(any(JobPostingRequest.class))).thenReturn(adminJob(JobStatus.OPEN));

        mockMvc.perform(post("/api/v1/admin/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Phụ bếp","employmentType":"PART_TIME","deadline":"2026-10-31",
                                 "description":"## Mô tả","storeIds":[1,2]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("phu-bep"));

        ArgumentCaptor<JobPostingRequest> captor = ArgumentCaptor.forClass(JobPostingRequest.class);
        verify(jobPostingService).create(captor.capture());
        assertThat(captor.getValue().deadline()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(captor.getValue().storeIds()).containsExactly(1L, 2L);
    }

    @Test
    void adminToggleStatusListAndDelete() throws Exception {
        when(jobPostingService.setStatus(5L, JobStatus.CLOSED)).thenReturn(adminJob(JobStatus.CLOSED));
        when(jobPostingService.searchAdmin(eq(JobStatus.OPEN), eq(null), eq(0), eq(20)))
                .thenReturn(new com.banhmyking.banhmyking.dto.common.PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(patch("/api/v1/admin/jobs/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(get("/api/v1/admin/jobs").param("status", "OPEN")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/jobs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xoá tin tuyển dụng"));
        verify(jobPostingService).delete(5L);
    }

    private static AdminJobResponse adminJob(JobStatus status) {
        return new AdminJobResponse(5L, "Phụ bếp", "phu-bep", EmploymentType.PART_TIME, null, null,
                LocalDate.of(2026, 10, 31), "## Mô tả", status, false, false,
                List.of(new StoreRef(1L, "CS01", "Cơ sở 1")), LocalDateTime.of(2026, 10, 2, 9, 0));
    }
}
