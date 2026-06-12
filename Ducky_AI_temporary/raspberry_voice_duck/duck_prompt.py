from __future__ import annotations


STUCK_KEYWORDS = ("막혔", "모르겠", "힌트", "어려워", "안돼", "안 되")
ERROR_KEYWORDS = (
    "에러",
    "오류",
    "exception",
    "traceback",
    "index",
    "인덱스",
    "none",
    "null",
    "undefined",
)
CODE_KEYWORDS = ("코드", "함수", "반복문", "파이썬", "자바", "javascript", "spring")


def _summary(user_text: str) -> str:
    cleaned = " ".join(user_text.split())
    if len(cleaned) <= 45:
        return cleaned
    return f"{cleaned[:45]}..."


def generate_local_duck_response(user_text: str) -> str:
    """
    Generate a local rubber-duck response when the server is unavailable.

    The response intentionally avoids giving the direct answer and asks for the
    next thinking step instead.
    """
    normalized = user_text.lower()
    short_summary = _summary(user_text)

    if any(keyword in normalized for keyword in STUCK_KEYWORDS):
        return (
            f"좋아요. 지금은 '{short_summary}' 부분에서 막힌 상황으로 보여요. "
            "힌트를 한 단계만 줄게요. 먼저 기대한 결과와 실제 결과를 나란히 말해볼래요?"
        )

    if any(keyword in normalized for keyword in ERROR_KEYWORDS):
        return (
            f"좋아요. '{short_summary}' 문제는 오류 위치를 좁히는 게 먼저예요. "
            "어느 줄에서 문제가 생기고, 그 줄의 입력값은 무엇인지 말해볼래요?"
        )

    if any(keyword in normalized for keyword in CODE_KEYWORDS):
        return (
            f"좋아요. '{short_summary}' 내용을 코드 실행 흐름 관점에서 다시 보죠. "
            "입력이 들어온 뒤 어떤 순서로 값이 바뀌는지 한 단계씩 설명해볼래요?"
        )

    return (
        f"좋아요. '{short_summary}'라고 설명해줬어요. "
        "바로 답을 찾기 전에, 지금 가장 먼저 확인해야 할 조건이 무엇인지 말해볼래요?"
    )
