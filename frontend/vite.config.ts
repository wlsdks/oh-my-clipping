/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [
    tailwindcss(),
    react({
      babel: {
        plugins: ["babel-plugin-react-compiler"]
      }
    })
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src")
    }
  },
  server: {
    proxy: {
      "/api": "http://localhost:8086",
      "/login": "http://localhost:8086",
      "/logout": "http://localhost:8086"
    }
  },
  base: "/",
  build: {
    outDir: path.resolve(__dirname, "../src/main/resources/static"),
    emptyOutDir: true,
    assetsDir: "assets",
    // 500KB 경고를 550KB 로 완화. recharts 같은 거대 라이브러리는 lazy 페이지에서만 로드되므로 초기 번들 영향 없음.
    chunkSizeWarningLimit: 550,
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          // 노드 모듈이 아닌 소스 파일은 기본 분할 로직에 위임 (route-level lazy split 유지).
          if (!id.includes("node_modules")) return undefined;

          // React 런타임 + 라우터 — 모든 페이지에서 필요. 별도 chunk 로 분리해 장기 캐시.
          if (/[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/.test(id)) {
            return "react-vendor";
          }
          // recharts — 분석/대시보드 페이지 전용. 라우트가 lazy 이므로 이 vendor 도 lazy load 됨.
          if (id.includes("/node_modules/recharts/") || id.includes("/node_modules/victory-vendor/") || id.includes("/node_modules/d3-")) {
            return "recharts";
          }
          // framer-motion — 일부 페이지(위자드 등)에서만 사용. 초기 번들에서 분리.
          if (id.includes("/node_modules/framer-motion/") || id.includes("/node_modules/motion-")) {
            return "framer-motion";
          }
          // TanStack Query — 전역 QueryProvider 에서 사용하지만 크기가 커서 별도 분리.
          if (id.includes("/node_modules/@tanstack/")) {
            return "tanstack";
          }
          // Radix UI primitives — shadcn/ui 전반에서 사용. 모아서 하나의 chunk 로.
          if (id.includes("/node_modules/@radix-ui/")) {
            return "radix";
          }
          // 폼 패키지 — 일부 페이지 전용.
          if (
            id.includes("/node_modules/react-hook-form/") ||
            id.includes("/node_modules/@hookform/") ||
            id.includes("/node_modules/zod/")
          ) {
            return "forms";
          }
          // Sentry — 초기 bootstrap 에서 로드되지만 독립 chunk 로 분리해 캐시 수명 최적화.
          if (id.includes("/node_modules/@sentry/")) {
            return "sentry";
          }
          // 아이콘/토스트 — 다수 페이지에서 import. 공용 UI vendor 로 묶음.
          if (
            id.includes("/node_modules/lucide-react/") ||
            id.includes("/node_modules/sonner/") ||
            id.includes("/node_modules/next-themes/")
          ) {
            return "ui-vendor";
          }
          return undefined;
        }
      }
    }
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    exclude: ["tests/e2e/**", "node_modules/**", "admin-spa/**"],
    coverage: {
      provider: "v8",
      reporter: ["text", "json-summary"],
      reportsDirectory: "./coverage",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/test/**", "src/**/*.test.*", "src/**/*.spec.*", "src/components/ui/**"]
    }
  }
});
