/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useRef, useEffect } from 'react';
import { useT } from '../i18n';

const MultiSelectDropdown = ({
  options,
  selectedValues,
  onChange,
  placeholder,
  emptyMessage
}) => {
  const { t } = useT();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);
  const placeholderText = placeholder || t('multiSelect.placeholder');
  const emptyText = emptyMessage || t('multiSelect.emptyMessage');

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };

    const handleEscapeKey = (event) => {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    };

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      document.addEventListener('keydown', handleEscapeKey);
    }

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscapeKey);
    };
  }, [isOpen]);

  const handleCheckboxChange = (value, isChecked) => {
    if (isChecked) {
      onChange([...selectedValues, value]);
    } else {
      onChange(selectedValues.filter((v) => v !== value));
    }
  };

  return (
    <div className="dropdown" ref={dropdownRef}>
      <button
        className="btn btn-outline-secondary dropdown-toggle w-100 text-start"
        type="button"
        aria-expanded={isOpen}
        onClick={(e) => {
          e.preventDefault();
          setIsOpen(!isOpen);
        }}
      >
        {selectedValues.length > 0 ? selectedValues.join(', ') : placeholderText}
      </button>
      <div 
        className={`dropdown-menu w-100 p-2 ${isOpen ? 'show' : ''}`} 
        style={{ maxHeight: '200px', overflowY: 'auto' }}
      >
        {options.length > 0 ? (
          options.map((option) => (
            <div key={option.id} className="form-check">
              <input
                className="form-check-input"
                type="checkbox"
                id={`group-${option.id}`}
                checked={selectedValues.includes(option.name)}
                onChange={(e) => handleCheckboxChange(option.name, e.target.checked)}
              />
              <label
                className="form-check-label w-100"
                htmlFor={`group-${option.id}`}
                style={{ cursor: 'pointer' }}
              >
                {option.name}
              </label>
            </div>
          ))
        ) : (
          <span className="text-muted">{emptyText}</span>
        )}
      </div>
    </div>
  );
};

export default MultiSelectDropdown;