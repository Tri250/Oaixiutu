import { useEffect } from "react";
import { useStore } from "@/store";
import { SCREENS, KEY_METRICS, ARCH_MAPPING } from "@/data";
import { PhoneShell } from "@/components/PhoneShell";
import { TabBar, SidePanel } from "@/components/SidePanel";
import { Icon } from "@/components/icons";
import { GalleryScreen } from "@/screens/GalleryScreen";
import { AISearchScreen } from "@/screens/AISearchScreen";
import { EditorScreen } from "@/screens/EditorScreen";
import { ColorScreen } from "@/screens/ColorScreen";
import { FilmScreen } from "@/screens/FilmScreen";
import { ExportScreen } from "@/screens/ExportScreen";
import { SettingsScreen } from "@/screens/SettingsScreen";

const SCREEN_COMPONENTS: Record<string, () => JSX.Element> = {
  gallery: GalleryScreen,
  ai: AISearchScreen,
  editor: EditorScreen,
  color: ColorScreen,
  film: FilmScreen,
  export: ExportScreen,
  settings: SettingsScreen,
};

export default function Home() {
  const activeScreen = useStore((s) => s.activeScreen);
  const setActiveScreen = useStore((s) => s.setActiveScreen);
  const activeTool = useStore((s) => s.activeTool);
  const setActiveTool = useStore((s) => s.setActiveTool);

  // Reset tool when switching away from editor
  useEffect(() => {
    if (activeScreen !== "editor" && activeTool) setActiveTool(null);
  }, [activeScreen, activeTool, setActiveTool]);

  const meta = SCREENS.find((s) => s.id === activeScreen)!;
  const ScreenComp = SCREEN_COMPONENTS[activeScreen];

  return (
    <div className="min-h-screen flex flex-col bg-bench-950 text-bench-900 grain relative">
      {/* Top header bar */}
      <header className="relative z-10 border-b border-bench-800/60 bg-bench-950/80 backdrop-blur">
        <div className="px-6 py-3 flex items-center gap-4">
          {/* Logo */}
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-amber-400 to-amber-600 flex items-center justify-center shadow-lg shadow-amber-500/30">
              <Icon.Aperture size={20} className="text-bench-950" />
            </div>
            <div>
              <div className="font-display text-[16px] font-semibold leading-none">
                Alcedo Studio
              </div>
              <div className="font-mono text-[9px] text-bench-500 mt-0.5 uppercase tracking-widest">
                Android · UI Showcase
              </div>
            </div>
          </div>

          <div className="flex-1" />

          {/* Key metrics */}
          <div className="hidden lg:flex items-center gap-5 mr-4">
            {KEY_METRICS.map((m) => (
              <div key={m.label} className="text-right">
                <div className="font-display text-[16px] font-semibold text-amber-300 leading-none">
                  {m.value}
                </div>
                <div className="text-[9px] text-bench-500 font-mono mt-0.5 uppercase tracking-wider">
                  {m.label}
                </div>
              </div>
            ))}
          </div>

          <a
            href="#"
            className="md-tap flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-bench-800/60 border border-bench-700 text-[11px] font-mono text-bench-900 hover:border-amber-400/40 hover:text-amber-300"
          >
            <Icon.Layers size={12} />
            v1.0.0
          </a>
        </div>

        {/* Tab bar */}
        <div className="px-6 pb-2">
          <TabBar active={activeScreen} onChange={setActiveScreen} />
        </div>
      </header>

      {/* Main three-column layout */}
      <main className="relative z-10 flex-1 flex overflow-hidden">
        <SidePanel screen={meta} />

        {/* Center: phone */}
        <section className="flex-1 flex items-center justify-center relative overflow-hidden">
          {/* Decorative grid backdrop */}
          <div
            className="absolute inset-0 opacity-[0.04] pointer-events-none"
            style={{
              backgroundImage:
                "linear-gradient(#d4823a 1px, transparent 1px), linear-gradient(90deg, #d4823a 1px, transparent 1px)",
              backgroundSize: "40px 40px",
            }}
          />
          {/* Glow */}
          <div className="absolute w-[480px] h-[480px] rounded-full bg-amber-400/8 blur-[100px] pointer-events-none" />

          <div className="relative">
            <PhoneShell>
              <ScreenComp />
            </PhoneShell>

            {/* Floating screen label */}
            <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-bench-900 px-3 py-1 rounded-full border border-bench-700 text-[9px] font-mono text-amber-300 uppercase tracking-widest whitespace-nowrap">
              {meta.shortName}
            </div>
          </div>

          {/* Side hints */}
          <div className="absolute left-4 top-1/2 -translate-y-1/2 vertical-text text-[10px] font-mono text-bench-600 uppercase">
            Alcedo Studio · Android Native UI
          </div>
          <div className="absolute right-4 top-1/2 -translate-y-1/2 vertical-text text-[10px] font-mono text-bench-600 uppercase">
            Material You · Compose
          </div>
        </section>
      </main>

      {/* Footer: architecture mapping */}
      <footer className="relative z-10 border-t border-bench-800/60 bg-bench-950/80 backdrop-blur">
        <div className="px-6 py-3">
          <div className="flex items-center gap-3 mb-2">
            <Icon.Cpu size={14} className="text-amber-300" />
            <span className="text-[11px] font-mono text-bench-500 uppercase tracking-widest">
              桌面端 → Android 端 · 架构等价映射
            </span>
            <div className="flex-1 h-px bg-bench-800" />
            <span className="text-[10px] font-mono text-bench-600">
              100% 功能覆盖
            </span>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-1.5">
            {ARCH_MAPPING.map((m) => (
              <div
                key={m.desktop}
                className="bg-bench-900/60 border border-bench-800/60 rounded p-2 hover:border-amber-400/30 transition-colors"
              >
                <div className="text-[9px] text-bench-500 line-through truncate" title={m.desktop}>
                  {m.desktop}
                </div>
                <div className="text-[10px] text-amber-300 font-mono leading-tight mt-0.5">
                  {m.android}
                </div>
                <div className="text-[8px] text-bench-600 mt-0.5">{m.note}</div>
              </div>
            ))}
          </div>
        </div>
      </footer>
    </div>
  );
}
