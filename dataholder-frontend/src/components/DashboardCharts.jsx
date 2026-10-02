/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useMemo, useState } from 'react';
import {
  Chart as ChartJS,
  BarElement,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Filler,
  Tooltip,
  Legend,
} from 'chart.js';
import { Bar, Line } from 'react-chartjs-2';
import { useT } from '../i18n';

ChartJS.register(BarElement, LineElement, PointElement, CategoryScale, LinearScale, Filler, Tooltip, Legend);

const OUTCOMES = [
  { key: 'approved',  color: '#0ca30c', labelKey: 'dashboard.charts.approved',  icon: 'fa-check' },
  { key: 'pending',   color: '#fab219', labelKey: 'dashboard.charts.pending',   icon: 'fa-hourglass-half' },
  { key: 'denied',    color: '#d03b3b', labelKey: 'dashboard.charts.denied',    icon: 'fa-xmark' },
  { key: 'cancelled', color: '#8a8a85', labelKey: 'dashboard.charts.cancelled', icon: 'fa-ban' },
];

const SERIES_HUE = '#2a78d6';

const ChartCard = ({ title, icon, actions, toolbar, empty, emptyLabel, height = 280, children }) => (
  <div className="card h-100">
    <div className="card-header d-flex justify-content-between align-items-center gap-2 flex-wrap">
      <h3 className="mb-0">
        <i className={`fa-solid ${icon}`}></i>
        {title}
      </h3>
      {actions}
    </div>
    <div className="card-body">
      {/* Sits above the empty/plot switch so the range stays changeable when a range is empty. */}
      {toolbar}
      {empty ? (
        <div className="d-flex flex-column align-items-center justify-content-center text-muted"
             style={{ minHeight: height }}>
          <i className="fa-solid fa-chart-line fa-2x mb-2 opacity-25"></i>
          <span className="small">{emptyLabel}</span>
        </div>
      ) : (
        <div style={{ height }}>{children}</div>
      )}
    </div>
  </div>
);

/*
 * The four outcome counts already appear as figures in the KPI row, so this adds the
 * one thing the figures cannot show: their proportion. One bar, not a pie.
 */
export const RequestOutcomeChart = ({ stats }) => {
  const { t } = useT();

  const segments = useMemo(() => OUTCOMES
    .map(o => ({ ...o, label: t(o.labelKey), value: Number(stats?.[o.key] ?? 0) }))
    .filter(o => o.value > 0), [stats, t]);

  const total = segments.reduce((sum, s) => sum + s.value, 0);

  const data = {
    labels: [''],
    datasets: segments.map(s => ({
      label: s.label,
      data: [s.value],
      backgroundColor: s.color,
      borderColor: '#fff',
      borderWidth: 2,
      borderRadius: 4,
      barThickness: 40,
    })),
  };

  const options = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      x: { stacked: true, display: false, beginAtZero: true },
      y: { stacked: true, display: false },
    },
    plugins: {
      legend: {
        position: 'bottom',
        labels: { boxWidth: 12, boxHeight: 12, usePointStyle: true, pointStyle: 'circle', padding: 14 },
      },
      tooltip: {
        callbacks: {
          label: (ctx) => {
            const share = total > 0 ? Math.round((ctx.parsed.x / total) * 100) : 0;
            return ` ${ctx.dataset.label}: ${ctx.parsed.x} (${share}%)`;
          },
        },
      },
    },
  };

  return (
    <ChartCard
      title={t('dashboard.charts.outcomes')}
      icon="fa-chart-simple"
      empty={total === 0}
      emptyLabel={t('dashboard.charts.noRequests')}
      height={150}
    >
      <Bar data={data} options={options} />
      {/* Status colour never carries meaning alone: every segment is named and counted. */}
      <div className="d-flex flex-wrap gap-3 mt-2 small">
        {segments.map(s => (
          <span key={s.key} className="text-muted">
            <i className={`fa-solid ${s.icon} me-1`} style={{ color: s.color }}></i>
            {s.label}: <strong className="text-body">{s.value}</strong>
          </span>
        ))}
      </div>
    </ChartCard>
  );
};

export const RANGE_OPTIONS = [7, 30, 90];

const todayIso = () => new Date().toISOString().slice(0, 10);

/*
 * The range is either one of the presets or an explicit from/to pair. Keeping both in
 * one value means the chart always knows which is in force, and the parent has a single
 * thing to hand to the API.
 */
export const presetRange = (days) => ({ mode: 'preset', days });

export const rangeParams = (range) => (range.mode === 'custom'
  ? { from: range.from, to: range.to }
  : { days: range.days });

