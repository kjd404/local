#!/usr/bin/env node
const fs = require("fs");

const env = {
  APP_ID: process.env.TELLER_APP_ID || "",
  ENROLLMENT_ID: process.env.TELLER_ENROLLMENT_ID || "",
  ENV: process.env.TELLER_ENV || "sandbox"
};

fs.writeFileSync(
  "env.js",
  "window.__ENV__ = " + JSON.stringify(env, null, 2) + ";"
);
console.log("env.js generated");
