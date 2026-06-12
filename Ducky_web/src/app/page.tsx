"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useLocalStorage } from "@/hooks/useLocalStorage";
import { Comfortaa } from "next/font/google";

const comfortaa = Comfortaa({
  subsets: ["latin"],
  weight: ["700"],
  variable: "--font-comfortaa",
});
import { SignupStep1 } from "@/components/auth/SignupStep1";
import { SignupStep2 } from "@/components/auth/SignupStep2";
import { SignupStep3 } from "@/components/auth/SignupStep3";
import { TermsModal } from "@/components/auth/TermsModal";
import { LoginOptions } from "@/components/auth/LoginOptions";
import { EmailLogin } from "@/components/auth/EmailLogin";

/**
 * 랜딩 페이지 컴포넌트
 * - 스플래시 화면, 로그인 옵션, 이메일 로그인, 회원가입 플로우를 관리합니다.
 */
export default function LandingPage() {
  const router = useRouter();
  const [phase, setPhase] = useState<1 | 2 | 3>(1);
  const [emailMode, setEmailMode] = useState<"options" | "login" | "signup">("options");
  const [signupStep, setSignupStep] = useState<1 | 2 | 3>(1);
  const [showTerms, setShowTerms] = useState<"service" | "marketing" | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [currentSplash, setCurrentSplash] = useState(1);
  const [error, setError] = useState("");

  // 사용자 로그인 상태 및 정보 영구 저장
  const [, setUser] = useLocalStorage("ducky_user", {
    id: "",
    name: "",
    level: "beginner" as "beginner" | "intermediate" | "advanced",
    learningStyle: {
      processing: "active" as "active" | "reflective",
      expression: "visual" as "visual" | "verbal",
      understanding: "sequential" as "sequential" | "global"
    },
    onboarded: false
  });

  // 회원가입 상태 관리
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [isEmailChecked, setIsEmailChecked] = useState(false);
  const [isCheckingEmail, setIsCheckingEmail] = useState(false);
  const [emailError, setEmailError] = useState("");
  const [emailSuccess, setEmailSuccess] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [passwordConfirmError, setPasswordConfirmError] = useState("");
  const [passwordConfirmSuccess, setPasswordConfirmSuccess] = useState("");
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [agreeMarketing, setAgreeMarketing] = useState(true);

  useEffect(() => {
    // 1단계 -> 2단계 전환 (1.2초)
    const t1 = setTimeout(() => {
      setPhase(2);
    }, 1200);

    // 2단계 -> 3단계 전환 (총 2.4초)
    const t2 = setTimeout(() => {
      setPhase(3);
    }, 2400);

    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, []);

  // 3단계일 때 1부터 7까지 스플래시 이미지 동적 순환
  useEffect(() => {
    if (phase !== 3) return;
    const interval = setInterval(() => {
      setCurrentSplash((prev) => (prev % 7) + 1);
    }, 4500); // 4.5초 주기
    return () => clearInterval(interval);
  }, [phase]);

  const handleSocialLogin = (platform: "kakao" | "naver") => {
    setIsLoading(true);
    setError("");
    // API 로그인 시뮬레이션
    setTimeout(() => {
      setIsLoading(false);
      setUser(prev => ({
        ...prev,
        id: `mock-${platform}-user`,
        name: platform === "kakao" ? "카카오 유저" : "네이버 유저",
      }));
      router.push("/dashboard");
    }, 1500);
  };

  const handleEmailLoginSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError("이메일과 비밀번호를 모두 입력해 주세요.");
      return;
    }
    setIsLoading(true);
    setError("");
    
    // 이메일 로그인 시뮬레이션
    setTimeout(() => {
      setIsLoading(false);
      setUser(prev => ({
        ...prev,
        id: "mock-email-user",
        name: email.split("@")[0],
      }));
      router.push("/dashboard");
    }, 1500);
  };

  const handleSignupSubmit = () => {
    setIsLoading(true);
    // 회원가입 시뮬레이션
    setTimeout(() => {
      setIsLoading(false);
      setUser(prev => ({
        ...prev,
        id: "mock-email-user",
        name: name,
        onboarded: false
      }));
      router.push("/onboarding");
    }, 1500);
  };

  // 회원가입 - 이메일 입력 핸들러
  const handleEmailChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setEmail(val);
    setIsEmailChecked(false);
    setEmailSuccess("");
    if (val.length > 0 && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(val)) {
      setEmailError("이메일 형식이 올바르지 않습니다.");
    } else {
      setEmailError("");
    }
  };

  // 회원가입 - 중복확인 핸들러
  const handleDuplicateCheck = () => {
    if (emailError || !email) return;
    setIsCheckingEmail(true);
    setEmailSuccess("");
    setEmailError("");
    
    setTimeout(() => {
      setIsCheckingEmail(false);
      if (email === "exist@ducky.com" || email === "test@test.com") {
        setEmailError("이미 존재하는 이메일입니다.");
        setIsEmailChecked(false);
      } else {
        setEmailSuccess("중복 확인이 완료되었습니다.");
        setIsEmailChecked(true);
      }
    }, 800);
  };

  // 회원가입 - 비밀번호 입력 핸들러
  const handlePasswordChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setPassword(val);
    if (val.length > 0 && !/^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#&*])[a-zA-Z\d!@#&*]{8,16}$/.test(val)) {
      setPasswordError("비밀번호 형식이 올바르지 않습니다.");
    } else {
      setPasswordError("");
    }
    
    if (passwordConfirm.length > 0) {
      if (val !== passwordConfirm) {
        setPasswordConfirmError("비밀번호가 일치하지 않습니다.");
        setPasswordConfirmSuccess("");
      } else {
        setPasswordConfirmError("");
        if (val.length > 0 && (!passwordError || /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#&*])[a-zA-Z\d!@#&*]{8,16}$/.test(val))) {
          setPasswordConfirmSuccess("비밀번호가 일치합니다.");
        }
      }
    }
  };

  // 회원가입 - 비밀번호 확인 입력 핸들러
  const handlePasswordConfirmChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const val = e.target.value;
    setPasswordConfirm(val);
    if (val.length > 0) {
      if (val !== password) {
        setPasswordConfirmError("비밀번호가 일치하지 않습니다.");
        setPasswordConfirmSuccess("");
      } else {
        setPasswordConfirmError("");
        if (!passwordError) {
          setPasswordConfirmSuccess("비밀번호가 일치합니다.");
        }
      }
    } else {
      setPasswordConfirmError("");
      setPasswordConfirmSuccess("");
    }
  };

  // 1단계: 순수 스플래시 화면 (선명한 옐로우 배경 #FECA43)
  if (phase === 1) {
    return (
      <div className={`flex min-h-dvh w-full flex-col items-center justify-center bg-[#FECA43] px-6 text-white ${comfortaa.className} transition-all duration-700`}>
        <div className="flex flex-col items-center space-y-4">
          <h1 className="text-[clamp(3rem,13vw,5rem)] font-bold tracking-tight select-none">Ducky</h1>
        </div>
      </div>
    );
  }

  // 2단계: 은은한 크림 베이지 배경 및 옐로우 로고 스플래시 (배경: #FAF8F5, 로고: #FECA43)
  if (phase === 2) {
    return (
      <div className={`flex min-h-dvh w-full flex-col items-center justify-center bg-[#FAF8F5] px-6 text-[#FECA43] ${comfortaa.className} transition-all duration-700`}>
        <div className="flex flex-col items-center space-y-2">
          <h1 className="text-[clamp(3rem,13vw,5rem)] font-bold tracking-tight select-none">Ducky</h1>
        </div>
      </div>
    );
  }

  // 3단계: 메인 로그인 화면
  return (
    <div className={`flex min-h-dvh w-full flex-col overflow-y-auto bg-[#FAF8F5] px-4 py-6 font-sans transition-all duration-500 sm:px-6 lg:px-10 ${
      emailMode === "options"
        ? "items-center justify-center"
        : "items-center justify-start sm:justify-center"
    }`}>
      <div className={`relative flex w-full flex-col animate-fade-in duration-500 ${
        emailMode === "options" ? "max-w-5xl" : "min-h-[calc(100dvh-3rem)] max-w-md sm:min-h-[520px] sm:max-w-lg"
      }`}>
        
        {/* Render Terms Modal overlay */}
        <TermsModal showTerms={showTerms} setShowTerms={setShowTerms} />
        
        {/* 옵션 화면 */}
        {emailMode === "options" && (
          <LoginOptions
            currentSplash={currentSplash}
            comfortaaClassName={comfortaa.className}
            isLoading={isLoading}
            setEmailMode={setEmailMode}
            handleSocialLogin={handleSocialLogin}
          />
        )}

        {/* 기존 이메일 로그인 양식 */}
        {emailMode === "login" && (
          <EmailLogin
            email={email}
            setEmail={setEmail}
            password={password}
            setPassword={setPassword}
            error={error}
            isLoading={isLoading}
            setEmailMode={setEmailMode}
            setError={setError}
            handleEmailLoginSubmit={handleEmailLoginSubmit}
            setSignupStep={setSignupStep}
          />
        )}

        {/* 3단계 회원가입 위저드 */}
        {emailMode === "signup" && (
          <>
            {signupStep === 1 && (
              <SignupStep1
                name={name}
                setName={setName}
                setSignupStep={setSignupStep}
                setEmailMode={setEmailMode}
                setError={setError}
              />
            )}
            {signupStep === 2 && (
              <SignupStep2
                email={email}
                emailError={emailError}
                emailSuccess={emailSuccess}
                isEmailChecked={isEmailChecked}
                isCheckingEmail={isCheckingEmail}
                handleEmailChange={handleEmailChange}
                handleDuplicateCheck={handleDuplicateCheck}
                setSignupStep={setSignupStep}
              />
            )}
            {signupStep === 3 && (
              <SignupStep3
                password={password}
                passwordError={passwordError}
                passwordConfirm={passwordConfirm}
                passwordConfirmError={passwordConfirmError}
                passwordConfirmSuccess={passwordConfirmSuccess}
                agreeTerms={agreeTerms}
                setAgreeTerms={setAgreeTerms}
                agreeMarketing={agreeMarketing}
                setAgreeMarketing={setAgreeMarketing}
                isLoading={isLoading}
                handlePasswordChange={handlePasswordChange}
                handlePasswordConfirmChange={handlePasswordConfirmChange}
                setShowTerms={setShowTerms}
                handleSignupSubmit={handleSignupSubmit}
                setSignupStep={setSignupStep}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}
