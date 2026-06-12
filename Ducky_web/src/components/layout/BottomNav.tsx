"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { navItems } from "./navItems";

export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-[#ECE7DC] bg-white/95 px-2 pb-[max(env(safe-area-inset-bottom),0.5rem)] pt-2 shadow-[0_-8px_24px_rgba(46,42,34,0.06)] backdrop-blur transition-colors dark:border-white/10 dark:bg-[#201D19]/95 md:hidden"
    >
      <div className="grid h-14 grid-cols-5 gap-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive =
            pathname === item.href || pathname.startsWith(`${item.href}/`);

          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className={cn(
                "flex min-w-0 flex-col items-center justify-center gap-1 rounded-lg px-1 text-[11px] font-medium leading-none text-gray-500 transition-colors hover:bg-[#FFF7E0] hover:text-gray-950 dark:text-gray-300 dark:hover:bg-white/10 dark:hover:text-white",
                isActive &&
                  "bg-[#FECA43] font-bold text-[#2E2A22] hover:bg-[#FECA43] dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#FECA43]",
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
                  "w-full truncate text-center",
                  isActive && "text-[#2E2A22] dark:text-[#2E2A22]",
                )}
              >
                {item.label}
              </span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
