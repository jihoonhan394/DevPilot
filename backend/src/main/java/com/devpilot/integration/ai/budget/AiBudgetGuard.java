package com.devpilot.integration.ai.budget;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.time.PlanDayCalculator;
import com.devpilot.common.time.UserTimeSettingsProvider;
import com.devpilot.common.time.UserTimeSettingsProvider.UserTimeSettings;
import com.devpilot.integration.ai.api.AiBudgetDecision;
import com.devpilot.integration.ai.api.AiConcurrencyReservation;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.AiPendingJobCounter;
import com.devpilot.integration.ai.api.AiStatus;
import com.devpilot.integration.ai.api.AiUsageSnapshot;
import com.devpilot.integration.ai.log.AiCallLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * AI 예산·한도 검사 (docs/17 §8.1~§8.3, docs/05 §1.9, BL-AIP-10). 검사 순서: provider {@code disabled} → 잔액
 * 소진 → 월 비용(서비스 전체, {@code default-zone} 달력 월) → 일일 호출 수(사용자별, plan-day) → 동시 실행({@code check}만,
 * 사용자별). 월·일일·동시 거부는 {@code BUDGET_BLOCKED} 행과 감사 {@code AI_BUDGET_BLOCKED}를 남기고({@link
 * AiBudgetBlockRecorder}), provider {@code disabled}·잔액 소진 거부는 둘 다 남기지 않는다.
 *
 * <p>동기 operation의 호출 모듈은 이 클래스를 직접 부르지 않는다 — {@code AiGateway.call}이 부른다. 직접 부르는 곳은 비동기 시작 요청
 * 경로뿐이다(docs/17 §8.3): 마스킹 다음, 리소스 INSERT 전에 {@link #check}를 부르고 INSERT 트랜잭션이 끝난 뒤 {@code
 * finally}에서 예약을 닫는다.
 */
@Component
public class AiBudgetGuard {

    /** 잔액 소진 503의 {@code Retry-After} (다음 잔액 확인 주기, docs/05 §1.9.3). */
    static final long BALANCE_RETRY_AFTER_SECONDS = 3_600L;

    /** 동시 실행 429의 {@code Retry-After} (docs/05 §1.9.3). */
    static final long CONCURRENCY_RETRY_AFTER_SECONDS = 5L;

    private static final long BP = 10_000L;

    private final DevPilotProperties.Ai settings;
    private final ZoneId zone;
    private final AiCallLogRepository repository;
    private final AiBalanceMonitor balanceMonitor;
    private final UserTimeSettingsProvider userTimeSettings;
    private final List<AiPendingJobCounter> pendingCounters;
    private final AiBudgetBlockRecorder blockRecorder;
    private final Clock clock;
    private final Map<UUID, Integer> inFlight = new ConcurrentHashMap<>();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public AiBudgetGuard(
            DevPilotProperties properties,
            AiCallLogRepository repository,
            AiBalanceMonitor balanceMonitor,
            UserTimeSettingsProvider userTimeSettings,
            List<AiPendingJobCounter> pendingCounters,
            AiBudgetBlockRecorder blockRecorder,
            Clock clock) {
        this.settings = properties.ai();
        this.zone = properties.time().defaultZone();
        this.repository = repository;
        this.balanceMonitor = balanceMonitor;
        this.userTimeSettings = userTimeSettings;
        this.pendingCounters = List.copyOf(pendingCounters);
        this.blockRecorder = blockRecorder;
        this.clock = clock;
    }

    /** provider → 잔액 → 월 → 일일 → 동시(예약 획득). 허용되면 예약을 닫아야 한다. */
    public AiBudgetDecision check(UUID userId, AiOperation operation) {
        AiBudgetDecision limits = checkLimitsOnly(userId, operation);
        if (!limits.allowed()) {
            return limits;
        }
        Object lock = locks.computeIfAbsent(userId, key -> new Object());
        synchronized (lock) {
            int running = inFlight.getOrDefault(userId, 0);
            int pending =
                    pendingCounters.stream()
                            .mapToInt(counter -> counter.countPendingOrRunning(userId))
                            .sum();
            if (running + pending >= settings.maxConcurrentPerUser()) {
                Instant now = clock.instant();
                return AiBudgetDecision.deny(
                        ErrorCode.AI_CONCURRENCY_LIMIT,
                        blockRecorder.record(
                                userId,
                                operation,
                                ErrorCode.AI_CONCURRENCY_LIMIT,
                                "CONCURRENCY_LIMIT",
                                monthCost(now),
                                now),
                        CONCURRENCY_RETRY_AFTER_SECONDS);
            }
            inFlight.merge(userId, 1, Integer::sum);
        }
        return AiBudgetDecision.allow(new Reservation(userId));
    }

