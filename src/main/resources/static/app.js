'use strict';

const state = {
  model: null,
  check: null,
  reduction: null,
};

const $ = (sel) => document.querySelector(sel);

async function getJSON(url) {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(await res.text());
  }
  return res.json();
}

async function postJSON(url, body) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
  });
  if (!res.ok) {
    throw new Error(await res.text());
  }
  return res.json();
}

function entrySpec(index) {
  return state.model.loopEntries.find((e) => e.index === index);
}

function stateById(id) {
  return state.model.states.find((s) => s.id === id);
}

async function init() {
  state.model = await getJSON('/api/model');
  renderControls();
  renderValues();
  await runCheck();
  bindEvents();
  await loadRecords();
}

function renderControls() {
  const select = $('#entrySelect');
  select.innerHTML = '';
  for (const entry of state.model.loopEntries) {
    const option = document.createElement('option');
    option.value = String(entry.index);
    option.textContent = `idx ${entry.index} · ${entry.stateId}`;
    select.appendChild(option);
  }
  $('#entryLabel').textContent = entrySpec(Number(select.value)).label;
}

function bindEvents() {
  $('#entrySelect').addEventListener('change', () => {
    $('#entryLabel').textContent = entrySpec(Number($('#entrySelect').value)).label;
    runCheck();
  });
  $('#fairnessToggle').addEventListener('change', runCheck);
  $('#checkBtn').addEventListener('click', runCheck);
  document.querySelectorAll('.reduce-controls button').forEach((btn) => {
    btn.addEventListener('click', () => runReduce(btn.dataset.kind));
  });
  $('#refreshRecords').addEventListener('click', loadRecords);
  $('#clearRecords').addEventListener('click', clearRecords);
  $('#exportRecords').addEventListener('click', exportRecords);
  $('#importRecords').addEventListener('click', importRecords);
}

async function runCheck() {
  const entry = Number($('#entrySelect').value);
  const fairness = $('#fairnessToggle').checked;
  state.check = await getJSON(`/api/check?entry=${entry}&fairness=${fairness}`);
  state.reduction = null;
  $('#candidates').innerHTML = '';
  renderCheck(state.check);
  await loadRecords();
}

async function runReduce(kind) {
  const entry = Number($('#entrySelect').value);
  const fairness = $('#fairnessToggle').checked;
  state.reduction = await getJSON(`/api/reduce?kind=${kind}&entry=${entry}&fairness=${fairness}`);
  renderReduction(state.reduction);
  await loadRecords();
}

function renderCheck(data) {
  renderLasso(data.lasso.stateIds, data.lasso.entryIndex, null);
  renderVerdict(data);
  renderSteps(data.steps);
  renderCoverage(data);
}

function verdictClass(verdict, replayed) {
  if (!replayed) return 'bad';
  if (verdict === 'VIOLATION_REPRODUCED') return 'warn';
  return 'ok';
}

function verdictTitle(verdict) {
  return {
    VIOLATION_REPRODUCED: '违例重现',
    REPLAY_FAILURE: '重放失败',
    ACCEPTANCE_NOT_INFINITE: '接受集未无限命中',
    FAIRNESS_VIOLATED: '公平性下不成立',
  }[verdict] || verdict;
}

function renderVerdict(data) {
  const box = $('#verdictBox');
  box.className = `verdict ${verdictClass(data.verdict, data.replayed)}`;
  const fairText = data.fairnessEnabled ? '启用公平性' : '不启用公平性';
  box.innerHTML = `<span class="title">[${verdictTitle(data.verdict)}]</span>`
    + `<span>${escapeHtml(data.explanation)}</span>`
    + `<div class="muted" style="margin-top:6px">假设：${fairText} · 回边见证：${data.lasso.backEdgeTransitionId || '—'}`
    + ` · 记录循环迭代数：${data.lasso.loopIterations} · 解释长度：${data.explanationLength}</div>`;
}

function renderSteps(steps) {
  const tbody = $('#stepsTable tbody');
  tbody.innerHTML = '';
  for (const step of steps) {
    const tr = document.createElement('tr');
    if (!step.legal) tr.classList.add('illegal');
    const idx = step.backEdge ? '回边' : String(step.index);
    const witness = step.witnessTransitionId
      ? `${step.witnessTransitionId}`
      : (step.guardStatus === 'NO_EDGE' ? '无此转移' : '无启用转移');
    const guard = step.guard || '—';
    tr.innerHTML = `<td>${idx}</td><td>${step.sourceStateId}</td><td>${step.targetStateId || '—'}</td>`
      + `<td>${witness}</td><td><code>${escapeHtml(guard)}</code></td>`
      + `<td><span class="pill ${pillClass(step.guardStatus)}">${step.guardStatus}</span></td>`;
    tr.title = step.detail;
    tbody.appendChild(tr);
  }
}

