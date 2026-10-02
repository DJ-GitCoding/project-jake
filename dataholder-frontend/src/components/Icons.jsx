/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React from 'react';

// Helper: renders a Font Awesome <i> tag inheriting the parent's size via "1em"
const FA = ({ className, style, ...props }) => (
  <i className={className} style={{ fontSize: '1em', ...style }} {...props} />
);

// ==================== NAVIGATION / GENERAL ====================

export const IconDashboard = (props) => <FA className="fa-solid fa-grip" {...props} />;
export const IconPending = (props) => <FA className="fa-regular fa-clock" {...props} />;
export const IconPolicy = (props) => <FA className="fa-solid fa-shield-halved" {...props} />;
export const IconLogs = (props) => <FA className="fa-solid fa-file-lines" {...props} />;
export const IconQuery = (props) => <FA className="fa-solid fa-magnifying-glass" {...props} />;
export const IconAgreement = (props) => <FA className="fa-solid fa-circle-check" {...props} />;
export const IconCheck = (props) => <FA className="fa-solid fa-check" {...props} />;
export const IconX = (props) => <FA className="fa-solid fa-xmark" {...props} />;
export const IconRefresh = (props) => <FA className="fa-solid fa-arrows-rotate" {...props} />;
export const IconEye = (props) => <FA className="fa-solid fa-eye" {...props} />;
export const IconUser = (props) => <FA className="fa-solid fa-user" {...props} />;
export const IconGlobe = (props) => <FA className="fa-solid fa-globe" {...props} />;
export const IconServer = (props) => <FA className="fa-solid fa-server" {...props} />;
export const IconNetwork = (props) => <FA className="fa-solid fa-network-wired" {...props} />;
export const IconInbox = (props) => <FA className="fa-solid fa-inbox" {...props} />;

// ==================== CHEVRONS / ARROWS ====================

export const IconChevronDown = (props) => <FA className="fa-solid fa-chevron-down" {...props} />;
export const IconChevronRight = (props) => <FA className="fa-solid fa-chevron-right" {...props} />;
export const IconChevronLeft = (props) => <FA className="fa-solid fa-chevron-left" {...props} />;
export const IconChevronUp = (props) => <FA className="fa-solid fa-chevron-up" {...props} />;
export const IconArrowRight = (props) => <FA className="fa-solid fa-arrow-right" {...props} />;
export const IconArrowLeft = (props) => <FA className="fa-solid fa-arrow-left" {...props} />;

// ==================== ACTIONS ====================

export const IconSettings = (props) => <FA className="fa-solid fa-gear" {...props} />;
export const IconLogOut = (props) => <FA className="fa-solid fa-right-from-bracket" {...props} />;
export const IconPlus = (props) => <FA className="fa-solid fa-plus" {...props} />;
export const IconTrash = (props) => <FA className="fa-solid fa-trash" {...props} />;
export const IconEdit = (props) => <FA className="fa-solid fa-pen-to-square" {...props} />;
export const IconUpload = (props) => <FA className="fa-solid fa-upload" {...props} />;
export const IconDownload = (props) => <FA className="fa-solid fa-download" {...props} />;
export const IconCopy = (props) => <FA className="fa-regular fa-copy" {...props} />;
export const IconSave = (props) => <FA className="fa-solid fa-floppy-disk" {...props} />;
export const IconExternalLink = (props) => <FA className="fa-solid fa-arrow-up-right-from-square" {...props} />;
export const IconMenu = (props) => <FA className="fa-solid fa-bars" {...props} />;
export const IconMoreVertical = (props) => <FA className="fa-solid fa-ellipsis-vertical" {...props} />;
export const IconMoreHorizontal = (props) => <FA className="fa-solid fa-ellipsis" {...props} />;
export const IconMaximize = (props) => <FA className="fa-solid fa-expand" {...props} />;
export const IconMinimize = (props) => <FA className="fa-solid fa-compress" {...props} />;
export const IconFilter = (props) => <FA className="fa-solid fa-filter" {...props} />;

