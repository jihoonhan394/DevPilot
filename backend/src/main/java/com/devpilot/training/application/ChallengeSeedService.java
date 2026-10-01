package com.devpilot.training.application;

import com.devpilot.skill.application.SkillCatalogQueryService;
import com.devpilot.skill.application.SkillRef;
import com.devpilot.training.domain.Challenge;
import com.devpilot.training.domain.Challenge.ChallengeContent;
import com.devpilot.training.domain.ChallengePurpose;
import com.devpilot.training.domain.ChallengeRubricItem;
import com.devpilot.training.domain.ChallengeStatus;
import com.devpilot.training.infrastructure.ChallengeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * seed challenge 적재 (docs/04 §9 5단계, BL-CNT-07·BL-CNT-08). {@code ContentSeeder}가 catalog upsert 뒤에
 * 부른다. 키는 {@code challenge.seed_key}다.
 *
 * <ul>
 *   <li>없으면 만들고 {@link ChallengeValidationService}로 {@code VALIDATED}/{@code REJECTED}를 정한다
 *   <li>있으면 구조 필드(skills, difficulty, purpose, isTransfer, rubric의 id·weightBp·axis,
 *       expectedConcepts)가 같아야 한다 — 다르면 기동 실패다. 텍스트·보조 필드는 갱신한다
 *   <li>YAML에서 사라진 seed는 {@code RETIRED}로 둔다(삭제하지 않는다)
 * </ul>
 */
@Service
public class ChallengeSeedService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeValidationService validationService;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final Clock clock;

    public ChallengeSeedService(
            ChallengeRepository challengeRepository,
            ChallengeValidationService validationService,
            SkillCatalogQueryService skillCatalogQueryService,
            Clock clock) {
        this.challengeRepository = challengeRepository;
        this.validationService = validationService;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.clock = clock;
    }

    /** seed challenge 전체를 upsert한다. 한 트랜잭션이다(docs/04 §9). */
    @Transactional
    public SeedOutcome upsert(List<ChallengeSeed> seeds) {
        Map<String, SkillRef> skills =
                skillCatalogQueryService.findActiveByCodes(skillCodes(seeds));
        Instant now = clock.instant();
        int created = 0;
        int updated = 0;
        int rejected = 0;
        Set<String> present = new LinkedHashSet<>();
        for (ChallengeSeed seed : seeds) {
            present.add(seed.seedKey());
            ChallengeContent content = content(seed, skills);
            Optional<Challenge> existing = challengeRepository.findBySeedKey(seed.seedKey());
            Challenge challenge;
            if (existing.isEmpty()) {
                challenge = Challenge.seed(seed.seedKey(), content, now);
                created++;
            } else {
                challenge = existing.get();
                if (!challenge.structureMatches(content)) {
                    throw new IllegalStateException(
                            "seed challenge structure changed for "
                                    + seed.seedKey()
                                    + " — retire the seed key and add a new one (docs/04 §9)");
                }
                challenge.updateSeedText(content);
                updated++;
            }
            if (!validationService.validate(challenge).validated()) {
                rejected++;
            }
            challengeRepository.save(challenge);
        }
        int retired = retireMissing(present, now);
        return new SeedOutcome(created, updated, rejected, retired);
    }

    private int retireMissing(Set<String> present, Instant now) {
        int retired = 0;
        for (Challenge challenge : challengeRepository.findAllSeeds()) {
            String seedKey = challenge.getSeedKey();
            if (seedKey == null
                    || present.contains(seedKey)
                    || challenge.getStatus() == ChallengeStatus.RETIRED) {
                continue;
            }
            challenge.retire(now);
            challengeRepository.save(challenge);
            retired++;
        }
        return retired;
    }

    private static ChallengeContent content(ChallengeSeed seed, Map<String, SkillRef> skills) {
        Set<UUID> skillIds = new LinkedHashSet<>();
        for (String code : seed.skillCodes()) {
            SkillRef ref = skills.get(code);
            if (ref == null) {
                throw new IllegalStateException(
                        "seed challenge " + seed.seedKey() + " references unknown skill " + code);
            }
            skillIds.add(ref.id());
        }
        return new ChallengeContent(
                seed.purpose(),
                seed.difficulty(),
                seed.isTransfer(),
                skillIds,
                seed.title(),
                seed.estimatedMinutes(),
                seed.scenario(),
                seed.prompt(),
                seed.constraints(),
                seed.expectedConcepts(),
                seed.rubric(),
                seed.commonMistakes(),
                seed.transferTargets(),
                seed.hints());
    }

    private static Set<String> skillCodes(List<ChallengeSeed> seeds) {
        Set<String> codes = new HashSet<>();
        seeds.forEach(seed -> codes.addAll(seed.skillCodes()));
        return codes;
    }

    /**
     * seed challenge 1개 (docs/19 §3.6 스키마).
     *
     * @param hints 1~3단계 사전 hint. 키는 {@code HintLevel} 이름
     */
    public record ChallengeSeed(
            String seedKey,
            ChallengePurpose purpose,
            int difficulty,
            boolean isTransfer,
            List<String> skillCodes,
            String title,
            @Nullable Integer estimatedMinutes,
            String scenario,
            String prompt,
            List<String> constraints,
            List<String> expectedConcepts,
            List<ChallengeRubricItem> rubric,
            List<String> commonMistakes,
            List<String> transferTargets,
            Map<String, String> hints) {

        public ChallengeSeed {
            skillCodes = List.copyOf(skillCodes);
            constraints = List.copyOf(constraints);
            expectedConcepts = List.copyOf(expectedConcepts);
            rubric = List.copyOf(rubric);
            commonMistakes = List.copyOf(commonMistakes);
            transferTargets = List.copyOf(transferTargets);
            hints = Map.copyOf(hints);
        }
    }

    /** 적재 결과 (로그용). */
    public record SeedOutcome(int created, int updated, int rejected, int retired) {}

    /** 로그에 남길 거절 seed key (기동을 막지는 않는다 — 검증이 먼저 걸러 준다). */
    public List<String> rejectedSeedKeys() {
        List<String> keys = new ArrayList<>();
        for (Challenge challenge : challengeRepository.findAllSeeds()) {
            if (challenge.getStatus() == ChallengeStatus.REJECTED) {
                String seedKey = challenge.getSeedKey();
                if (seedKey != null) {
                    keys.add(seedKey);
                }
            }
        }
        return List.copyOf(keys);
    }
}
