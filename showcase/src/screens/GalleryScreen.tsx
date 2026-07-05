import { GALLERY_THUMBS, AI_LABELS } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";

export function GalleryScreen() {
  const setActiveScreen = useStore((s) => s.setActiveScreen);

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Top bar */}
      <div className="px-4 pt-2 pb-3 flex items-center gap-3">
        <button className="md-tap w-9 h-9 rounded-full flex items-center justify-center bg-md-surface2 text-md-onSurface">
          <Icon.Search size={18} onClick={() => setActiveScreen("ai")} />
        </button>
        <div className="flex-1">
          <div className="font-display text-[20px] leading-none font-medium tracking-tight">
            Alcedo
          </div>
          <div className="text-[10px] text-md-onSurfaceVar mt-0.5 font-mono">
            1,247 张 · 60+ RAW
          </div>
        </div>
        <button className="md-tap w-9 h-9 rounded-full flex items-center justify-center bg-md-surface2 text-md-onSurface">
          <Icon.More size={18} />
        </button>
      </div>

      {/* AI label chips */}
      <div className="px-4 pb-2">
        <div className="text-[10px] text-amber-300/80 mb-1.5 font-mono uppercase tracking-wider flex items-center gap-1">
          <Icon.Sparkle size={10} /> AI 自动标签
        </div>
        <div className="flex gap-1.5 overflow-x-auto phone-content pb-1">
          {AI_LABELS.flatMap((g) => g.items.slice(0, 3)).map((label, i) => (
            <button
              key={i}
              className={`md-tap shrink-0 px-3 py-1 rounded-full text-[11px] border ${
                i === 0
                  ? "bg-amber-400/15 border-amber-400/40 text-amber-200"
                  : "bg-md-surface2 border-md-outline/40 text-md-onSurfaceVar"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {/* Thumbnail grid */}
      <div className="flex-1 overflow-y-auto phone-content px-4 pb-2">
        <div className="grid grid-cols-3 gap-1.5">
          {GALLERY_THUMBS.map((t, i) => (
            <div
              key={i}
              className="relative aspect-square rounded-lg overflow-hidden md-tap group"
            >
              <div className={`absolute inset-0 bg-gradient-to-br ${t.grad}`} />
              <div className="absolute inset-0 scanline opacity-30" />
              {/* Star rating */}
              <div className="absolute bottom-1 right-1 flex items-center gap-0.5 bg-black/40 backdrop-blur-sm rounded-full px-1.5 py-0.5">
                <Icon.Star
                  size={9}
                  filled
                  className="text-amber-300"
                />
                <span className="text-[8px] text-white font-mono">{t.stars}</span>
              </div>
              {/* Label */}
              <div className="absolute top-1 left-1 text-[8px] text-white/80 font-mono bg-black/30 px-1 rounded">
                {t.label}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Bottom navigation */}
      <BottomNav active="gallery" />
    </div>
  );
}

export function BottomNav({ active }: { active: string }) {
  const setActiveScreen = useStore((s) => s.setActiveScreen);
  const items = [
    { id: "gallery", label: "图库", icon: <Icon.Grid size={20} /> },
    { id: "ai", label: "搜索", icon: <Icon.Search size={20} /> },
    { id: "editor", label: "编辑", icon: <Icon.Edit size={20} /> },
    { id: "export", label: "导出", icon: <Icon.Download size={20} /> },
    { id: "settings", label: "我的", icon: <Icon.Settings size={20} /> },
  ];
  return (
    <div className="shrink-0 bg-md-surface/95 backdrop-blur border-t border-md-outline/30 px-2 py-1.5">
      <div className="flex items-center justify-around">
        {items.map((it) => {
          const isActive = active === it.id;
          return (
            <button
              key={it.id}
              onClick={() => setActiveScreen(it.id as any)}
              className={`md-tap flex flex-col items-center gap-0.5 px-3 py-1 rounded-xl ${
                isActive ? "text-amber-300" : "text-md-onSurfaceVar"
              }`}
            >
              {it.icon}
              <span className="text-[9px] font-medium">{it.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
