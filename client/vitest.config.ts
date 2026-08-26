import { defineConfig } from "vitest/config";

// A separate config file (rather than a `test` block bolted onto vite.config.ts) keeps the two
// tools' concerns apart: vite.config.ts is only ever about building/serving the app, this is only
// ever about running tests. Environment is "node" rather than "jsdom" since everything under test
// so far is plain TypeScript (validation rules, request serialization, form-state helpers) with no
// DOM involved -- add "jsdom" (and a setup file) here if/when component-level tests are added.
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
