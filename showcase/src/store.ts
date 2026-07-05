import { create } from "zustand";
import type { ScreenId } from "./data";

interface AppState {
  activeScreen: ScreenId;
  setActiveScreen: (id: ScreenId) => void;
  // editor sub-state
  activeTool: string | null;
  setActiveTool: (id: string | null) => void;
  // color engine
  colorEngine: "aces" | "opendrt";
  setColorEngine: (e: "aces" | "opendrt") => void;
  // selected film
  selectedFilm: string;
  setSelectedFilm: (id: string) => void;
  // export format
  exportFormat: string;
  setExportFormat: (id: string) => void;
  // settings toggles
  settings: Record<string, boolean>;
  toggleSetting: (key: string) => void;
  // ai search query
  searchQuery: string;
  searchSubmitted: boolean;
  setSearchQuery: (q: string) => void;
  submitSearch: () => void;
}

export const useStore = create<AppState>((set) => ({
  activeScreen: "gallery",
  setActiveScreen: (id) =>
    set({ activeScreen: id, activeTool: null, searchSubmitted: false }),
  activeTool: null,
  setActiveTool: (id) => set({ activeTool: id }),
  colorEngine: "aces",
  setColorEngine: (e) => set({ colorEngine: e }),
  selectedFilm: "portra400",
  setSelectedFilm: (id) => set({ selectedFilm: id }),
  exportFormat: "ultrahdr",
  setExportFormat: (id) => set({ exportFormat: id }),
  settings: {
    "mobileclip-s2": true,
    "jina-clip-v2": false,
    "siglip2": false,
    "gles": true,
    "vulkan": false,
    "embedIcc": true,
    "hdrGainMap": true,
  },
  toggleSetting: (key) =>
    set((s) => ({ settings: { ...s.settings, [key]: !s.settings[key] } })),
  searchQuery: "",
  searchSubmitted: false,
  setSearchQuery: (q) => set({ searchQuery: q, searchSubmitted: false }),
  submitSearch: () => set({ searchSubmitted: true }),
}));
