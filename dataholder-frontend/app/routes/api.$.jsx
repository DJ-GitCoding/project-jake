/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import { proxy } from "../lib/proxy.server.js";

// Same-origin BFF proxy for all /api/* backend calls.
export const loader = ({ request }) => proxy(request);
export const action = ({ request }) => proxy(request);
