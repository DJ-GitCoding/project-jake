/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import axios from 'axios';

/*
 * Same-origin BFF: all calls hit the SSR proxy (/api/*, plus root-level RDAP endpoints
 * /domain, /ip, /autnum), which attaches the Bearer token from the httpOnly session and
 * forwards to the dataholder backend. No auth interceptors here — proxy handles auth +
 * refresh server-side. `adminApi` is an alias of `api`.
 */
const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json',
  },
});

const adminApi = api;

/*
 * No-op stubs kept so importers that call these don't break. The session lives in
 * an httpOnly cookie the browser can't read; there is no client-side token storage.
 */
export const storeTokens = () => {};
export const clearTokens = () => {};

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

// Health & Info (public)
export const getHealth = () => api.get('/health');
export const getInfo = () => api.get('/');

// ==================== Internal Auth APIs ====================

// Login with username/password
export const login = (username, password) =>
  api.post('/api/auth/login', { username, password });

// Refresh token
export const refreshToken = (refreshToken) =>
  api.post('/api/auth/refresh', { refreshToken });

// Validate token
export const validateToken = (token) =>
  api.post('/api/auth/validate', { token });

// Request a password reset link (public). Always succeeds.
export const forgotPassword = (email) =>
  api.post('/api/auth/forgot-password', { email });

// Reset password using a token from the reset link (public).
export const resetPassword = (token, newPassword) =>
  api.post('/api/auth/reset-password', { token, newPassword });

// Get current user info
export const getCurrentUser = () => adminApi.get('/api/auth/me');

// Change password
export const changePassword = (currentPassword, newPassword) =>
  adminApi.post('/api/auth/change-password', { currentPassword, newPassword });

// Logout
export const logout = () => adminApi.post('/api/auth/logout');

// ==================== Group Admin Settings (Multi-Connection) ====================
// Backend endpoints: /api/admin/group-admin-settings/*

/**
 * Get all Group Admin connections
 * Returns: { configured: bool, connections: [...], settings: {...} (first active, for compat) }
 */
export const getGroupAdminSettings = () =>
  adminApi.get('/api/admin/group-admin-settings');

/**
 * Get a specific connection by ID
 */
export const getGroupAdminSettingsById = (id) =>
  adminApi.get(`/api/admin/group-admin-settings/${id}`);

/**
 * Add a new Group Admin connection
 * Does NOT replace existing connections
 */
export const saveGroupAdminSettings = (data) =>
  adminApi.post('/api/admin/group-admin-settings', data);

/**
 * Update a specific connection by ID
 */
export const updateGroupAdminSettingsById = (id, data) =>
  adminApi.put(`/api/admin/group-admin-settings/${id}`, data);

/**
 * Update Group Admin connection settings (legacy — updates first active)
 */
export const updateGroupAdminSettings = (data) =>
  adminApi.put('/api/admin/group-admin-settings', data);

/**
 * Delete a Group Admin connection by ID
 */
export const deleteGroupAdminSettings = (id) =>
  adminApi.delete(`/api/admin/group-admin-settings/${id}`);

/**
 * Toggle a connection's active status
 */
export const toggleGroupAdminConnection = (id) =>
  adminApi.post(`/api/admin/group-admin-settings/${id}/toggle`);

/**
 * Test a specific connection by ID
 */
export const testGroupAdminConnectionById = (id) =>
  adminApi.post(`/api/admin/group-admin-settings/${id}/test`);

/**
 * Test connection to the Group Admin (legacy — tests first active)
 */
export const testGroupAdminConnection = () =>
  adminApi.post('/api/admin/group-admin-settings/test');

/**
 * Fetch templates from Group Admin (proxied through our backend, aggregated)
 */
export const getGroupAdminTemplates = () =>
  adminApi.get('/api/admin/group-admin-settings/templates');

/**
 * Fetch, per data holder group, the group's fellow member data holders
 * (proxied through our backend, aggregated across all Group Admins).
 */
export const getGroupMembers = () =>
  adminApi.get('/api/admin/group-admin-settings/group-members');

/**
 * Fetch requestor-group subscriptions from Group Admin (proxied through our
 * backend, aggregated). Each subscription is tied to a data holder group via
 * its dataHolderGroupId.
 */
