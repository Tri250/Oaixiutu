import { type ReactNode } from "react";
import { Icon } from "./icons";

interface PhoneShellProps {
  children: ReactNode;
  time?: string;
}

/**
 * Android device frame: status bar + screen content area + bottom nav
 */
export function PhoneShell({ children, time = "9:41" }: PhoneShellProps) {
  return (
    <div className="phone-shell">
      {/* side buttons */}
      <div className="absolute left-[-2px] top-[140px] w-[3px] h-[40px] rounded-l bg-bench-700" />
      <div className="absolute left-[-2px] top-[200px] w-[3px] h-[80px] rounded-l bg-bench-700" />
      <div className="absolute right-[-2px] top-[160px] w-[3px] h-[80px] rounded-r bg-bench-700" />

      <div className="phone-screen">
        {/* Status bar */}
        <div className="relative h-7 px-5 flex items-center justify-between text-[11px] text-md-onBg shrink-0 z-20">
          <span className="font-mono font-medium tracking-tight">{time}</span>
          {/* Punch-hole camera */}
          <div className="absolute left-1/2 -translate-x-1/2 top-1.5 w-2.5 h-2.5 rounded-full bg-bench-950 ring-1 ring-bench-700" />
          <div className="flex items-center gap-1.5">
            <Icon.Signal size={12} />
            <Icon.Wifi size={12} />
            <Icon.Battery size={16} />
          </div>
        </div>

        {/* Screen content */}
        <div className="flex-1 relative overflow-hidden">{children}</div>

        {/* Android gesture nav */}
        <div className="h-6 flex items-center justify-center shrink-0 z-20">
          <div className="w-32 h-1 rounded-full bg-md-onSurfaceVar/40" />
        </div>
      </div>
    </div>
  );
}
