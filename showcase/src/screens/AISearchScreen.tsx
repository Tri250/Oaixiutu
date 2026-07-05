import { SEARCH_PRESETS, SEARCH_RESULTS, AI_LABELS } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";
import { BottomNav } from "./GalleryScreen";

export function AISearchScreen() {
  const { searchQuery, searchSubmitted, setSearchQuery, submitSearch } = useStore();

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Header */}
      <div className="px-4 pt-2 pb-3">
        <div className="flex items-center gap-2 mb-3">
          <Icon.Sparkle size={18} className="text-amber-300" />
          <div className="font-display text-[18px] font-medium">AI 语义搜索</div>
        </div>
        {/* Search box */}
        <div className="flex items-center gap-2 bg-md-surface2 rounded-2xl px-3 py-2.5 border border-md-outline/40">
          <Icon.Search size={18} className="text-md-onSurfaceVar" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submitSearch()}
            placeholder="用自然语言描述照片…"
            className="flex-1 bg-transparent outline-none text-[13px] text-md-onBg placeholder:text-md-onSurfaceVar/60"
          />
          <button className="md-tap w-7 h-7 rounded-full bg-amber-400/15 flex items-center justify-center text-amber-300">
            <Icon.Mic size={14} />
          </button>
        </div>
      </div>

      {/* Suggestions or Results */}
      <div className="flex-1 overflow-y-auto phone-content px-4 pb-2">
        {!searchSubmitted && searchQuery.length === 0 ? (
          <>
            <div className="text-[10px] text-md-onSurfaceVar uppercase tracking-wider mb-2 font-mono">
              试试搜索
            </div>
            <div className="flex flex-wrap gap-1.5 mb-4">
              {SEARCH_PRESETS.map((s) => (
                <button
                  key={s}
                  onClick={() => {
                    setSearchQuery(s);
                    setTimeout(submitSearch, 50);
                  }}
                  className="md-tap px-3 py-1.5 rounded-full text-[11px] bg-md-surface2 border border-md-outline/40 text-md-onSurfaceVar hover:border-amber-400/40 hover:text-amber-200"
                >
                  {s}
                </button>
              ))}
            </div>
            {/* Sample label groups */}
            <div className="text-[10px] text-md-onSurfaceVar uppercase tracking-wider mb-2 font-mono mt-2">
              AI 已识别标签
            </div>
            <div className="space-y-2">
              {AI_LABELS.slice(0, 3).map((g) => (
                <div key={g.group} className="bg-md-surface2/60 rounded-xl p-2.5 border border-md-outline/30">
                  <div className="text-[10px] text-amber-300/70 mb-1.5">{g.group}</div>
                  <div className="flex flex-wrap gap-1">
                    {g.items.map((it) => (
                      <span
                        key={it}
                        className="text-[10px] px-2 py-0.5 rounded bg-md-surface3 text-md-onSurface/80"
                      >
                        {it}
                      </span>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="animate-slide-up">
            <div className="flex items-center gap-1.5 text-[10px] text-amber-300/80 mb-2 font-mono">
              <Icon.Sparkle size={10} />
              <span>
                {searchSubmitted
                  ? `检索完成 · ${SEARCH_RESULTS.length} 张匹配`
                  : "AI 正在分析 1,247 张照片…"}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {SEARCH_RESULTS.map((r) => (
                <div key={r.id} className="rounded-xl overflow-hidden border border-md-outline/30 bg-md-surface2">
                  <div className={`aspect-[4/5] bg-gradient-to-br ${r.grad} relative`}>
                    <div className="absolute inset-0 scanline opacity-30" />
                    <div className="absolute top-1.5 left-1.5 text-[8px] font-mono bg-black/40 backdrop-blur text-white px-1.5 py-0.5 rounded">
                      {(r.sim * 100).toFixed(0)}%
                    </div>
                    <div className="absolute bottom-1.5 left-1.5 right-1.5 text-[9px] text-white/90 bg-black/40 backdrop-blur px-1.5 py-0.5 rounded text-center">
                      {r.label}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      <BottomNav active="ai" />
    </div>
  );
}
