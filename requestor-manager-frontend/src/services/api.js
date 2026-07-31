/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import axios from 'axios';

/*
 * Same-origin axios: all calls hit the SSR /api/* BFF proxy, which attaches the Bearer
 * token from the httpOnly session and forwards to the backend. No auth interceptors
 * needed here — the proxy handles auth + refresh server-side.
 */
const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

// On 401 (session expired / not authenticated) send the user to the login page.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ==================== Auth API ====================
export const authApi = {
  login: (email, password) =>
    api.post('/api/v1/auth/login', { email, password }),

  refresh: (refreshToken) =>
    api.post('/api/v1/auth/refresh', { refreshToken }),

  getCurrentUser: () =>
    api.get('/api/v1/auth/me'),

  validateToken: (token) =>
    api.post('/api/v1/auth/validate', { token }),

  forgotPassword: (email) =>
    api.post('/api/v1/auth/forgot-password', { email }),

  resetPassword: (token, newPassword) =>
    api.post('/api/v1/auth/reset-password', { token, newPassword }),
};

// ==================== Users API ====================
export const usersApi = {
  /*
   * Optional params are passed through for forward-compatibility. The users
   * endpoint is Keycloak-backed and not server-side paginated, so the Users
   * page still paginates client-side; calling with no args returns the full list.
   */
  getAll: (params) =>
    api.get('/api/v1/users', { params }),

  getById: (id) =>
    api.get(`/api/v1/users/${id}`),

  getByGroup: (groupName) =>
    api.get(`/api/v1/users/group/${groupName}`),

  getCurrentUser: () =>
    api.get('/api/v1/users/me'),

  create: (data) =>
    api.post('/api/v1/users', data),

  checkExists: (email, targetGroupName) =>
    api.post('/api/v1/users/check', { email, targetGroupName }),

  addToGroup: (userId, groupName) =>
    api.post('/api/v1/users/add-to-group', { userId, groupName }),

  update: (id, data) =>
    api.put(`/api/v1/users/${id}`, data),

  delete: (id) =>
    api.delete(`/api/v1/users/${id}`),
};

// ==================== Requestor Groups API ====================
export const requestorGroupsApi = {
  /*
   * Pass { page, size, search, sortBy, sortDir } for a server-side paginated
   * PagedResponse; call with no args for the full list (backward-compatible).
   */
  getAll: (params) =>
    api.get('/api/v1/requestor-groups', { params }),

  getById: (id) =>
    api.get(`/api/v1/requestor-groups/${id}`),

  getIntrospectionDefault: () =>
    api.get('/api/v1/requestor-groups/introspection-default'),

  create: (data) =>
    api.post('/api/v1/requestor-groups', data),

  update: (id, data) =>
    api.put(`/api/v1/requestor-groups/${id}`, data),

  delete: (id) =>
    api.delete(`/api/v1/requestor-groups/${id}`),
};

// ==================== Agreements API ====================
export const agreementsApi = {
  getAll: () =>
    api.get('/api/v1/agreements'),

  getById: (id) =>
    api.get(`/api/v1/agreements/${id}`),

  getByRequestorGroup: (requestorGroupId) =>
    api.get(`/api/v1/agreements/requestor-group/${requestorGroupId}`),

  search: (name) =>
    api.get('/api/v1/agreements/search', { params: { name } }),

  create: (data) =>
    api.post('/api/v1/agreements', data),

  update: (id, data) =>
    api.put(`/api/v1/agreements/${id}`, data),

  delete: (id) =>
    api.delete(`/api/v1/agreements/${id}`),
};

