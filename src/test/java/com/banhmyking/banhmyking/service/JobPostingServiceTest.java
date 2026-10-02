package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.dto.job.StoreRef;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.JobPostingRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private StoreRepository storeRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM);
    private JobPostingService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingService(jobPostingRepository, storeRepository, clock);
    }

    @Test
    void createWithStoresAndGeneratedSlug() {
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(store(1L, "CS-A", true)));
        when(storeRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(store(2L, "CS-B", true)));
        when(jobPostingRepository.existsBySlug("phu-bep-ca-toi")).thenReturn(false);
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> withId(inv.getArgument(0), 21L));

        AdminJobResponse created = service.create(request("Phụ bếp ca tối", TODAY.plusDays(10), List.of(2L, 1L, 2L)));

        assertThat(created.slug()).isEqualTo("phu-bep-ca-toi");
        assertThat(created.status()).isEqualTo(JobStatus.OPEN);
        assertThat(created.chainWide()).isFalse();
        assertThat(created.stores()).extracting(StoreRef::code).containsExactly("CS-A", "CS-B");
    }

    @Test
    void noStoresMeansChainWide() {
        when(jobPostingRepository.existsBySlug("thu-ngan")).thenReturn(false);
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> withId(inv.getArgument(0), 22L));

        AdminJobResponse created = service.create(request("Thu ngân", null, List.of()));

        assertThat(created.chainWide()).isTrue();
        assertThat(created.stores()).isEmpty();
    }

    @Test
    void rejectsInactiveStoreAndInvalidFields() {
        when(storeRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(store(2L, "CS-B", false)));

        assertThatThrownBy(() -> service.create(request("Phụ bếp", null, List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cơ sở không hợp lệ hoặc đã ngừng hoạt động: 2");
        assertThatThrownBy(() -> service.create(request("AB", null, null)))
                .hasMessage("Tên vị trí phải từ 3 đến 200 ký tự");
        assertThatThrownBy(() -> service.create(new JobPostingRequest("Phụ bếp", null, null, null, null, null,
                "Mô tả", null, null)))
                .hasMessage("Vui lòng chọn hình thức làm việc");
        assertThatThrownBy(() -> service.create(new JobPostingRequest("Phụ bếp", null, EmploymentType.FULL_TIME,
                null, 0, null, "Mô tả", null, null)))
                .hasMessage("Số lượng cần tuyển phải từ 1 đến 1000");
        assertThatThrownBy(() -> service.create(new JobPostingRequest("Phụ bếp", null, EmploymentType.FULL_TIME,
                null, null, null, "  ", null, null)))
                .hasMessage("Mô tả công việc không được để trống");
        verify(jobPostingRepository, never()).save(any());
    }

    @Test
    void pastDeadlineRejectedOnCreateButUnchangedPastDeadlineAllowedOnUpdate() {
        assertThatThrownBy(() -> service.create(request("Phụ bếp", TODAY.minusDays(1), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Hạn nộp hồ sơ không được ở quá khứ");

        JobPosting existing = job(30L, "phu-bep", JobStatus.OPEN, TODAY.minusDays(1));
        when(jobPostingRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminJobResponse updated = service.update(30L, request("Phụ bếp (sửa)", TODAY.minusDays(1), null));

        assertThat(updated.expired()).isTrue();
        assertThat(updated.slug()).isEqualTo("phu-bep");
    }

    @Test
    void updateKeepsAlreadyLinkedInactiveStoreButCreateRejectsInactive() {
        Store inactiveLinked = store(2L, "CS-B", false);
        JobPosting existing = job(31L, "bep", JobStatus.OPEN, null);
        existing.getStores().add(inactiveLinked);
        when(jobPostingRepository.findByIdAndDeletedFalse(31L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminJobResponse updated = service.update(31L, request("Phụ bếp", null, List.of(2L)));

        assertThat(updated.stores()).extracting(StoreRef::code).containsExactly("CS-B");

        when(storeRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(inactiveLinked));
        assertThatThrownBy(() -> service.create(request("Phụ bếp", null, List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cơ sở không hợp lệ hoặc đã ngừng hoạt động: 2");
    }

    @Test
    void updateRejectsNewlyAddedInactiveStore() {
        JobPosting existing = job(32L, "bep", JobStatus.OPEN, null);
        existing.getStores().add(store(1L, "CS-A", true));
        when(jobPostingRepository.findByIdAndDeletedFalse(32L)).thenReturn(Optional.of(existing));
        when(storeRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(store(3L, "CS-C", false)));

        assertThatThrownBy(() -> service.update(32L, request("Phụ bếp", null, List.of(1L, 3L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cơ sở không hợp lệ hoặc đã ngừng hoạt động: 3");
        verify(jobPostingRepository, never()).save(any());
    }

    @Test
    void acceptingApplicationsFollowsStatusAndDeadline() {
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc())
                .thenReturn(List.of(store(1L, "CS-A", true), store(2L, "CS-B", true)));
        when(jobPostingRepository.findBySlugAndDeletedFalse("dong"))
                .thenReturn(Optional.of(job(1L, "dong", JobStatus.CLOSED, null)));
        when(jobPostingRepository.findBySlugAndDeletedFalse("het-han"))
                .thenReturn(Optional.of(job(2L, "het-han", JobStatus.OPEN, TODAY.minusDays(1))));
        when(jobPostingRepository.findBySlugAndDeletedFalse("hom-nay"))
                .thenReturn(Optional.of(job(3L, "hom-nay", JobStatus.OPEN, TODAY)));

        assertThat(service.getBySlug("dong").acceptingApplications()).isFalse();
        assertThat(service.getBySlug("het-han").acceptingApplications()).isFalse();
        JobDetailResponse today = service.getBySlug("hom-nay");
        assertThat(today.acceptingApplications()).isTrue();
        assertThat(today.chainWide()).isTrue();
        assertThat(today.stores()).extracting(StoreRef::code).containsExactly("CS-A", "CS-B");
    }

    @Test
    void receivingStoresSkipInactiveOrDeletedStoresOfThePosting() {
        Store active = store(1L, "CS-A", true);
        Store inactive = store(2L, "CS-B", false);
        Store deleted = store(3L, "CS-C", true);
        deleted.setDeleted(true);
        JobPosting posting = job(5L, "x", JobStatus.OPEN, null);
        posting.getStores().addAll(List.of(active, inactive, deleted));

        assertThat(service.receivingStores(posting)).containsExactly(active);
    }

    @Test
    void listOpenPassesTodayAndStore() {
        when(jobPostingRepository.findOpen(TODAY, 3L)).thenReturn(List.of(job(9L, "bep", JobStatus.OPEN, null)));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(store(3L, "CS-C", true)));

        List<JobSummaryResponse> jobs = service.listOpen(3L);

        assertThat(jobs).singleElement().satisfies(j -> {
            assertThat(j.slug()).isEqualTo("bep");
            assertThat(j.acceptingApplications()).isTrue();
        });
    }

    @Test
    void listOpenLoadsActiveStoresOnceForSeveralChainWidePostings() {
        when(jobPostingRepository.findOpen(TODAY, null)).thenReturn(List.of(
                job(1L, "a", JobStatus.OPEN, null), job(2L, "b", JobStatus.OPEN, null),
                job(3L, "c", JobStatus.OPEN, null)));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc())
                .thenReturn(List.of(store(1L, "CS-A", true)));

        List<JobSummaryResponse> jobs = service.listOpen(null);

        assertThat(jobs).hasSize(3).allSatisfy(j -> assertThat(j.acceptingApplications()).isTrue());
        verify(storeRepository, times(1)).findByActiveTrueAndDeletedFalseOrderByCodeAsc();
    }

    @Test
    void listOpenDoesNotLoadActiveStoresWhenNoPostingIsChainWide() {
        JobPosting linked = job(1L, "a", JobStatus.OPEN, null);
        linked.getStores().add(store(1L, "CS-A", true));
        when(jobPostingRepository.findOpen(TODAY, null)).thenReturn(List.of(linked));

        assertThat(service.listOpen(null)).hasSize(1);
        verify(storeRepository, never()).findByActiveTrueAndDeletedFalseOrderByCodeAsc();
    }

    @Test
    void postingWhoseLinkedStoresAreAllInactiveIsNotAccepting() {
        JobPosting posting = job(1L, "a", JobStatus.OPEN, null);
        posting.getStores().add(store(1L, "CS-A", false));
        when(jobPostingRepository.findBySlugAndDeletedFalse("a")).thenReturn(Optional.of(posting));

        JobDetailResponse detail = service.getBySlug("a");

        assertThat(detail.stores()).isEmpty();
        assertThat(detail.acceptingApplications()).isFalse();
    }

    @Test
    void updateWithoutStatusKeepsCurrentStatus() {
        JobPosting existing = job(50L, "bep", JobStatus.CLOSED, null);
        when(jobPostingRepository.findByIdAndDeletedFalse(50L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminJobResponse updated = service.update(50L, request("Phụ bếp", null, null));

        assertThat(updated.status()).isEqualTo(JobStatus.CLOSED);
    }

    @Test
    void nullStoreIdIsA400NotA500() {
        assertThatThrownBy(() -> service.create(request("Phụ bếp", null, Arrays.asList(1L, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Danh sách cơ sở không hợp lệ");
        verify(jobPostingRepository, never()).save(any());
    }

    @Test
    void setStatusAndSoftDeleteAndMissingSlug() {
        JobPosting existing = job(40L, "bep", JobStatus.OPEN, null);
        when(jobPostingRepository.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobPostingRepository.findBySlugAndDeletedFalse("khong-co")).thenReturn(Optional.empty());

        assertThat(service.setStatus(40L, JobStatus.CLOSED).status()).isEqualTo(JobStatus.CLOSED);
        service.delete(40L);
        assertThat(existing.isDeleted()).isTrue();
        assertThatThrownBy(() -> service.getBySlug("khong-co"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng");
    }

    // ----------------------------------------------------------------------- helpers

    private static JobPostingRequest request(String title, LocalDate deadline, List<Long> storeIds) {
        return new JobPostingRequest(title, null, EmploymentType.PART_TIME, "22–25k/giờ", 2, deadline,
                "## Mô tả\n- Phụ bếp", null, storeIds);
    }

    private static Store store(Long id, String code, boolean active) {
        Store store = new Store();
        store.setId(id);
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ");
        store.setActive(active);
        return store;
    }

    private static JobPosting job(Long id, String slug, JobStatus status, LocalDate deadline) {
        JobPosting job = new JobPosting();
        job.setId(id);
        job.setTitle("Tin " + slug);
        job.setSlug(slug);
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setDescription("Mô tả");
        job.setStatus(status);
        job.setDeadline(deadline);
        return job;
    }

    private static JobPosting withId(JobPosting job, Long id) {
        job.setId(id);
        return job;
    }
}
