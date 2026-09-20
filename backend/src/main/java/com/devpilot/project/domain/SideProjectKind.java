package com.devpilot.project.domain;

/**
 * 사이드 프로젝트 분류 (docs/04 §3). 상태가 아니라 분류이므로 전이표가 없다(docs/04 §4.9, I-23). {@code PAST_WORK} 프로젝트는
 * planner의 {@code PROJECT_TASK} 대상에서 빠진다(docs/06 SP-3).
 */
public enum SideProjectKind {
    SIDE,
    PAST_WORK
}
