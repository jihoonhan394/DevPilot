package com.devpilot.common.domain;

/**
 * 콘텐츠 출처 (docs/04 §3 {@code ContentOrigin}, common). challenge·review item이 같이 쓰므로 common에 둔다.
 *
 * <ul>
 *   <li>{@code SEED}: 저장소 {@code content/} YAML
 *   <li>{@code MANUAL}: 사용자가 직접 만든 것
 *   <li>{@code AI_GENERATED}: AI 출력에서 만든 것 (서버 가드를 거친 뒤)
 * </ul>
 */
public enum ContentOrigin {
    SEED,
    MANUAL,
    AI_GENERATED
}
