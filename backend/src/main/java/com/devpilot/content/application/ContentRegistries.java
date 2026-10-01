package com.devpilot.content.application;

import com.devpilot.plan.application.PlanTemplateRegistry;
import com.devpilot.review.application.SeedCardRegistry;
import com.devpilot.review.application.TermRegistry;
import com.devpilot.today.application.ChecklistRegistry;
import com.devpilot.today.application.ConceptReadingRegistry;
import com.devpilot.today.application.CuratedReadingRegistry;
import com.devpilot.today.application.DailyTipRegistry;
import com.devpilot.today.application.LessonRegistry;
import org.springframework.stereotype.Component;

/**
 * 기동 시 콘텐츠를 받는 메모리 registry 묶음 (docs/03 §2.2, docs/04 §9). 콘텐츠 종류가 늘 때마다 {@link
 * ContentRegistration}의 생성자를 늘리지 않으려고 한 자리에 모았다.
 *
 * <p>registry는 각자 자기 모듈이 갖는다 — {@code content}는 값을 넣어 줄 뿐이다.
 */
@Component
record ContentRegistries(
        PlanTemplateRegistry planTemplates,
        SeedCardRegistry seedCards,
        CuratedReadingRegistry curatedReadings,
        ConceptReadingRegistry conceptReadings,
        LessonRegistry lessons,
        DailyTipRegistry tips,
        ChecklistRegistry checklists,
        TermRegistry terms) {}
