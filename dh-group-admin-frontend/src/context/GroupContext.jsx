/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { getUserGroups } from '../services/api';

const SELECTED_GROUP_KEY = 'dh_admin_selected_group';

/*
 * Group context for all protected pages. Exposes selectedGroupId, selectedGroup,
 * groups, isMaster, isAllMode plus selectGroup (Header's selector) and
 * refreshGroups (Data Holder Groups page).
 */
export const GroupContext = createContext({
  selectedGroupId: null,
  selectedGroup: null,
  groups: [],
  isMaster: false,
  isAllMode: false,
  selectGroup: () => {},
  refreshGroups: () => {},
});

export const GroupProvider = ({ user, children }) => {
  const isMaster = user?.type === 1;

  const [groups, setGroups] = useState(() =>
    user?.groups && user.groups.length > 0 ? user.groups : []
  );
  /*
   * Start null on both server and first client render to avoid hydration
   * mismatches; the persisted selection is applied client-side after mount.
   */
  const [selectedGroupId, setSelectedGroupId] = useState(null);
  const [hydrated, setHydrated] = useState(false);

  // Load groups (fallback to the API when the user payload didn't carry them).
  const loadGroups = useCallback(async () => {
    if (!user) return;
    try {
      if (user.groups && user.groups.length > 0) {
        setGroups(user.groups);
      } else {
        const res = await getUserGroups(user.id);
        setGroups(res || []);
      }
    } catch {
      setGroups([]);
    }
  }, [user]);

  useEffect(() => { loadGroups(); }, [loadGroups]);

  // Apply the persisted selection once, after mount (client-only).
  useEffect(() => {
    try {
      const stored = localStorage.getItem(SELECTED_GROUP_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        if (parsed !== null) setSelectedGroupId(parsed);
      }
    } catch { /* ignore */ }
    setHydrated(true);
  }, []);

  // Auto-select the first group for non-master users, and keep the selection valid.
  useEffect(() => {
    if (!user || isMaster) return;
    if (selectedGroupId === null && groups.length > 0) {
      setSelectedGroupId(groups[0].id);
    } else if (selectedGroupId !== null && groups.length > 0) {
      const stillValid = groups.some(g => g.id === selectedGroupId);
      if (!stillValid) setSelectedGroupId(groups[0].id);
    }
  }, [user, isMaster, groups, selectedGroupId]);

  // Persist the selection (only after the initial hydration read).
  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(SELECTED_GROUP_KEY, JSON.stringify(selectedGroupId));
    } catch { /* ignore */ }
  }, [selectedGroupId, hydrated]);

  const selectGroup = useCallback((groupId) => setSelectedGroupId(groupId), []);

  const refreshGroups = useCallback(async () => {
    if (!user) return;
    try {
      const res = await getUserGroups(user.id);
      setGroups(res || []);
    } catch { /* ignore */ }
  }, [user]);

  const selectedGroup = selectedGroupId
    ? groups.find(g => g.id === selectedGroupId) || null
    : null;
  const isAllMode = isMaster && selectedGroupId === null;

  const value = {
    selectedGroupId,
    selectedGroup,
    groups,
    isMaster,
    isAllMode,
    selectGroup,
    refreshGroups,
  };

  return <GroupContext.Provider value={value}>{children}</GroupContext.Provider>;
};

export const useGroup = () => useContext(GroupContext);

export default GroupContext;
