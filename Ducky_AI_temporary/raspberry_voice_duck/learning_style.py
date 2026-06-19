"""
Learning style prompt helpers aligned with ChatResponseService on the server.
"""

from __future__ import annotations

from typing import TypedDict


class LearningStyle(TypedDict):
    processing: str
    expression: str
    understanding: str


DEFAULT_LEARNING_STYLE: LearningStyle = {
    "processing": "active",
    "expression": "visual",
    "understanding": "sequential",
}


def normalize_learning_style(value: str | None, fallback: str) -> str:
    if not value or not value.strip():
        return fallback
    return value.strip().lower()


def resolve_learning_style(raw: dict[str, str] | None) -> LearningStyle:
    if raw is None:
        return dict(DEFAULT_LEARNING_STYLE)

    return {
        "processing": normalize_learning_style(
            raw.get("processing"),
            DEFAULT_LEARNING_STYLE["processing"],
        ),
        "expression": normalize_learning_style(
            raw.get("expression"),
            DEFAULT_LEARNING_STYLE["expression"],
        ),
        "understanding": normalize_learning_style(
            raw.get("understanding") or raw.get("structure"),
            DEFAULT_LEARNING_STYLE["understanding"],
        ),
    }


def _processing_instruction(processing: str) -> str:
    if processing == "reflective":
        return "조건, 원인, 가정을 먼저 정리하게 돕고 성급한 결론보다 점검 순서를 제안한다."
    if processing == "active":
        return "바로 해볼 작은 실험, 명령, 확인 단계를 먼저 제안한다."
    return "작게 시도할 단계와 생각을 정리할 단계를 함께 제안한다."


def _expression_instruction(expression: str) -> str:
    if expression == "verbal":
        return "말로 풀어 설명하고 짧은 비유나 핵심 문장으로 정리한다."
    if expression == "visual":
        return "구조, 흐름, 비교표, 단계 구분처럼 눈에 보이는 형태로 정리한다."
    return "구조와 말 설명을 함께 사용한다."


def _understanding_instruction(understanding: str) -> str:
    if understanding == "global":
        return "전체 그림을 먼저 잡고 세부 단계로 내려간다."
    if understanding == "sequential":
        return "순서대로 한 단계씩 이어지게 설명한다."
    return "전체 맥락과 다음 단계를 모두 짧게 연결한다."


def build_learning_style_instructions(style: LearningStyle) -> str:
    return (
        f"- 처리 방식({style['processing']}): {_processing_instruction(style['processing'])}\n"
        f"- 표현 선호({style['expression']}): {_expression_instruction(style['expression'])}\n"
        f"- 이해 구조({style['understanding']}): {_understanding_instruction(style['understanding'])}"
    )
