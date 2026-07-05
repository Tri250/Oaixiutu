import { COLOR_WHEELS, HSL_HUES } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";

export function ColorScreen() {
  const { colorEngine, setColorEngine } = useStore();

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Top */}
      <div className="px-4 pt-2 pb-3 flex items-center gap-2">
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-md-onSurfaceVar">
          <Icon.ChevronLeft size={18} />
        </button>
        <div className="flex-1">
          <div className="font-display text-[16px] font-medium">色彩调整</div>
          <div className="text-[9px] text-md-onSurfaceVar font-mono">双引擎色彩科学</div>
        </div>
      </div>

      {/* Engine switch */}
      <div className="px-4 mb-3">
        <div className="bg-md-surface2 rounded-2xl p-1 flex border border-md-outline/30">
          {(["aces", "opendrt"] as const).map((e) => (
            <button
              key={e}
              onClick={() => setColorEngine(e)}
              className={`md-tap flex-1 py-2 rounded-xl text-[12px] font-medium ${
                colorEngine === e
                  ? "bg-amber-400 text-md-onPrimary shadow-lg"
                  : "text-md-onSurfaceVar"
              }`}
            >
              {e === "aces" ? "ACES 2.0" : "OpenDRT"}
            </button>
          ))}
        </div>
        <div className="text-[9px] text-md-onSurfaceVar mt-1.5 font-mono px-1">
          {colorEngine === "aces"
            ? "// 行业标准 · 真实还原 · 跨媒介一致"
            : "// 对数空间 · 自然影调 · 艺术化"}
        </div>
      </div>

      <div className="flex-1 overflow-y-auto phone-content px-4 pb-3 space-y-3">
        {/* Color wheels */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Wheel size={10} /> 三色轮 · 分区调色
          </div>
          <div className="grid grid-cols-3 gap-2">
            {COLOR_WHEELS.map((w) => (
              <div key={w.id} className="bg-md-surface2 rounded-xl p-2 border border-md-outline/30 flex flex-col items-center">
                <div className="relative w-16 h-16 mb-1">
                  <div className="absolute inset-0 rounded-full color-wheel opacity-90" />
                  <div className="absolute inset-2 rounded-full bg-md-bg/40 backdrop-blur" />
                  {/* center handle */}
                  <div
                    className="absolute w-2.5 h-2.5 rounded-full bg-white border-2 border-amber-400 shadow-lg"
                    style={{
                      left: `calc(50% + ${Math.cos(w.lum * 0.04) * 18}px - 5px)`,
                      top: `calc(50% + ${Math.sin(w.lum * 0.04) * 14}px - 5px)`,
                    }}
                  />
                </div>
                <div className="text-[9px] text-md-onSurface">{w.name}</div>
                <div className="text-[8px] text-md-onSurfaceVar font-mono">L {w.lum}</div>
              </div>
            ))}
          </div>
        </section>

        {/* HSL */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Palette size={10} /> HSL · 八色相
          </div>
          <div className="bg-md-surface2 rounded-xl p-2.5 border border-md-outline/30 space-y-1.5">
            {HSL_HUES.map((h) => (
              <div key={h.name} className="flex items-center gap-2">
                <div className="w-5 h-5 rounded-full shrink-0" style={{ background: h.color }} />
                <span className="text-[10px] text-md-onSurface w-6">{h.name}</span>
                <input type="range" className="md-slider flex-1" defaultValue={0} min={-180} max={180} />
                <span className="text-[9px] font-mono text-md-onSurfaceVar w-8 text-right">0°</span>
              </div>
            ))}
          </div>
        </section>

        {/* Channel mixer */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Layers size={10} /> 通道混合器
          </div>
          <div className="bg-md-surface2 rounded-xl p-2.5 border border-md-outline/30">
            <div className="grid grid-cols-4 gap-1 text-[9px] font-mono">
              <div></div>
              <div className="text-rose-300 text-center">R</div>
              <div className="text-green-300 text-center">G</div>
              <div className="text-arctic-100 text-center">B</div>
              {[
                ["R", 100, 0, 0],
                ["G", 0, 100, 0],
                ["B", 0, 0, 100],
              ].map((row, i) => (
                <div key={i} className="contents">
                  <div className="text-md-onSurfaceVar">{row[0]}</div>
                  {row.slice(1).map((v, j) => (
                    <div
                      key={j}
                      className="bg-md-surface3 rounded text-center py-1 text-md-onSurface"
                    >
                      {v}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Tone curve */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Curve size={10} /> 色调曲线
          </div>
          <div className="bg-md-surface2 rounded-xl p-2 border border-md-outline/30">
            <svg viewBox="0 0 120 80" className="w-full h-20">
              <defs>
                <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                  <path d="M20 0L0 0 0 20" fill="none" stroke="#3a3f48" strokeWidth="0.3" />
                </pattern>
              </defs>
              <rect width="120" height="80" fill="url(#grid)" />
              <path d="M0,80 Q60,40 120,0" fill="none" stroke="#d4823a" strokeWidth="1.5" />
              <circle cx="30" cy="60" r="2" fill="#d4823a" />
              <circle cx="60" cy="40" r="2.5" fill="#d4823a" />
              <circle cx="90" cy="20" r="2" fill="#d4823a" />
            </svg>
          </div>
        </section>
      </div>
    </div>
  );
}
