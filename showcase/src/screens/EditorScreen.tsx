import { TOOL_PARAMS } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";
import { EditorToolStrip } from "../components/EditorToolStrip";

export function EditorScreen() {
  const { activeTool, setActiveTool } = useStore();
  const params = activeTool ? TOOL_PARAMS[activeTool] : null;

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Top toolbar */}
      <div className="px-3 py-2 flex items-center gap-1 bg-md-surface2/60 backdrop-blur border-b border-md-outline/30">
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-md-onSurfaceVar">
          <Icon.ChevronLeft size={18} />
        </button>
        <div className="flex-1 min-w-0">
          <div className="text-[11px] font-mono text-md-onSurface truncate">
            DSC_2841.RAW
          </div>
          <div className="text-[9px] text-md-onSurfaceVar font-mono">
            150MP · 16-bit
          </div>
        </div>
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-md-onSurfaceVar">
          <Icon.Undo size={16} />
        </button>
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-md-onSurfaceVar">
          <Icon.Redo size={16} />
        </button>
        <button className="md-tap w-8 h-8 rounded-full flex items-center justify-center text-amber-300">
          <Icon.History size={16} />
        </button>
      </div>

      {/* Preview area */}
      <div className="flex-1 relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-200 via-rose-300 to-film-portra" />
        <div className="absolute inset-0 scanline opacity-20" />
        {/* Simulated grain */}
        <div
          className="absolute inset-0 opacity-25 mix-blend-overlay"
          style={{
            backgroundImage:
              "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='2.5' numOctaves='1'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>\")",
          }}
        />

        {/* Histogram overlay */}
        <div className="absolute top-2 right-2 w-32 h-16 bg-black/50 backdrop-blur-sm rounded-lg p-1.5 border border-white/10">
          <div className="text-[8px] text-white/70 font-mono mb-0.5 flex justify-between">
            <span>HISTOGRAM</span>
            <span className="text-amber-300">RGB</span>
          </div>
          <svg viewBox="0 0 120 40" className="w-full h-8" preserveAspectRatio="none">
            <defs>
              <linearGradient id="hg-r" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0%" stopColor="#ff5252" stopOpacity="0.6" />
                <stop offset="50%" stopColor="#ff8a5a" stopOpacity="0.9" />
                <stop offset="100%" stopColor="#ff5252" stopOpacity="0.4" />
              </linearGradient>
              <linearGradient id="hg-g" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0%" stopColor="#69f0ae" stopOpacity="0.6" />
                <stop offset="40%" stopColor="#69f0ae" stopOpacity="0.9" />
                <stop offset="100%" stopColor="#69f0ae" stopOpacity="0.4" />
              </linearGradient>
              <linearGradient id="hg-b" x1="0" x2="1" y1="0" y2="0">
                <stop offset="0%" stopColor="#448aff" stopOpacity="0.4" />
                <stop offset="60%" stopColor="#448aff" stopOpacity="0.9" />
                <stop offset="100%" stopColor="#448aff" stopOpacity="0.6" />
              </linearGradient>
            </defs>
            <path
              d="M0,40 L0,32 L10,28 L20,18 L30,8 L40,4 L50,6 L60,10 L70,14 L80,22 L90,28 L100,32 L110,36 L120,40 Z"
              fill="url(#hg-r)"
            />
            <path
              d="M0,40 L0,34 L10,30 L20,22 L30,14 L40,10 L50,12 L60,16 L70,20 L80,26 L90,30 L100,34 L110,38 L120,40 Z"
              fill="url(#hg-g)"
              opacity="0.7"
            />
            <path
              d="M0,40 L0,36 L10,34 L20,30 L30,24 L40,20 L50,22 L60,24 L70,26 L80,30 L90,34 L100,36 L110,38 L120,40 Z"
              fill="url(#hg-b)"
              opacity="0.6"
            />
          </svg>
        </div>

        {/* Floating badge */}
        <div className="absolute top-2 left-2 flex items-center gap-1 bg-black/50 backdrop-blur-sm rounded-full px-2 py-1 border border-white/10">
          <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse-soft" />
          <span className="text-[9px] text-white/90 font-mono">实时 · 60fps</span>
        </div>

        {/* Tool params panel (when active) */}
        {activeTool && params && (
          <div className="absolute bottom-2 left-2 right-2 bg-md-surface/95 backdrop-blur rounded-2xl border border-md-outline/50 p-3 animate-slide-up">
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-medium text-amber-300">
                {activeTool.toUpperCase()} 调整
              </span>
              <button
                onClick={() => setActiveTool(null)}
                className="md-tap text-[10px] text-md-onSurfaceVar px-2 py-0.5 rounded bg-md-surface3"
              >
                收起
              </button>
            </div>
            <div className="space-y-2 max-h-[140px] overflow-y-auto phone-content">
              {params.map((p) => (
                <div key={p.name}>
                  <div className="flex justify-between items-baseline mb-0.5">
                    <span className="text-[10px] text-md-onSurfaceVar">{p.name}</span>
                    <span className="text-[10px] font-mono text-amber-300">
                      {p.default}
                      {p.unit ? ` ${p.unit}` : ""}
                    </span>
                  </div>
                  <input
                    type="range"
                    className="md-slider"
                    min={p.min}
                    max={p.max}
                    defaultValue={p.default}
                  />
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Tool strip */}
      <EditorToolStrip active={activeTool} onSelect={setActiveTool} />
    </div>
  );
}
