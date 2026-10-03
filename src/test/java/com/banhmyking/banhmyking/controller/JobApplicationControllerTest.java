package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.CvFile;
import com.banhmyking.banhmyking.dto.job.JobApplicationForm;
import com.banhmyking.banhmyking.dto.job.JobApplicationResponse;
import com.banhmyking.banhmyking.dto.job.UpdateApplicationRequest;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.security.SubmissionRateLimiter;
import com.banhmyking.banhmyking.service.JobApplicationService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class JobApplicationControllerTest {

    private static final UserDetails MANAGER = User.withUsername("50").password("x").authorities("ROLE_MANAGER").build();
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final String SUBMITTED = "Đã gửi hồ sơ ứng tuyển. Cửa hàng sẽ liên hệ với bạn sớm.";

    @Mock private JobApplicationService jobApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new JobApplyController(jobApplicationService, new ClientIpResolver(false)),
                        new JobApplicationController(jobApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MANAGER, null, MANAGER.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void multipartSubmissionBindsFieldsCvAndClientIp() throws Exception {
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF);
        when(jobApplicationService.submit(eq("phu-bep"), any(JobApplicationForm.class), any(MultipartFile.class),
                eq("10.1.2.3"))).thenReturn(true);

        mockMvc.perform(multipart("/api/v1/jobs/phu-bep/applications").file(cv)
                        .param("storeId", "3")
                        .param("fullName", "Nguyễn Văn An")
                        .param("phone", "0901234567")
                        .param("email", "an@banhmy.vn")
                        .param("message", "Em làm được ca tối")
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SUBMITTED))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<JobApplicationForm> form = ArgumentCaptor.forClass(JobApplicationForm.class);
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(jobApplicationService).submit(eq("phu-bep"), form.capture(), file.capture(), eq("10.1.2.3"));
        assertThat(form.getValue().storeId()).isEqualTo(3L);
        assertThat(form.getValue().fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(form.getValue().website()).isNull();
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("cv.pdf");
    }

    @Test
    void honeypotResponseIsIdenticalToRealSubmission() throws Exception {
        when(jobApplicationService.submit(eq("phu-bep"), any(JobApplicationForm.class), isNull(), any()))
                .thenReturn(false);

        mockMvc.perform(multipart("/api/v1/jobs/phu-bep/applications")
                        .param("fullName", "Bot").param("website", "http://spam.example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SUBMITTED));
    }

    @Test
    void rateLimitValidationAndBadParamsMapToStatusCodes() throws Exception {
        when(jobApplicationService.submit(eq("a"), any(JobApplicationForm.class), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, SubmissionRateLimiter.MESSAGE));
        when(jobApplicationService.submit(eq("b"), any(JobApplicationForm.class), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "File CV phải là PDF, JPG hoặc PNG"));

        mockMvc.perform(multipart("/api/v1/jobs/a/applications").param("fullName", "An"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("Bạn thao tác quá nhanh, vui lòng thử lại sau"));
        mockMvc.perform(multipart("/api/v1/jobs/b/applications").param("fullName", "An"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File CV phải là PDF, JPG hoặc PNG"));
        mockMvc.perform(multipart("/api/v1/jobs/c/applications").param("storeId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerEndpointsPassActorId() throws Exception {
        when(jobApplicationService.search(50L, 10L, null, ApplicationStatus.NEW, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true));
        when(jobApplicationService.countNew(50L)).thenReturn(4L);
        when(jobApplicationService.get(50L, 7L)).thenReturn(response());
        when(jobApplicationService.update(eq(50L), eq(7L), any(UpdateApplicationRequest.class))).thenReturn(response());

        mockMvc.perform(get("/api/v1/job-applications").param("jobId", "10").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyễn Văn An"));
        mockMvc.perform(get("/api/v1/job-applications/count-new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
        mockMvc.perform(get("/api/v1/job-applications/7")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/job-applications/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTACTED\",\"internalNote\":\"Đã gọi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã cập nhật hồ sơ"));

        ArgumentCaptor<UpdateApplicationRequest> captor = ArgumentCaptor.forClass(UpdateApplicationRequest.class);
        verify(jobApplicationService).update(eq(50L), eq(7L), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ApplicationStatus.CONTACTED);
        assertThat(captor.getValue().internalNote()).isEqualTo("Đã gọi");
    }

    @Test
    void cvDownloadIsAttachmentWithNosniff() throws Exception {
        when(jobApplicationService.loadCv(50L, 7L))
                .thenReturn(new CvFile(new ByteArrayResource(PDF), "CV Nguyễn Văn An.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/job-applications/7/cv"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("UTF-8")))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void outOfScopeCvIs404Json() throws Exception {
        when(jobApplicationService.loadCv(50L, 8L)).thenThrow(new ResourceNotFoundException("Không tìm thấy hồ sơ ứng tuyển"));

        mockMvc.perform(get("/api/v1/job-applications/8/cv"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void anonymousCallToManagerEndpointIs401FromService() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/job-applications/count-new"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobApplicationService);
    }

    private static JobApplicationResponse response() {
        return new JobApplicationResponse(7L, 10L, "Phụ bếp", "phu-bep", 1L, "Cơ sở 1", "Nguyễn Văn An",
                "0901234567", null, null, true, "cv.pdf", ApplicationStatus.NEW, null, null, null,
                LocalDateTime.of(2026, 10, 2, 9, 0));
    }
}
