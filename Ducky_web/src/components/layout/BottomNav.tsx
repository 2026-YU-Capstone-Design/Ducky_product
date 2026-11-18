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
      className="fixed inset-x-0 bottom-0 z-40 h-[calc(4.5rem+env(safe-area-inset-bottom))] border-t border-[#ECE7DC] bg-[#FFFCF5]/95 px-2 pb-[calc(env(safe-area-inset-bottom)+0.5rem)] pt-2 shadow-[0_-8px_24px_rgba(46,42,34,0.06)] backdrop-blur transition-colors dark:border-white/10 dark:bg-[#1C1812]/95 md:hidden"
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
                "relative flex min-w-0 flex-col items-center justify-center gap-1 rounded-[9px] px-1 text-[11px] font-normal leading-none text-[#9B9483] transition-colors hover:bg-[#FFF7E0]/60 hover:text-[#5A4300] dark:text-[#948C7C] dark:hover:bg-white/5 dark:hover:text-[#FBF6EC]",
                isActive &&
                  "bg-[#FFF7E0] font-medium text-[#5A4300] hover:bg-[#FFF7E0] dark:bg-transparent dark:text-[#FBF6EC] dark:hover:bg-transparent",
              )}
            >
              {isActive && (
                <span className="absolute inset-x-2 top-1 h-[3px] rounded-full bg-[#FECA43]" />
              )}
              <Icon
                className={cn(
                  "size-5 shrink-0 text-[#9B9483] dark:text-[#948C7C]",
                  isActive && "text-[#B88700] dark:text-[#FECA43]",
                )}
                aria-hidden="true"
              />
              <span className="w-full truncate text-center">{item.label}</span>
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