export const RequestsOverTimeChart = ({ points, range, onChangeRange, loading }) => {
  const { t } = useT();
  const [customOpen, setCustomOpen] = useState(range?.mode === 'custom');
  const [draftFrom, setDraftFrom] = useState(range?.from || '');
  const [draftTo, setDraftTo] = useState(range?.to || todayIso());

  const rows = points || [];
  /* Dense windows get sparse tick labels so dates never collide. */
  const tickEvery = rows.length > 180 ? 30 : rows.length > 45 ? 7 : rows.length > 14 ? 3 : 1;

  const data = {
    labels: rows.map(p => p.date),
    datasets: [{
      label: t('dashboard.charts.requests'),
      data: rows.map(p => Number(p.count ?? 0)),
      borderColor: SERIES_HUE,
      backgroundColor: 'rgba(42, 120, 214, 0.12)',
      borderWidth: 2,
      pointRadius: rows.length > 45 ? 0 : 3,
      pointHoverRadius: 5,
      pointBackgroundColor: SERIES_HUE,
      fill: true,
      tension: 0.25,
    }],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    scales: {
      x: {
        grid: { display: false },
        ticks: {
          autoSkip: false,
          maxRotation: 0,
          callback: function (value, index) {
            if (index % tickEvery !== 0) return '';
            const label = this.getLabelForValue(value);
            return label ? label.slice(5) : '';
          },
        },
      },
      y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: 'rgba(0,0,0,.05)' } },
    },
    plugins: {
      /* One series: the card title names it, so a legend box would be noise. */
      legend: { display: false },
      tooltip: {
        callbacks: {
          title: (items) => items?.[0]?.label ?? '',
          label: (ctx) => ` ${t('dashboard.charts.requestsOnDay', { count: ctx.parsed.y })}`,
        },
      },
    },
  };

  const applyCustom = () => {
    if (!draftFrom || !draftTo) return;
    onChangeRange({ mode: 'custom', from: draftFrom, to: draftTo });
  };

  const choosePreset = (days) => {
    setCustomOpen(false);
    onChangeRange(presetRange(days));
  };

  const actions = (
    <div className="btn-group btn-group-sm" role="group" aria-label={t('dashboard.charts.rangeLabel')}>
      {RANGE_OPTIONS.map(option => (
        <button
          key={option}
          type="button"
          className={`btn btn-outline-secondary ${range?.mode === 'preset' && option === range.days ? 'active' : ''}`}
          onClick={() => choosePreset(option)}
          disabled={loading}
        >
          {t('dashboard.charts.lastDays', { count: option })}
        </button>
      ))}
      <button
        type="button"
        className={`btn btn-outline-secondary ${range?.mode === 'custom' ? 'active' : ''}`}
        onClick={() => setCustomOpen(open => !open)}
        aria-expanded={customOpen}
        disabled={loading}
      >
        <i className="fa-solid fa-calendar-days me-1"></i>
        {t('dashboard.charts.custom')}
      </button>
    </div>
  );

  const toolbar = customOpen ? (
    <div className="d-flex align-items-end gap-2 flex-wrap mb-3">
      <div>
        <label className="form-label small mb-1" htmlFor="requests-from">
          {t('dashboard.charts.from')}
        </label>
        <input
          type="date"
          id="requests-from"
          className="form-control form-control-sm"
          value={draftFrom}
          max={draftTo || todayIso()}
          onChange={(e) => setDraftFrom(e.target.value)}
        />
      </div>
      <div>
        <label className="form-label small mb-1" htmlFor="requests-to">
          {t('dashboard.charts.to')}
        </label>
        <input
          type="date"
          id="requests-to"
          className="form-control form-control-sm"
          value={draftTo}
          min={draftFrom || undefined}
          max={todayIso()}
          onChange={(e) => setDraftTo(e.target.value)}
        />
      </div>
      <button
        type="button"
        className="btn btn-primary btn-sm"
        onClick={applyCustom}
        disabled={loading || !draftFrom || !draftTo}
      >
        {t('dashboard.charts.apply')}
      </button>
      {range?.mode === 'custom' && (
        <span className="text-muted small ms-1">
          {t('dashboard.charts.showingRange', { from: range.from, to: range.to })}
        </span>
      )}
    </div>
  ) : null;

  return (
    <ChartCard
      title={t('dashboard.charts.requestsOverTime')}
      icon="fa-chart-line"
      actions={actions}
      toolbar={toolbar}
      empty={rows.length === 0}
      emptyLabel={t('dashboard.charts.noRequests')}
    >
      <Line data={data} options={options} />
    </ChartCard>
  );
};
