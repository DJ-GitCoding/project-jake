/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect } from 'react';
import { useAuth, RoleBadgeClasses } from '../context/AuthContext';
import { useAlert } from '../context/AlertContext';
import { usersApi, requestorGroupsApi } from '../services/api';
import Loading from '../components/Loading';
import Modal from '../components/Modal';
import MultiSelectDropdown from '../components/MultiSelectDropdown';
import Pagination from '../components/Pagination';
import usePagination from '../hooks/usePagination';
import { useT } from '../i18n';

const Users = () => {
  const { isGroupAdmin, isRequestorGroupAdmin, canManageGroup, user: currentUser } = useAuth();
  const { success, error: showError, confirm } = useAlert();
  const { t } = useT();

  const [users, setUsers] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterGroup, setFilterGroup] = useState('');
  const [filterRole, setFilterRole] = useState('');

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [showAddToGroupModal, setShowAddToGroupModal] = useState(false);
  const [editingUser, setEditingUser] = useState(null);
  const [existingUserData, setExistingUserData] = useState(null);
  const [formData, setFormData] = useState({
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    requestorGroupNames: [],
    type: 'REQUESTOR_GROUP_USER',
  });
  const [submitting, setSubmitting] = useState(false);

  // Map backend UserType enum values to display names with hierarchy level
  const allUserTypeOptions = [
    { value: 'JADDAR_MASTER_ADMIN', altValue: 'jaddar_master_admin', label: t('users.roles.masterAdmin'), level: 1 },
    { value: 'GROUP_ADMIN', altValue: 'group_admin', label: t('users.roles.groupAdmin'), level: 2 },
    { value: 'REQUESTOR_GROUP_ADMIN', altValue: 'requestor_group_admin', label: t('users.roles.requestorAdmin'), level: 3 },
    { value: 'REQUESTOR_GROUP_USER', altValue: 'requestor_group_user', label: t('users.roles.requestorUser'), level: 4 },
  ];

  // Get current user's level (lower number = higher privilege)
  const getCurrentUserLevel = () => {
    if (!currentUser) return 99;
    
    let userType = null;
    
    if (typeof currentUser.type === 'string' && currentUser.type) {
      userType = currentUser.type;
    } else if (currentUser.type && typeof currentUser.type === 'object' && currentUser.type.name) {
      userType = currentUser.type.name;
    }
    
    if (!userType && currentUser.roles && Array.isArray(currentUser.roles)) {
      for (const opt of allUserTypeOptions) {
        if (currentUser.roles.includes(opt.value) || currentUser.roles.includes(opt.altValue)) {
          userType = opt.value;
          break;
        }
      }
    }
    
    if (!userType) return 99;
    
    const upperType = userType.toUpperCase();
    const lowerType = userType.toLowerCase();
    const option = allUserTypeOptions.find(opt => 
      opt.value === userType || 
      opt.altValue === userType ||
      opt.value === upperType ||
      opt.altValue === lowerType
    );
    
    return option?.level ?? 99;
  };

  const currentUserLevel = getCurrentUserLevel();

  // Get a target user's hierarchy level
  const getUserLevel = (user) => {
    if (!user) return 99;
    let userType = typeof user.type === 'string' ? user.type : (user.type?.name || user.type);
    if (!userType) return 99;
    
    const upperType = userType.toUpperCase();
    const lowerType = userType.toLowerCase();
    const option = allUserTypeOptions.find(opt => 
      opt.value === userType || 
      opt.altValue === userType ||
      opt.value === upperType ||
      opt.altValue === lowerType
    );
    return option?.level ?? 99;
  };

  // Filter role options: can only assign roles strictly below current user's level
  const userTypeOptions = allUserTypeOptions.filter(opt => {
    // Never allow creating master admins
    if (opt.value === 'JADDAR_MASTER_ADMIN') return false;
    // Only show roles strictly below current user's level
    return opt.level > currentUserLevel;
  });

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const [usersRes, groupsRes] = await Promise.all([
        usersApi.getAll(),
        requestorGroupsApi.getAll(),
      ]);
      setUsers(usersRes.data.data || []);
      setGroups(groupsRes.data.data || []);
    } catch (err) {
      console.error('Failed to load data:', err);
      showError(t('users.errors.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  const handleOpenModal = (user = null) => {
    if (user) {
      setEditingUser(user);
      const userType = typeof user.type === 'string' ? user.type : (user.type?.name || 'REQUESTOR_GROUP_USER');
      setFormData({
        email: user.email || '',
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        password: '',
        requestorGroupNames: user.groups || [],
        type: userType,
      });
    } else {
      setEditingUser(null);
      setFormData({
        email: '',
        firstName: '',
        lastName: '',
        password: '',
        requestorGroupNames: [],
        type: userTypeOptions.length > 0 ? userTypeOptions[userTypeOptions.length - 1].value : 'REQUESTOR_GROUP_USER',
      });
    }
    setShowModal(true);
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingUser(null);
    setExistingUserData(null);
    setFormData({
      email: '',
      firstName: '',
      lastName: '',
      password: '',
      requestorGroupNames: [],
      type: 'REQUESTOR_GROUP_USER',
    });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!formData.email.trim()) {
      showError(t('users.validation.emailRequired'));
      return;
    }

    if (!editingUser && !formData.password) {
      showError(t('users.validation.passwordRequired'));
      return;
    }

    setSubmitting(true);

    try {
      if (editingUser) {
        await usersApi.update(editingUser.id, {
          firstName: formData.firstName,
          lastName: formData.lastName,
          groupNames: formData.requestorGroupNames,
          type: formData.type,
        });
        success(t('users.success.updated'));
        handleCloseModal();
        loadData();
      } else {
        const response = await usersApi.create({
          email: formData.email,
          firstName: formData.firstName,
          lastName: formData.lastName,
          password: formData.password,
          requestorGroupNames: formData.requestorGroupNames,
          type: formData.type,
        });

        if (response.data.success) {
          success(t('users.success.created'));
          handleCloseModal();
          loadData();
        }
      }
    } catch (err) {
      console.error('Failed to save user:', err);
      
      if (err.response?.status === 409 && err.response?.data?.data) {
        const existsData = err.response.data.data;
        setExistingUserData(existsData);
        
        if (existsData.alreadyInTargetGroup) {
          showError(existsData.message);
        } else {
          setShowAddToGroupModal(true);
          setShowModal(false);
        }
      } else {
        const responseData = err.response?.data;
        
        if (responseData?.data && typeof responseData.data === 'object' && !(responseData.data instanceof Array)) {
          const messages = Object.entries(responseData.data)
            .map(([field, msg]) => `${msg}`)
            .join(', ');
          showError(messages || responseData.message || t('users.errors.saveFailed'));
        } else {
          showError(responseData?.message || t('users.errors.saveFailed'));
        }
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleAddToGroup = async () => {
    if (!existingUserData?.existingUser?.id) return;

    setSubmitting(true);
    try {
      await usersApi.addToGroup(existingUserData.existingUser.id, existingUserData.targetGroup);
      success(t('users.success.addedToGroup', { group: existingUserData.targetGroup }));
      setShowAddToGroupModal(false);
      setExistingUserData(null);
      loadData();
    } catch (err) {
      console.error('Failed to add user to group:', err);
      showError(err.response?.data?.message || t('users.errors.addToGroupFailed'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (user) => {
    if (user.id === currentUser?.id) {
      showError(t('users.errors.cannotDeleteSelf'));
      return;
    }

    const confirmed = await confirm(
      t('users.deleteConfirm.title'),
      t('users.deleteConfirm.message', {
        name: `${user.firstName} ${user.lastName}`,
        email: user.email,
      }),
      null,
      null,
      {
        confirmText: t('common.delete'),
        confirmVariant: 'danger',
        icon: 'fa-user-times',
        iconColor: 'danger',
      }
    );

    if (confirmed) {
      try {
        await usersApi.delete(user.id);
        success(t('users.success.deleted'));
        loadData();
      } catch (err) {
        console.error('Failed to delete user:', err);
        showError(err.response?.data?.message || t('users.errors.deleteFailed'));
      }
    }
  };

  // Filter users
  const filteredUsers = users.filter((user) => {
    const matchesSearch =
      user.email?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      user.firstName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      user.lastName?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesGroup = !filterGroup || user.groups?.includes(filterGroup);
    const userType = typeof user.type === 'string' ? user.type : user.type?.name;
    const matchesRole = !filterRole || userType === filterRole;
    return matchesSearch && matchesGroup && matchesRole;
  });

  const usersPagination = usePagination(filteredUsers);
  useEffect(() => {
    usersPagination.resetPage();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchTerm, filterGroup, filterRole]);

  // Permission checks - enforce "cannot manage same level or above"
  const canCreate = isRequestorGroupAdmin();
  
  const canEdit = (user) => {
    const targetLevel = getUserLevel(user);
    const isSelf = user.id === currentUser?.id;

    // Master Admin: can edit themselves and all users
    if (currentUserLevel === 1) return true;

    // Group Admin: can edit all users below their level, but not self or Master Admin
    if (currentUserLevel === 2) {
      if (isSelf) return false;
      return targetLevel > currentUserLevel;
    }

    // Requestor Group Admin: can edit users below their level in own groups, not self
    if (currentUserLevel === 3) {
      if (isSelf) return false;
      if (targetLevel <= currentUserLevel) return false;
      return user.groups?.some((g) => canManageGroup(g));
    }

    // Requestor User: cannot edit anyone
    return false;
  };

  const canDelete = (user) => {
    // Cannot delete yourself
    if (user.id === currentUser?.id) return false;
    // Must be at least group admin
    if (!isGroupAdmin()) return false;
    // Must be strictly higher privilege than target user
    const targetLevel = getUserLevel(user);
    return currentUserLevel < targetLevel;
  };

  const getRoleBadge = (user) => {
    let userType = typeof user.type === 'string' ? user.type : user.type?.name || user.type;
    if (!userType) return <span className="badge bg-secondary">{t('users.roles.defaultUser')}</span>;
    
    const upperType = userType.toUpperCase();
    const lowerType = userType.toLowerCase();
    const option = allUserTypeOptions.find(opt => 
      opt.value === userType || 
      opt.altValue === userType ||
      opt.value === upperType ||
      opt.altValue === lowerType
    );
    
    const displayName = option?.label || userType;
    const badgeClass = RoleBadgeClasses[lowerType] || 'bg-secondary';
    return <span className={`badge ${badgeClass}`}>{displayName}</span>;
  };

  if (loading) {
    return <Loading message={t('users.loadingUsers')} />;
  }

  return (
    <div>
      {/* Page Header */}
      <div className="page-header d-flex justify-content-between align-items-start">
        <div>
          <h1>{t('users.title')}</h1>
          <p>{t('users.subtitle')}</p>
        </div>
        {canCreate && (
          <button className="btn btn-primary" onClick={() => handleOpenModal()}>
            <i className="fas fa-user-plus me-2"></i>
            {t('users.newUser')}
          </button>
        )}
      </div>

      {/* Filters */}
      <div className="card mb-4">
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-4">
              <div className="input-group">
                <span className="input-group-text">
                  <i className="fas fa-search"></i>
                </span>
                <input
                  type="text"
                  className="form-control"
                  placeholder={t('users.searchPlaceholder')}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                />
              </div>
            </div>
            <div className="col-md-3">
              <select
                className="form-select"
                value={filterGroup}
                onChange={(e) => setFilterGroup(e.target.value)}
              >
                <option value="">{t('users.filters.allGroups')}</option>
                {groups.map((group) => (
                  <option key={group.id} value={group.name}>
                    {group.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-md-3">
              <select
                className="form-select"
                value={filterRole}
                onChange={(e) => setFilterRole(e.target.value)}
              >
                <option value="">{t('users.filters.allRoles')}</option>
                {allUserTypeOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-md-2">
              <button
                className="btn btn-outline-secondary w-100"
                onClick={() => {
                  setSearchTerm('');
                  setFilterGroup('');
                  setFilterRole('');
                }}
              >
                <i className="fas fa-times me-2"></i>
                {t('common.clear')}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Users Table */}
      <div className="data-table">
        <div className="table-header">
          <h5>
            <i className="fas fa-users me-2"></i>
            {t('users.tableTitle', { count: filteredUsers.length })}
          </h5>
          <button className="btn btn-sm btn-outline-secondary" onClick={loadData}>
            <i className="fas fa-sync-alt"></i>
          </button>
        </div>

        {filteredUsers.length > 0 ? (
          <div className="table-responsive">
            <table className="table">
              <thead>
                <tr>
                  <th>{t('users.table.user')}</th>
                  <th>{t('users.table.email')}</th>
                  <th>{t('users.table.groups')}</th>
                  <th>{t('users.table.role')}</th>
                  <th>{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {usersPagination.pageItems.map((user) => (
                  <tr key={user.id}>
                    <td>
                      <div className="d-flex align-items-center">
                        <div className="user-avatar me-2">
                          {(user.firstName?.[0] || '') + (user.lastName?.[0] || '') || '?'}
                        </div>
                        <div>
                          <div className="fw-semibold">
                            {user.firstName} {user.lastName}
                          </div>
                          <small className="text-muted">{user.username}</small>
                        </div>
                      </div>
                    </td>
                    <td>{user.email}</td>
                    <td>
                      {user.groups?.length > 0 ? (
                        user.groups.map((group, idx) => (
                          <span key={idx} className="badge bg-secondary me-1">
                            {group}
                          </span>
                        ))
                      ) : (
                        <span className="text-muted">-</span>
                      )}
                    </td>
                    <td>{getRoleBadge(user)}</td>
                    <td>
                      {(canEdit(user) || canDelete(user)) && (
                        <div className="btn-group">
                          {canEdit(user) && (
                            <button
                              className="btn btn-sm btn-outline-primary btn-action"
                              onClick={() => handleOpenModal(user)}
                              title={t('common.edit')}
                            >
                              <i className="fas fa-edit"></i>
                            </button>
                          )}
                          {canDelete(user) && (
                            <button
                              className="btn btn-sm btn-outline-danger btn-action"
                              onClick={() => handleDelete(user)}
                              title={t('common.delete')}
                            >
                              <i className="fas fa-trash-alt"></i>
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={usersPagination.page}
              pageSize={usersPagination.pageSize}
              totalItems={usersPagination.totalItems}
              onPageChange={usersPagination.setPage}
              onPageSizeChange={usersPagination.setPageSize}
              itemLabel={t('users.itemLabel')}
            />
          </div>
        ) : (
          <div className="empty-state">
            <div className="empty-icon">
              <i className="fas fa-users"></i>
            </div>
            <h5>{t('users.empty.title')}</h5>
            <p>
              {searchTerm || filterGroup || filterRole
                ? t('users.empty.filtered')
                : t('users.empty.default')}
            </p>
            {canCreate && !searchTerm && !filterGroup && !filterRole && (
              <button className="btn btn-primary" onClick={() => handleOpenModal()}>
                <i className="fas fa-user-plus me-2"></i>
                {t('users.empty.createButton')}
              </button>
            )}
          </div>
        )}
      </div>

      {/* Create/Edit Modal */}
      <Modal
        show={showModal}
        onHide={handleCloseModal}
        title={editingUser ? t('users.editUser') : t('users.newUser')}
        footer={
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleCloseModal}
              disabled={submitting}
            >
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleSubmit}
              disabled={submitting}
            >
              {submitting ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2"></span>
                  {t('common.saving')}
                </>
              ) : (
                <>
                  <i className="fas fa-save me-2"></i>
                  {editingUser ? t('common.update') : t('common.create')}
                </>
              )}
            </button>
          </>
        }
      >
        <form onSubmit={handleSubmit}>
          <div className="mb-3">
            <label htmlFor="email" className="form-label">
              {t('users.form.email')} <span className="text-danger">*</span>
            </label>
            <input
              type="email"
              className="form-control"
              id="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              placeholder={t('users.form.emailPlaceholder')}
              required
              disabled={!!editingUser}
            />
          </div>

          <div className="row">
            <div className="col-md-6 mb-3">
              <label htmlFor="firstName" className="form-label">
                {t('users.form.firstName')}
              </label>
              <input
                type="text"
                className="form-control"
                id="firstName"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                placeholder={t('users.form.firstNamePlaceholder')}
              />
            </div>
            <div className="col-md-6 mb-3">
              <label htmlFor="lastName" className="form-label">
                {t('users.form.lastName')}
              </label>
              <input
                type="text"
                className="form-control"
                id="lastName"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                placeholder={t('users.form.lastNamePlaceholder')}
              />
            </div>
          </div>

          {!editingUser && (
            <div className="mb-3">
              <label htmlFor="password" className="form-label">
                {t('users.form.password')} <span className="text-danger">*</span>
              </label>
              <input
                type="password"
                className="form-control"
                id="password"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                placeholder={t('users.form.passwordPlaceholder')}
                required={!editingUser}
              />
            </div>
          )}

          <div className="mb-3">
            <label className="form-label">{t('users.form.groups')}</label>
            <MultiSelectDropdown
              options={groups}
              selectedValues={formData.requestorGroupNames}
              onChange={(values) => setFormData({ ...formData, requestorGroupNames: values })}
              placeholder={t('users.form.groupsPlaceholder')}
              emptyMessage={t('users.form.groupsEmpty')}
            />
          </div>

          <div className="mb-3">
            <label htmlFor="type" className="form-label">
              {t('users.form.role')}
            </label>
            {userTypeOptions.length > 0 ? (
              <select
                className="form-select"
                id="type"
                value={formData.type}
                onChange={(e) => setFormData({ ...formData, type: e.target.value })}
              >
                {userTypeOptions.map((opt) => (
                  <option key={opt.value} value={opt.value}>
                    {opt.label}
                  </option>
                ))}
              </select>
            ) : (
              <div className="alert alert-warning mb-0">
                <small>{t('users.form.noRoles')}</small>
              </div>
            )}
          </div>
        </form>
      </Modal>

      {/* Add to Group Modal */}
      <Modal
        show={showAddToGroupModal}
        onHide={() => {
          setShowAddToGroupModal(false);
          setExistingUserData(null);
        }}
        title={t('users.addToGroup.title')}
        footer={
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => {
                setShowAddToGroupModal(false);
                setExistingUserData(null);
              }}
              disabled={submitting}
            >
              {t('common.cancel')}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              onClick={handleAddToGroup}
              disabled={submitting}
            >
              {submitting ? (
                <>
                  <span className="spinner-border spinner-border-sm me-2"></span>
                  {t('users.addToGroup.adding')}
                </>
              ) : (
                <>
                  <i className="fas fa-user-plus me-2"></i>
                  {t('users.addToGroup.button')}
                </>
              )}
            </button>
          </>
        }
      >
        {existingUserData && (
          <div>
            <div className="alert alert-info">
              <i className="fas fa-info-circle me-2"></i>
              {existingUserData.message}
            </div>

            <div className="card bg-light">
              <div className="card-body">
                <h6 className="card-subtitle mb-2 text-muted">{t('users.addToGroup.existingUser')}</h6>
                <p className="card-text">
                  <strong>{t('users.addToGroup.nameLabel')}</strong> {existingUserData.existingUser?.displayName || t('common.na')}
                  <br />
                  <strong>{t('users.addToGroup.emailLabel')}</strong> {existingUserData.existingUser?.email}
                  <br />
                  <strong>{t('users.addToGroup.currentGroupsLabel')}</strong>{' '}
                  {existingUserData.currentGroups?.join(', ') || t('users.addToGroup.none')}
                </p>
              </div>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default Users;