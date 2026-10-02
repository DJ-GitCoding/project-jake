/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useEffect, useRef, useState } from 'react';

/*
 * Renders the deployment's uploaded logo, falling back to the built-in mark when no
 * logo has been uploaded. The image endpoint is public because the login screen has to
 * render before anyone has authenticated, and a 404 from it is the "no custom logo"
 * signal — that keeps the component to a single request with no extra probe.
 *
 * The result is cached at module scope so moving between pages doesn't re-flash the
 * default mark, and `notifyLogoChanged` lets the settings screen push the new state to
 * every mounted instance the moment an admin uploads or removes one.
 */

export const LOGO_URL = '/api/public/branding/logo';

const subscribers = new Set();
const isBrowser = typeof window !== 'undefined';

// 'unknown' until the first load settles; 'present' or 'absent' after.
let status = 'unknown';
// Bumped on every admin change so the <img> URL is unique and can't serve stale bytes.
let version = 0;

const publish = () => subscribers.forEach((fn) => fn());

/** Called by the branding settings screen after a successful upload or removal. */
export const notifyLogoChanged = (present) => {
  status = present ? 'present' : 'absent';
  version += 1;
  publish();
};

const settle = (next) => {
  if (status === next) return;
  status = next;
  publish();
};

/**
 * Subscribe to the logo's state: 'unknown' | 'present' | 'absent'.
 *
 * Call sites use this when the surrounding chrome has to change too — a container
 * styled for a lettermark usually wants different treatment behind a real logo.
 */
export const useBrandingLogoStatus = () => {
  const [, forceRender] = useState(0);

  useEffect(() => {
    const rerender = () => forceRender((n) => n + 1);
    subscribers.add(rerender);
    return () => {
      subscribers.delete(rerender);
    };
  }, []);

  /*
   * SSR always reports 'unknown': module state is shared across requests on the server,
   * so trusting it there would emit markup the client can't reproduce on hydration.
   * The browser settles it during the first paint.
   */
  return isBrowser ? status : 'unknown';
};

const BrandingLogo = ({ fallback = null, alt = 'Logo', className, style }) => {
  const current = useBrandingLogoStatus();

  const imgRef = useRef(null);

  /*
   * A cached image can finish loading before React attaches onLoad during hydration,
   * which would leave the logo hidden for good. Re-check the element on mount:
   * `complete` with a zero natural width is the browser's way of saying it failed.
   */
  useEffect(() => {
    const img = imgRef.current;
    if (img?.complete) settle(img.naturalWidth > 0 ? 'present' : 'absent');
  }, []);

  // Nothing uploaded — render the built-in mark and skip the request that would 404.
  if (current === 'absent') return fallback;

  const src = version > 0 ? `${LOGO_URL}?v=${version}` : LOGO_URL;

  return (
    <>
      {current !== 'present' && fallback}
      <img
        ref={imgRef}
        key={version}
        src={src}
        alt={alt}
        className={className}
        onLoad={() => settle('present')}
        onError={() => settle('absent')}
        style={current === 'present' ? style : { ...style, display: 'none' }}
      />
    </>
  );
};

export default BrandingLogo;
