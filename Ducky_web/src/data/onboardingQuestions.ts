import { OnboardingQuestion } from "@/types/onboarding";

export const onboardingQuestions: OnboardingQuestion[] = [
  // 1. 정보 처리 방식 (Active vs Reflective)
  {
    id: 1,
    axis: "processing",
    text: "새로운 개념을 배울 때 나는",
    optionA: { text: "직접 해보면서 이해하는 편이다", value: "active" },
    optionB: { text: "먼저 생각하고 정리하면서 이해하는 편이다", value: "reflective" },
  },
  {
    id: 2,
    axis: "processing",
    text: "문제를 해결할 때 나는",
    optionA: { text: "일단 시도해보며 해결 방법을 찾는다", value: "active" },
    optionB: { text: "해결 방법을 충분히 생각한 뒤 시작한다", value: "reflective" },
  },
  {
    id: 3,
    axis: "processing",
    text: "공부할 때 더 도움이 되는 것은",
    optionA: { text: "토론하거나 설명하는 과정", value: "active" },
    optionB: { text: "혼자 생각하며 정리하는 시간", value: "reflective" },
  },
  {
    id: 4,
    axis: "processing",
    text: "막히는 문제가 생기면 나는",
    optionA: { text: "이것저것 시도해보며 해결한다", value: "active" },
    optionB: { text: "원인을 먼저 분석하고 정리한다", value: "reflective" },
  },

  // 2. 이해 구조 방식 (Sequential vs Global)
  {
    id: 5,
    axis: "structure",
    text: "새로운 내용을 배울 때 나는",
    optionA: { text: "단계별로 차근차근 배우는 것이 좋다", value: "sequential" },
    optionB: { text: "전체 흐름과 구조를 먼저 알고 싶다", value: "global" },
  },
  {
    id: 6,
    axis: "structure",
    text: "문제를 해결할 때 나는",
    optionA: { text: "순서대로 하나씩 해결한다", value: "sequential" },
    optionB: { text: "전체 아이디어를 먼저 떠올린다", value: "global" },
  },
  {
    id: 7,
    axis: "structure",
    text: "설명을 들을 때 더 이해가 잘 되는 것은",
    optionA: { text: "단계별 설명", value: "sequential" },
    optionB: { text: "전체 개요 설명", value: "global" },
  },
  {
    id: 8,
    axis: "structure",
    text: "공부할 때 나는",
    optionA: { text: "작은 개념부터 쌓아가는 편이다", value: "sequential" },
    optionB: { text: "전체 그림을 먼저 이해하려고 한다", value: "global" },
  },

  // 3. 정보 표현 방식 (Visual vs Verbal)
  {
    id: 9,
    axis: "representation",
    text: "새로운 개념을 이해할 때 나는",
    optionA: { text: "그림이나 구조를 보면 이해가 쉽다", value: "visual" },
    optionB: { text: "설명을 들으면 이해가 쉽다", value: "verbal" },
  },
  {
    id: 10,
    axis: "representation",
    text: "강의나 설명을 들은 후 기억에 남는 것은",
    optionA: { text: "흐름도, 구조, 화면 구성", value: "visual" },
    optionB: { text: "설명 내용이나 말", value: "verbal" },
  },
  {
    id: 11,
    axis: "representation",
    text: "문제를 이해할 때 나는",
    optionA: { text: "구조나 흐름을 시각적으로 떠올린다", value: "visual" },
    optionB: { text: "문장이나 설명으로 정리한다", value: "verbal" },
  },
  {
    id: 12,
    axis: "representation",
    text: "학습 자료 중 더 선호하는 것은",
    optionA: { text: "도표, 다이어그램, 시각 자료", value: "visual" },
    optionB: { text: "텍스트 설명, 말로 된 설명", value: "verbal" },
  },
];