// ==================== STATUS / ALERTS ====================

export const IconAlert = (props) => <FA className="fa-solid fa-circle-exclamation" {...props} />;
export const IconAlertTriangle = (props) => <FA className="fa-solid fa-triangle-exclamation" {...props} />;
export const IconInfo = (props) => <FA className="fa-solid fa-circle-info" {...props} />;
export const IconWarning = (props) => <FA className="fa-solid fa-circle-exclamation" {...props} />;

// ==================== DATA / FILES ====================

export const IconDatabase = (props) => <FA className="fa-solid fa-database" {...props} />;
export const IconFile = (props) => <FA className="fa-regular fa-file" {...props} />;
export const IconFileText = (props) => <FA className="fa-solid fa-file-lines" {...props} />;
export const IconCode = (props) => <FA className="fa-solid fa-code" {...props} />;
export const IconTable = (props) => <FA className="fa-solid fa-table" {...props} />;
export const IconMap = (props) => <FA className="fa-solid fa-map" {...props} />;
export const IconTemplate = (props) => <FA className="fa-solid fa-table-columns" {...props} />;

// ==================== SECURITY / ACCESS ====================

export const IconShield = (props) => <FA className="fa-solid fa-shield-halved" {...props} />;
export const IconEyeOff = (props) => <FA className="fa-solid fa-eye-slash" {...props} />;
export const IconLock = (props) => <FA className="fa-solid fa-lock" {...props} />;
export const IconUnlock = (props) => <FA className="fa-solid fa-lock-open" {...props} />;
export const IconKey = (props) => <FA className="fa-solid fa-key" {...props} />;
export const IconLayers = (props) => <FA className="fa-solid fa-layer-group" {...props} />;

// ==================== MISC ====================

export const IconCalendar = (props) => <FA className="fa-regular fa-calendar" {...props} />;
export const IconClock = (props) => <FA className="fa-regular fa-clock" {...props} />;
export const IconActivity = (props) => <FA className="fa-solid fa-chart-line" {...props} />;
export const IconZap = (props) => <FA className="fa-solid fa-bolt" {...props} />;
export const IconSliders = (props) => <FA className="fa-solid fa-sliders" {...props} />;
export const IconToggleLeft = (props) => <FA className="fa-solid fa-toggle-off" {...props} />;
export const IconToggleRight = (props) => <FA className="fa-solid fa-toggle-on" {...props} />;
export const IconLink = (props) => <FA className="fa-solid fa-link" {...props} />;
export const IconSubscription = (props) => <FA className="fa-solid fa-rss" {...props} />;
export const IconUsers = (props) => <FA className="fa-solid fa-users" {...props} />;
export const IconMapping = (props) => <FA className="fa-solid fa-right-left" {...props} />;

// ==================== FILE MANAGEMENT ====================

export const IconFilePdf = (props) => <FA className="fa-solid fa-file-pdf" {...props} />;
export const IconFileWord = (props) => <FA className="fa-solid fa-file-word" {...props} />;
export const IconFileExcel = (props) => <FA className="fa-solid fa-file-excel" {...props} />;
export const IconFileImage = (props) => <FA className="fa-solid fa-file-image" {...props} />;
export const IconFileAlt = (props) => <FA className="fa-solid fa-file-alt" {...props} />;
export const IconPaperclip = (props) => <FA className="fa-solid fa-paperclip" {...props} />;
export const IconRobot = (props) => <FA className="fa-solid fa-robot" {...props} />;
export const IconSkullCrossbones = (props) => <FA className="fa-solid fa-skull-crossbones" {...props} />;
export const IconBug = (props) => <FA className="fa-solid fa-bug" {...props} />;
export const IconBan = (props) => <FA className="fa-solid fa-ban" {...props} />;
export const IconGavel = (props) => <FA className="fa-solid fa-gavel" {...props} />;
export const IconPlay = (props) => <FA className="fa-solid fa-play" {...props} />;
export const IconPause = (props) => <FA className="fa-solid fa-pause" {...props} />;
