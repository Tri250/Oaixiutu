import { EXPORT_FORMATS, COLOR_SPACES, ICC_PROFILES } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";
import { BottomNav } from "./GalleryScreen";

export function ExportScreen() {
  const { exportFormat, setExportFormat, settings, toggleSetting } = useStore();

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Header */}
      <div className="px-4 pt-2 pb-3 flex items-center gap-2">
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-md-onSurfaceVar">
          <Icon.ChevronLeft size={18} />
        </button>
        <div className="flex-1">
          <div className="font-display text-[16px] font-medium">导出设置</div>
          <div className="text-[9px] text-md-onSurfaceVar font-mono">
            HDR · ICC · Ultra HDR Gain Map
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto phone-content px-4 pb-3 space-y-3">
        {/* Format */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider">
            格式
          </div>
          <div className="grid grid-cols-2 gap-1.5">
            {EXPORT_FORMATS.map((f) => {
              const isSel = f.id === exportFormat;
              return (
                <button
                  key={f.id}
                  onClick={() => setExportFormat(f.id)}
                  className={`md-tap p-2.5 rounded-xl border text-left ${
                    isSel
                      ? "bg-amber-400/15 border-amber-400/50 text-amber-200"
                      : "bg-md-surface2 border-md-outline/40 text-md-onSurface"
                  }`}
                >
                  <div className="text-[12px] font-semibold">{f.name}</div>
                  <div className="text-[9px] text-md-onSurfaceVar">{f.desc}</div>
                </button>
              );
            })}
          </div>
        </section>

        {/* Color space */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider">
            色彩空间
          </div>
          <div className="bg-md-surface2 rounded-xl p-1 flex border border-md-outline/30">
            {COLOR_SPACES.map((c) => (
              <button
                key={c.id}
                className={`md-tap flex-1 py-1.5 rounded-lg text-[11px] font-medium ${
                  c.id === "p3"
                    ? "bg-amber-400 text-md-onPrimary"
                    : "text-md-onSurfaceVar"
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        </section>

        {/* ICC */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center justify-between">
            <span>ICC 配置文件</span>
            <span className="text-md-onSurfaceVar">{ICC_PROFILES.length} 内置</span>
          </div>
          <div className="bg-md-surface2 rounded-xl border border-md-outline/40 overflow-hidden">
            <div className="px-3 py-2.5 flex items-center justify-between">
              <div>
                <div className="text-[11px] font-mono text-md-onSurface">
                  p3_d65_pq.icc
                </div>
                <div className="text-[9px] text-md-onSurfaceVar">Display P3 · PQ · HDR</div>
              </div>
              <Icon.ChevronRight size={14} className="text-md-onSurfaceVar" />
            </div>
            <div className="border-t border-md-outline/30 px-3 py-2 flex flex-wrap gap-1">
              {ICC_PROFILES.slice(0, 6).map((p) => (
                <span
                  key={p}
                  className="text-[8px] font-mono px-1.5 py-0.5 rounded bg-md-surface3 text-md-onSurfaceVar"
                >
                  {p.replace(".icc", "")}
                </span>
              ))}
              <span className="text-[8px] font-mono px-1.5 py-0.5 rounded bg-md-surface3 text-amber-300">
                +5 more
              </span>
            </div>
          </div>
        </section>

        {/* Toggles */}
        <section className="space-y-1.5">
          <ToggleRow
            label="嵌入 ICC 配置"
            sub="确保跨媒介色彩精准"
            on={settings.embedIcc}
            onToggle={() => toggleSetting("embedIcc")}
          />
          <ToggleRow
            label="HDR Gain Map"
            sub="Ultra HDR 显示器支持"
            on={settings.hdrGainMap}
            onToggle={() => toggleSetting("hdrGainMap")}
          />
        </section>

        {/* Quality / size */}
        <section className="bg-md-surface2 rounded-xl p-3 border border-md-outline/30 space-y-2.5">
          <div>
            <div className="flex justify-between text-[10px] mb-1">
              <span className="text-md-onSurfaceVar">质量</span>
              <span className="font-mono text-amber-300">95</span>
            </div>
            <input type="range" className="md-slider" defaultValue={95} min={1} max={100} />
          </div>
          <div>
            <div className="flex justify-between text-[10px] mb-1">
              <span className="text-md-onSurfaceVar">长边像素</span>
              <span className="font-mono text-amber-300">4096</span>
            </div>
            <input type="range" className="md-slider" defaultValue={4096} min={512} max={8192} />
          </div>
        </section>

        {/* Export button */}
        <button className="md-tap w-full py-3 bg-amber-400 text-md-onPrimary rounded-2xl text-[13px] font-bold flex items-center justify-center gap-2 shadow-lg shadow-amber-500/20">
          <Icon.Download size={16} />
          导出 1 张 · 队列 4 并发
        </button>
      </div>

      <BottomNav active="export" />
    </div>
  );
}

function ToggleRow({
  label,
  sub,
  on,
  onToggle,
}: {
  label: string;
  sub: string;
  on: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      onClick={onToggle}
      className="md-tap w-full flex items-center justify-between bg-md-surface2 rounded-xl px-3 py-2.5 border border-md-outline/30"
    >
      <div className="text-left">
        <div className="text-[11px] text-md-onSurface">{label}</div>
        <div className="text-[9px] text-md-onSurfaceVar">{sub}</div>
      </div>
      <div
        className={`w-9 h-5 rounded-full p-0.5 transition-colors ${
          on ? "bg-amber-400" : "bg-md-surface3"
        }`}
      >
        <div
          className={`w-4 h-4 rounded-full bg-white transition-transform ${
            on ? "translate-x-4" : ""
          }`}
        />
      </div>
    </button>
  );
}
