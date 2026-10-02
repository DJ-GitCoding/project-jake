/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import PolicyExpressionForm from './PolicyExpressionForm';
import { useT } from '../i18n';

const PolicyExpressionModal = ({
  isOpen,
  onClose,
  policy,
  formData,
  enumValues,
  onChange,
  onSubmit,
  saving,
  existingPolicies = [],
}) => {
  const { t } = useT();

  if (!isOpen) return null;

  const isNew = !policy?.id;

  const handleBackdropClick = (e) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  return (
    <>
      <div className="modal d-block" tabIndex="-1" onClick={handleBackdropClick}>
        <div className="modal-dialog modal-dialog-centered modal-lg modal-dialog-scrollable">
          <div className="modal-content">
            <div className="modal-header">
              <h3 className="modal-title h5 d-flex align-items-center gap-2 mb-0">
                <i className={`fa-solid ${isNew ? 'fa-plus-circle' : 'fa-pen-to-square'}`} />
                {isNew ? t('policy.modal.createTitle') : t('policy.modal.editTitle', { name: policy?.name })}
              </h3>
              <button type="button" className="btn-close" onClick={onClose}></button>
            </div>

            <div className="modal-body">
              <PolicyExpressionForm
                formData={formData}
                enumValues={enumValues}
                onChange={onChange}
                onSubmit={onSubmit}
                saving={saving}
                isNew={isNew}
                existingPolicies={existingPolicies}
                editingPolicyId={policy?.id}
              />
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
};

export default PolicyExpressionModal;