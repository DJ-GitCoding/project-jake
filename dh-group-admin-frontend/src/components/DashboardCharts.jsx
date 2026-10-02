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

/* Bootstrap's palette, so the charts read as part of the same UI as the badges. */
const COLORS = {
  primary: '#0d6efd',
  success: '#198754',
  info: '#0dcaf0',
  warning: '#ffc107',
  danger: '#dc3545',
  dark: '#343a40',
  secondary: '#adb5bd',
};

const num = (v) => (typeof v === 'number' ? v : 0);

const ChartCard = ({ title, icon, empty, emptyLabel, height = 280, children }) => (
  <div className="card h-100">
    <div className="card-header bg-transparent">
      <h6 className="mb-0">
        <i className={`${icon} text-muted me-2`} />
        {title}
      </h6>
    </div>
    <div className="card-body">
      {empty ? (
        <div className="d-flex flex-column align-items-center justify-content-center text-muted h-100"
             style={{ minHeight: height }}>
          <i className="fa-solid fa-chart-pie fa-2x mb-2 opacity-25" />
          <span className="small">{emptyLabel}</span>
        </div>
      ) : (
        <div style={{ height }}>{children}</div>
      )}
    </div>
  </div>
);

export const SubscriptionStatusChart = ({ stats }) => {
  const { t } = useT();

  const slices = useMemo(() => ([
    { key: 'pending', label: t('dashboard.stats.pending'), value: num(stats?.pendingSubscriptions), color: COLORS.warning },
    { key: 'approved', label: t('dashboard.stats.approved'), value: num(stats?.approvedSubscriptions), color: COLORS.info },
    { key: 'testing', label: t('dashboard.stats.testing'), value: num(stats?.testingSubscriptions), color: COLORS.primary },
    { key: 'active', label: t('common.active'), value: num(stats?.activeSubscriptions), color: COLORS.success },
    { key: 'suspended', label: t('dashboard.stats.suspended'), value: num(stats?.suspendedSubscriptions), color: COLORS.danger },
    { key: 'denied', label: t('dashboard.stats.denied'), value: num(stats?.deniedSubscriptions), color: COLORS.dark },
  ]), [stats, t]);

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
            const value = ctx.parsed;
            const share = total > 0 ? Math.round((value / total) * 100) : 0;
            return ` ${ctx.label}: ${value} (${share}%)`;
          },
        },
      },
    },
  };

  return (
    <ChartCard
      title={t('dashboard.charts.subscriptionsByStatus')}
      icon="fa-solid fa-chart-pie"
      empty={total === 0}
      emptyLabel={t('dashboard.charts.noData')}
    >
      <Doughnut data={data} options={options} />
    </ChartCard>
  );
};

export const EstateChart = ({ stats, isMaster }) => {
  const { t } = useT();

  /* Each row is a population split into the part that is live and the part that is not,
   * so one glance says how much of the estate is actually doing anything. */
  const rows = useMemo(() => {
    const all = [
      {
        label: t('dashboard.charts.templates'),
        live: num(stats?.activeTemplates),
        idle: Math.max(0, num(stats?.totalTemplates) - num(stats?.activeTemplates)),
        show: true,
      },
      {
        label: t('dashboard.charts.dataHolders'),
        live: num(stats?.activeDataHolders),
        idle: Math.max(0, num(stats?.totalDataHolders) - num(stats?.activeDataHolders)),
        show: true,
      },
      {
        label: t('dashboard.charts.dhGroups'),
        live: num(stats?.activeDataHolderGroups),
        idle: Math.max(0, num(stats?.totalDataHolderGroups) - num(stats?.activeDataHolderGroups)),
        show: isMaster,
      },
      {
        label: t('dashboard.charts.instances'),
        live: num(stats?.runningInstances),
        idle: num(stats?.stoppedInstances),
        show: num(stats?.totalInstances) > 0,
      },
    ];
    return all.filter(r => r.show);
  }, [stats, isMaster, t]);

  const total = rows.reduce((sum, r) => sum + r.live + r.idle, 0);

  const data = {
    labels: rows.map(r => r.label),
    datasets: [
      {
        label: t('dashboard.charts.live'),
        data: rows.map(r => r.live),
        backgroundColor: COLORS.success,
        borderRadius: 4,
        barThickness: 22,
      },
      {
        label: t('dashboard.charts.idle'),
        data: rows.map(r => r.idle),
        backgroundColor: COLORS.secondary,
        borderRadius: 4,
        barThickness: 22,
      },
    ],
  };

  const options = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: {
        stacked: true,
        beginAtZero: true,
        ticks: { precision: 0 },
        grid: { color: 'rgba(0,0,0,.05)' },
      },
      y: { stacked: true, grid: { display: false } },
    },
    plugins: {
      legend: {
        position: 'bottom',
        labels: { boxWidth: 12, boxHeight: 12, usePointStyle: true, pointStyle: 'circle', padding: 14 },
      },
    },
  };

  return (
    <ChartCard
      title={t('dashboard.charts.estate')}
      icon="fa-solid fa-chart-simple"
      empty={total === 0}
      emptyLabel={t('dashboard.charts.noData')}
    >
      <Bar data={data} options={options} />
    </ChartCard>
  );
};
