import { Dispatch, SetStateAction } from "react";
import { ChevronLeft } from "lucide-react";

/**
 * 약관 모달 컴포넌트에 전달되는 props
 */
interface TermsModalProps {
  /** 현재 표시할 약관 종류 (service: 이용약관, marketing: 마케팅동의, null: 숨김) */
  showTerms: "service" | "marketing" | null;
  /** 약관 모달 표시 상태를 업데이트하는 함수 */
  setShowTerms: Dispatch<SetStateAction<"service" | "marketing" | null>>;
}

/**
 * 서비스 이용 약관 및 마케팅 수신 동의 내용을 보여주는 모달 컴포넌트
 */
export function TermsModal({ showTerms, setShowTerms }: TermsModalProps) {
  if (!showTerms) return null;
  const isService = showTerms === "service";
  const title = isService ? "서비스 이용 약관" : "마케팅 및 메시지 수신 동의";

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#FAF8F5] p-4 animate-fade-in duration-300 sm:absolute sm:p-0 sm:rounded-lg">
      <div className="mb-2 flex shrink-0 items-center justify-between border-b border-zinc-200/60 p-2 pb-4">
        <button
          type="button"
          onClick={() => setShowTerms(null)}
          className="p-2 hover:bg-zinc-200/50 rounded-full transition-colors focus:outline-none -ml-2"
        >
          <ChevronLeft className="h-6 w-6 text-zinc-800" />
        </button>
        <h3 className="min-w-0 flex-1 text-center text-base font-extrabold text-zinc-800 break-keep sm:text-lg">{title}</h3>
        <div className="w-10"></div> {/* Spacer for centering */}
      </div>
      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto pb-10 pr-1 text-sm leading-relaxed text-zinc-600 sm:pr-2">
        {isService ? (
          <>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">제1조 (목적)</p>
              <p>
                본 약관은 Ducky(이하 &quot;회사&quot;)가 제공하는 AI 러버덕 학습 서비스(이하
                &quot;서비스&quot;)의 이용과 관련하여 회사와 회원 간의 권리, 의무 및
                책임사항 등을 규정함을 목적으로 합니다.
              </p>
            </div>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">
                제2조 (용어의 정의)
              </p>
              <p>
                1. &quot;회원&quot;이란 본 약관에 따라 회사와 이용계약을 체결하고 회사가
                제공하는 서비스를 이용하는 자를 말합니다.
              </p>
              <p>
                2. &quot;덕버깅(DuckBugging)&quot;이란 사용자가 AI 러버덕과 대화하며 스스로
                문제를 해결하도록 돕는 학습 방식을 의미합니다.
              </p>
            </div>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">
                제3조 (약관의 효력 및 변경)
              </p>
              <p>
                본 약관은 회원이 동의함으로써 효력이 발생하며, 회사는 관련 법령을
                위배하지 않는 범위 내에서 약관을 개정할 수 있습니다.
              </p>
            </div>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">
                제4조 (서비스의 제공 및 중단)
              </p>
              <p>
                회사는 연중무휴 1일 24시간 서비스 제공을 원칙으로 하나, 시스템
                점검, 증설 및 교체 등의 이유로 서비스를 일시 중단할 수 있습니다.
              </p>
            </div>
          </>
        ) : (
          <>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">
                마케팅 정보 수신 동의
              </p>
              <p>
                회사는 회원의 학습 효율을 높이기 위한 맞춤형 팁, 신규 기능
                업데이트 소식, 이벤트 및 혜택 정보를 제공하기 위해 마케팅 정보를
                발송할 수 있습니다.
              </p>
            </div>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">
                수집 및 이용 목적
              </p>
              <ul className="list-disc pl-4 space-y-1">
                <li>신규 서비스 안내 및 맞춤형 학습 콘텐츠 제안</li>
                <li>이벤트 당첨 결과 안내 및 경품 제공</li>
              </ul>
            </div>
            <div>
              <p className="font-bold text-zinc-800 text-base mb-1">
                보유 및 이용 기간
              </p>
              <p>동의 철회 시 또는 회원 탈퇴 시까지 보관 및 이용됩니다.</p>
            </div>
            <p className="text-xs font-bold text-[#FECA43] bg-[#FECA43]/10 p-3 rounded-xl">
              * 본 동의는 선택사항이므로 동의하지 않으셔도 서비스의 기본 기능은
              이용하실 수 있습니다.
            </p>
          </>
        )}
      </div>
    </div>
  );
}
