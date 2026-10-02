/*
 * SPDX-FileCopyrightText: 2025-2026 Edgemoor Research Institute and Derek Jenkins
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Author: Derek Jenkins <derek@pure-code.net>
 * Additional terms under AGPL-3.0 Section 7 apply. See NOTICE at the repository root.
 */

import React, { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import toast from 'react-hot-toast';
import {
  getRdapDataMappings,
  getRdapDataMapping,
  introspectAndSaveMapping,
} from '../services/api';
import { useT } from '../i18n';

/* Layout constants (pure geometry — SSR-safe) */
const NODE_W = 240;
const HEADER_H = 34;
const ROW_H = 20;
const MAX_ROWS = 18;      // cap columns shown per table to keep nodes readable
const GAP_X = 70;
const GAP_Y = 40;
const PAD = 20;

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));

// Height of a table node given how many column rows it will display.
const nodeHeight = (columnCount) => {
  const shown = Math.min(columnCount, MAX_ROWS);
  const overflow = columnCount > MAX_ROWS ? 1 : 0;
  return HEADER_H + (shown + overflow) * ROW_H + 6;
};

/* Deterministic SSR-safe auto-layout: most-referenced tables first, round-robin into balanced columns. */
function computeLayout(schema) {
  const tables = (schema && Array.isArray(schema.tables)) ? schema.tables : [];
  if (tables.length === 0) return { nodes: {}, order: [], width: 400, height: 300 };

  const ordered = [...tables].sort((a, b) => {
    const ra = (a.referencedBy || []).length;
    const rb = (b.referencedBy || []).length;
    if (rb !== ra) return rb - ra;
    return (a.name || '').localeCompare(b.name || '');
  });

  const cols = Math.max(1, Math.round(Math.sqrt(ordered.length)));
  const columnYs = new Array(cols).fill(PAD);
  const nodes = {};
  const order = [];

  ordered.forEach((table, idx) => {
    const c = idx % cols;
    const columns = table.columns || [];
    const h = nodeHeight(columns.length);
    const x = PAD + c * (NODE_W + GAP_X);
    const y = columnYs[c];
    columnYs[c] += h + GAP_Y;
    nodes[table.name] = { name: table.name, x, y, w: NODE_W, h, table };
    order.push(table.name);
  });

  const width = PAD + cols * (NODE_W + GAP_X);
  const height = Math.max(...columnYs, 300) + PAD;
  return { nodes, order, width, height };
}

/* Architecture + Data-Integrity scores from scanned facts (PKs, declared vs inferred FKs, indexes/unique, nullability). SSR-safe. */
const gradeFor = (s) => (s >= 90 ? 'A' : s >= 80 ? 'B' : s >= 70 ? 'C' : s >= 60 ? 'D' : 'F');

