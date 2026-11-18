interface DuckyMarkProps {
  size?: number;
  className?: string;
}

export function DuckyMark({ size = 34, className }: DuckyMarkProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 34 34"
      fill="none"
      role="img"
      aria-label="Ducky 로고"
      className={className}
    >
      <rect width="34" height="34" rx="10" fill="#FECA43" />
      <ellipse cx="14.17" cy="18.13" rx="9.07" ry="7.65" fill="#FFFCF5" />
      <path
        d="M20.97 19.27
           Q28.33 17.86 30.6 19.84
           Q30.89 22.13 28.61 23.27
           Q25.21 24.7 20.97 22.13 Z"
        fill="#FFFCF5"
      />
    </svg>
  );
}
