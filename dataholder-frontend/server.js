/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { createRequestHandler } from "@react-router/express";
import compression from "compression";
import express from "express";
import helmet from "helmet";
import morgan from "morgan";
import crypto from "node:crypto";

const build = await import("./build/server/index.js");

const app = express();
app.disable("x-powered-by");
app.use(compression());

// Per-request CSP nonce so inline hydration scripts can drop 'unsafe-inline'.
app.use((req, res, next) => {
  res.locals.cspNonce = crypto.randomBytes(16).toString("base64");
  next();
});

app.use(
  helmet({
    contentSecurityPolicy: {
      directives: {
        defaultSrc: ["'self'"],
        scriptSrc: [
          "'self'",
          (req, res) => `'nonce-${res.locals.cspNonce}'`,
        ],
        styleSrc: ["'self'", "'unsafe-inline'"],
        imgSrc: ["'self'", "data:"],
        fontSrc: ["'self'", "data:"],
        connectSrc: ["'self'"],
        objectSrc: ["'none'"],
        frameAncestors: ["'self'"],
        baseUri: ["'self'"],
      },
    },
    hsts: false,
    frameguard: false,
  })
);
app.use(morgan("tiny"));
app.use(
  "/assets",
  express.static("build/client/assets", { immutable: true, maxAge: "1y" })
);
app.use(express.static("build/client", { maxAge: "1h" }));
app.all(
  "*",
  createRequestHandler({
    build,
    mode: process.env.NODE_ENV,
    getLoadContext(req, res) {
      return { cspNonce: res.locals.cspNonce };
    },
  })
);

const port = process.env.PORT || 3000;
app.listen(port, () => console.log("SSR listening on", port));
