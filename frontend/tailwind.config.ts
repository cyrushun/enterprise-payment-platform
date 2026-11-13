import type { Config } from "tailwindcss";
const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: { extend: { colors: { ink: "#101828", muted: "#667085", canvas: "#f5f7fb", brand: "#3157d5", mint: "#d9f5eb" }, boxShadow: { soft: "0 8px 30px rgba(16,24,40,.06)" } } },
  plugins: [],
};
export default config;