function computeRatings(schema) {
  const tables = (schema && Array.isArray(schema.tables)) ? schema.tables : [];
  const total = tables.length;
  if (total === 0) return null;

  let withPk = 0, withIndex = 0, withUnique = 0, declaredFk = 0, inferredFk = 0;
  let totalCols = 0, notNullCols = 0;
  tables.forEach((t) => {
    const pks = (t.primaryKeys && t.primaryKeys.length)
      ? t.primaryKeys
      : (t.columns || []).filter((c) => c.primaryKey).map((c) => c.name);
    if (pks && pks.length) withPk += 1;
    (t.columns || []).forEach((c) => { totalCols += 1; if (c.nullable === false) notNullCols += 1; });
    const idx = t.indexes || [];
    if (idx.length) withIndex += 1;
    if (idx.some((i) => i.unique)) withUnique += 1;
    (t.foreignKeys || []).forEach((fk) => { if (fk.inferred) inferredFk += 1; else declaredFk += 1; });
  });

  const rels = declaredFk + inferredFk;
  const pkCov = withPk / total;
  const idxCov = withIndex / total;
  const uniqCov = withUnique / total;
  const declRatio = rels === 0 ? 1 : declaredFk / rels;
  const notNullRatio = totalCols === 0 ? 1 : notNullCols / totalCols;
  const band = (v, warn, good) => (v >= good ? 'good' : v >= warn ? 'warn' : 'bad');

  const archScore = Math.round(100 * (0.45 * pkCov + 0.30 * declRatio + 0.25 * idxCov));
  const diScore = Math.round(100 * (0.35 * pkCov + 0.35 * declRatio + 0.15 * uniqCov + 0.15 * notNullRatio));

  return {
    architecture: {
      score: archScore,
      grade: gradeFor(archScore),
      factors: [
        { status: band(pkCov, 0.5, 0.99), label: 'Primary keys', detail: `${withPk} of ${total} tables have a primary key` },
        { status: rels === 0 ? 'warn' : band(declRatio, 0.01, 0.99), label: 'Enforced relationships',
          detail: rels === 0 ? 'No relationships detected'
            : `${declaredFk} of ${rels} relationships are enforced foreign keys${inferredFk ? ` (${inferredFk} inferred)` : ''}` },
        { status: band(idxCov, 0.3, 0.75), label: 'Index coverage', detail: `${withIndex} of ${total} tables are indexed` },
      ],
    },
    dataIntegrity: {
      score: diScore,
      grade: gradeFor(diScore),
      factors: [
        { status: band(pkCov, 0.5, 0.99), label: 'Entity integrity',
          detail: withPk === total ? 'Every table has a primary key'
            : `${total - withPk} table(s) lack a primary key (duplicate rows possible)` },
        { status: rels === 0 ? 'warn' : (declaredFk === 0 ? 'bad' : (inferredFk ? 'warn' : 'good')), label: 'Referential integrity',
          detail: rels === 0 ? 'No foreign-key relationships to enforce'
            : declaredFk === 0 ? `All ${rels} relationships are inferred, not enforced by the database`
            : `${inferredFk} relationship(s) inferred and not enforced` },
        { status: uniqCov > 0 ? 'good' : 'warn', label: 'Unique constraints', detail: `${withUnique} of ${total} tables have a unique index` },
        { status: notNullRatio >= 0.5 ? 'good' : 'warn', label: 'NOT NULL usage', detail: `${Math.round(notNullRatio * 100)}% of columns are NOT NULL` },
      ],
    },
  };
}

// Build a deduped edge list from every table's foreignKeys.
function computeEdges(schema, nodes) {
  const tables = (schema && Array.isArray(schema.tables)) ? schema.tables : [];
  const seen = new Set();
  const edges = [];
  tables.forEach((table) => {
    (table.foreignKeys || []).forEach((fk) => {
      const from = table.name;
      const to = fk.pkTable;
      if (!nodes[from] || !nodes[to] || from === to) return;
      const key = `${from}|${fk.fkColumn}|${to}`;
      if (seen.has(key)) return;
      seen.add(key);
      edges.push({
        id: key,
        from,
        to,
        fkColumn: fk.fkColumn,
        pkColumn: fk.pkColumn,
        inferred: !!fk.inferred,
      });
    });
  });
  return edges;
}

// Point on a node's rectangle border along the direction to (tx, ty).
function edgePoint(node, tx, ty) {
  const cx = node.x + node.w / 2;
  const cy = node.y + node.h / 2;
  const dx = tx - cx;
  const dy = ty - cy;
  if (dx === 0 && dy === 0) return { x: cx, y: cy };
  const sx = dx !== 0 ? (node.w / 2) / Math.abs(dx) : Infinity;
  const sy = dy !== 0 ? (node.h / 2) / Math.abs(dy) : Infinity;
  const s = Math.min(sx, sy);
  return { x: cx + dx * s, y: cy + dy * s };
}

