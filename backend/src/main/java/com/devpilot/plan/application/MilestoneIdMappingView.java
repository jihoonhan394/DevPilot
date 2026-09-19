package com.devpilot.plan.application;

import java.util.UUID;

/** replan의 milestone id 대응 (docs/05 §7.8). 새 milestone은 모두 새 UUID를 받는다. */
public record MilestoneIdMappingView(UUID previousId, UUID newId) {}