export const getGroupAdminSubscriptions = () =>
  adminApi.get('/api/admin/group-admin-settings/subscriptions');


// ==================== RDAP Queries (public) ====================

export const queryDomain = (domain, agreements = [], authHeader = null) => {
  const params = agreements.length > 0 ? { agreements: agreements.join(',') } : {};
  const headers = authHeader ? { Authorization: authHeader } : {};
  return api.get(`/domain/${domain}`, { params, headers });
};

export const queryIp = (ip, agreements = [], authHeader = null) => {
  const params = agreements.length > 0 ? { agreements: agreements.join(',') } : {};
  const headers = authHeader ? { Authorization: authHeader } : {};
  return api.get(`/ip/${ip}`, { params, headers });
};

export const queryAsn = (asn, agreements = [], authHeader = null) => {
  const params = agreements.length > 0 ? { agreements: agreements.join(',') } : {};
  const headers = authHeader ? { Authorization: authHeader } : {};
  return api.get(`/autnum/${asn}`, { params, headers });
};

export const checkRequestStatus = (requestId) => api.get(`/api/rdap/status/${requestId}`);
export const getAvailableDomains = () => api.get('/api/rdap/domains');
export const getAvailableSubscriptions = () => api.get('/api/rdap/subscriptions');
export const getMySubscriptions = () => api.get('/api/rdap/my-subscriptions');

/**
 * Fetch the data holder's JAKE compliance data:
 * group memberships, published templates with request types, active subscriptions.
 */
export const getJakeCompliance = () => api.get('/api/rdap/jake-compliance');

// Legacy - redirect to subscriptions
export const getAvailableAgreements = () => getAvailableSubscriptions();
export const getMyAgreements = () => getMySubscriptions();

// ==================== Admin APIs (requires JWT) ====================

// ==================== SUBSCRIPTION MANAGEMENT (NEW) ====================
// Backend endpoints: /api/admin/agreement-management/subscriptions/*

/**
 * Get all subscriptions with optional filters
 */
export const getSubscriptions = (params = {}) => 
  adminApi.get('/api/admin/agreement-management/subscriptions', { params });

/**
 * Get subscription by ID
 */
export const getSubscriptionById = (id) => 
  adminApi.get(`/api/admin/agreement-management/subscriptions/${id}`);

/**
 * Get pending subscriptions only
 */
export const getPendingSubscriptions = () => 
  adminApi.get('/api/admin/agreement-management/subscriptions/pending');

/**
 * Get active subscriptions only
 */
export const getActiveSubscriptions = () => 
  adminApi.get('/api/admin/agreement-management/subscriptions/active');

/**
 * Get subscription statistics
 */
export const getSubscriptionStats = () => 
  adminApi.get('/api/admin/agreement-management/stats');

/**
 * Approve a pending subscription
 */
export const approveSubscription = (id, reviewedBy, notes = '') => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/approve`, {
    reviewedBy,
    notes,
  });

/**
 * Deny a pending subscription
 */
export const denySubscription = (id, reviewedBy, reason) => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/deny`, {
    reviewedBy,
    notes: reason,
  });

/**
 * Start testing phase for an approved subscription
 */
export const startSubscriptionTest = (id, reviewedBy) => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/start-test`, {
    initiatedBy: reviewedBy,
  });

/**
 * Run test cases for a subscription in testing phase
 */
export const runSubscriptionTest = (id) => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/run-test`);

/**
 * Activate a subscription after successful testing
 */
export const activateSubscription = (id, reviewedBy, grantedAccessLevel, grantedSensitivityLevel) => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/activate`, {
    initiatedBy: reviewedBy,
    grantedAccessLevel,
    grantedSensitivityLevel,
  });

/**
 * Suspend an active subscription
 */
export const suspendSubscription = (id, reviewedBy, reason) => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/suspend`, {
    reviewedBy,
    notes: reason,
  });

/**
 * Reactivate a suspended subscription
 */
export const reactivateSubscription = (id, reviewedBy) => 
  adminApi.post(`/api/admin/agreement-management/subscriptions/${id}/reactivate`, {
    initiatedBy: reviewedBy,
  });

// ==================== RDAP POLICY ASSIGNMENT ====================