    /** provider → 잔액 → 월 → 일일. 비동기 task 안에서 쓴다(작업 자신이 이미 동시 실행에 집계되어 있다). */
    public AiBudgetDecision checkLimitsOnly(UUID userId, AiOperation operation) {
        if ("disabled".equals(settings.provider())) {
            return AiBudgetDecision.deny(ErrorCode.AI_UNAVAILABLE, null, null);
        }
        if (balanceMonitor.exhausted()) {
            return AiBudgetDecision.deny(
                    ErrorCode.AI_UNAVAILABLE, null, BALANCE_RETRY_AFTER_SECONDS);
        }
        Instant now = clock.instant();
        YearMonth month = YearMonth.from(now.atZone(zone));
        long spent = monthCost(now);
        if (spent >= settings.monthlyBudgetMicroUsd()) {
            return AiBudgetDecision.deny(
                    ErrorCode.AI_MONTHLY_BUDGET_EXCEEDED,
                    blockRecorder.record(
                            userId,
                            operation,
                            ErrorCode.AI_MONTHLY_BUDGET_EXCEEDED,
                            "MONTHLY_BUDGET_EXCEEDED",
                            spent,
                            now),
                    secondsUntil(now, monthStart(month.plusMonths(1), zone)));
        }
        UserTimeSettings time = userTimeSettings.timeSettings(userId);
        LocalDate today = PlanDayCalculator.planDate(now, time.zoneId(), time.dayStartHour());
        Instant dayStart =
                PlanDayCalculator.planDayStart(today, time.zoneId(), time.dayStartHour());
        Instant nextDay =
                PlanDayCalculator.planDayStart(
                        today.plusDays(1), time.zoneId(), time.dayStartHour());
        if (repository.countCallsBetween(userId, dayStart, nextDay)
                >= settings.dailyCallLimitPerUser()) {
            return AiBudgetDecision.deny(
                    ErrorCode.AI_DAILY_LIMIT_EXCEEDED,
                    blockRecorder.record(
                            userId,
                            operation,
                            ErrorCode.AI_DAILY_LIMIT_EXCEEDED,
                            "DAILY_LIMIT_EXCEEDED",
                            spent,
                            now),
                    secondsUntil(now, nextDay));
        }
        return AiBudgetDecision.allow(AiConcurrencyReservation.NONE);
    }

    /** {@code GET /me}의 {@code aiStatus}·{@code aiUsage} (docs/05 §1.9.1). */
    public AiUsageSnapshot usage(UUID userId) {
        Instant now = clock.instant();
        long spent = monthCost(now);
        UserTimeSettings time = userTimeSettings.timeSettings(userId);
        LocalDate today = PlanDayCalculator.planDate(now, time.zoneId(), time.dayStartHour());
        Instant dayStart =
                PlanDayCalculator.planDayStart(today, time.zoneId(), time.dayStartHour());
        Instant nextDay =
                PlanDayCalculator.planDayStart(
                        today.plusDays(1), time.zoneId(), time.dayStartHour());
        int todayCalls = Math.toIntExact(repository.countCallsBetween(userId, dayStart, nextDay));
        long budget = settings.monthlyBudgetMicroUsd();
        return new AiUsageSnapshot(
                status(spent, budget), todayCalls, settings.dailyCallLimitPerUser(), spent, budget);
    }

    /**
     * 서비스 AI 상태 (docs/05 §1.9.1): provider {@code disabled} → {@code DISABLED} / 월 예산 도달 → {@code
     * DISABLED} / 잔액 소진 → {@code BALANCE_EXHAUSTED} / 경고 비율 이상 → {@code BUDGET_WARNING} / 그 외
     * {@code ENABLED}. 일일 한도는 반영하지 않는다. Today의 {@code TaskProposalPolicy}가 쓴다(docs/06 §5.3).
     */
    public AiStatus status() {
        return status(monthCost(clock.instant()), settings.monthlyBudgetMicroUsd());
    }

    private AiStatus status(long spent, long budget) {
        if ("disabled".equals(settings.provider()) || spent >= budget) {
            return AiStatus.DISABLED;
        }
        if (balanceMonitor.exhausted()) {
            return AiStatus.BALANCE_EXHAUSTED;
        }
        if (spent * BP >= budget * settings.budgetWarningBp()) {
            return AiStatus.BUDGET_WARNING;
        }
        return AiStatus.ENABLED;
    }

    private long monthCost(Instant now) {
        YearMonth month = YearMonth.from(now.atZone(zone));
        return repository.sumCostBetween(
                monthStart(month, zone), monthStart(month.plusMonths(1), zone));
    }

    /** {@code default-zone} 달력 월의 시작 시각. */
    static Instant monthStart(YearMonth month, ZoneId zone) {
        return month.atDay(1).atStartOfDay(zone).toInstant();
    }

    private static long secondsUntil(Instant now, Instant target) {
        return Math.max(1L, Duration.between(now, target).toSeconds());
    }

    private void release(UUID userId) {
        Object lock = locks.computeIfAbsent(userId, key -> new Object());
        synchronized (lock) {
            inFlight.computeIfPresent(userId, (key, count) -> count <= 1 ? null : count - 1);
        }
    }

    /** 동시 실행 예약. {@code close}는 한 번만 효과가 있다. */
    private final class Reservation implements AiConcurrencyReservation {

        private final UUID userId;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Reservation(UUID userId) {
            this.userId = userId;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release(userId);
            }
        }
    }
}
