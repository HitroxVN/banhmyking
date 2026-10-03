package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.JobPostingRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.util.ContactFields;
import com.banhmyking.banhmyking.util.PageableFactory;
import com.banhmyking.banhmyking.util.SlugUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tin tuyển dụng (spec D §4). Chỉ ADMIN soạn; khách xem tin OPEN chưa hết hạn. */
@Service
@RequiredArgsConstructor
public class JobPostingService {

    static final String NOT_FOUND = "Không tìm thấy tin tuyển dụng";
    private static final String DEFAULT_SLUG = "tuyen-dung";
    private static final int MAX_HEADCOUNT = 1000;
    private static final Sort ADMIN_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final JobPostingRepository jobPostingRepository;
    private final StoreRepository storeRepository;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------ khách

    @Transactional(readOnly = true)
    public List<JobSummaryResponse> listOpen(Long storeId) {
        LocalDate today = today();
        // Cơ sở đang hoạt động chỉ nạp một lần cho cả danh sách (và chỉ khi có tin toàn chuỗi).
        Supplier<List<Store>> activeStores = memoize(this::activeStores);
        return jobPostingRepository.findOpen(today, storeId).stream()
                .map(job -> {
                    List<Store> receiving = receivingStores(job, activeStores);
                    return JobSummaryResponse.of(job, receiving, isAccepting(job, receiving, today));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getBySlug(String slug) {
        JobPosting job = requireBySlug(slug);
        List<Store> receiving = receivingStores(job);
        return JobDetailResponse.of(job, receiving, isAccepting(job, receiving, today()));
    }

    /**
     * Quy tắc duy nhất "còn nhận hồ sơ": OPEN, chưa xoá, chưa quá hạn VÀ có ít nhất một cơ sở nhận hồ sơ.
     * Dùng chung cho acceptingApplications của trang chi tiết/danh sách và cho việc nộp hồ sơ.
     */
    public boolean isAccepting(JobPosting job, List<Store> receivingStores, LocalDate today) {
        return job.acceptsApplicationsOn(today) && !receivingStores.isEmpty();
    }

    public JobPosting requireBySlug(String slug) {
        return jobPostingRepository.findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** Cơ sở nhận hồ sơ: cơ sở đang hoạt động của tin, hoặc mọi cơ sở đang hoạt động nếu tuyển toàn chuỗi. */
    public List<Store> receivingStores(JobPosting job) {
        return receivingStores(job, this::activeStores);
    }

    private List<Store> receivingStores(JobPosting job, Supplier<List<Store>> activeStores) {
        if (job.isChainWide()) {
            return activeStores.get();
        }
        return job.getStores().stream()
                .filter(store -> store.isActive() && !store.isDeleted())
                .sorted(Comparator.comparing(Store::getCode))
                .toList();
    }

    private List<Store> activeStores() {
        return storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc();
    }

    private static <T> Supplier<T> memoize(Supplier<T> delegate) {
        Object[] holder = new Object[1];
        boolean[] loaded = new boolean[1];
        return () -> {
            if (!loaded[0]) {
                holder[0] = delegate.get();
                loaded[0] = true;
            }
            @SuppressWarnings("unchecked")
            T value = (T) holder[0];
            return value;
        };
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional(readOnly = true)
    public PageResponse<AdminJobResponse> searchAdmin(JobStatus status, String keyword, int page, int size) {
        LocalDate today = today();
        return PageResponse.from(jobPostingRepository.searchAdmin(status, ContactFields.trimToNull(keyword),
                        PageableFactory.of(page, size, ADMIN_ORDER))
                .map(job -> AdminJobResponse.from(job, today)));
    }

    @Transactional(readOnly = true)
    public AdminJobResponse getAdmin(Long id) {
        return AdminJobResponse.from(requireById(id), today());
    }

    @Transactional
    public AdminJobResponse create(JobPostingRequest request) {
        JobPosting job = new JobPosting();
        apply(job, request);
        return AdminJobResponse.from(jobPostingRepository.save(job), today());
    }

    @Transactional
    public AdminJobResponse update(Long id, JobPostingRequest request) {
        JobPosting job = requireById(id);
        apply(job, request);
        return AdminJobResponse.from(jobPostingRepository.save(job), today());
    }

    @Transactional
    public AdminJobResponse setStatus(Long id, JobStatus status) {
        if (status == null) {
            throw invalid("Vui lòng chọn trạng thái tin tuyển dụng");
        }
        JobPosting job = requireById(id);
        job.setStatus(status);
        return AdminJobResponse.from(jobPostingRepository.save(job), today());
    }

    /** Xoá mềm — hồ sơ và CV đã nộp giữ nguyên (spec D §4). */
    @Transactional
    public void delete(Long id) {
        JobPosting job = requireById(id);
        job.setDeleted(true);
        jobPostingRepository.save(job);
    }

    // ------------------------------------------------------------------ nội bộ

    private void apply(JobPosting job, JobPostingRequest request) {
        String title = ContactFields.trimToNull(request.title());
        if (title == null || title.length() < 3 || title.length() > 200) {
            throw invalid("Tên vị trí phải từ 3 đến 200 ký tự");
        }
        if (request.employmentType() == null) {
            throw invalid("Vui lòng chọn hình thức làm việc");
        }
        String salary = ContactFields.optionalText(request.salaryText(), 100, "Mức lương tối đa 100 ký tự");
        Integer headcount = request.headcount();
        if (headcount != null && (headcount < 1 || headcount > MAX_HEADCOUNT)) {
            throw invalid("Số lượng cần tuyển phải từ 1 đến 1000");
        }
        LocalDate deadline = request.deadline();
        if (deadline != null && !deadline.equals(job.getDeadline()) && deadline.isBefore(today())) {
            throw invalid("Hạn nộp hồ sơ không được ở quá khứ");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw invalid("Mô tả công việc không được để trống");
        }
        Set<Store> stores = resolveStores(request.storeIds(), job.getStores());
        String slug = resolveSlug(job, request.slug(), title);

        job.setTitle(title);
        job.setSlug(slug);
        job.setEmploymentType(request.employmentType());
        job.setSalaryText(salary);
        job.setHeadcount(headcount);
        job.setDeadline(deadline);
        job.setDescription(request.description());
        if (request.status() != null) {
            job.setStatus(request.status());
        } else if (job.getStatus() == null) {
            job.setStatus(JobStatus.OPEN); // tin mới; khi sửa giữ nguyên trạng thái hiện tại
        }
        job.getStores().clear();
        job.getStores().addAll(stores);
    }

    /**
     * Cơ sở đã gắn với tin (khi sửa) được giữ nguyên dù nay ngừng hoạt động; chỉ cơ sở mới thêm
     * phải đang hoạt động (R7).
     */
    private Set<Store> resolveStores(List<Long> storeIds, Set<Store> currentlyLinked) {
        Set<Store> stores = new LinkedHashSet<>();
        if (storeIds == null) {
            return stores;
        }
        if (storeIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("Danh sách cơ sở không hợp lệ");
        }
        for (Long storeId : new LinkedHashSet<>(storeIds)) {
            Store linked = currentlyLinked.stream()
                    .filter(store -> store.getId() != null && store.getId().equals(storeId))
                    .findFirst().orElse(null);
            if (linked != null) {
                stores.add(linked);
                continue;
            }
            stores.add(storeRepository.findByIdAndDeletedFalse(storeId)
                    .filter(Store::isActive)
                    .orElseThrow(() -> invalid("Cơ sở không hợp lệ hoặc đã ngừng hoạt động: " + storeId)));
        }
        return stores;
    }

    private String resolveSlug(JobPosting job, String requested, String title) {
        Long selfId = job.getId();
        String wanted;
        if (ContactFields.trimToNull(requested) != null) {
            wanted = SlugUtils.slugify(requested);
            if (wanted.isEmpty()) {
                throw invalid("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
            }
        } else if (selfId != null) {
            return job.getSlug();
        } else {
            wanted = SlugUtils.slugify(title);
            if (wanted.isEmpty()) {
                wanted = DEFAULT_SLUG;
            }
        }
        if (wanted.equals(job.getSlug())) {
            return wanted;
        }
        return SlugUtils.uniqueSlug(wanted, candidate -> selfId == null
                ? jobPostingRepository.existsBySlug(candidate)
                : jobPostingRepository.existsBySlugAndIdNot(candidate, selfId));
    }

    private JobPosting requireById(Long id) {
        return jobPostingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