// ==================== Data Holder Groups API ====================
export const dataHolderGroupsApi = {
  /*
   * Pass { page, size, search, activeOnly, sortBy, sortDir } for a server-side
   * paginated PagedResponse; call with no args for the full list (backward-compatible).
   */
  getAll: (params) =>
    api.get('/api/data-holder-groups', { params }),

  getActive: () =>
    api.get('/api/data-holder-groups/active'),

  getHealthy: () =>
    api.get('/api/data-holder-groups/healthy'),

  getById: (id) =>
    api.get(`/api/data-holder-groups/${id}`),

  getByCode: (code) =>
    api.get(`/api/data-holder-groups/code/${code}`),

  create: (data) =>
    api.post('/api/data-holder-groups', data),

  update: (id, data) =>
    api.put(`/api/data-holder-groups/${id}`, data),

  delete: (id) =>
    api.delete(`/api/data-holder-groups/${id}`),

  toggleActive: (id) =>
    api.post(`/api/data-holder-groups/${id}/toggle-active`),

  // Health checks
  checkHealth: (id) =>
    api.get(`/api/data-holder-groups/${id}/health`),

  checkAllHealth: () =>
    api.post('/api/data-holder-groups/health-check-all'),

  // Templates
  getTemplates: (id) =>
    api.get(`/api/data-holder-groups/${id}/templates`),

  getAllTemplates: () =>
    api.get('/api/data-holder-groups/templates'),

  // Agreement initiation (legacy - use subscriptionsApi instead)
  initiateAgreement: (id, data) =>
    api.post(`/api/data-holder-groups/${id}/initiate`, data),

  getAgreementStatus: (id, requestId) =>
    api.get(`/api/data-holder-groups/${id}/status/${requestId}`),
};

// ==================== Subscriptions API ====================
export const subscriptionsApi = {
  /*
   * Subscription Requests
   * Pass { page, size, search, status, requestorGroupId, sortBy, sortDir } for a
   * server-side paginated PagedResponse; call with no args for the full list.
   */
  getAll: (params) =>
    api.get('/api/v1/subscriptions', { params }),

  // Aggregate status counts (total + byStatus map) respecting role scoping.
  getStats: () =>
    api.get('/api/v1/subscriptions/stats'),

  getById: (id) =>
    api.get(`/api/v1/subscriptions/${id}`),

  getByInternalId: (internalRequestId) =>
    api.get(`/api/v1/subscriptions/by-internal-id/${internalRequestId}`),

  getByRequestorGroup: (requestorGroupId) =>
    api.get(`/api/v1/subscriptions/requestor-group/${requestorGroupId}`),

  getPending: () =>
    api.get('/api/v1/subscriptions/pending'),

  getActionable: () =>
    api.get('/api/v1/subscriptions/actionable'),

  create: (data) =>
    api.post('/api/v1/subscriptions', data),

  update: (id, data) =>
    api.put(`/api/v1/subscriptions/${id}`, data),

  submit: (id) =>
    api.post(`/api/v1/subscriptions/${id}/submit`),

  cancel: (id) =>
    api.post(`/api/v1/subscriptions/${id}/cancel`),

  refreshStatus: (id) =>
    api.post(`/api/v1/subscriptions/${id}/refresh`),

  // Testing Workflow
  startTesting: (id) =>
    api.post(`/api/v1/subscriptions/${id}/start-testing`),

  runTest: (id) =>
    api.post(`/api/v1/subscriptions/${id}/run-test`),

  activate: (id) =>
    api.post(`/api/v1/subscriptions/${id}/activate`),

  // Data Holder Agreements (approved subscriptions)
  getAgreements: () =>
    api.get('/api/v1/subscriptions/agreements'),

  getAgreementById: (id) =>
    api.get(`/api/v1/subscriptions/agreements/${id}`),

  getAgreementsByRequestorGroup: (requestorGroupId) =>
    api.get(`/api/v1/subscriptions/agreements/requestor-group/${requestorGroupId}`),

  getActiveAgreementsByRequestorGroup: (requestorGroupId) =>
    api.get(`/api/v1/subscriptions/agreements/requestor-group/${requestorGroupId}/active`),
};

export default api;