package com.devpilot.today.infrastructure;

import com.devpilot.learning.domain.TipFeedback;
import com.devpilot.today.domain.UserDailyTip;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code user_daily_tip} 조회 (docs/05 §20.2·§20.3, docs/06 §5.12). */
public interface UserDailyTipRepository extends JpaRepository<UserDailyTip, UUID> {

    /** 그 plan-day에 이미 보여 준 팁 (TIP-3). 하루 1개다. */
    Optional<UserDailyTip> findByUserIdAndShownOn(UUID userId, LocalDate shownOn);

    Optional<UserDailyTip> findByUserIdAndTipKey(UUID userId, String tipKey);

    /** 이미 받은 팁 key 전부 (TIP-2 제외 목록). */
    @Query("select t.tipKey from UserDailyTip t where t.userId = :userId")
    List<String> findShownTipKeys(@Param("userId") UUID userId);

    /**
     * 실험 후보 (TIP-6): {@code WILL_TRY}로 표시한 팁 중 표시일이 <b>오늘보다 앞선</b> 것. 표시한 그날은 붙이지 않는다.
     *
     * <p>정렬은 {@code shown_on DESC → tip_key ASC}이고 호출자가 첫 번째만 쓴다.
     */
    @Query(
            """
            select t from UserDailyTip t
             where t.userId = :userId
               and t.feedback = com.devpilot.learning.domain.TipFeedback.WILL_TRY
               and t.shownOn < :today
             order by t.shownOn desc, t.tipKey asc
            """)
    List<UserDailyTip> findExperimentCandidates(
            @Param("userId") UUID userId, @Param("today") LocalDate today, Limit limit);

    /** 그 사용자가 고른 값 (목록 응답의 {@code feedback}). */
    @Query("select t.tipKey, t.feedback from UserDailyTip t where t.userId = :userId")
    List<Object[]> findFeedbackByUserId(@Param("userId") UUID userId);

    default java.util.Map<String, TipFeedback> feedbackByTipKey(UUID userId) {
        java.util.Map<String, TipFeedback> byKey = new java.util.LinkedHashMap<>();
        for (Object[] row : findFeedbackByUserId(userId)) {
            if (row[1] != null) {
                byKey.put((String) row[0], (TipFeedback) row[1]);
            }
        }
        return java.util.Map.copyOf(byKey);
    }
}
