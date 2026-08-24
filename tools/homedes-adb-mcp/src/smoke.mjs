#!/usr/bin/env node
/** Quick tools/list smoke against the stdio MCP server. */
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const serverPath = path.join(__dirname, "index.mjs");

const transport = new StdioClientTransport({
  command: "node",
  args: [serverPath],
  cwd: path.join(__dirname, ".."),
});

const client = new Client({ name: "smoke", version: "1.0.0" });
await client.connect(transport);
const tools = await client.listTools();
console.log(
  "TOOLS",
  tools.tools.map((t) => t.name).sort().join(", "),
);
const status = await client.callTool({ name: "hd_status", arguments: {} });
console.log("STATUS", status.content?.[0]?.text?.slice(0, 800));
await client.close();
process.exit(0);
