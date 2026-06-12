"use client";

import { useState } from "react";

export function useLocalStorage<T>(key: string, initialValue: T): [T, (value: T | ((val: T) => T)) => void] {
  // 값을 보관할 상태(State) 선언
  // useState의 초기값 함수 전달로 로컬스토리지 조회 로직이 최초 1회만 실행되도록 설정
  const [storedValue, setStoredValue] = useState<T>(() => {
    if (typeof window === "undefined") {
      return initialValue;
    }
    try {
      const item = window.localStorage.getItem(key);
      return item ? JSON.parse(item) : initialValue;
    } catch (error) {
      console.log(error);
      return initialValue;
    }
  });
  // 로컬스토리지에 새 값을 저장하고 상태를 업데이트하는 함수
  const setValue = (value: T | ((val: T) => T)) => {
    try {
      // 일반 값 외에도 useState처럼 함수형 업데이트를 지원하도록 처리
      const valueToStore = value instanceof Function ? value(storedValue) : value;
      // 상태 저장
      setStoredValue(valueToStore);
      // 로컬스토리지에 저장
      if (typeof window !== "undefined") {
        window.localStorage.setItem(key, JSON.stringify(valueToStore));
      }
    } catch (error) {
      console.log(error);
    }
  };

  return [storedValue, setValue];
}
