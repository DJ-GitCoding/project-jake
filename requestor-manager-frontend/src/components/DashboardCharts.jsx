/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useMemo } from 'react';
import {
  Chart as ChartJS,
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from 'chart.js';
import { Doughnut, Bar } from 'react-chartjs-2';
import { useT } from '../i18n';

ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend);

/* Bootstrap's palette, matching the status badges on the Subscriptions page. */
const COLORS = {
  DRAFT: '#adb5bd',
  SUBMITTED: '#0dcaf0',
  PENDING_REVIEW: '#ffc107',
  APPROVED: '#0d6efd',
  TESTING: '#6f42c1',
  ACTIVE: '#198754',
  DECLINED: '#dc3545',
  SUSPENDED: '#fd7e14',
  CANCELLED: '#6c757d',
  EXPIRED: '#343a40',
};

const STATUS_LABEL_KEYS = {
  DRAFT: 'subscriptions.status.draft',
  SUBMITTED: 'subscriptions.status.submitted',
  PENDING_REVIEW: 'subscriptions.status.pendingReview',
  APPROVED: 'subscriptions.status.approved',
  TESTING: 'subscriptions.status.testing',
  ACTIVE: 'subscriptions.status.active',
  DECLINED: 'subscriptions.status.declined',
  SUSPENDED: 'subscriptions.status.suspended',
  CANCELLED: 'subscriptions.status.cancelled',
  EXPIRED: 'subscriptions.status.expired',
};

const ChartCard = ({ title, icon, empty, emptyLabel, height = 280, children }) => (
  <div className="card h-100">
    <div className="card-header">
      <h5 className="mb-0">
        <i className={`${icon} me-2 text-muted`}></i>
        {title}
      </h5>
    </div>
    <div className="card-body">
      {empty ? (
        <div className="d-flex flex-column align-items-center justify-content-center text-muted"
             style={{ minHeight: height }}>
          <i className="fas fa-chart-pie fa-2x mb-2 opacity-25"></i>
          <span className="small">{emptyLabel}</span>
        </div>
      ) : (
        <div style={{ height }}>{children}</div>
      )}
    </div>
  </div>
);

export const SubscriptionStatusChart = ({ byStatus }) => {
  const { t } = useT();

  /* Ten statuses is too many for a readable legend, so only the ones in play are drawn. */
  const slices = useMemo(() => Object.keys(COLORS)
    .map(status => ({
      status,
      label: t(STATUS_LABEL_KEYS[status]),
      value: Number(byStatus?.[status] ?? 0),
      color: COLORS[status],
    }))
    .filter(s => s.value > 0), [byStatus, t]);

  const total = slices.reduce((sum, s) => sum + s.value, 0);

  const data = {
    labels: slices.map(s => s.label),
    datasets: [{
      data: slices.map(s => s.value),
      backgroundColor: slices.map(s => s.color),
      borderColor: '#fff',
      borderWidth: 2,
      hoverOffset: 6,
    }],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: {
        position: 'right',
        labels: { boxWidth: 12, boxHeight: 12, usePointStyle: true, pointStyle: 'circle', padding: 14 },
      },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const share = total > 0 ? Math.round((ctx.parsed / total) * 100) : 0;
            return ` ${ctx.label}: ${ctx.parsed} (${share}%)`;
          },
        },
      },
    },
  };

  return (
    <ChartCard
      title={t('dashboard.charts.byStatus')}
      icon="fas fa-chart-pie"
      empty={total === 0}
      emptyLabel={t('dashboard.charts.noSubscriptions')}
    >
      <Doughnut data={data} options={options} />
    </ChartCard>
  );
};

/*
 * A proposed change is not a subscription status -- it is a separate flag the data holder
 * group sets -- so it is reported in the tooltip rather than drawn as a slice of the stack.
 */
export const isAwaitingResponse = (summary) => summary?.pendingChangeStatus === 'PROPOSED';

export const isActiveSubscription = (summary) => summary?.status === 'ACTIVE';

export const GroupActivityChart = ({ groups }) => {
  const { t } = useT();

  const rows = useMemo(() => (groups || [])
    .map(g => {
      const byStatus = {};
      let awaiting = 0;
      (g.subscriptionRequests || []).forEach((sr) => {
        if (!sr?.status) return;
        byStatus[sr.status] = (byStatus[sr.status] || 0) + 1;
        if (isAwaitingResponse(sr)) awaiting += 1;
      });
      const total = Object.values(byStatus).reduce((sum, n) => sum + n, 0);
      return { name: g.name || `#${g.id}`, byStatus, awaiting, total };
    })
    .filter(r => r.total > 0)
    .sort((a, b) => b.total - a.total)
    .slice(0, 8), [groups]);

  /* Only statuses actually present are drawn, so the legend never lists empty categories. */
  const statuses = useMemo(() => Object.keys(COLORS)
    .filter(status => rows.some(r => (r.byStatus[status] || 0) > 0)), [rows]);

  const data = {
    labels: rows.map(r => r.name),
    datasets: statuses.map(status => ({
      label: t(STATUS_LABEL_KEYS[status]),
      data: rows.map(r => r.byStatus[status] || 0),
      backgroundColor: COLORS[status],
      borderRadius: 4,
      barThickness: 20,
    })),
  };

  const options = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { color: 'rgba(0,0,0,.05)' } },
      y: { stacked: true, grid: { display: false } },
    },
    plugins: {
      legend: {
        position: 'bottom',
        labels: { boxWidth: 12, boxHeight: 12, usePointStyle: true, pointStyle: 'circle', padding: 14 },
      },
      tooltip: {
        callbacks: {
          footer: (items) => {
            const row = rows[items?.[0]?.dataIndex];
            return row?.awaiting > 0
              ? t('dashboard.charts.awaitingResponse', { count: row.awaiting })
              : '';
          },
        },
      },
    },
  };

  return (
    <ChartCard
      title={t('dashboard.charts.byGroup')}
      icon="fas fa-chart-simple"
      empty={rows.length === 0}
      emptyLabel={t('dashboard.charts.noGroupActivity')}
    >
      <Bar data={data} options={options} />
    </ChartCard>
  );
};
