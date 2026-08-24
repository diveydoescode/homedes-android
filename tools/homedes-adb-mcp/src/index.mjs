#!/usr/bin/env node
/**
 * HomeDesign Android control MCP — stdio transport.
 * Lets Grok assemble, install, launch, screenshot, dump UI, tap, and read logcat.
 */
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, "../../..");
const SCRIPTS = path.join(REPO, "scripts");
const ARTIFACTS = path.join(REPO, "artifacts");

const JAVA_HOME =
  process.env.JAVA_HOME ||
  "C:\\Program Files\\Microsoft\\jdk-17.0.20.101-hotspot";
const ANDROID_HOME =
  process.env.ANDROID_HOME ||
  path.join(process.env.LOCALAPPDATA || "", "Android", "Sdk");
const ADB = path.join(ANDROID_HOME, "platform-tools", "adb.exe");
const GRADLE = path.join(REPO, "gradlew.bat");
const DEBUG_PKG = "com.homedesign.android.debug";
const COMPONENT = `${DEBUG_PKG}/com.homedesign.android.MainActivity`;
const APK = path.join(REPO, "app", "build", "outputs", "apk", "debug", "app-debug.apk");

function env() {
  return {
    ...process.env,
    JAVA_HOME,
    ANDROID_HOME,
    ANDROID_SDK_ROOT: ANDROID_HOME,
    PATH: [
      path.join(JAVA_HOME, "bin"),
      path.join(ANDROID_HOME, "platform-tools"),
      path.join(ANDROID_HOME, "emulator"),
      process.env.PATH || "",
    ].join(path.delimiter),
  };
}

function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, {
    encoding: "utf8",
    env: env(),
    cwd: opts.cwd || REPO,
    timeout: opts.timeout ?? 600_000,
    maxBuffer: 8 * 1024 * 1024,
    shell: false,
  });
  const out = `${r.stdout || ""}${r.stderr || ""}`.trim();
  if (r.error) {
    return { ok: false, code: -1, out: String(r.error) };
  }
  return { ok: r.status === 0, code: r.status ?? 1, out };
}

function runPs(scriptName, extraArgs = [], timeout = 600_000) {
  const script = path.join(SCRIPTS, scriptName);
  return run(
    "powershell.exe",
    [
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      script,
      ...extraArgs,
    ],
    { timeout },
  );
}

function adb(args, timeout = 120_000) {
  if (!fs.existsSync(ADB)) {
    return { ok: false, code: -1, out: `adb missing: ${ADB}` };
  }
  return run(ADB, args, { timeout });
}

function devices() {
  const r = adb(["devices"]);
  if (!r.ok) return [];
  return r.out
    .split(/\r?\n/)
    .slice(1)
    .map((l) => l.trim())
    .filter((l) => l.endsWith("\tdevice") || /\tdevice$/.test(l))
    .map((l) => l.split(/\s+/)[0])
    .filter(Boolean);
}

function textResult(obj) {
  const text = typeof obj === "string" ? obj : JSON.stringify(obj, null, 2);
  return { content: [{ type: "text", text }] };
}

fs.mkdirSync(ARTIFACTS, { recursive: true });

const server = new McpServer({
  name: "homedes-adb",
  version: "1.0.0",
});

server.tool(
  "hd_status",
  "Report repo paths, JAVA/ANDROID homes, adb devices, and APK presence.",
  {},
  async () =>
    textResult({
      repo: REPO,
      javaHome: JAVA_HOME,
      androidHome: ANDROID_HOME,
      adb: ADB,
      adbExists: fs.existsSync(ADB),
      apk: APK,
      apkExists: fs.existsSync(APK),
      debugPackage: DEBUG_PKG,
      component: COMPONENT,
      devices: devices(),
    }),
);

server.tool(
  "hd_devices",
  "List online adb devices/emulators.",
  {},
  async () => textResult({ devices: devices(), raw: adb(["devices"]).out }),
);

server.tool(
  "hd_ensure_emulator",
  "If no device is online, start the first AVD (or named AVD) and wait until boot completes.",
  { avd: z.string().optional().describe("AVD name; default = first listed") },
  async ({ avd }) => {
    const args = [];
    if (avd) args.push("-Avd", avd);
    const r = runPs("ensure-emulator.ps1", args, 300_000);
    return textResult({ ...r, devices: devices() });
  },
);

server.tool(
  "hd_assemble",
  "Run :app:assembleDebug (and optionally unit tests).",
  { test: z.boolean().optional().describe("Also run :app:testDebugUnitTest") },
  async ({ test }) => {
    const args = test ? ["-Test"] : [];
    return textResult(runPs("assemble.ps1", args, 600_000));
  },
);

