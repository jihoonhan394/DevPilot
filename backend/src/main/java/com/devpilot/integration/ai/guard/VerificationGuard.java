package com.devpilot.integration.ai.guard;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.integration.ai.api.AiOperation;
import com.devpilot.integration.ai.api.GuardContext;
import com.devpilot.integration.ai.api.output.CoachFindingOutput;
import com.devpilot.integration.ai.api.output.CoachReviewOutput;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 검증 상태 가드 (docs/06 §10, docs/17 §6.2). {@code COACH_REVIEW}의 finding마다 규칙 1~5를 순서대로 적용한다. 수정만 한다.
 * <b>서버는 URL을 fetch하지 않는다</b> — 호스트 문자열만 검사한다. curated ID는 기동 시 {@code
 * devpilot.ai.curated-sources-location}에서 읽는다.
 */
@Component
public class VerificationGuard implements OutputGuard {

    private static final Set<String> TOOL_SOURCES =
            Set.of("COMPILER", "TEST_RESULT", "STATIC_ANALYSIS");
    private static final Set<String> DOC_SOURCES = Set.of("OFFICIAL_DOC", "SECURITY_GUIDE");
    private static final String AI_REASONING = "AI_REASONING";
    private static final String AI_JUDGMENT = "AI_JUDGMENT";
    private static final String UNCERTAIN = "UNCERTAIN";
    private static final String SUPPORTED = "SUPPORTED";
    private static final String VERIFIED = "VERIFIED";
    private static final String CURATED_SOURCE = "CURATED_SOURCE";

    private final List<String> trustedHosts;
    private final Set<String> curatedIds;

    @Autowired
    public VerificationGuard(DevPilotProperties properties, ResourceLoader resourceLoader) {
        this(
                properties.ai().trustedSourceHosts(),
                loadCuratedIds(
                        resourceLoader.getResource(properties.ai().curatedSourcesLocation())));
    }

    VerificationGuard(List<String> trustedHosts, Set<String> curatedIds) {
        this.trustedHosts = List.copyOf(trustedHosts);
        this.curatedIds = Set.copyOf(curatedIds);
    }

    @Override
    public GuardName name() {
        return GuardName.VERIFICATION;
    }

    @Override
    public GuardOutcome apply(
            AiOperation operation, Object output, GuardContext context, boolean lastAttempt) {
        if (!(output instanceof CoachReviewOutput review)) {
            return GuardOutcome.unchanged(output);
        }
        GuardOutcome.Builder result = new GuardOutcome.Builder(name());
        List<CoachFindingOutput> findings = new ArrayList<>();
        boolean changed = false;
        for (int i = 0; i < review.findings().size(); i++) {
            CoachFindingOutput finding = review.findings().get(i);
            CoachFindingOutput checked = check(finding, "finding[" + i + "]", result);
            changed |= !checked.equals(finding);
            findings.add(checked);
        }
        Object value =
                changed
                        ? new CoachReviewOutput(
                                review.confidentialSuspected(),
                                review.selfReviewAxes(),
                                review.incorrectClaims(),
                                findings)
                        : review;
        return result.build(value);
    }

    /** docs/06 §10 규칙 1~5. */
    CoachFindingOutput check(CoachFindingOutput finding, String path, GuardOutcome.Builder result) {
        Claim claim = new Claim(finding.sourceType(), finding.verificationStatus());
        String reference = finding.sourceReference();
        downgradeToolClaim(claim, path, result);
        downgradeUnknownCurated(claim, reference, path, result);
        downgradeVerified(claim, reference, path, result);
        downgradeUntrustedSource(claim, reference, path, result);
        downgradeSupportedWithoutSource(claim, path, result);
        String status = claim.status;
        String source = claim.source;
        if (status.equals(finding.verificationStatus()) && source.equals(finding.sourceType())) {
            return finding;
        }
        return new CoachFindingOutput(
                finding.findingType(),
                finding.category(),
                finding.summary(),
                finding.learningQuestion(),
                finding.startLine(),
                finding.endLine(),
                status,
                finding.confidence(),
                source,
                finding.sourceReference(),
                finding.relatedSkillCode(),
                finding.mentionedByUser());
    }