/* Deduped edges from the mapping's app-defined joins (not DB foreign keys); emits only when both endpoint tables are nodes. SSR-safe. */
function computeAppEdges(mapping, nodes) {
  if (!mapping || !nodes) return [];

  const domainsTable = mapping.domainsTable;
  const contactsTable = mapping.contactsTable;
  const hostsTable = mapping.hostsTable;
  const ipsTable = mapping.ipsTable;
  const asnsTable = mapping.asnsTable;
  const entitiesTable = mapping.entitiesTable;

  const seen = new Set();
  const edges = [];
  // Emit only if both tables are nodes; dedupe by from|col|to.
  const push = (from, fromCol, to, label) => {
    if (!from || !to || !nodes[from] || !nodes[to]) return;
    const col = fromCol || '';
    const key = `${from}|${col}|${to}`;
    if (seen.has(key)) return;
    seen.add(key);
    edges.push({ id: `app:${key}`, from, fromCol: col, to, label: label || '', kind: 'app' });
  };

  // domain -> contact, one edge per configured contact role
  const dcm = mapping.domainContactJoinMappings || {};
  Object.keys(dcm).forEach((role) => {
    const fkCol = dcm[role];
    if (!fkCol) return;
    push(domainsTable, fkCol, contactsTable, role);
  });

  // domain -> host (target column contactJoinKey unused here; edge routes table→table)
  const hjc = mapping.hostJoinConfig || {};
  if (hjc.domainColumn) {
    push(domainsTable, hjc.domainColumn, hostsTable, 'host');
  }

  // custom table -> its join target
  const targetFor = {
    domain: domainsTable,
    contact: contactsTable,
    host: hostsTable,
    ip: ipsTable,
    asn: asnsTable,       // UI dropdown value
    autnum: asnsTable,    // RDAP object key (per task spec) — both map to asnsTable
    entity: entitiesTable,
  };
  (mapping.customTableMappings || []).forEach((ct) => {
    if (!ct || !ct.tableName) return;
    const jcfg = ct.joinConfig || null;
    if (jcfg && jcfg.enabled === false) return;
    const joinType = (jcfg && jcfg.joinType) || 'domain';
    const target = targetFor[joinType];
    const label = ct.label || joinType;
    const conditions = (jcfg && jcfg.joinConditions) || [];
    if (conditions.length === 0) {
      push(ct.tableName, '', target, label);
      return;
    }
    conditions.forEach((jc) => push(ct.tableName, jc && jc.sourceColumn, target, label));
  });

  return edges;
}

