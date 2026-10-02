/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';
import Modal from './Modal';
import { useT } from '../i18n';

/**
 * Reusable delete confirmation modal
 */
const DeleteConfirmModal = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  itemType,
  itemName,
  itemDetails = [],
  warning,
  submitting = false
}) => {
  const { t } = useT();
  const resolvedTitle = title !== undefined ? title : t('deleteConfirm.title');
  const resolvedItemType = itemType !== undefined ? itemType : t('deleteConfirm.defaultItemType');
  const resolvedWarning = warning !== undefined ? warning : t('deleteConfirm.defaultWarning');

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={resolvedTitle}
      footer={
        <>
          <button
            className="btn btn-secondary"
            onClick={onClose}
            disabled={submitting}
          >
            {t('common.cancel')}
          </button>
          <button
            className="btn btn-danger"
            onClick={onConfirm}
            disabled={submitting}
          >
            {submitting ? t('common.deleting') : t('common.delete')}
          </button>
        </>
      }
    >
      <div>
        <div style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: '16px',
          marginBottom: '16px'
        }}>
          <div style={{
            width: '48px',
            height: '48px',
            borderRadius: '50%',
            backgroundColor: 'var(--accent-error)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <i className="fa-solid fa-triangle-exclamation" style={{ color: 'white', fontSize: '24px' }}></i>
          </div>
          <div>
            <p style={{ margin: '0 0 8px 0' }}>
              {t('deleteConfirm.confirmMessage', { itemType: resolvedItemType })}
            </p>
            {itemName && (
              <div
                style={{
                  padding: '12px 16px',
                  backgroundColor: 'var(--bg-secondary)',
                  borderRadius: '6px',
                  marginTop: '12px'
                }}
              >
                <div className="text-muted small">{t('common.name')}</div>
                <strong>{itemName}</strong>

                {itemDetails.map((detail, idx) => (
                  <div key={idx} style={{ marginTop: '8px' }}>
                    <div className="text-muted small">{detail.label}</div>
                    <div>{detail.value}</div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {resolvedWarning && (
          <p className="text-danger small" style={{
            margin: 0,
            padding: '12px',
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            borderRadius: '6px'
          }}>
            ⚠️ {resolvedWarning}
          </p>
        )}
      </div>
    </Modal>
  );
};

export default DeleteConfirmModal;
