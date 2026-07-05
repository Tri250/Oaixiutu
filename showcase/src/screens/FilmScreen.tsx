import { FILM_PRESETS } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";
import { useState } from "react";

export function FilmScreen() {
  const { selectedFilm, setSelectedFilm } = useStore();
  const [grain, setGrain] = useState(28);
  const [halation, setHalation] = useState(18);

  const selected = FILM_PRESETS.find((f) => f.id === selectedFilm) ?? FILM_PRESETS[0];

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Top */}
      <div className="px-4 pt-2 pb-3 flex items-center gap-2">
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-md-onSurfaceVar">
          <Icon.ChevronLeft size={18} />
        </button>
        <div className="flex-1">
          <div className="font-display text-[16px] font-medium">经典胶片模拟</div>
          <div className="text-[9px] text-md-onSurfaceVar font-mono">真实胶片特性数字复刻</div>
        </div>
        <Icon.Sparkle size={16} className="text-amber-300" />
      </div>

      <div className="flex-1 overflow-y-auto phone-content px-4 pb-3">
        {/* Preview with before/after */}
        <div className="relative aspect-[4/3] rounded-xl overflow-hidden mb-3 border border-md-outline/40">
          <div className={`absolute inset-0 bg-gradient-to-br ${selected.grad}`} />
          <div className="absolute inset-0 scanline opacity-30" />
          {/* Grain overlay */}
          <div
            className="absolute inset-0 opacity-30 mix-blend-overlay pointer-events-none"
            style={{
              backgroundImage:
                "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='2' numOctaves='1'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>\")",
            }}
          />
          {/* Halation glow */}
          <div
            className="absolute inset-0 pointer-events-none"
            style={{
              background:
                "radial-gradient(circle at 30% 30%, rgba(255,180,120,0.4), transparent 60%)",
              opacity: halation / 100 + 0.2,
              mixBlendMode: "screen",
            }}
          />
          {/* Before/after split line */}
          <div className="absolute inset-y-0 left-1/2 w-0.5 bg-white/80 shadow-lg" />
          <div className="absolute top-2 left-2 text-[8px] font-mono text-white/90 bg-black/40 px-1.5 py-0.5 rounded">
            BEFORE
          </div>
          <div className="absolute top-2 right-2 text-[8px] font-mono text-amber-300 bg-black/40 px-1.5 py-0.5 rounded">
            AFTER · {selected.name}
          </div>
        </div>

        {/* Selected film name */}
        <div className="mb-3">
          <div className="font-display text-[15px] font-medium">{selected.name}</div>
          <div className="text-[10px] text-md-onSurfaceVar font-mono">
            ISO {selected.iso} · {selected.type}
          </div>
          <div className="text-[10px] text-md-onSurface mt-1">{selected.desc}</div>
        </div>

        {/* Physics params */}
        <div className="bg-md-surface2 rounded-xl p-3 border border-md-outline/30 space-y-3 mb-3">
          <div className="text-[10px] text-amber-300/80 font-mono uppercase tracking-wider mb-1">
            物理特性模拟
          </div>
          <div>
            <div className="flex justify-between text-[10px] mb-1">
              <span className="text-md-onSurfaceVar">胶片颗粒</span>
              <span className="font-mono text-amber-300">{grain}%</span>
            </div>
            <input
              type="range"
              className="md-slider"
              value={grain}
              onChange={(e) => setGrain(+e.target.value)}
              min={0}
              max={100}
            />
          </div>
          <div>
            <div className="flex justify-between text-[10px] mb-1">
              <span className="text-md-onSurfaceVar">Halation 光晕</span>
              <span className="font-mono text-amber-300">{halation}%</span>
            </div>
            <input
              type="range"
              className="md-slider"
              value={halation}
              onChange={(e) => setHalation(+e.target.value)}
              min={0}
              max={100}
            />
          </div>
        </div>

        {/* Film preset grid */}
        <div className="text-[10px] text-amber-300/80 font-mono uppercase tracking-wider mb-2">
          胶片预设
        </div>
        <div className="grid grid-cols-3 gap-2">
          {FILM_PRESETS.map((f) => {
            const isSel = f.id === selectedFilm;
            return (
              <button
                key={f.id}
                onClick={() => {
                  setSelectedFilm(f.id);
                  setGrain(f.grain);
                  setHalation(f.halation);
                }}
                className={`md-tap rounded-lg overflow-hidden border-2 ${
                  isSel ? "border-amber-400" : "border-transparent"
                }`}
              >
                <div className={`aspect-square bg-gradient-to-br ${f.grad} relative`}>
                  <div className="absolute inset-0 scanline opacity-30" />
                  {isSel && (
                    <div className="absolute top-1 right-1 w-4 h-4 rounded-full bg-amber-400 flex items-center justify-center">
                      <Icon.Check size={10} className="text-md-onPrimary" />
                    </div>
                  )}
                </div>
                <div className="px-1.5 py-1 bg-md-surface2 text-left">
                  <div className="text-[9px] font-medium text-md-onSurface truncate">
                    {f.name.split(" ").slice(-1)}
                  </div>
                  <div className="text-[8px] text-md-onSurfaceVar font-mono">
                    ISO {f.iso}
                  </div>
                </div>
              </button>
            );
          })}
        </div>

        {/* Apply button */}
        <button className="md-tap w-full mt-3 py-2.5 bg-amber-400 text-md-onPrimary rounded-xl text-[12px] font-semibold">
          应用于当前照片
        </button>
      </div>
    </div>
  );
}