// ==================== Policy Expressions ====================

/**
 * Get all policy expressions
 * @param {boolean} activeOnly - Filter to only active policies
 */
export const getPolicyExpressions = (activeOnly = false) =>
  adminApi.get('/api/admin/policy-expressions', { params: { activeOnly } });

/**
 * Server-side paginated policy expressions.
 * @param {Object} params - { page, size, search, active, sortBy, sortDir }
 *   Reads res.data.content / res.data.totalElements.
 */
export const getPolicyExpressionsPaged = (params = {}) =>
  adminApi.get('/api/admin/policy-expressions', { params });

/**
 * Get a specific policy expression by ID
 * @param {number} id - Policy expression ID
 */
export const getPolicyExpression = (id) => 
  adminApi.get(`/api/admin/policy-expressions/${id}`);

/**
 * Get the default active policy expression
 */
export const getDefaultPolicyExpression = () => 
  adminApi.get('/api/admin/policy-expressions/default');

/**
 * Get available enum values for legal and protected status
 */
export const getPolicyEnumValues = () => 
  adminApi.get('/api/admin/policy-expressions/enum-values');

/**
 * Create a new policy expression
 * @param {Object} data - Policy expression data
 */
export const createPolicyExpression = (data) => 
  adminApi.post('/api/admin/policy-expressions', data);

/**
 * Update an existing policy expression
 * @param {number} id - Policy expression ID
 * @param {Object} data - Updated policy data
 */
export const updatePolicyExpression = (id, data) => 
  adminApi.put(`/api/admin/policy-expressions/${id}`, data);

/**
 * Delete a policy expression
 * @param {number} id - Policy expression ID
 */
export const deletePolicyExpression = (id) => 
  adminApi.delete(`/api/admin/policy-expressions/${id}`);

/**
 * Set a policy expression as the default
 * @param {number} id - Policy expression ID
 */
export const setPolicyExpressionDefault = (id) => 
  adminApi.post(`/api/admin/policy-expressions/${id}/set-default`);

/**
 * Toggle active status of a policy expression
 * @param {number} id - Policy expression ID
 */
export const togglePolicyExpressionActive = (id) => 
  adminApi.post(`/api/admin/policy-expressions/${id}/toggle-active`);

/**
 * Preview importing JSON policy files (server-side parsing)
 * @param {File[]} files - JSON files to preview
 */
