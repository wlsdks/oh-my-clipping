import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import reactCompiler from "eslint-plugin-react-compiler";
import tseslint from "typescript-eslint";
import prettierConfig from "eslint-config-prettier";

export default tseslint.config(
  {
    ignores: ["dist", "node_modules", "src/components/ui/**", "tests/**"]
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, prettierConfig],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: globals.browser
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
      "react-compiler": reactCompiler
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // React Compiler handles exhaustive-deps automatically — disable to avoid conflicts
      "react-hooks/exhaustive-deps": "off",
      "react-refresh/only-export-components": ["warn", { allowConstantExport: true }],
      "react-compiler/react-compiler": "error"
    }
  },
  {
    files: [
      "src/pages/**/*.{ts,tsx}",
      "src/features/**/*.{ts,tsx}",
      "src/components/**/*.{ts,tsx}"
    ],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/pages/*"],
              message: "페이지/기능/컴포넌트 내부에서는 다른 page slice를 절대 경로로 import하지 마세요. 공유 UI는 features 또는 components/shared로 올리고, 같은 page 내부는 상대 경로를 쓰세요."
            }
          ]
        }
      ]
    }
  },
  {
    // Files that intentionally mix component and non-component exports (constants, utils)
    files: [
      "src/pages/dashboard/ui/QuickSetupStepPersona.tsx",
      "src/features/news-intelligence/ui/*.tsx",
      "src/shared/ui/*.tsx",
      // 테스트용 상수 re-export 포함 (__*_FOR_TEST)
      "src/components/shared/BottomNavigation.tsx",
      "src/components/shared/UserBottomNavigation.tsx",
      // 차트 팔레트 유틸 + 렌더 컴포넌트 공존 (TossTooltip/AreaGradientDef + 팔레트 헬퍼)
      "src/utils/chartTheme.tsx"
    ],
    rules: {
      "react-refresh/only-export-components": "off"
    }
  }
);
