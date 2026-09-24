package com.devpilot.today.domain;

/**
 * 설명이 안 통한 이유 (docs/05 §21.10, ADR-047). 재설명의 방향을 잡는 데만 쓴다.
 *
 * <p><b>자유 입력을 받지 않는 이유</b>: 고정 선택지 하나면 방향을 잡기에 충분하고, 마스킹·개인정보·프롬프트 주입을 다룰 일이 생기지 않는다. 부족하면 그때 넓힌다.
 */
public enum ConfusionReason {
    /** 용어가 낯설다. 쓰인 말부터 풀어 준다. */
    UNFAMILIAR_TERMS,

    /** 무엇인지는 알겠는데 왜 그런지 모르겠다. 이유와 배경을 짚는다. */
    WHY_NOT_CLEAR,

    /** 예제가 무엇을 보여 주는지 모르겠다. 예제를 줄 단위로 읽어 준다. */
    EXAMPLE_UNCLEAR
}
