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
import bootstrapIconsHref from "bootstrap-icons/font/bootstrap-icons.css?url";
import indexHref from "../src/index.css?url";
import appStylesHref from "../src/App.scss?url";
import { I18nProvider } from "../src/i18n";
import { AuthProvider } from "../src/contexts/AuthContext";
import { AlertProvider } from "../src/contexts/AlertContext";
import { ConfigProvider } from "../src/contexts/ConfigContext";
import { AlertContainer } from "../src/components/AlertModal";
import Footer from "../src/components/Footer";
import { getSession, getUser, commitSession } from "./lib/session.server.js";
import { getCsrfToken } from "./lib/csrf.server.js";
import { getLanguage } from "./lib/i18n.server.js";
import { KEYCLOAK_ADMIN_URL, APP_VERSION, SOURCE_CODE_URL } from "./lib/config.server.js";

// Emit real <link rel="stylesheet"> tags via <Links/> so styles are server-rendered
// identically in dev and prod (side-effect CSS imports only inject via client JS in dev).
export const links = () => [
  { rel: "stylesheet", href: bootstrapIconsHref },
  { rel: "stylesheet", href: indexHref },
  { rel: "stylesheet", href: appStylesHref },
];

export async function loader({ request, context }) {
  const session = await getSession(request);
  const { token: csrf, created } = getCsrfToken(session);
  const payload = {
    user: getUser(session),
    language: getLanguage(request),
    config: {
      keycloakAdminUrl: KEYCLOAK_ADMIN_URL,
      appVersion: APP_VERSION,
      sourceCodeUrl: SOURCE_CODE_URL,
    },
    csrf,
    cspNonce: context?.cspNonce,
  };
  // Commit the session only when a token was just minted, so the id cookie.
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
  return [{ title: "Jaddar" }];
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
  const { user, language, config } = useLoaderData();
  return (
    <ConfigProvider config={config}>
      <I18nProvider initialLanguage={language}>
        <AuthProvider initialUser={user}>
          <AlertProvider>
            <div className="App">
              <Toaster
                position="top-right"
                toastOptions={{
                  duration: 3000,
                  style: { borderRadius: "8px", background: "#333", color: "#fff" },
                  success: { iconTheme: { primary: "#10b981", secondary: "#fff" } },
                  error: { duration: 5000, iconTheme: { primary: "#ef4444", secondary: "#fff" } },
                }}
              />
              <AlertContainer />
              <Outlet />
              <Footer />
            </div>
          </AlertProvider>
        </AuthProvider>
      </I18nProvider>
    </ConfigProvider>
  );
}
