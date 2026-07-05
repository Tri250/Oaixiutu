import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

const base = (size: number) => ({
  width: size,
  height: size,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.6,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
});

export const Icon = {
  Sun: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
    </svg>
  ),
  Circle: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3v18M3 12h18" opacity="0.4" />
    </svg>
  ),
  Droplet: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 2.5S5 10 5 14a7 7 0 0 0 14 0c0-4-7-11.5-7-11.5z" />
    </svg>
  ),
  Palette: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 2a10 10 0 0 0 0 20c1.1 0 2-.9 2-2 0-.5-.2-1-.5-1.3-.3-.4-.5-.8-.5-1.2 0-1 .9-1.5 2-1.5h2.5A4 4 0 0 0 22 12c0-5.5-4.5-10-10-10z" />
      <circle cx="7.5" cy="11.5" r="1" fill="currentColor" />
      <circle cx="10.5" cy="7.5" r="1" fill="currentColor" />
      <circle cx="15" cy="8" r="1" fill="currentColor" />
    </svg>
  ),
  Wheel: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="2.5" />
      <path d="M12 3v5.5M12 15.5V21M3 12h5.5M15.5 12H21M5.6 5.6l3.9 3.9M14.5 14.5l3.9 3.9M5.6 18.4l3.9-3.9M14.5 9.5l3.9-3.9" />
    </svg>
  ),
  Curve: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M3 21C3 10 7 3 21 3" />
      <path d="M3 21h18M3 3v18" opacity="0.3" />
    </svg>
  ),
  Crop: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M6 2v14a2 2 0 0 0 2 2h14M2 6h14a2 2 0 0 1 2 2v14" />
    </svg>
  ),
  Lens: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="12" r="5" />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" />
    </svg>
  ),
  Aperture: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 3l5 8M19 9h-9M5 9l4 7M9 21l4-7M19 15l-5-1M5 15l5 1" />
    </svg>
  ),
  Triangle: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 3l9 16H3z" />
    </svg>
  ),
  Grain: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="6" cy="6" r="1" fill="currentColor" />
      <circle cx="12" cy="6" r="1" fill="currentColor" />
      <circle cx="18" cy="6" r="1" fill="currentColor" />
      <circle cx="6" cy="12" r="1" fill="currentColor" />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" />
      <circle cx="18" cy="12" r="1" fill="currentColor" />
      <circle cx="6" cy="18" r="1" fill="currentColor" />
      <circle cx="12" cy="18" r="1" fill="currentColor" />
      <circle cx="18" cy="18" r="1" fill="currentColor" />
    </svg>
  ),
  Layers: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 2l10 5-10 5L2 7z" />
      <path d="M2 12l10 5 10-5M2 17l10 5 10-5" opacity="0.6" />
    </svg>
  ),
  Search: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="11" cy="11" r="7" />
      <path d="M21 21l-4.5-4.5" />
    </svg>
  ),
  Mic: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <rect x="9" y="2" width="6" height="11" rx="3" />
      <path d="M5 11a7 7 0 0 0 14 0M12 18v3" />
    </svg>
  ),
  Star: ({ size = 20, filled = false, ...p }: IconProps & { filled?: boolean }) => (
    <svg {...base(size)} {...p} fill={filled ? "currentColor" : "none"}>
      <path d="M12 2l3 6.5 7 .9-5 4.8 1.4 7L12 17.8 5.6 21l1.4-7-5-4.8 7-.9z" />
    </svg>
  ),
  Grid: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <rect x="3" y="3" width="7" height="7" rx="1" />
      <rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" />
      <rect x="14" y="14" width="7" height="7" rx="1" />
    </svg>
  ),
  Edit: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" />
      <path d="M14.06 6.19l3.75 3.75 2.13-2.13a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-2.13 2.13z" />
    </svg>
  ),
  Download: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
    </svg>
  ),
  Settings: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1A1.7 1.7 0 0 0 9 19.4a1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 4.6 9a1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1A1.7 1.7 0 0 0 15 4.6a1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
    </svg>
  ),
  ChevronLeft: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M15 18l-6-6 6-6" />
    </svg>
  ),
  ChevronRight: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M9 18l6-6-6-6" />
    </svg>
  ),
  Undo: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M3 7v6h6" />
      <path d="M3 13a9 9 0 1 0 3-7L3 9" />
    </svg>
  ),
  Redo: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M21 7v6h-6" />
      <path d="M21 13a9 9 0 1 1-3-7l3 3" />
    </svg>
  ),
  History: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
      <path d="M3 4v4h4M12 7v5l3 2" />
    </svg>
  ),
  More: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <circle cx="5" cy="12" r="1.5" fill="currentColor" />
      <circle cx="12" cy="12" r="1.5" fill="currentColor" />
      <circle cx="19" cy="12" r="1.5" fill="currentColor" />
    </svg>
  ),
  Check: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M4 12l5 5L20 6" />
    </svg>
  ),
  Sparkle: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 2l1.8 5.2L19 9l-5.2 1.8L12 16l-1.8-5.2L5 9l5.2-1.8z" />
      <path d="M19 14l.7 2 2 .7-2 .7L19 20l-.7-2-2-.7 2-.7z" opacity="0.6" />
    </svg>
  ),
  Cpu: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <rect x="6" y="6" width="12" height="12" rx="1.5" />
      <rect x="9" y="9" width="6" height="6" rx="0.5" />
      <path d="M9 2v3M15 2v3M9 19v3M15 19v3M2 9h3M2 15h3M19 9h3M19 15h3" />
    </svg>
  ),
  Shield: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M12 2L4 6v6c0 5 3.5 9 8 10 4.5-1 8-5 8-10V6z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  ),
  Lock: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  ),
  Battery: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <rect x="2" y="8" width="18" height="8" rx="1.5" />
      <rect x="4" y="10" width="11" height="4" fill="currentColor" stroke="none" />
      <path d="M22 11v2" />
    </svg>
  ),
  Wifi: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M5 12.5a10 10 0 0 1 14 0M8 16a5 5 0 0 1 8 0M12 19.5h.01" />
    </svg>
  ),
  Signal: ({ size = 20, ...p }: IconProps) => (
    <svg {...base(size)} {...p}>
      <path d="M3 18l3-3v3zM9 18l3-3v3zM15 18l3-3v3z" fill="currentColor" />
    </svg>
  ),
};

export type IconName = keyof typeof Icon;
export const ICON_MAP: Record<string, (p: IconProps) => any> = {
  sun: Icon.Sun,
  circle: Icon.Circle,
  droplet: Icon.Droplet,
  palette: Icon.Palette,
  wheel: Icon.Wheel,
  curve: Icon.Curve,
  crop: Icon.Crop,
  lens: Icon.Lens,
  aperture: Icon.Aperture,
  triangle: Icon.Triangle,
  grain: Icon.Grain,
  layers: Icon.Layers,
};