function pillClass(status) {
  if (status === 'TRUE') return 'true';
  if (status === 'NO_EDGE' || status === 'FALSE') return 'false';
  if (status === 'TRAILING_NOTE') return 'loopnote';
  return 'unknown';
}

function renderCoverage(data) {
  const wrap = $('#coverageTables');
  const rows = (covs) => covs.map((c) => `
    <div class="cov-card">
      <h3>${escapeHtml(c.name)}</h3>
      <div>状态：<span class="status-${c.status}">${statusText(c.status)}</span></div>
      <div class="kv"><b>循环命中（无限次）：</b>${c.loopHits.join(', ') || '—'}</div>
      <div class="kv"><b>仅前缀命中：</b>${c.prefixHits.join(', ') || '—'}</div>
    </div>`).join('');
  wrap.innerHTML = `<div class="cov-grid">${rows(data.acceptanceCoverage)}${rows(data.fairnessCoverage)}</div>`;
}

function statusText(status) {
  return {
    INFINITELY_OFTEN: '循环上无限次满足',
    PREFIX_ONLY: '仅前缀出现（不算无限次）',
    NEVER: '从不出现',
  }[status] || status;
}

function renderLasso(ids, entryIndex, highlightDeleted) {
  const acceptance = new Set(state.model.acceptanceSets.flatMap((s) => s.memberStateIds));
  const fairness = new Set(state.model.fairnessSets.flatMap((s) => s.memberStateIds));
  const width = Math.max(760, ids.length * 118);
  const height = 300;
  const yPrefix = 90;
  const yLoop = 220;
  const xs = ids.map((_, i) => 50 + i * 110);
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">`);
  parts.push('<defs><marker id="arrow" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">'
    + '<path d="M0,0 L8,3 L0,6 Z" fill="#8a94a6"/></marker>'
    + '<marker id="arrowBack" markerWidth="9" markerHeight="9" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">'
    + '<path d="M0,0 L8,3 L0,6 Z" fill="#d9822b"/></marker></defs>');

  for (let i = 0; i < ids.length - 1; i++) {
    const y1 = i < entryIndex ? yPrefix : yLoop;
    const y2 = (i + 1) < entryIndex ? yPrefix : yLoop;
    if (i + 1 === entryIndex) {
      parts.push(line(xs[i] + 22, y1, xs[i + 1] - 24, y2, '#8a94a6', 'arrow'));
      parts.push(text(xs[i + 1], y2 - 52, '进入循环', '#8a4fff', 11));
    } else {
      parts.push(line(xs[i] + 22, y1, xs[i + 1] - 24, y2, '#b8c0cf', 'arrow'));
    }
  }
  const lastX = xs[ids.length - 1];
  const entryX = xs[entryIndex];
  parts.push(`<path d="M ${lastX} ${yLoop - 26} C ${lastX - 30} ${yLoop - 120}, ${entryX + 30} ${yLoop - 120}, ${entryX} ${yLoop - 26}"`
    + ` fill="none" stroke="#d9822b" stroke-width="2" stroke-dasharray="7 5" marker-end="url(#arrowBack)"/>`);
  parts.push(text((lastX + entryX) / 2, yLoop - 112, '回边：必须存在模型转移见证', '#d9822b', 12));

  ids.forEach((id, i) => {
    const inLoop = i >= entryIndex;
    const y = inLoop ? yLoop : yPrefix;
    const x = xs[i];
    let fill = inLoop ? '#e7efff' : '#f1f3f7';
    let stroke = inLoop ? '#2f6fed' : '#9aa6b8';
    if (i === entryIndex) {
      fill = '#f1eaff';
      stroke = '#8a4fff';
    }
    const deleted = highlightDeleted && highlightDeleted.has(id);
    if (deleted) {
      fill = '#fdecec';
      stroke = '#d64545';
    }
    parts.push(`<circle cx="${x}" cy="${y}" r="24" fill="${fill}" stroke="${stroke}" stroke-width="2"/>`);
    parts.push(text(x, y - 34, inLoop ? '循环' : '前缀', inLoop ? '#2f6fed' : '#6b7686', 10));
    parts.push(`<text x="${x}" y="${y + 5}" text-anchor="middle" font-size="13" font-weight="700">${id}</text>`);
    if (acceptance.has(id)) {
      parts.push(`<circle cx="${x + 17}" cy="${y - 17}" r="7" fill="#1c9b5c"/><text x="${x + 17}" y="${y - 13}" text-anchor="middle" font-size="9" fill="#fff">A</text>`);
    }
    if (fairness.has(id)) {
      parts.push(`<circle cx="${x - 17}" cy="${y - 17}" r="7" fill="#e0a800"/><text x="${x - 17}" y="${y - 13}" text-anchor="middle" font-size="9" fill="#fff">F</text>`);
    }
    parts.push(text(x, y + 42, `idx ${i}`, '#6b7686', 10));
  });
  parts.push('</svg>');
  $('#lassoSvg').innerHTML = parts.join('');
}

