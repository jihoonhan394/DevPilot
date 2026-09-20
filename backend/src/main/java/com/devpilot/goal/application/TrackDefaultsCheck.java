package com.devpilot.goal.application;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.config.TrackDefaults;
import com.devpilot.skill.domain.TargetRole;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code devpilot.tracks}에 {@link TargetRole} 값마다 기본값이 있는지 기동 시 검사한다 (docs/03 §3·§9, docs/06 §5.3).
 * 없으면 기동 실패다 — 트랙 기본값이 빠지면 planner가 난이도 상한과 {@code READ_CODE} 문턱을 정할 수 없다.
 *
 * <p>{@code common}은 {@code goal}·{@code skill}을 의존할 수 없으므로 검사를 {@link DevPilotProperties} 쪽이 아니라
 * 여기에 둔다(docs/03 §2.2).
 */
@Component
public class TrackDefaultsCheck {

    private final Map<String, TrackDefaults> tracks;

    TrackDefaultsCheck(DevPilotProperties properties) {
        this.tracks = properties.tracks();
    }

    @PostConstruct
    void verifyEveryTargetRoleHasDefaults() {
        List<String> missing =
                Arrays.stream(TargetRole.values())
                        .map(Enum::name)
                        .filter(role -> !tracks.containsKey(role))
                        .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "devpilot.tracks is missing defaults for " + String.join(", ", missing));
        }
    }

    /** 그 트랙의 기본값. 기동 검사를 통과했으므로 값이 반드시 있다. */
    public TrackDefaults of(TargetRole targetRole) {
        return tracks.get(targetRole.name());
    }
}
