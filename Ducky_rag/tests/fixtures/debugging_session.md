# 빈 리스트 평균 계산 디버깅

## 재현 코드

```python
def average(values):
    return sum(values) / len(values)

average([])
```

## 실행 결과

```text
Traceback (most recent call last):
  File "average.py", line 4, in <module>
    average([])
  File "average.py", line 2, in average
    return sum(values) / len(values)
ZeroDivisionError: division by zero
```

## 기대 동작

일반 입력 [2, 4, 6]의 평균은 4다. 빈 리스트에 대한 처리 방식은 함수의 입력 계약으로 먼저 정한다.

## 확인 질문

- 빈 리스트를 전달하면 sum(values)와 len(values)는 각각 어떤 값인가?
- 실제 결과와 기대 동작은 어디에서 달라지는가?
- 정답을 바로 수정하기 전에 입력 조건과 각 실행 단계를 말로 설명해 보자.