function line(x1, y1, x2, y2, color, marker) {
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="1.6" marker-end="url(#${marker})"/>`;
}

function text(x, y, value, color, size) {
  return `<text x="${x}" y="${y}" text-anchor="middle" font-size="${size}" fill="${color}">${escapeHtml(value)}</text>`;
}

function renderValues() {
  const vars = state.model.variables.map((v) => v.name);
  const table = $('#valuesTable');
  table.querySelector('thead').innerHTML = '<tr><th>状态</th>' + vars.map((v) => `<th>${v}</th>`).join('') + '</tr>';
  const tbody = table.querySelector('tbody');
  tbody.innerHTML = '';
  for (const s of state.model.states) {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td><b>${s.id}</b> <span class="muted">${escapeHtml(s.label)}</span></td>`
      + vars.map((v) => `<td>${s.values[v] === undefined ? '—' : s.values[v]}</td>`).join('');
    tbody.appendChild(tr);
  }
}

function renderReduction(data) {
  const wrap = $('#candidates');
  const kindName = { STATE: '最少状态', VARIABLE: '最少变量', ITERATION: '最少循环迭代' }[data.kind.kind || data.kind];
  if (!data.candidates.length) {
    wrap.innerHTML = `<div class="candidate-card"><h3>${kindName}</h3><p>当前入口/假设下没有可缩减空间（例如只记录了一轮循环时无法折叠迭代）。</p></div>`;
    return;
  }
  wrap.innerHTML = data.candidates.map((c, i) => {
    const r = c.replay;
    const deleted = JSON.stringify(r.deletedElements, null, 2);
    const kept = (r.lasso.keptVariables || []).join(', ') || '（无）';
    const shortest = c.shortestExplanation ? '<span class="badge gold">最短解释</span>' : '';
    return `<div class="candidate-card ${c.shortestExplanation ? 'shortest' : ''}" data-candidate="${i}" role="button" tabindex="0">
      <h3><span>${kindName}候选 ${i + 1}</span><span class="badge">分数 ${c.score}</span>${shortest}</h3>
      <div class="kv"><b>重放：</b>${verdictTitle(r.verdict)} · 重现违例=${r.violationReproduced}</div>
      <div class="kv"><b>状态数：</b>${r.lasso.stateIds.length} · <b>迭代数：</b>${r.lasso.loopIterations} · <b>保留变量：</b>${kept}</div>
      <div class="kv"><b>回边见证：</b>${r.lasso.backEdgeTransitionId}</div>
      <div class="kv">${escapeHtml(r.explanation)}</div>
      <pre class="deleted">${escapeHtml(deleted)}</pre>
    </div>`;
  }).join('');
  wrap.querySelectorAll('.candidate-card').forEach((card) => {
    card.addEventListener('click', () => {
      const chosen = data.candidates[Number(card.dataset.candidate)].replay;
      renderVerdict(chosen);
      renderSteps(chosen.steps);
      renderCoverage(chosen);
      renderLasso(chosen.lasso.stateIds, chosen.lasso.entryIndex, null);
    });
  });
}

async function loadRecords() {
  const rows = await getJSON('/api/records');
  const tbody = $('#recordsTable tbody');
  tbody.innerHTML = rows.map((r) => `<tr>
    <td>${r.id}</td><td>${r.createdAt}</td><td>${r.action}</td><td>${r.entryIndex}</td>
    <td>${r.fairnessEnabled ? '启用' : '关闭'}</td><td>${r.reductionKind || '—'}</td>
    <td>${r.verdict}</td><td>${r.violationReproduced ? '是' : '否'}</td><td>${r.source}</td>
  </tr>`).join('');
}

async function clearRecords() {
  if (!confirm('确定清空 SQLite 中的全部运行记录？')) return;
  await fetch('/api/records', { method: 'DELETE' });
  await loadRecords();
}

async function exportRecords() {
  const data = await getJSON('/api/records/export');
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `lasso-run-records-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

async function importRecords() {
  const file = $('#importFile').files[0];
  if (!file) {
    $('#importSummary').textContent = '请先选择导出的 JSON 文件。';
    return;
  }
  const text = await file.text();
  const summary = await postJSON('/api/records/import', text);
  $('#importSummary').textContent = `导入 ${summary.imported} 条，复核一致 ${summary.verified} 条，不一致 ${summary.mismatched} 条。`;
  await loadRecords();
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[ch]));
}

init().catch((err) => {
  $('#verdictBox').className = 'verdict bad';
  $('#verdictBox').textContent = `初始化失败：${err.message}`;
});
