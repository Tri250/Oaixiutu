import { AI_MODELS } from "../data";
import { Icon } from "../components/icons";
import { useStore } from "../store";
import { BottomNav } from "./GalleryScreen";

export function SettingsScreen() {
  const { settings, toggleSetting } = useStore();

  return (
    <div className="absolute inset-0 flex flex-col bg-md-bg text-md-onBg animate-fade-in">
      {/* Header */}
      <div className="px-4 pt-2 pb-3">
        <div className="font-display text-[20px] font-medium">设置</div>
        <div className="text-[10px] text-md-onSurfaceVar font-mono">
          Alcedo Studio · v1.0.0 · 开源
        </div>
      </div>

      <div className="flex-1 overflow-y-auto phone-content px-4 pb-3 space-y-3">
        {/* AI Models */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Cpu size={10} /> AI 模型管理
          </div>
          <div className="space-y-1.5">
            {AI_MODELS.map((m) => (
              <div
                key={m.id}
                className="flex items-center gap-2.5 bg-md-surface2 rounded-xl p-2.5 border border-md-outline/30"
              >
                <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-amber-400/30 to-arctic-400/20 flex items-center justify-center">
                  <Icon.Sparkle size={16} className="text-amber-300" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-[11px] text-md-onSurface font-medium">{m.name}</div>
                  <div className="text-[9px] text-md-onSurfaceVar truncate">{m.desc}</div>
                  <div className="text-[8px] text-md-onSurfaceVar font-mono mt-0.5">
                    {m.size} · {m.id}.onnx
                  </div>
                </div>
                <button
                  onClick={() => toggleSetting(m.id)}
                  className={`w-9 h-5 rounded-full p-0.5 transition-colors shrink-0 ${
                    settings[m.id] ? "bg-amber-400" : "bg-md-surface3"
                  }`}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-white transition-transform ${
                      settings[m.id] ? "translate-x-4" : ""
                    }`}
                  />
                </button>
              </div>
            ))}
          </div>
        </section>

        {/* GPU backend */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Aperture size={10} /> GPU 计算后端
          </div>
          <div className="bg-md-surface2 rounded-xl p-1 flex border border-md-outline/30">
            <button
              onClick={() => {
                if (!settings.gles) {
                  toggleSetting("gles");
                  toggleSetting("vulkan");
                }
              }}
              className={`md-tap flex-1 py-2 rounded-lg text-[11px] font-medium ${
                settings.gles ? "bg-amber-400 text-md-onPrimary" : "text-md-onSurfaceVar"
              }`}
            >
              GLES Compute
            </button>
            <button
              onClick={() => {
                if (!settings.vulkan) {
                  toggleSetting("vulkan");
                  toggleSetting("gles");
                }
              }}
              className={`md-tap flex-1 py-2 rounded-lg text-[11px] font-medium ${
                settings.vulkan ? "bg-amber-400 text-md-onPrimary" : "text-md-onSurfaceVar"
              }`}
            >
              Vulkan
            </button>
          </div>
          <div className="text-[9px] text-md-onSurfaceVar mt-1.5 font-mono px-1">
            25 着色器 · 实时管线 · 60fps
          </div>
        </section>

        {/* Asset libraries */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider">
            内置资产
          </div>
          <div className="bg-md-surface2 rounded-xl divide-y divide-md-outline/30 border border-md-outline/30">
            <AssetRow icon={<Icon.Lens size={14} />} label="镜头校准库" value="60+ XML" />
            <AssetRow icon={<Icon.Palette size={14} />} label="ICC 配置文件" value="11 个" />
            <AssetRow icon={<Icon.Layers size={14} />} label="胶片 LUT 预设" value="6 款" />
            <AssetRow icon={<Icon.Aperture size={14} />} label="OpenDRT 配置" value="已加载" />
          </div>
        </section>

        {/* Security */}
        <section>
          <div className="text-[10px] text-amber-300/80 mb-2 font-mono uppercase tracking-wider flex items-center gap-1">
            <Icon.Shield size={10} /> 隐私与安全
          </div>
          <div className="bg-md-surface2 rounded-xl divide-y divide-md-outline/30 border border-md-outline/30">
            <AssetRow icon={<Icon.Lock size={14} />} label="证书锁定" value="已启用" />
            <AssetRow icon={<Icon.Shield size={14} />} label="SQLCipher 加密" value="已启用" />
            <AssetRow icon={<Icon.Cpu size={14} />} label="端侧 AI 推理" value="无需联网" />
          </div>
        </section>

        {/* About */}
        <section className="text-center pt-2">
          <div className="font-display text-[14px] text-amber-300 mb-1">Alcedo Studio</div>
          <div className="text-[9px] text-md-onSurfaceVar font-mono">
            v1.0.0 · build 20260705
          </div>
          <div className="text-[9px] text-md-onSurfaceVar font-mono mt-1">
            AI 驱动 · 开源免费 · 专业 RAW
          </div>
        </section>
      </div>

      <BottomNav active="settings" />
    </div>
  );
}

function AssetRow({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-2.5 px-3 py-2.5">
      <div className="text-amber-300/70">{icon}</div>
      <div className="flex-1 text-[11px] text-md-onSurface">{label}</div>
      <div className="text-[10px] font-mono text-md-onSurfaceVar">{value}</div>
      <Icon.ChevronRight size={12} className="text-md-onSurfaceVar" />
    </div>
  );
}
