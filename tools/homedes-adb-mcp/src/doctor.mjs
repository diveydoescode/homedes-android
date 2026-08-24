#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, "../../..");
const JAVA_HOME =
  process.env.JAVA_HOME ||
  "C:\\Program Files\\Microsoft\\jdk-17.0.20.101-hotspot";
const ANDROID_HOME =
  process.env.ANDROID_HOME ||
  path.join(process.env.LOCALAPPDATA || "", "Android", "Sdk");
const ADB = path.join(ANDROID_HOME, "platform-tools", "adb.exe");

const checks = [];
function ok(name, pass, detail) {
  checks.push({ name, pass: !!pass, detail });
  console.log(`${pass ? "OK " : "FAIL"}  ${name}${detail ? ` — ${detail}` : ""}`);
}

ok("repo", fs.existsSync(path.join(REPO, "settings.gradle.kts")), REPO);
ok("java", fs.existsSync(JAVA_HOME), JAVA_HOME);
ok("android_sdk", fs.existsSync(ANDROID_HOME), ANDROID_HOME);
ok("adb", fs.existsSync(ADB), ADB);
ok("gradlew", fs.existsSync(path.join(REPO, "gradlew.bat")));
ok("node_modules", fs.existsSync(path.join(__dirname, "..", "node_modules")));

if (fs.existsSync(ADB)) {
  const r = spawnSync(ADB, ["devices"], { encoding: "utf8" });
  const devices = (r.stdout || "")
    .split(/\r?\n/)
    .slice(1)
    .filter((l) => /\tdevice$/.test(l));
  ok("adb_device", devices.length > 0, devices.join(", ") || "none online");
}

const failed = checks.filter((c) => !c.pass);
process.exit(failed.length ? 1 : 0);
