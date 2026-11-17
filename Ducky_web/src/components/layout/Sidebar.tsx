"use client";

import Image from "next/image";
import Link from "next/link";
import { LogOut, PanelLeftClose, PanelLeftOpen } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { clearAuthSession } from "@/lib/auth/logout";
import { cn } from "@/lib/utils";
import { navItems } from "./navItems";

const SIDEBAR_COLLAPSED_STORAGE_KEY = "ducky.sidebar.collapsed";

export function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const [isCollapsed, setIsCollapsed] = useState(() => {
    if (typeof window === "undefined") {
      return false;
    }

    try {
      return (
        window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === "true"
      );
    } catch {
      return false;
    }
  });

  const handleLogout = () => {
    clearAuthSession();
    router.replace("/");
  };

  const toggleLabel = isCollapsed ? "사이드바 펼치기" : "사이드바 접기";
  const ToggleIcon = isCollapsed ? PanelLeftOpen : PanelLeftClose;

  const handleToggleSidebar = () => {
    setIsCollapsed((current) => {
      const next = !current;

      try {
        window.localStorage.setItem(
          SIDEBAR_COLLAPSED_STORAGE_KEY,
          String(next),
        );
      } catch {}

      return next;
    });
  };

  return (
    <aside
      className={cn(
        "hidden min-h-dvh shrink-0 border-r border-[#ECE7DC] bg-white py-5 transition-all duration-200 dark:border-white/10 dark:bg-[#201D19] md:flex md:flex-col",
        isCollapsed ? "w-20 px-3" : "w-64 px-4",
      )}
    >
      <div
        className={cn(
          "mb-8 flex",
          isCollapsed ? "flex-col items-center gap-3" : "items-center gap-2",
        )}
      >
        <Link
          href="/dashboard"
          className={cn(
            "flex min-w-0 items-center gap-3 rounded-lg px-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43]",
            isCollapsed && "justify-center px-0",
          )}
          aria-label={isCollapsed ? "Ducky 대시보드" : undefined}
        >
          <span className="flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#FECA43]">
            <Image
              src="/icons/ducky-character.png"
              alt=""
              width={40}
              height={40}
              className="size-10 scale-[1.15] object-contain"
              aria-hidden="true"
            />
          </span>
          <span
            className={cn(
              "truncate text-2xl font-bold tracking-tight text-[#FECA43]",
              isCollapsed && "sr-only",
            )}
          >
            Ducky
          </span>
        </Link>

        <button
          type="button"
          onClick={handleToggleSidebar}
          title={toggleLabel}
          aria-label={toggleLabel}
          aria-expanded={!isCollapsed}
          className={cn(
            "flex size-9 shrink-0 items-center justify-center rounded-lg border border-[#E7DDC8] bg-white text-gray-600 shadow-sm transition-colors hover:border-[#FECA43] hover:bg-[#FFF7E0] hover:text-[#2E2A22] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300 dark:hover:bg-white/10 dark:hover:text-white",
            !isCollapsed && "ml-auto",
          )}
        >
          <ToggleIcon className="size-[18px]" aria-hidden="true" />
        </button>
      </div>

      <nav aria-label="주요 메뉴" className="flex flex-1 flex-col gap-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive =
            pathname === item.href || pathname.startsWith(`${item.href}/`);

          return (
            <Link
              key={item.href}
              href={item.href}
              title={
                isCollapsed ? `${item.label} - ${item.description}` : undefined
              }
              aria-label={
                isCollapsed ? `${item.label} ${item.description}` : undefined
              }
              aria-current={isActive ? "page" : undefined}
              className={cn(
                "flex min-h-12 items-center gap-3 rounded-lg px-3 text-sm font-medium text-gray-600 transition-colors hover:bg-[#FFF7E0] hover:text-gray-950 dark:text-gray-300 dark:hover:bg-white/10 dark:hover:text-white",
                isCollapsed && "justify-center px-0",
                isActive &&
                  "bg-[#FECA43] font-bold text-[#2E2A22] shadow-sm hover:bg-[#FECA43] dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#FECA43]",
              )}
            >
              <Icon
                className={cn(
                  "size-5 shrink-0",
                  isActive && "text-[#2E2A22] dark:text-[#2E2A22]",
                )}
                aria-hidden="true"
              />
              <span
                className={cn(
                  "flex min-w-0 flex-col",
                  isCollapsed && "sr-only",
                )}
              >
                <span
                  className={cn(
                    "truncate",
                    isActive && "text-[#2E2A22] dark:text-[#2E2A22]",
                  )}
                >
                  {item.label}
                </span>
                <span
                  className={cn(
                    "truncate text-xs font-normal text-gray-400 dark:text-gray-500",
                    isActive &&
                      "font-medium text-[#5A4300] dark:text-[#5A4300]",
                  )}
                >
                  {item.description}
                </span>
              </span>
            </Link>
          );
        })}
      </nav>

      <button
        type="button"
        onClick={handleLogout}
        title={isCollapsed ? "로그아웃" : undefined}
        aria-label="로그아웃"
        className={cn(
          "mt-4 flex min-h-11 items-center gap-3 rounded-lg px-3 text-sm font-bold text-gray-500 transition-colors hover:bg-red-50 hover:text-red-600 dark:text-gray-300 dark:hover:bg-red-500/10 dark:hover:text-red-200",
          isCollapsed && "justify-center px-0",
        )}
      >
        <LogOut className="size-5 shrink-0" aria-hidden="true" />
        <span className={cn(isCollapsed && "sr-only")}>로그아웃</span>
      </button>
    </aside>
  );
}
