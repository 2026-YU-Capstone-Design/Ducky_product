import {
  BarChart3,
  History,
  LayoutDashboard,
  MessageCircle,
  Settings,
  type LucideIcon,
} from "lucide-react";

export type AppRoute = "/dashboard" | "/chat" | "/analysis" | "/sessions" | "/settings";

export interface NavItem {
  href: AppRoute;
  label: string;
  description: string;
  icon: LucideIcon;
}

export const navItems: NavItem[] = [
  {
    href: "/dashboard",
    label: "대시보드",
    description: "학습 요약",
    icon: LayoutDashboard,
  },
  {
    href: "/chat",
    label: "대화",
    description: "러버덕 학습",
    icon: MessageCircle,
  },
  {
    href: "/analysis",
    label: "분석",
    description: "학습 리포트",
    icon: BarChart3,
  },
  {
    href: "/sessions",
    label: "기록",
    description: "세션 목록",
    icon: History,
  },
  {
    href: "/settings",
    label: "설정",
    description: "프로필과 환경",
    icon: Settings,
  },
];