export const previewPolicyImport = (files) => {
  const formData = new FormData();
  files.forEach(file => formData.append('files', file));
  return adminApi.post('/api/admin/policy-expressions/import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/**
 * Import JSON policy files (server-side parsing + persist)
 * @param {File[]} files - JSON files to import
 */
export const importPolicyExpressions = (files) => {
  const formData = new FormData();
  files.forEach(file => formData.append('files', file));
  return adminApi.post('/api/admin/policy-expressions/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

// ==================== Custom Contact Roles (requires JWT) ====================

/** List custom contact roles. Pass { page, size, search, sortBy, sortDir } for
 *  server-side pagination; call with no args for the full list (backward compatible). */
export const getCustomRoles = (params = {}) => adminApi.get('/api/admin/custom-roles', { params });

/** Create a custom contact role */
export const createCustomRole = (data) => adminApi.post('/api/admin/custom-roles', data);

/** Update a custom contact role */
export const updateCustomRole = (id, data) => adminApi.put(`/api/admin/custom-roles/${id}`, data);

/** Toggle a custom contact role's active status */
export const toggleCustomRole = (id) => adminApi.post(`/api/admin/custom-roles/${id}/toggle-active`);

/** Delete a custom contact role */
export const deleteCustomRole = (id) => adminApi.delete(`/api/admin/custom-roles/${id}`);

// ==================== Request Management (requires JWT) ====================

// Get all requests (not just pending)
export const getPendingRequests = async (statusFilter = null) => {
  const params = new URLSearchParams();
  if (statusFilter && statusFilter !== 'ALL') {
    params.append('status', statusFilter);
  }
  params.append('limit', '200');
  
  const response = await adminApi.get(`/api/admin/requests?${params.toString()}`);
  return { data: response.data.requests || [] };
};

/*
 * Server-side paginated requests. Reads res.data.content / res.data.totalElements.
 * params: { page, size, search, status, sortBy, sortDir }
 */
export const getPendingRequestsPaged = (params = {}) =>
  adminApi.get('/api/admin/requests', { params });

// Get all requests with full response
export const getAllRequests = async (statusFilter = null) => {
  const params = new URLSearchParams();
  if (statusFilter && statusFilter !== 'ALL') {
    params.append('status', statusFilter);
  }
  params.append('limit', '200');
  
  const response = await adminApi.get(`/api/admin/requests?${params.toString()}`);
  return response.data;
};

// Update a request (change status, notes, etc.)
export const updateRequest = async (requestId, data) => {
  const response = await adminApi.put(`/api/admin/requests/${requestId}`, data);
  return response.data;
};

// Delete a request
export const deleteRequest = async (requestId) => {
  const response = await adminApi.delete(`/api/admin/requests/${requestId}`);
  return response.data;
};

// Bulk update requests
export const bulkUpdateRequests = async (requestIds, action, adminNotes = null) => {
  const response = await adminApi.post('/api/admin/requests/bulk-update', {
    requestIds,
    action,
    adminNotes,
    updatedBy: 'admin'
  });
  return response.data;
};

export const getPendingRequest = (requestId) => adminApi.get(`/api/admin/pending-requests/${requestId}`);
export const reviewRequest = (requestId, action, adminNotes, grantedAccessLevel, denialReason) => 
  adminApi.post(`/api/admin/pending-requests/${requestId}/review`, {
    action,
    adminNotes,
    grantedAccessLevel,
    denialReason,
  });
export const getRequestStats = () => adminApi.get('/api/admin/pending-requests/stats');

// ==================== DEPRECATED: Old Agreements API ====================
// These functions are kept for backward compatibility but redirect to subscription APIs

/**
 * @deprecated Use getActiveSubscriptions() instead
 */
export const getAgreements = () => {
  console.warn('getAgreements() is deprecated. Use getActiveSubscriptions() instead.');
  return getActiveSubscriptions();
};

/**
 * @deprecated Use getSubscriptionStats() instead
 */
export const getAgreementStats = () => {
  console.warn('getAgreementStats() is deprecated. Use getSubscriptionStats() instead.');
  return getSubscriptionStats();
};

/**
 * @deprecated Use getSubscriptionById() instead
 */
export const getAgreementById = (id) => {
  console.warn('getAgreementById() is deprecated. Use getSubscriptionById() instead.');
  return getSubscriptionById(id);
};

/**
 * @deprecated Standalone agreement creation no longer supported - use subscription workflow
 */
export const createAgreement = (data) => {
  console.error('createAgreement() is deprecated. Agreements are created through the subscription workflow.');
  return Promise.reject(new Error('Standalone agreement creation is no longer supported.'));
};

/**
 * @deprecated Use subscription-specific APIs instead
 */
export const updateAgreement = (id, data) => {
  console.error('updateAgreement() is deprecated. Use subscription-specific update APIs.');
  return Promise.reject(new Error('Use subscription-specific update APIs instead.'));
};

/**
 * @deprecated Use suspendSubscription() instead
 */
export const deleteAgreement = (id) => {
  console.warn('deleteAgreement() is deprecated. Use suspendSubscription() instead.');
  return suspendSubscription(id, 'admin', 'Deprecated API call');
};

/**
 * @deprecated Use suspendSubscription() or reactivateSubscription() instead
 */
export const toggleAgreementActive = (id) => {
  console.error('toggleAgreementActive() is deprecated.');
  return Promise.reject(new Error('Use suspendSubscription() or reactivateSubscription() instead.'));
};

/**
 * @deprecated Bulk operations not supported in subscription model
 */
export const bulkUpdateAgreementLevels = (ids, accessLevel) => {
  console.error('bulkUpdateAgreementLevels() is deprecated.');
  return Promise.reject(new Error('Bulk operations are not supported in the subscription model.'));
};

// Legacy aliases
export const getActiveAgreements = getActiveSubscriptions;
export const getAgreementsByGroup = (groupId) => 
  adminApi.get('/api/admin/subscriptions', { params: { requestorGroupId: groupId } });
export const getAgreementLevels = getActiveSubscriptions;

// ==================== DEPRECATED: Old Agreement Requests API ====================
// These redirect to subscription APIs

/**
 * @deprecated Use getSubscriptions() instead
 */
export const getAgreementRequests = () => {
  console.warn('getAgreementRequests() is deprecated. Use getSubscriptions() instead.');
  return getSubscriptions();
};

/**
 * @deprecated Use getPendingSubscriptions() instead
 */
export const getPendingAgreementRequests = () => {
  console.warn('getPendingAgreementRequests() is deprecated. Use getPendingSubscriptions() instead.');
  return getPendingSubscriptions();
};

/**
 * @deprecated Use getSubscriptionById() instead
 */
export const getAgreementRequestById = (id) => {
  console.warn('getAgreementRequestById() is deprecated. Use getSubscriptionById() instead.');
  return getSubscriptionById(id);
};

/**
 * @deprecated Use approveSubscription() instead
 */
export const approveAgreementRequest = (id, reviewedBy, notes) => {
  console.warn('approveAgreementRequest() is deprecated. Use approveSubscription() instead.');
  return approveSubscription(id, reviewedBy, notes);
};

/**
 * @deprecated Use denySubscription() instead
 */
export const declineAgreementRequest = (id, reviewedBy, notes) => {
  console.warn('declineAgreementRequest() is deprecated. Use denySubscription() instead.');
  return denySubscription(id, reviewedBy, notes);
};

/**
 * @deprecated Use startSubscriptionTest() instead
 */
export const startAgreementTest = (id, initiatedBy) => {
  console.warn('startAgreementTest() is deprecated. Use startSubscriptionTest() instead.');
  return startSubscriptionTest(id, initiatedBy);
};

/**
 * @deprecated Use runSubscriptionTest() instead
 */
export const runAgreementTest = (id) => {
  console.warn('runAgreementTest() is deprecated. Use runSubscriptionTest() instead.');
  return runSubscriptionTest(id);
};

/**
 * @deprecated Use activateSubscription() instead
 */
export const activateAgreement = (id, initiatedBy) => {
  console.warn('activateAgreement() is deprecated. Use activateSubscription() instead.');
  return activateSubscription(id, initiatedBy);
};

/**
 * @deprecated Use suspendSubscription() instead
 */
export const suspendAgreement = (id, reviewedBy, notes) => {
  console.warn('suspendAgreement() is deprecated. Use suspendSubscription() instead.');
  return suspendSubscription(id, reviewedBy, notes);
};

/**
 * @deprecated Use reactivateSubscription() instead
 */
export const reactivateAgreement = (id, initiatedBy) => {
  console.warn('reactivateAgreement() is deprecated. Use reactivateSubscription() instead.');
  return reactivateSubscription(id, initiatedBy);
};

/**
 * @deprecated Use getSubscriptionStats() instead
 */
export const getAgreementManagementStats = () => {
  console.warn('getAgreementManagementStats() is deprecated. Use getSubscriptionStats() instead.');
  return getSubscriptionStats();
};

// ==================== Access Policy (Legacy - use Policy Expressions instead) ====================

/**
 * Get the active policy (legacy)
 * @deprecated Use getDefaultPolicyExpression() instead
 */
export const getActivePolicy = () => adminApi.get('/api/admin/policy');

/**
 * Update the active policy (legacy)
 * @deprecated Use updatePolicyExpression() instead
 */
export const updatePolicy = (policy) => adminApi.put('/api/admin/policy', policy);

// Audit Logs (unified: RDAP queries, logins, security threats, CRUD, etc.)
export const getAuditLogs = (page = 0, size = 50, eventType = null, severity = null) => 
  adminApi.get('/api/admin/audit-logs', { 
    params: { page, size, ...(eventType && { eventType }), ...(severity && { severity }) } 
  });

// Admin Health
export const getAdminHealth = () => adminApi.get('/api/admin/health');

// ==================== OAuth APIs for Third-Party Testing ====================

export const getOAuthConfig = () => api.get('/api/oauth/config');
export const getKeycloakToken = (username, password) =>
  api.post('/api/oauth/token/password', { username, password });
export const introspectOAuthToken = (token) =>
  api.post('/api/oauth/introspect', { token });

export default api;

// ==================== Agreement Templates ====================
// Backend endpoints: /api/admin/agreement-management/templates/*

export const getAgreementTemplates = () => 
  adminApi.get('/api/admin/agreement-management/templates');
export const getAgreementTemplateById = (id) => 
  adminApi.get(`/api/admin/agreement-management/templates/${id}`);
export const createAgreementTemplate = (data) => 
  adminApi.post('/api/admin/agreement-management/templates', data);
export const updateAgreementTemplate = (id, data) => 
  adminApi.put(`/api/admin/agreement-management/templates/${id}`, data);
export const deleteAgreementTemplate = (id) => 
  adminApi.delete(`/api/admin/agreement-management/templates/${id}`);
export const toggleAgreementTemplatePublish = (id) => 
  adminApi.post(`/api/admin/agreement-management/templates/${id}/publish`);

// Template RDAP Parameters
export const getTemplateWithRdapParams = (id) => 
  adminApi.get(`/api/admin/agreement-management/templates/${id}/with-rdap-params`);
export const updateTemplateRdapParams = (id, rdapParameters) => 
  adminApi.put(`/api/admin/agreement-management/templates/${id}/rdap-parameters`, { rdapParameters });

// ==================== RDAP Parameter Presets ====================
export const getRdapParameterPresets = () => 
  adminApi.get('/api/admin/rdap-parameters/presets');
export const createRdapParameterPreset = (name, description, parameters) => 
  adminApi.post('/api/admin/rdap-parameters/presets', { name, description, parameters });
export const deleteRdapParameterPreset = (id) => 
  adminApi.delete(`/api/admin/rdap-parameters/presets/${id}`);

// ==================== RDAP Data Management ====================

// ==================== Domain API ====================

export const getRdapDomains = (page = 0, size = 20, sortBy = 'ldhName', sortDir = 'asc', search = '') =>
  adminApi.get('/api/admin/rdap/domains', { params: { page, size, sortBy, sortDir, search } });
export const getRdapDomain = (id) => adminApi.get(`/api/admin/rdap/domains/${id}`);
export const createRdapDomain = (data) => adminApi.post('/api/admin/rdap/domains', data);
export const updateRdapDomain = (id, data) => adminApi.put(`/api/admin/rdap/domains/${id}`, data);
export const deleteRdapDomain = (id) => adminApi.delete(`/api/admin/rdap/domains/${id}`);
export const bulkDeleteRdapDomains = (ids) => adminApi.delete('/api/admin/rdap/domains/bulk', { data: ids });

// ==================== IP API ====================

export const getRdapIps = (page = 0, size = 20, sortBy = 'handle', sortDir = 'asc', search = '') =>
  adminApi.get('/api/admin/rdap/ips', { params: { page, size, sortBy, sortDir, search } });
export const getRdapIp = (id) => adminApi.get(`/api/admin/rdap/ips/${id}`);
export const createRdapIp = (data) => adminApi.post('/api/admin/rdap/ips', data);
export const updateRdapIp = (id, data) => adminApi.put(`/api/admin/rdap/ips/${id}`, data);
export const deleteRdapIp = (id) => adminApi.delete(`/api/admin/rdap/ips/${id}`);
export const bulkDeleteRdapIps = (ids) => adminApi.delete('/api/admin/rdap/ips/bulk', { data: ids });

// ==================== ASN API ====================

export const getRdapAsns = (page = 0, size = 20, sortBy = 'handle', sortDir = 'asc', search = '') =>
  adminApi.get('/api/admin/rdap/asns', { params: { page, size, sortBy, sortDir, search } });
export const getRdapAsn = (id) => adminApi.get(`/api/admin/rdap/asns/${id}`);
export const createRdapAsn = (data) => adminApi.post('/api/admin/rdap/asns', data);
export const updateRdapAsn = (id, data) => adminApi.put(`/api/admin/rdap/asns/${id}`, data);
export const deleteRdapAsn = (id) => adminApi.delete(`/api/admin/rdap/asns/${id}`);
export const bulkDeleteRdapAsns = (ids) => adminApi.delete('/api/admin/rdap/asns/bulk', { data: ids });

// ==================== Entity API ====================

export const getRdapEntityById = (id) => adminApi.get(`/api/admin/rdap/entities/${id}`);
export const getEntityEvents = (id) => adminApi.get(`/api/admin/rdap/entities/${id}/events`);
export const getEntityLinks = (id) => adminApi.get(`/api/admin/rdap/entities/${id}/links`);
export const getEntityNameservers = (id) => adminApi.get(`/api/admin/rdap/entities/${id}/nameservers`);
export const getEntityRemarks = (id) => adminApi.get(`/api/admin/rdap/entities/${id}/remarks`);
export const getEntitySecureDns = (id) => adminApi.get(`/api/admin/rdap/entities/${id}/secure-dns`);

// ==================== RDAP Import API Functions ====================

export const previewCsvImport = (file, type, delimiter = ',', hasHeader = true) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('delimiter', delimiter);
  formData.append('hasHeader', hasHeader);
  return adminApi.post(`/api/admin/rdap/${type}/import/csv/preview`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const importCsv = (file, type, delimiter = ',', hasHeader = true, columnMappings = {}) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('delimiter', delimiter);
  formData.append('hasHeader', hasHeader);
  Object.entries(columnMappings).forEach(([fieldName, columnIndex]) => {
    if (columnIndex !== undefined && columnIndex !== null) {
      formData.append(`col_${fieldName}`, columnIndex.toString());
    }
  });
  return adminApi.post(`/api/admin/rdap/${type}/import/csv`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const importJson = (type, data) => {
  return adminApi.post(`/api/admin/rdap/${type}/import/json`, { data });
};

export const importJsonFile = (file, type) => {
  const formData = new FormData();
  formData.append('file', file);
  return adminApi.post(`/api/admin/rdap/${type}/import/json/file`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

export const exportRdapData = (type) => {
  return adminApi.get(`/api/admin/rdap/export/${type}`);
};

// ==================== Stats ====================
export const getRdapStats = () => adminApi.get('/api/admin/rdap/stats');

// ==================== Mapped Data Viewing ====================
export const getMappedDataStatus = () => adminApi.get('/api/admin/rdap/mapped/status');
export const getMappedDomains = (page = 0, size = 20, search = '', mappingId = null) =>
  adminApi.get(`/api/admin/rdap/mapped/domains?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ''}${mappingId ? `&mappingId=${mappingId}` : ''}`);

// ==================== Template Test Data Management ====================
// Backend endpoints: /api/admin/agreement-management/templates/{templateId}/test-data/*

/**
 * Get all test data entries for a template
 */
export const getTemplateTestData = (templateId) =>
  adminApi.get(`/api/admin/agreement-management/templates/${templateId}/test-data`);

/**
 * Add a test data entry to a template
 * @param {number} templateId
 * @param {Object} data - { rdapEntityId, queryType?, queryValue?, requestTypeName?, label?, description?, verifyContactAccess?, verifyRedaction?, sortOrder?, isActive? }
 */
export const addTemplateTestData = (templateId, data) =>
  adminApi.post(`/api/admin/agreement-management/templates/${templateId}/test-data`, data);

/**
 * Update a test data entry
 */
export const updateTemplateTestData = (templateId, entryId, data) =>
  adminApi.put(`/api/admin/agreement-management/templates/${templateId}/test-data/${entryId}`, data);

/**
 * Delete a test data entry
 */
export const deleteTemplateTestData = (templateId, entryId) =>
  adminApi.delete(`/api/admin/agreement-management/templates/${templateId}/test-data/${entryId}`);

/**
 * Bulk add test data entries by RDAP entity IDs
 * @param {number} templateId
 * @param {Object} data - { rdapEntityIds: number[], requestTypeName?: string }
 */
export const bulkAddTemplateTestData = (templateId, data) =>
  adminApi.post(`/api/admin/agreement-management/templates/${templateId}/test-data/bulk`, data);

// ==================== User Management (MASTER only) ====================

/*
 * Pass a params object ({ page, size, search, sortBy, sortDir }) for server-side
 * pagination; call with no args for the full list (backward compatible).
 */
export const getUsers = (params = {}) => adminApi.get('/api/auth/users', { params });
export const getUserById = (id) => adminApi.get(`/api/auth/users/${id}`);
export const createUser = (data) => adminApi.post('/api/auth/users', data);
export const updateUser = (id, data) => adminApi.put(`/api/auth/users/${id}`, data);
export const deleteUser = (id) => adminApi.delete(`/api/auth/users/${id}`);

// ==================== RDAP Data Mapping ====================

export const getRdapDataMappings = () => adminApi.get('/api/admin/rdap-data-mappings');
export const getActiveRdapDataMappings = () => adminApi.get('/api/admin/rdap-data-mappings/active');
export const getActiveRdapDataMapping = getActiveRdapDataMappings; // backward compat alias
export const getRdapDataMapping = (id) => adminApi.get(`/api/admin/rdap-data-mappings/${id}`);
export const createRdapDataMapping = (data) => adminApi.post('/api/admin/rdap-data-mappings', data);
export const updateRdapDataMapping = (id, data) => adminApi.put(`/api/admin/rdap-data-mappings/${id}`, data);
export const toggleRdapDataMappingActive = (id) => adminApi.post(`/api/admin/rdap-data-mappings/${id}/toggle-active`);
export const deleteRdapDataMapping = (id) => adminApi.delete(`/api/admin/rdap-data-mappings/${id}`);
export const getRdapDataMappingDefaults = () => adminApi.get('/api/admin/rdap-data-mappings/defaults');
export const testMappingConnection = (data) => adminApi.post('/api/admin/rdap-data-mappings/test-connection', data);
export const introspectMappingSchema = (data) => adminApi.post('/api/admin/rdap-data-mappings/introspect', data);
export const introspectAndSaveMapping = (id) => adminApi.post(`/api/admin/rdap-data-mappings/${id}/introspect`);
export const suggestMappingColumns = (data) => adminApi.post('/api/admin/rdap-data-mappings/suggest-mappings', data);
export const previewMappingData = (id, objectType, limit) => adminApi.post(`/api/admin/rdap-data-mappings/${id}/preview?objectType=${objectType}&limit=${limit || 5}`);
export const livePreviewMappingData = (id, objectType, limit, formState) => adminApi.post(`/api/admin/rdap-data-mappings/${id}/live-preview?objectType=${objectType}&limit=${limit || 5}`, formState);
export const listMappingTables = (id) => adminApi.get(`/api/admin/rdap-data-mappings/${id}/tables`);
export const browseMappingTable = (id, tableName, params) => adminApi.get(`/api/admin/rdap-data-mappings/${id}/browse/${tableName}`, { params });

// ==================== Global Configuration ====================

export const getDataHolderConfig = () => adminApi.get('/api/admin/config');
export const updateDataHolderConfig = (data) => adminApi.put('/api/admin/config', data);

// ==================== File Management ====================

// File attachments for requests
export const getFilesForRequest = (requestId) => 
  adminApi.get(`/api/admin/files/request/${requestId}`);
export const getFileMetadata = (fileId) =>
  adminApi.get(`/api/admin/files/metadata/${fileId}`);
/*
 * Raw URL returns
 */
export const getFileViewUrl = (fileId) =>
  `/api/admin/files/view/${fileId}`;

export const getFileDownloadUrl = (fileId) =>
  `/api/admin/files/view/${fileId}`;
// NOTE: no backend endpoint implements bulk download yet — this link will 404 until one exists.
export const downloadAllFiles = (requestId) =>
  `/api/admin/files/request/${requestId}/download-all`;

/*
 * File automation rules. Pass { page, size, search, sortBy, sortDir } for server-side
 * pagination; call with no args for the full list (backward compatible).
 */
export const getFileAutomationRules = (params = {}) =>
  adminApi.get('/api/admin/files/automation-rules', { params });
export const getFileAutomationRule = (id) =>
  adminApi.get(`/api/admin/files/automation-rules/${id}`);
export const createFileAutomationRule = (data) =>
  adminApi.post('/api/admin/files/automation-rules', data);
export const updateFileAutomationRule = (id, data) =>
  adminApi.put(`/api/admin/files/automation-rules/${id}`, data);
export const deleteFileAutomationRule = (id) =>
  adminApi.delete(`/api/admin/files/automation-rules/${id}`);
export const toggleFileAutomationRule = (id) =>
  adminApi.post(`/api/admin/files/automation-rules/${id}/toggle`);