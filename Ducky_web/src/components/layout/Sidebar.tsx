"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ChevronLeft, LogOut } from "lucide-react";
import { cn } from "@/lib/utils";
import { DuckyMark } from "./DuckyMark";
import { DuckyWordmark } from "./DuckyWordmark";
import { clearAuthSession } from "@/lib/auth/logout";
import type { User } from "@/types/user";
import { navItems } from "./navItems";

function getInitialCollapsedState() {
  if (typeof window === "undefined") {
    return false;
  }

  try {
    return window.localStorage.getItem("ducky.sidebar.collapsed") === "true";
  } catch {
    return false;
  }
}

export function Sidebar({ user }: { user: User }) {
  const router = useRouter();
  const pathname = usePathname();
  const [isCollapsed, setIsCollapsed] = useState(getInitialCollapsedState);
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);
  const profileMenuRef = useRef<HTMLDivElement>(null);

  function toggleSidebar() {
    setIsProfileMenuOpen(false);
    const next = !isCollapsed;
    setIsCollapsed(next);
    try {
      window.localStorage.setItem("ducky.sidebar.collapsed", String(next));
    } catch {}
  }

  useEffect(() => {
    if (!isProfileMenuOpen) return;

    function handlePointerDown(event: MouseEvent) {
      if (
        profileMenuRef.current &&
        !profileMenuRef.current.contains(event.target as Node)
      ) {
        setIsProfileMenuOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, [isProfileMenuOpen]);

  function handleLogout() {
    setIsProfileMenuOpen(false);
    clearAuthSession();
    router.replace("/");
  }

  return (
    <aside
      className={cn(
        "sticky top-0 hidden h-dvh shrink-0 border-r border-[#ECE7DC] bg-[#FFFCF5] py-5 transition-[width,padding] duration-200 dark:border-white/5 dark:bg-[#1C1812] md:flex md:flex-col",
        isCollapsed ? "w-[88px] px-2.5" : "w-64 px-3.5",
      )}
    >
      <div
        className={cn(
          "mb-6 flex items-center",
          isCollapsed ? "flex-col gap-2" : "justify-between gap-2 px-2.5",
        )}
      >
        <Link
          href="/dashboard"
          className={cn(
            "flex items-center py-1.5",
            isCollapsed ? "justify-center" : "gap-2",
          )}
          aria-label="대시보드로 이동"
        >
          <DuckyMark size={34} className="shrink-0" />
          {!isCollapsed && <DuckyWordmark />}
        </Link>

        <button
          type="button"
          onClick={toggleSidebar}
          aria-label={isCollapsed ? "사이드바 펼치기" : "사이드바 축소"}
          aria-pressed={isCollapsed}
          className={cn(
            "hidden size-8 shrink-0 items-center justify-center rounded-[9px] border border-[#ECE7DC] bg-[#FFF7E0] text-[#8C8470] outline-none transition-colors hover:bg-[#FFF3CF] focus-visible:ring-2 focus-visible:ring-[#FECA43]/60 dark:border-transparent dark:bg-white/5 dark:text-[#948C7C] dark:hover:bg-white/10 md:inline-flex",
          )}
        >
          <ChevronLeft
            className={cn(
              "size-4 transition-transform",
              isCollapsed && "rotate-180",
            )}
            aria-hidden="true"
          />
        </button>
      </div>

      {!isCollapsed && (
        <p className="px-3.5 pb-2 text-[11px] font-semibold tracking-wide text-[#A99B72] dark:text-[#7A7264]">
          메뉴
        </p>
      )}

      <nav
        aria-label="주요 메뉴"
        className="flex flex-1 flex-col gap-px overflow-y-auto"
      >
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive =
            pathname === item.href || pathname.startsWith(`${item.href}/`);

          return (
            <Link
              key={item.href}
              href={item.href}
              title={isCollapsed ? item.label : undefined}
              aria-label={isCollapsed ? item.label : undefined}
              aria-current={isActive ? "page" : undefined}
              className={cn(
                "group relative flex min-h-10 items-center rounded-[9px] py-2 text-[13.5px] font-normal text-[#5C574A] transition-colors hover:bg-[#FFF7E0]/60 dark:text-[#BEB7A6] dark:hover:bg-white/5",
                isCollapsed
                  ? "justify-center px-2"
                  : "items-center gap-2.5 pl-4 pr-3",
                isActive &&
                  "bg-[#FFF7E0] font-medium text-[#5A4300] hover:bg-[#FFF7E0] dark:bg-transparent dark:text-[#FBF6EC] dark:hover:bg-transparent",
              )}
            >
              {isActive && (
                <span
                  className={cn(
                    "absolute top-1.5 bottom-1.5 rounded-full bg-[#FECA43]",
                    isCollapsed ? "left-1.5 w-[3px]" : "left-0 w-[3px]",
                  )}
                />
              )}
              <Icon
                className={cn(
                  "size-[17px] shrink-0 text-[#9B9483] dark:text-[#948C7C]",
                  isActive && "text-[#B88700] dark:text-[#FECA43]",
                )}
                aria-hidden="true"
              />
              {!isCollapsed && <span className="truncate">{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      {isCollapsed ? (
        <div className="relative mt-4 flex justify-center" ref={profileMenuRef}>
          <button
            type="button"
            onClick={() => setIsProfileMenuOpen((prev) => !prev)}
            aria-label="프로필 메뉴 열기"
            aria-haspopup="menu"
            aria-expanded={isProfileMenuOpen}
            className="flex size-12 items-center justify-center rounded-[14px] border border-[#ECE7DC] bg-[#FFF7E0] outline-none transition-colors hover:bg-[#FFF3CF] focus-visible:ring-2 focus-visible:ring-[#FECA43]/60 dark:border-transparent dark:bg-white/5 dark:hover:bg-white/10"
          >
            <span className="flex size-[30px] shrink-0 items-center justify-center rounded-full bg-[#E7DDC8] dark:bg-white/10">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                aria-hidden="true"
              >
                <circle
                  cx="8"
                  cy="5.6"
                  r="3.1"
                  className="fill-[#8C8470] dark:fill-[#BEB7A6]"
                />
                <path
                  d="M2 15c0-3.6 2.7-5.4 6-5.4s6 1.8 6 5.4"
                  className="fill-[#8C8470] dark:fill-[#BEB7A6]"
                />
              </svg>
            </span>
          </button>

          {isProfileMenuOpen && (
            <div
              role="menu"
              aria-label="프로필 메뉴"
              className="absolute bottom-0 left-[calc(100%+12px)] z-20 min-w-[140px] rounded-[14px] border border-[#ECE7DC] bg-white p-2 shadow-[0_10px_30px_rgba(46,42,34,0.12)] dark:border-[#3C3327] dark:bg-[#221D16]"
            >
              <button
                type="button"
                onClick={handleLogout}
                role="menuitem"
                className="flex min-h-9 w-full items-center gap-2 rounded-[10px] px-3 text-left text-[13px] font-medium text-[#6B5200] outline-none transition-colors hover:bg-[#FFF7E0] focus-visible:ring-2 focus-visible:ring-[#FECA43]/60 dark:text-[#E6D7B0] dark:hover:bg-white/5"
              >
                <LogOut className="size-[15px]" aria-hidden="true" />
                로그아웃
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className="mt-4 rounded-[14px] border border-[#ECE7DC] bg-[#FFF7E0] p-2.5 dark:border-transparent dark:bg-white/5">
          <div className="flex items-center gap-2">
            <span className="flex size-[30px] shrink-0 items-center justify-center rounded-full bg-[#E7DDC8] dark:bg-white/10">
              <svg
                width="16"
                height="16"
                viewBox="0 0 16 16"
                fill="none"
                aria-hidden="true"
              >
                <circle
                  cx="8"
                  cy="5.6"
                  r="3.1"
                  className="fill-[#8C8470] dark:fill-[#BEB7A6]"
                />
                <path
                  d="M2 15c0-3.6 2.7-5.4 6-5.4s6 1.8 6 5.4"
                  className="fill-[#8C8470] dark:fill-[#BEB7A6]"
                />
              </svg>
            </span>
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="truncate text-[13px] font-medium text-[#2E2A22] dark:text-[#FBF6EC]">
                {user.name}
              </span>
              <span className="truncate text-[11px] text-[#8C8470] dark:text-[#948C7C]">
                {user.email}
              </span>
            </span>
          </div>

          <div className="my-2 h-px bg-[#ECE7DC] dark:bg-[#352C20]" />

          <button
            type="button"
            onClick={handleLogout}
            className="flex min-h-9 w-full items-center justify-center gap-1.5 rounded-[9px] border border-[#E3D6A8] bg-white text-[12.5px] font-medium text-[#6B5200] outline-none transition-colors hover:bg-[#FFF7E0] focus-visible:ring-2 focus-visible:ring-[#FECA43]/60 dark:border-[#3C3327] dark:bg-transparent dark:text-[#BEB7A6] dark:hover:bg-white/5"
          >
            <LogOut className="size-[15px]" aria-hidden="true" />
            로그아웃
          </button>
        </div>
      )}
    </aside>
  );
}
