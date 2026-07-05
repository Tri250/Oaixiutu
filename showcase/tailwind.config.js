/** @type {import('tailwindcss').Config} */

export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    container: { center: true },
    extend: {
      colors: {
        // Workbench (web shell)
        bench: {
          950: "#0c0d10",
          900: "#131519",
          850: "#16181d",
          800: "#1c1f25",
          700: "#262a31",
          600: "#3a3f48",
          500: "#565b66",
        },
        // Alcedo amber accent
        amber: {
          DEFAULT: "#d4823a",
          50: "#fdf3ea",
          100: "#f7dcbf",
          200: "#efbf8a",
          300: "#e59e57",
          400: "#d4823a",
          500: "#b66a2a",
          600: "#8f511f",
        },
        // Cool secondary
        arctic: {
          DEFAULT: "#4a90a4",
          100: "#cfe3ea",
          400: "#4a90a4",
          600: "#2d6473",
        },
        // Material You surface (in-phone, dark theme)
        md: {
          bg: "#0d0f12",
          surface: "#16181d",
          surface2: "#1f2229",
          surface3: "#2a2e36",
          outline: "#3a3f48",
          onBg: "#f5f1ea",
          onSurface: "#e8e3da",
          onSurfaceVar: "#a8a39a",
          primary: "#d4823a",
          onPrimary: "#1a0d04",
          secondary: "#4a90a4",
          tertiary: "#7c6f5a",
          error: "#cf6679",
        },
        // Film tones for thumbnails
        film: {
          portra: "#c89668",
          velvia: "#1f5fa8",
          trix: "#2a2724",
          ektar: "#b8472e",
          gold: "#d4a64a",
          hp5: "#5c5852",
        },
      },
      fontFamily: {
        sans: ['Manrope', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'monospace'],
        display: ['Fraunces', 'Georgia', 'serif'],
      },
      borderRadius: {
        phone: '36px',
      },
      animation: {
        'fade-in': 'fadeIn 0.6s ease-out both',
        'slide-up': 'slideUp 0.5s ease-out both',
        'pulse-soft': 'pulseSoft 2.4s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        pulseSoft: {
          '0%,100%': { opacity: '0.55' },
          '50%': { opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};
