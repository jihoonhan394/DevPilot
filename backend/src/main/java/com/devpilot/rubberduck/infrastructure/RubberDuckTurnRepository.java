package com.devpilot.rubberduck.infrastructure;

import com.devpilot.rubberduck.domain.RubberDuckTurn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 러버덕 턴 (docs/04 §2, append-only). 소유 검증은 부모 세션으로 한다(I-15). */
public interface RubberDuckTurnRepository extends JpaRepository<RubberDuckTurn, UUID> {

    List<RubberDuckTurn> findBySessionIdOrderByTurnNoAsc(UUID sessionId);
}
