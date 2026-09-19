package com.devpilot.user.infrastructure;

import com.devpilot.user.domain.AppUser;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** {@code app_user} 조회·JIT 생성 (docs/03 §4.3). */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByExternalAuthId(UUID externalAuthId);

    /** 온보딩처럼 한 사용자에게 동시에 들어온 요청을 줄 세울 때 쓴다 (docs/05 §4.1 처리 1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") UUID id);

    /**
     * JIT 생성 (docs/03 §4.3 3-b). 동시 최초 요청은 {@code external_auth_id} unique로 한 행만 남는다. 삽입한 행 수(0 또는
     * 1)를 돌려준다. role·status·학습 시간·version은 DB 기본값이다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    insert into devpilot.app_user
                        (id, external_auth_id, display_name, timezone, day_start_hour, created_at,
                         updated_at)
                    values (:id, :externalAuthId, :displayName, :timezone, :dayStartHour, :now, :now)
                    on conflict (external_auth_id) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("externalAuthId") UUID externalAuthId,
            @Param("displayName") String displayName,
            @Param("timezone") String timezone,
            @Param("dayStartHour") int dayStartHour,
            @Param("now") Instant now);
}
