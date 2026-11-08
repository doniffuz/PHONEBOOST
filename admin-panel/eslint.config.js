import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["dist/**", "node_modules/**"] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.ts"],
    languageOptions: { globals: globals.browser },
    rules: {
      // XSS himoyasi: HTML satrlari bilan ishlash taqiqlanadi, faqat dom.ts (textContent) orqali.
      "no-restricted-properties": ["error",
        { property: "innerHTML", message: "innerHTML taqiqlangan: h() yordamchisidan foydalaning." },
        { property: "outerHTML", message: "outerHTML taqiqlangan." },
        { property: "insertAdjacentHTML", message: "insertAdjacentHTML taqiqlangan." }],
      "no-eval": "error",
      "no-implied-eval": "error",
    },
  },
  { files: ["scripts/**/*.mjs", "test/**/*.ts"], languageOptions: { globals: globals.node } },
);
