package com.devpilot.project.domain;

/** 사이드 프로젝트 상태 (docs/04 §3, §4.9). 세 상태 사이 모든 전이를 허용한다. */
public enum SideProjectStatus {
    ACTIVE,
    PAUSED,
    DONE
}
