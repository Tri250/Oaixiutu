import { SCREENS, ARCH_MAPPING, type ScreenMeta } from "../data";
import { Icon } from "./icons";

interface TabBarProps {
  active: string;
  onChange: (id: any) => void;
}

export function TabBar({ active, onChange }: TabBarProps) {
  return (
    <div className="flex items-center gap-1 overflow-x-auto pb-1">
      {SCREENS.map((s, i) => {
        const isActive = active === s.id;
        return (
          <button
            key={s.id}
            onClick={() => onChange(s.id)}
            className={`md-tap shrink-0 px-4 py-2 rounded-lg text-[12px] font-medium transition-colors ${
              isActive
                ? "bg-amber-400/15 text-amber-300"
                : "text-bench-500 hover:text-bench-900 hover:bg-bench-800"
            }`}
          >
            <span className="font-mono text-[9px] opacity-60 mr-2">
              {String(i + 1).padStart(2, "0")}
            </span>
            {s.name}
          </button>
        );
      })}
    </div>
  );
}

const TAG_STYLES: Record<string, string> = {
  cpp: "bg-amber-400/10 text-amber-300 border-amber-400/30",
  kt: "bg-arctic-400/10 text-arctic-100 border-arctic-400/30",
  gpu: "bg-fuchsia-400/10 text-fuchsia-300 border-fuchsia-400/30",
  data: "bg-emerald-400/10 text-emerald-300 border-emerald-400/30",
  ai: "bg-rose-400/10 text-rose-300 border-rose-400/30",
};

export function SidePanel({ screen }: { screen: ScreenMeta }) {
  const idx = SCREENS.findIndex((s) => s.id === screen.id);
  return (
    <>
      {/* Left: description */}
      <aside className="w-[280px] shrink-0 border-r border-bench-800/60 overflow-y-auto">
        <div className="sticky top-0 px-6 py-5">
          <div className="flex items-center gap-2 text-[10px] font-mono text-amber-300/70 mb-3">
            <span>{String(idx + 1).padStart(2, "0")} / 07</span>
            <span className="flex-1 h-px bg-bench-800" />
            <span>{screen.shortName}</span>
          </div>

          <div className="font-mono text-[10px] text-bench-500 mb-1 uppercase tracking-widest">
            {screen.tagline}
          </div>
          <h2 className="font-display text-[28px] font-medium leading-tight text-bench-900 mb-2">
            {screen.title}
          </h2>
          <p className="text-[12px] text-bench-500 leading-relaxed mb-5">
            {screen.desc}
          </p>

          {/* Features */}
          <div className="space-y-1.5 mb-5">
            {screen.features.map((f) => (
              <div key={f} className="flex items-start gap-2 text-[12px] text-bench-900">
                <Icon.Check size={14} className="text-amber-400 mt-0.5 shrink-0" />
                <span>{f}</span>
              </div>
            ))}
          </div>

          {/* Source files */}
          <div className="mb-2 text-[10px] font-mono text-bench-500 uppercase tracking-widest">
            涉及源码
          </div>
          <div className="space-y-1">
            {screen.sourceFiles.map((f) => (
              <div
                key={f}
                className="font-mono text-[10px] text-bench-900 bg-bench-900/60 border border-bench-800/60 rounded px-2 py-1 truncate hover:border-amber-400/40 transition-colors"
              >
                <span className="text-amber-400/70 mr-1.5">›</span>
                {f}
              </div>
            ))}
          </div>
        </div>
      </aside>

      {/* Right: tech stack + metrics */}
      <aside className="w-[240px] shrink-0 border-l border-bench-800/60 overflow-y-auto">
        <div className="sticky top-0 px-5 py-5 space-y-5">
          {/* Tech stack */}
          <div>
            <div className="text-[10px] font-mono text-bench-500 uppercase tracking-widest mb-2">
              技术栈
            </div>
            <div className="flex flex-wrap gap-1.5">
              {screen.techStack.map((t) => (
                <span
                  key={t.label}
                  className={`text-[10px] font-mono px-2 py-0.5 rounded border ${TAG_STYLES[t.tag]}`}
                >
                  {t.label}
                </span>
              ))}
            </div>
          </div>

          {/* Metrics */}
          <div>
            <div className="text-[10px] font-mono text-bench-500 uppercase tracking-widest mb-2">
              指标
            </div>
            <div className="space-y-1.5">
              {screen.metrics.map((m) => (
                <div
                  key={m.label}
                  className="flex items-baseline justify-between border-b border-bench-800/60 pb-1.5"
                >
                  <span className="text-[11px] text-bench-500">{m.label}</span>
                  <span className="font-display text-[16px] font-semibold text-amber-300">
                    {m.value}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* Architecture mapping (only on relevant screens) */}
          {idx >= 4 && (
            <div>
              <div className="text-[10px] font-mono text-bench-500 uppercase tracking-widest mb-2">
                桌面 → Android
              </div>
              <div className="space-y-1.5">
                {ARCH_MAPPING.slice(0, 3).map((m) => (
                  <div
                    key={m.desktop}
                    className="bg-bench-900/40 border border-bench-800/60 rounded p-2"
                  >
                    <div className="text-[9px] text-bench-500 line-through">{m.desktop}</div>
                    <div className="text-[10px] text-amber-300 font-mono">{m.android}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </aside>
    </>
  );
}