const RdapSchemaViewer = () => {
  const { t } = useT();

  const [mappings, setMappings] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [schema, setSchema] = useState(null);
  const [mapping, setMapping] = useState(null); // full mapping config (holds app-defined joins)
  const [showAppRels, setShowAppRels] = useState(false);
  const [loadingMappings, setLoadingMappings] = useState(true);
  const [loadingSchema, setLoadingSchema] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  // Pan/zoom view + per-node drag overrides. Defaults are SSR-safe constants.
  const [view, setView] = useState({ tx: 0, ty: 0, scale: 1 });
  const [overrides, setOverrides] = useState({});
  const svgRef = useRef(null);
  const dragRef = useRef(null);

  // ---- data loading ----
  const loadMappings = useCallback(async () => {
    try {
      setLoadingMappings(true);
      const res = await getRdapDataMappings();
      setMappings(res?.data?.mappings || []);
    } catch (e) {
      toast.error(t('rdapSchema.loadMappingsFailed'));
    } finally {
      setLoadingMappings(false);
    }
  }, [t]);

  useEffect(() => { loadMappings(); }, [loadMappings]);

  const resetView = useCallback(() => {
    setView({ tx: 0, ty: 0, scale: 1 });
    setOverrides({});
  }, []);

  const introspect = useCallback(async (id, silent) => {
    const res = await introspectAndSaveMapping(id);
    if (res?.data?.success) {
      const fresh = res.data.schema;
      setSchema(fresh);
      if (!silent) {
        toast.success(t('rdapSchema.refreshed', { count: fresh?.tables?.length || 0 }));
      }
      return fresh;
    }
    const err = res?.data?.schema?.error || t('rdapSchema.unknownError');
    toast.error(t('rdapSchema.introspectFailed', { error: err }));
    return null;
  }, [t]);

  const loadSchema = useCallback(async (id) => {
    setSchema(null);
    setMapping(null);
    resetView();
    if (!id) return;
    try {
      setLoadingSchema(true);
      const res = await getRdapDataMapping(id);
      // Keep the full mapping — it carries the app-defined join config introspection never touches.
      setMapping(res?.data?.mapping || null);
      const cached = res?.data?.mapping?.discoveredSchema;
      if (cached && Array.isArray(cached.tables)) {
        setSchema(cached);
      } else {
        await introspect(id, true);
      }
    } catch (e) {
      toast.error(t('rdapSchema.loadSchemaFailed'));
    } finally {
      setLoadingSchema(false);
    }
  }, [t, resetView, introspect]);

  const handleSelect = (e) => {
    const id = e.target.value;
    setSelectedId(id);
    loadSchema(id);
  };

  const handleRefresh = async () => {
    if (!selectedId) return;
    try {
      setRefreshing(true);
      await introspect(selectedId, false);
      resetView();
    } catch (e) {
      toast.error(t('rdapSchema.loadSchemaFailed'));
    } finally {
      setRefreshing(false);
    }
  };

  // ---- derived layout (pure, memoized) ----
  const layout = useMemo(() => computeLayout(schema), [schema]);
  const edges = useMemo(() => computeEdges(schema, layout.nodes), [schema, layout]);
  const appEdges = useMemo(() => computeAppEdges(mapping, layout.nodes), [mapping, layout]);

  const posOf = useCallback((name) => {
    const base = layout.nodes[name];
    if (!base) return null;
    const ov = overrides[name];
    return ov ? { ...base, x: ov.x, y: ov.y } : base;
  }, [layout, overrides]);

  // ---- interaction: wheel zoom (non-passive listener, browser only) ----
  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    const svg = svgRef.current;
    if (!svg) return undefined;
    const onWheel = (e) => {
      e.preventDefault();
      const rect = svg.getBoundingClientRect();
      const px = e.clientX - rect.left;
      const py = e.clientY - rect.top;
      setView((v) => {
        const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
        const ns = clamp(v.scale * factor, 0.2, 3);
        const wx = (px - v.tx) / v.scale;
        const wy = (py - v.ty) / v.scale;
        return { tx: px - wx * ns, ty: py - wy * ns, scale: ns };
      });
    };
    svg.addEventListener('wheel', onWheel, { passive: false });
    return () => svg.removeEventListener('wheel', onWheel);
  }, [schema]);

  // ---- interaction: pan + node drag ----
  const onBackgroundMouseDown = (e) => {
    dragRef.current = {
      mode: 'pan',
      startX: e.clientX,
      startY: e.clientY,
      origTx: view.tx,
      origTy: view.ty,
    };
  };

  const onNodeMouseDown = (e, name) => {
    e.stopPropagation();
    const p = posOf(name);
    if (!p) return;
    dragRef.current = {
      mode: 'node',
      name,
      startX: e.clientX,
      startY: e.clientY,
      origX: p.x,
      origY: p.y,
      scale: view.scale,
    };
  };

  const onMouseMove = (e) => {
    const d = dragRef.current;
    if (!d) return;
    if (d.mode === 'pan') {
      setView((v) => ({ ...v, tx: d.origTx + (e.clientX - d.startX), ty: d.origTy + (e.clientY - d.startY) }));
    } else if (d.mode === 'node') {
      const dx = (e.clientX - d.startX) / (d.scale || 1);
      const dy = (e.clientY - d.startY) / (d.scale || 1);
      setOverrides((o) => ({ ...o, [d.name]: { x: d.origX + dx, y: d.origY + dy } }));
    }
  };

  const endDrag = () => { dragRef.current = null; };

  const zoomBy = (factor) => {
    const svg = svgRef.current;
    const rect = svg ? svg.getBoundingClientRect() : { width: 800, height: 500 };
    const px = rect.width / 2;
    const py = rect.height / 2;
    setView((v) => {
      const ns = clamp(v.scale * factor, 0.2, 3);
      const wx = (px - v.tx) / v.scale;
      const wy = (py - v.ty) / v.scale;
      return { tx: px - wx * ns, ty: py - wy * ns, scale: ns };
    });
  };

  const hasSchema = !!(schema && Array.isArray(schema.tables) && schema.tables.length > 0);
  const busy = loadingSchema || refreshing;

  // ---- render helpers ----
  const renderNode = (name) => {
    const p = posOf(name);
    if (!p) return null;
    const table = p.table;
    const columns = table.columns || [];
    const fkCols = new Set((table.foreignKeys || []).map((fk) => fk.fkColumn));
    const shown = columns.slice(0, MAX_ROWS);
    const overflow = columns.length - shown.length;

    return (
      <g
        key={name}
        transform={`translate(${p.x}, ${p.y})`}
        onMouseDown={(e) => onNodeMouseDown(e, name)}
        style={{ cursor: 'move' }}
      >
        <rect
          x={0} y={0} width={p.w} height={p.h} rx={8} ry={8}
          fill="#ffffff" stroke="#cbd5e1" strokeWidth={1.5}
          style={{ filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.08))' }}
        />
        {/* header band */}
        <path
          d={`M0,8 a8,8 0 0 1 8,-8 h${p.w - 16} a8,8 0 0 1 8,8 v${HEADER_H - 8} h${-p.w} Z`}
          fill="#0066cc"
        />
        <text x={12} y={HEADER_H / 2 + 5} fill="#ffffff" fontSize={13} fontWeight={700}>
          {name.length > 22 ? `${name.slice(0, 21)}…` : name}
        </text>
        {typeof table.rowCount === 'number' && table.rowCount >= 0 && (
          <g>
            <rect x={p.w - 66} y={7} width={58} height={HEADER_H - 14} rx={6}
              fill="rgba(255,255,255,0.22)" />
            <text x={p.w - 37} y={HEADER_H / 2 + 4} fill="#ffffff" fontSize={10}
              textAnchor="middle">
              {t('rdapSchema.rows', { count: table.rowCount })}
            </text>
          </g>
        )}
        {/* columns */}
        {shown.map((col, i) => {
          const y = HEADER_H + i * ROW_H;
          const isPk = col.primaryKey === true;
          const isFk = fkCols.has(col.name);
          const isKey = isPk || isFk;
          const typeText = col.typeDisplay || col.type || '';
          return (
            <g key={col.name || i} opacity={col.nullable ? 0.55 : 1}>
              {i % 2 === 1 && (
                <rect x={1} y={y} width={p.w - 2} height={ROW_H} fill="#f8fafc" />
              )}
              {isKey && (
                <circle cx={13} cy={y + ROW_H / 2} r={3.5}
                  fill={isPk ? '#d97706' : '#0066cc'}>
                  <title>{isPk ? 'Primary key' : 'Foreign key'}</title>
                </circle>
              )}
              <text
                x={isKey ? 24 : 12} y={y + ROW_H - 6} fontSize={11}
                fill="#1e293b" fontWeight={isKey ? 600 : 400}
              >
                {(col.name || '').length > 18 ? `${col.name.slice(0, 17)}…` : col.name}
              </text>
              <text x={p.w - 10} y={y + ROW_H - 6} fontSize={10}
                fill="#64748b" textAnchor="end">
                {typeText.length > 14 ? `${typeText.slice(0, 13)}…` : typeText}
              </text>
            </g>
          );
        })}
        {overflow > 0 && (
          <text x={12} y={HEADER_H + shown.length * ROW_H + ROW_H - 6}
            fontSize={10} fill="#94a3b8" fontStyle="italic">
            {t('rdapSchema.moreColumns', { count: overflow })}
          </text>
        )}
      </g>
    );
  };

  const renderEdge = (edge) => {
    const s = posOf(edge.from);
    const tnode = posOf(edge.to);
    if (!s || !tnode) return null;
    const sc = { x: s.x + s.w / 2, y: s.y + s.h / 2 };
    const tc = { x: tnode.x + tnode.w / 2, y: tnode.y + tnode.h / 2 };
    const start = edgePoint(s, tc.x, tc.y);
    const end = edgePoint(tnode, sc.x, sc.y);
    return (
      <line
        key={edge.id}
        x1={start.x} y1={start.y} x2={end.x} y2={end.y}
        stroke={edge.inferred ? '#f59e0b' : '#6366f1'}
        strokeWidth={1.6}
        strokeDasharray={edge.inferred ? '6 4' : undefined}
        markerEnd={edge.inferred ? 'url(#arrow-inferred)' : 'url(#arrow-declared)'}
      >
        <title>{`${edge.from}.${edge.fkColumn} → ${edge.to}.${edge.pkColumn}${edge.inferred ? ' (inferred)' : ''}`}</title>
      </line>
    );
  };

  /* App-defined edge: solid teal, distinct from declared (indigo) and inferred (dashed amber). */
  const renderAppEdge = (edge) => {
    const s = posOf(edge.from);
    const tnode = posOf(edge.to);
    if (!s || !tnode) return null;
    const sc = { x: s.x + s.w / 2, y: s.y + s.h / 2 };
    const tc = { x: tnode.x + tnode.w / 2, y: tnode.y + tnode.h / 2 };
    const start = edgePoint(s, tc.x, tc.y);
    const end = edgePoint(tnode, sc.x, sc.y);
    const src = edge.fromCol ? `${edge.from}.${edge.fromCol}` : edge.from;
    return (
      <line
        key={edge.id}
        x1={start.x} y1={start.y} x2={end.x} y2={end.y}
        stroke="#0d9488"
        strokeWidth={2.6}
        strokeLinecap="round"
        markerEnd="url(#arrow-app)"
      >
        <title>{`${src} → ${edge.to} (application-defined: ${edge.label})`}</title>
      </line>
    );
  };

  return (
    <div>
      {/* Controls */}
      <div className="card mb-3">
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-12 col-md-5">
              <label className="form-label small fw-semibold mb-1">{t('rdapSchema.selectMapping')}</label>
              <select
                className="form-select"
                value={selectedId}
                onChange={handleSelect}
                disabled={loadingMappings}
              >
                <option value="">{t('rdapSchema.selectMappingPlaceholder')}</option>
                {mappings.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}{m.dbType ? ` (${m.dbType})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="col-auto">
              <button
                className="btn btn-outline-primary"
                onClick={handleRefresh}
                disabled={!selectedId || busy}
              >
                <i className={`fa-solid fa-arrows-rotate me-1 ${refreshing ? 'fa-spin' : ''}`} />
                {refreshing ? t('rdapSchema.refreshing') : t('rdapSchema.refresh')}
              </button>
            </div>
            {hasSchema && (
              <div className="col-auto ms-auto text-md-end">
                <div className="small text-muted">
                  {(schema.productName || '')}{schema.productVersion ? ` ${schema.productVersion}` : ''}
                  {schema.schema ? ` · ${t('rdapSchema.schemaLabel')}: ${schema.schema}` : ''}
                </div>
                <div className="small text-muted">
                  {t('rdapSchema.tableCount', { count: schema.tables.length })}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Schema ratings scorecard (computed from the scan findings) */}
      {hasSchema && (() => {
        const ratings = computeRatings(schema);
        if (!ratings) return null;
        const gradeColor = (g) => (g === 'A' || g === 'B') ? '#16a34a' : g === 'C' ? '#d97706' : g === 'D' ? '#ea580c' : '#dc2626';
        const fcolor = (s) => s === 'good' ? '#16a34a' : s === 'warn' ? '#d97706' : '#dc2626';
        const ficon = (s) => s === 'good' ? 'fa-circle-check' : s === 'warn' ? 'fa-triangle-exclamation' : 'fa-circle-xmark';
        const RatingCard = ({ title, subtitle, r }) => (
          <div className="col-12 col-md-6">
            <div className="card h-100">
              <div className="card-body py-3">
                <div className="d-flex align-items-center gap-3 mb-2">
                  <div className="d-flex align-items-center justify-content-center rounded flex-shrink-0"
                    style={{ width: 52, height: 52, background: gradeColor(r.grade), color: '#fff', fontSize: 26, fontWeight: 700 }}>
                    {r.grade}
                  </div>
                  <div>
                    <div className="fw-semibold">{title}</div>
                    <div className="text-muted small">{subtitle}</div>
                    <div className="small"><span className="fw-semibold">{r.score}</span><span className="text-muted">/100</span></div>
                  </div>
                </div>
                <ul className="list-unstyled mb-0 small">
                  {r.factors.map((f, i) => (
                    <li key={i} className="d-flex align-items-start gap-2 mb-1">
                      <i className={`fa-solid ${ficon(f.status)} mt-1 flex-shrink-0`} style={{ color: fcolor(f.status) }} />
                      <span><span className="fw-semibold">{f.label}:</span> <span className="text-muted">{f.detail}</span></span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>
        );
        return (
          <div className="row g-3 mb-3">
            <RatingCard title={t('rdapSchema.archRating')} subtitle={t('rdapSchema.archRatingSub')} r={ratings.architecture} />
            <RatingCard title={t('rdapSchema.integrityRating')} subtitle={t('rdapSchema.integrityRatingSub')} r={ratings.dataIntegrity} />
          </div>
        );
      })()}

      {/* Diagram surface */}
      <div
        className="position-relative border rounded bg-light"
        style={{ height: '70vh', overflow: 'hidden' }}
      >
        {/* Toolbar overlay */}
        {hasSchema && (
          <div className="position-absolute top-0 start-0 m-2 d-flex gap-1"
            style={{ zIndex: 5 }}>
            <button className="btn btn-sm btn-light border" title={t('rdapSchema.zoomIn')}
              onClick={() => zoomBy(1.2)}>
              <i className="fa-solid fa-plus" />
            </button>
            <button className="btn btn-sm btn-light border" title={t('rdapSchema.zoomOut')}
              onClick={() => zoomBy(1 / 1.2)}>
              <i className="fa-solid fa-minus" />
            </button>
            <button className="btn btn-sm btn-light border" title={t('rdapSchema.resetView')}
              onClick={resetView}>
              <i className="fa-solid fa-expand" />
            </button>
            <div className="btn btn-sm btn-light border d-flex align-items-center"
              style={{ cursor: 'default' }}>
              <div className="form-check form-switch m-0">
                <input
                  className="form-check-input"
                  type="checkbox"
                  role="switch"
                  id="rdapSchemaAppRelsToggle"
                  checked={showAppRels}
                  onChange={(e) => setShowAppRels(e.target.checked)}
                />
                <label className="form-check-label small ms-1"
                  htmlFor="rdapSchemaAppRelsToggle" style={{ cursor: 'pointer' }}>
                  {t('rdapSchema.showAppRels')}
                </label>
              </div>
            </div>
          </div>
        )}

        {/* Legend overlay */}
        {hasSchema && (
          <div className="position-absolute bottom-0 end-0 m-2 p-2 bg-white border rounded shadow-sm"
            style={{ zIndex: 5, fontSize: 11 }}>
            <div className="fw-semibold mb-1 text-muted">{t('rdapSchema.legendTitle')}</div>
            <div className="d-flex align-items-center gap-2 mb-1">
              <svg width="34" height="8"><line x1="0" y1="4" x2="34" y2="4" stroke="#6366f1" strokeWidth="2" /></svg>
              <span>{t('rdapSchema.legendDeclared')}</span>
            </div>
            <div className="d-flex align-items-center gap-2">
              <svg width="34" height="8"><line x1="0" y1="4" x2="34" y2="4" stroke="#f59e0b" strokeWidth="2" strokeDasharray="6 4" /></svg>
              <span>{t('rdapSchema.legendInferred')}</span>
            </div>
            {showAppRels && (
              <div className="d-flex align-items-center gap-2 mt-1">
                <svg width="34" height="8"><line x1="0" y1="4" x2="34" y2="4" stroke="#0d9488" strokeWidth="2.6" strokeLinecap="round" /></svg>
                <span>{t('rdapSchema.legendApp')}</span>
              </div>
            )}
          </div>
        )}

        {/* States */}
        {busy && (
          <div className="position-absolute top-50 start-50 translate-middle text-center text-muted">
            <div className="spinner-border text-primary mb-2" role="status" />
            <div>{t('rdapSchema.loading')}</div>
          </div>
        )}
        {!busy && loadingMappings && (
          <div className="position-absolute top-50 start-50 translate-middle text-muted">
            {t('rdapSchema.loading')}
          </div>
        )}
        {!busy && !loadingMappings && mappings.length === 0 && (
          <div className="position-absolute top-50 start-50 translate-middle text-center text-muted px-3">
            <i className="fa-solid fa-circle-info fa-2x mb-2 d-block opacity-50" />
            {t('rdapSchema.noMappings')}
          </div>
        )}
        {!busy && !loadingMappings && mappings.length > 0 && !selectedId && (
          <div className="position-absolute top-50 start-50 translate-middle text-center text-muted px-3">
            <i className="fa-solid fa-diagram-project fa-2x mb-2 d-block opacity-50" />
            {t('rdapSchema.selectPrompt')}
          </div>
        )}
        {!busy && selectedId && !hasSchema && (
          <div className="position-absolute top-50 start-50 translate-middle text-center text-muted px-3">
            <i className="fa-solid fa-table fa-2x mb-2 d-block opacity-50" />
            {t('rdapSchema.emptySchema')}
          </div>
        )}

        {/* SVG diagram */}
        {hasSchema && (
          <svg
            ref={svgRef}
            width="100%"
            height="100%"
            style={{ display: 'block', cursor: dragRef.current?.mode === 'pan' ? 'grabbing' : 'grab', userSelect: 'none' }}
            onMouseDown={onBackgroundMouseDown}
            onMouseMove={onMouseMove}
            onMouseUp={endDrag}
            onMouseLeave={endDrag}
          >
            <defs>
              <marker id="arrow-declared" viewBox="0 0 10 10" refX="9" refY="5"
                markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                <path d="M0,0 L10,5 L0,10 z" fill="#6366f1" />
              </marker>
              <marker id="arrow-inferred" viewBox="0 0 10 10" refX="9" refY="5"
                markerWidth="7" markerHeight="7" orient="auto-start-reverse">
                <path d="M0,0 L10,5 L0,10 z" fill="#f59e0b" />
              </marker>
              <marker id="arrow-app" viewBox="0 0 10 10" refX="9" refY="5"
                markerWidth="8" markerHeight="8" orient="auto-start-reverse">
                <path d="M0,0 L10,5 L0,10 z" fill="#0d9488" />
              </marker>
            </defs>
            <g transform={`translate(${view.tx}, ${view.ty}) scale(${view.scale})`}>
              <g>{edges.map(renderEdge)}</g>
              {showAppRels && <g>{appEdges.map(renderAppEdge)}</g>}
              <g>{layout.order.map(renderNode)}</g>
            </g>
          </svg>
        )}
      </div>
    </div>
  );
};

export default RdapSchemaViewer;