server.tool(
  "hd_test",
  "Run unit tests, optionally a single class.",
  {
    className: z
      .string()
      .optional()
      .describe("Fully-qualified test class, e.g. com.homedesign.android.domain.io.Sh3dReaderTest"),
  },
  async ({ className }) => {
    const args = className ? ["-Class", className] : [];
    return textResult(runPs("test.ps1", args, 600_000));
  },
);

server.tool(
  "hd_install_launch",
  "Install debug APK (-r) and start MainActivity. Builds first unless noBuild.",
  {
    noBuild: z.boolean().optional(),
    serial: z.string().optional(),
  },
  async ({ noBuild, serial }) => {
    const args = [];
    if (noBuild) args.push("-NoBuild");
    if (serial) args.push("-Serial", serial);
    return textResult(runPs("install-launch.ps1", args, 600_000));
  },
);

server.tool(
  "hd_stop_app",
  "Force-stop the debug app.",
  {},
  async () => textResult(runPs("stop-app.ps1")),
);

server.tool(
  "hd_dev_up",
  "Full pipeline: optional emulator + assemble(+tests) + install + launch.",
  {
    skipTests: z.boolean().optional(),
    startEmulator: z.boolean().optional(),
    sketchProxy: z.boolean().optional(),
    avd: z.string().optional(),
  },
  async ({ skipTests, startEmulator, sketchProxy, avd }) => {
    const args = [];
    if (skipTests) args.push("-SkipTests");
    if (startEmulator) args.push("-StartEmulator");
    if (sketchProxy) args.push("-SketchProxy");
    if (avd) args.push("-Avd", avd);
    return textResult(runPs("dev-up.ps1", args, 900_000));
  },
);

server.tool(
  "hd_screenshot",
  "Capture a PNG screenshot to artifacts/ and return its path (+ optional note).",
  { out: z.string().optional() },
  async ({ out }) => {
    const args = out ? ["-Out", out] : [];
    const r = runPs("screenshot.ps1", args);
    return textResult(r);
  },
);

server.tool(
  "hd_ui_dump",
  "Dump UI Automator XML hierarchy for the current screen (agent-readable).",
  { out: z.string().optional() },
  async ({ out }) => {
    const args = out ? ["-Out", out] : [];
    return textResult(runPs("dump-ui.ps1", args));
  },
);

server.tool(
  "hd_tap",
  "Tap pixel coordinates on the device.",
  { x: z.number().int(), y: z.number().int() },
  async ({ x, y }) => textResult(runPs("tap.ps1", ["-X", String(x), "-Y", String(y)])),
);

server.tool(
  "hd_swipe",
  "Swipe between two points.",
  {
    x1: z.number().int(),
    y1: z.number().int(),
    x2: z.number().int(),
    y2: z.number().int(),
    durationMs: z.number().int().optional(),
  },
  async ({ x1, y1, x2, y2, durationMs }) => {
    const args = [
      "-X1",
      String(x1),
      "-Y1",
      String(y1),
      "-X2",
      String(x2),
      "-Y2",
      String(y2),
    ];
    if (durationMs != null) args.push("-DurationMs", String(durationMs));
    return textResult(runPs("swipe.ps1", args));
  },
);

server.tool(
  "hd_input_text",
  "Type text into the focused field.",
  { text: z.string() },
  async ({ text }) => textResult(runPs("input-text.ps1", ["-Text", text])),
);

server.tool(
  "hd_press_key",
  "Send a keyevent code (BACK=4 HOME=3 ENTER=66 DEL=67 TAB=61).",
  { code: z.number().int() },
  async ({ code }) => textResult(runPs("press-key.ps1", ["-Code", String(code)])),
);

server.tool(
  "hd_logcat",
  "Return recent logcat lines filtered to the app / crashes.",
  {
    lines: z.number().int().optional(),
    filter: z.string().optional(),
  },
  async ({ lines, filter }) => {
    const args = [];
    if (lines != null) args.push("-Lines", String(lines));
    if (filter) args.push("-Filter", filter);
    return textResult(runPs("logcat.ps1", args));
  },
);

server.tool(
  "hd_shell",
  "Run a raw adb shell command (careful).",
  { command: z.string() },
  async ({ command }) => {
    const serials = devices();
    const args = [];
    if (serials[0]) args.push("-s", serials[0]);
    args.push("shell", command);
    return textResult(adb(args));
  },
);

server.tool(
  "hd_gradle",
  "Run arbitrary gradle tasks in the repo (e.g. :app:assembleDebug).",
  {
    tasks: z.array(z.string()).min(1),
    extraArgs: z.array(z.string()).optional(),
  },
  async ({ tasks, extraArgs }) => {
    const args = [...tasks, "--no-daemon", ...(extraArgs || [])];
    return textResult(run(GRADLE, args, { timeout: 900_000 }));
  },
);

const transport = new StdioServerTransport();
await server.connect(transport);
