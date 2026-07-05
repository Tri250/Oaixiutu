import { ICON_MAP } from "./icons";
import { EDITOR_TOOLS } from "../data";

interface EditorToolStripProps {
  active: string | null;
  onSelect: (id: string | null) => void;
}

export function EditorToolStrip({ active, onSelect }: EditorToolStripProps) {
  return (
    <div className="px-2 py-2 bg-md-surface2/80 backdrop-blur border-t border-md-outline/40">
      <div className="flex gap-1 overflow-x-auto phone-content">
        {EDITOR_TOOLS.map((tool) => {
          const Ico = ICON_MAP[tool.icon] ?? ICON_MAP.sun;
          const isActive = active === tool.id;
          return (
            <button
              key={tool.id}
              onClick={() => onSelect(isActive ? null : tool.id)}
              className={`md-tap flex flex-col items-center justify-center min-w-[52px] px-2 py-1.5 rounded-xl ${
                isActive
                  ? "bg-amber-400/15 text-amber-300"
                  : "text-md-onSurfaceVar hover:text-md-onSurface"
              }`}
              aria-label={tool.name}
            >
              <Ico size={20} />
              <span className="text-[9px] mt-0.5 font-medium">{tool.name}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