    /** 규칙 1: 도구 실행을 주장하는 출처는 AI 추론으로 낮춘다. */
    private static void downgradeToolClaim(Claim claim, String path, GuardOutcome.Builder result) {
        if (!TOOL_SOURCES.contains(claim.source)) {
            return;
        }
        String before = claim.status;
        claim.source = AI_REASONING;
        claim.status = UNCERTAIN.equals(before) ? UNCERTAIN : AI_JUDGMENT;
        result.action("DOWNGRADED_TOOL_CLAIM", path + " " + before + "→" + claim.status);
    }

    /** 규칙 2: 큐레이션 목록에 없는 curated 출처. */
    private void downgradeUnknownCurated(
            Claim claim, @Nullable String reference, String path, GuardOutcome.Builder result) {
        if (!CURATED_SOURCE.equals(claim.source)
                || (reference != null && curatedIds.contains(reference.strip()))) {
            return;
        }
        String before = claim.status;
        claim.source = AI_REASONING;
        claim.status = AI_JUDGMENT;
        result.action("DOWNGRADED_UNKNOWN_CURATED", path + " " + before + "→" + claim.status);
    }

    /** 규칙 3: {@code VERIFIED}는 curated 출처만. */
    private void downgradeVerified(
            Claim claim, @Nullable String reference, String path, GuardOutcome.Builder result) {
        if (!VERIFIED.equals(claim.status) || CURATED_SOURCE.equals(claim.source)) {
            return;
        }
        String downgraded =
                DOC_SOURCES.contains(claim.source) && trusted(reference) ? SUPPORTED : AI_JUDGMENT;
        result.action("DOWNGRADED_VERIFIED", path + " VERIFIED→" + downgraded);
        claim.status = downgraded;
    }

    /** 규칙 4: 문서 출처인데 참조가 allowlist 밖. */
    private void downgradeUntrustedSource(
            Claim claim, @Nullable String reference, String path, GuardOutcome.Builder result) {
        if (!DOC_SOURCES.contains(claim.source) || trusted(reference)) {
            return;
        }
        String before = claim.status;
        claim.source = AI_REASONING;
        claim.status = UNCERTAIN.equals(before) ? UNCERTAIN : AI_JUDGMENT;
        result.action("DOWNGRADED_UNTRUSTED_SOURCE", path + " " + before + "→" + claim.status);
    }

    /** 규칙 5: 출처 없는 {@code SUPPORTED}. */
    private static void downgradeSupportedWithoutSource(
            Claim claim, String path, GuardOutcome.Builder result) {
        if (SUPPORTED.equals(claim.status) && AI_REASONING.equals(claim.source)) {
            claim.status = AI_JUDGMENT;
            result.action(
                    "DOWNGRADED_SUPPORTED_WITHOUT_SOURCE", path + " SUPPORTED→" + claim.status);
        }
    }

    /** 규칙을 차례로 적용하는 동안의 출처·검증 상태. */
    private static final class Claim {
        private String source;
        private String status;

        Claim(String source, String status) {
            this.source = source;
            this.status = status;
        }
    }

    /**
     * https URL이고 사용자 정보가 없고 호스트가 allowlist(정확 일치 또는 하위 도메인)인가 (docs/17 §6.2). 클라이언트도 이 값이 true일 때만
     * {@code sourceReference}를 링크로 만든다(docs/17 §10.1).
     */
    public boolean trusted(@Nullable String reference) {
        if (reference == null) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(reference.strip());
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!"https".equals(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String allowed : trustedHosts) {
            if (host.equals(allowed) || host.endsWith("." + allowed)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> loadCuratedIds(Resource resource) {
        if (!resource.exists()) {
            return Set.of();
        }
        try {
            Object root =
                    new Yaml(new SafeConstructor(new LoaderOptions()))
                            .load(resource.getContentAsString(StandardCharsets.UTF_8));
            Set<String> ids = new HashSet<>();
            if (root instanceof Map<?, ?> map && map.get("sources") instanceof List<?> sources) {
                for (Object source : sources) {
                    if (source instanceof Map<?, ?> entry && entry.get("id") instanceof String id) {
                        ids.add(id);
                    }
                }
            }
            return ids;
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot read curated sources", exception);
        }
    }
}
