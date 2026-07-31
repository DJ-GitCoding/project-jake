/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

/*
 * Same-origin axios: all calls hit the SSR /api/* BFF proxy, which attaches the Bearer
 * token from the httpOnly session and forwards to the backend. No token management or
 * auth interceptors here — the proxy handles auth + refresh server-side.
 */
const API_URL = '';

async function request(path, options = {}) {
  const url = `${API_URL}${path}`;
  const config = {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  };
  if (config.body && typeof config.body === 'object') {
    config.body = JSON.stringify(config.body);
  }
  const res = await fetch(url, config);

  // On a final 401 (session expired / not authenticated) send the user to login.
  if (res.status === 401 && !path.startsWith('/api/auth/')) {
    if (typeof window !== 'undefined') {
      window.location.href = '/login';
    }
    throw new Error('Session expired');
  }

  const data = res.headers.get('content-type')?.includes('application/json')
    ? await res.json()
    : await res.text();
  if (!res.ok) {
    const err = new Error(data?.error || data?.message || `Request failed: ${res.status}`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

const get = (path) => request(path);
const post = (path, body) => request(path, { method: 'POST', body });

/*
 * Build a query string from a params object, omitting undefined/null/empty values.
 * Returns '' when there are no usable params (so callers stay backward-compatible and
 * the backend returns the full unpaginated list).
 */
const buildQuery = (params) => {
  if (!params || typeof params !== 'object') return '';
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') usp.append(k, v);
  });
  const s = usp.toString();
  return s ? `?${s}` : '';
};
const put = (path, body) => request(path, { method: 'PUT', body });
const patch = (path, body) => request(path, { method: 'PATCH', body });
const del = (path) => request(path, { method: 'DELETE' });

// ==================== Templates ====================
export const getTemplates = (params) => get(`/api/admin/templates${buildQuery(params)}`);
export const getTemplate = (id) => get(`/api/admin/templates/${id}`);
export const createTemplate = (data) => post('/api/admin/templates', data);
export const updateTemplate = (id, data) => put(`/api/admin/templates/${id}`, data);
export const deleteTemplate = (id) => del(`/api/admin/templates/${id}`);
export const toggleTemplatePublish = (id) => post(`/api/admin/templates/${id}/publish`);

// ==================== Request Types ====================
export const getRequestTypes = (tid) => get(`/api/admin/templates/${tid}/request-types`);
export const addRequestType = (tid, data) => post(`/api/admin/templates/${tid}/request-types`, data);
export const updateRequestType = (tid, rtId, data) => put(`/api/admin/templates/${tid}/request-types/${rtId}`, data);
export const deleteRequestType = (tid, rtId) => del(`/api/admin/templates/${tid}/request-types/${rtId}`);

// ==================== Subscriptions ====================
export const getSubscriptions = (params) => get(`/api/admin/subscriptions${buildQuery(params)}`);
export const getPendingSubscriptions = () => get('/api/admin/subscriptions/pending');
export const getActiveSubscriptions = () => get('/api/admin/subscriptions/active');
export const getSubscription = (id) => get(`/api/admin/subscriptions/${id}`);
export const approveSubscription = (id, data) => post(`/api/admin/subscriptions/${id}/approve`, data);
export const denySubscription = (id, data) => post(`/api/admin/subscriptions/${id}/deny`, data);
export const startTest = (id, data) => post(`/api/admin/subscriptions/${id}/start-test`, data);
export const runSubscriptionTest = (id) => post(`/api/admin/subscriptions/${id}/run-test`, {});
export const recordTestResult = (id, data) => post(`/api/admin/subscriptions/${id}/record-test-result`, data);
export const activateSubscription = (id, data) => post(`/api/admin/subscriptions/${id}/activate`, data);
export const suspendSubscription = (id, data) => post(`/api/admin/subscriptions/${id}/suspend`, data);
export const reactivateSubscription = (id, data) => post(`/api/admin/subscriptions/${id}/reactivate`, data);
export const updateSubscriptionIntrospectionUrl = (id, introspectionUrl) =>
  patch(`/api/admin/subscriptions/${id}/introspection-url`, { introspectionUrl });

// ==================== Data Holders ====================
export const getDataHolders = (params) => get(`/api/admin/data-holders${buildQuery(params)}`);
export const getDataHolder = (id) => get(`/api/admin/data-holders/${id}`);
export const createDataHolder = (data) => post('/api/admin/data-holders', data);
export const updateDataHolder = (id, data) => put(`/api/admin/data-holders/${id}`, data);
export const deleteDataHolder = (id) => del(`/api/admin/data-holders/${id}`);
export const revealCredentials = (id) => post(`/api/admin/data-holders/${id}/reveal-credentials`);
export const regenerateCredentials = (id) => post(`/api/admin/data-holders/${id}/regenerate-credentials`);

// ==================== Data Holder Groups ====================
export const getDataHolderGroups = (params) => get(`/api/admin/data-holder-groups${buildQuery(params)}`);
export const getDataHolderGroup = (id) => get(`/api/admin/data-holder-groups/${id}`);
export const createDataHolderGroup = (data) => post('/api/admin/data-holder-groups', data);
export const updateDataHolderGroup = (id, data) => put(`/api/admin/data-holder-groups/${id}`, data);
export const deleteDataHolderGroup = (id) => del(`/api/admin/data-holder-groups/${id}`);
export const getGroupMembers = (id) => get(`/api/admin/data-holder-groups/${id}/members`);
export const addGroupMember = (id, data) => post(`/api/admin/data-holder-groups/${id}/members`, data);
export const removeGroupMember = (groupId, userId, callerUserId) => {
  const qs = callerUserId ? `?callerUserId=${callerUserId}` : '';
  return del(`/api/admin/data-holder-groups/${groupId}/members/${userId}${qs}`);
};
export const getUserGroups = (userId) => get(`/api/admin/users/${userId}/groups`);

// ==================== Users ====================
export const getUsers = (params) => get(`/api/admin/users${buildQuery(params)}`);
export const getUser = (id) => get(`/api/admin/users/${id}`);
export const createUser = (data) => post('/api/admin/users', data);
export const updateUser = (id, data) => put(`/api/admin/users/${id}`, data);
export const deleteUser = (id) => del(`/api/admin/users/${id}`);

// ==================== Auth ====================
export const login = (data) => post('/api/auth/login', data);
export const changePassword = (data) => post('/api/auth/change-password', data);
// Public (no auth token required)
export const forgotPassword = (email) => post('/api/auth/forgot-password', { email });
export const resetPassword = (token, newPassword) => post('/api/auth/reset-password', { token, newPassword });

// ==================== Requestor Groups ====================
export const getRequestorGroups = (params) => get(`/api/admin/requestor-groups${buildQuery(params)}`);
export const getPendingRequestorGroups = () => get('/api/admin/requestor-groups/pending');
export const getRequestorGroup = (id) => get(`/api/admin/requestor-groups/${id}`);
export const acceptRequestorGroup = (id, data) => post(`/api/admin/requestor-groups/${id}/accept`, data);
export const denyRequestorGroup = (id, data) => post(`/api/admin/requestor-groups/${id}/deny`, data);
export const updateRequestorGroup = (id, data) => put(`/api/admin/requestor-groups/${id}`, data);
export const deleteRequestorGroup = (id) => del(`/api/admin/requestor-groups/${id}`);
export const revealRgCredentials = (id) => post(`/api/admin/requestor-groups/${id}/reveal-credentials`);
export const regenerateRgCredentials = (id) => post(`/api/admin/requestor-groups/${id}/regenerate-credentials`);

// ==================== Stats ====================
export const getStats = () => get('/api/admin/stats');

// ==================== Audit Logs ====================
export const getAuditLogs = (params = {}) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v != null && v !== '') query.append(k, v); });
  const qs = query.toString();
  return get(`/api/admin/audit-logs${qs ? '?' + qs : ''}`);
};
export const getAuditLogStats = () => get('/api/admin/audit-logs/stats');

