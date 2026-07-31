/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import {
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  useLoaderData,
  useRouteLoaderData,
  data,
} from "react-router";
import { Toaster } from "react-hot-toast";
import bootstrapHref from "bootstrap/dist/css/bootstrap.min.css?url";
import fontAwesomeHref from "@fortawesome/fontawesome-free/css/all.min.css?url";
import indexHref from "../src/index.css?url";
import mainStylesHref from "../src/styles/main.scss?url";
import { I18nProvider } from "../src/i18n";
import { AuthProvider } from "../src/contexts/AuthContext";
import Footer from "../src/components/Footer";
import { getSession, getUser, commitSession } from "./lib/session.server.js";
import { getCsrfToken } from "./lib/csrf.server.js";
import { getLanguage } from "./lib/i18n.server.js";
import { SOURCE_CODE_URL } from "./lib/config.server.js";

// Emit real <link rel="stylesheet"> tags via <Links/> so styles are server-rendered
// identically in dev and prod (side-effect CSS imports only inject via client JS in dev).
export const links = () => [
  { rel: "stylesheet", href: bootstrapHref },
  { rel: "stylesheet", href: fontAwesomeHref },
  { rel: "stylesheet", href: indexHref },
  { rel: "stylesheet", href: mainStylesHref },
];

export async function loader({ request, context }) {
  const session = await getSession(request);
  const { token: csrf, created } = getCsrfToken(session);
  const payload = {
    user: getUser(session),
    language: getLanguage(request),
    config: { sourceCodeUrl: SOURCE_CODE_URL },
    csrf,
    cspNonce: context?.cspNonce,
  };
  // Commit the session only when a token was just minted.
  if (created) {
    return data(payload, {
      headers: { "Set-Cookie": await commitSession(session) },
    });
  }
  return payload;
}

/*
 * Default document title. <Meta/> renders whatever the deepest matching route exports, so this
 * applies to any route that does not define its own meta.
 */
export function meta() {
  return [{ title: "Data Holder" }];
}

export function Layout({ children }) {
  // Read the per-request CSP nonce from the root loader.
  const nonce = useRouteLoaderData("root")?.cspNonce;
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration nonce={nonce} />
        <Scripts nonce={nonce} />
      </body>
    </html>
  );
}

export default function App() {
  const { user, language } = useLoaderData();
  return (
    <I18nProvider initialLanguage={language}>
      <AuthProvider initialUser={user}>
        <div className="app">
          <Outlet />
          <Toaster
            position="bottom-right"
            toastOptions={{
              duration: 4000,
              style: {
                background: "var(--bg-secondary)",
                color: "var(--text-primary)",
                border: "1px solid var(--border-primary)",
                borderRadius: "var(--radius-md)",
              },
              success: {
                iconTheme: {
                  primary: "var(--accent-success)",
                  secondary: "var(--bg-secondary)",
                },
              },
              error: {
                iconTheme: {
                  primary: "var(--accent-danger)",
                  secondary: "var(--bg-secondary)",
                },
              },
            }}
          />
          <Footer />
        </div>
      </AuthProvider>
    </I18nProvider>
  );
}
