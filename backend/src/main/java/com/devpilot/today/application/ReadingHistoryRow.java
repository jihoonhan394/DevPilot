package com.devpilot.today.application;

import java.time.LocalDate;

/** 내가 받은 읽기 과제 한 줄 (docs/05 §19.14). 같은 reading이 여러 번 나왔으면 가장 최근 plan-day로 묶는다. */
public record ReadingHistoryRow(String readingKey, LocalDate lastPlanDate) {}