// ==================== Public (no auth) ====================
export const getPublicDataHolderGroups = () => get('/api/external/data-holder-groups');
export const submitDataHolderApplication = (data) => post('/api/external/data-holders/apply', data);
export const checkApplicationStatus = (email, groupId) =>
  get(`/api/external/data-holders/apply/status?email=${encodeURIComponent(email)}&groupId=${groupId}`);

// ==================== Data Holder Applications (admin) ====================
export const getDataHolderApplications = (params) => get(`/api/admin/data-holder-applications${buildQuery(params)}`);
export const getPendingApplications = () => get('/api/admin/data-holder-applications/pending');
export const getDataHolderApplication = (id) => get(`/api/admin/data-holder-applications/${id}`);
export const approveApplication = (id, data) => post(`/api/admin/data-holder-applications/${id}/approve`, data);
export const denyApplication = (id, data) => post(`/api/admin/data-holder-applications/${id}/deny`, data);
export const deleteApplication = (id) => del(`/api/admin/data-holder-applications/${id}`);

// ==================== Data Holder Instances (Provisioning) ====================
export const getInstances = (params) => get(`/api/admin/instances${buildQuery(params)}`);
export const getInstance = (subdomain) => get(`/api/admin/instances/${subdomain}`);
export const getInstanceLiveStatus = (subdomain) => get(`/api/admin/instances/${subdomain}/live-status`);
export const provisionInstance = (data) => post('/api/admin/instances', data);
export const stopInstance = (subdomain) => post(`/api/admin/instances/${subdomain}/stop`);
export const startInstance = (subdomain) => post(`/api/admin/instances/${subdomain}/start`);
export const teardownInstance = (subdomain) => del(`/api/admin/instances/${subdomain}`);
export const updateInstance = (subdomain, data) => post(`/api/admin/instances/${subdomain}/update`, data);
export const updateAllInstances = (data) => post('/api/admin/instances/update-all', data);
