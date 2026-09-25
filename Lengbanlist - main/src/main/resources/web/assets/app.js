/* Lengbanlist 管理面板 · 前端逻辑
   无构建步骤、无外部依赖，全部事件走委托，接口契约与拆分前保持一致。 */
(function () {
  'use strict';

  var BASE = '';
  var TOKEN_KEY = 'lengbanlist_token';
  var THEME_KEY = 'lengbanlist_theme';
  var EULA_KEY = 'lengbanlist_eula';
  var PAGE_SIZE = 5;

  var token = '';
  try { token = localStorage.getItem(TOKEN_KEY) || ''; } catch (e) { token = ''; }
  var currentUser = '';
  var authed = false;

  var refreshTab = 'dashboard';
  var refreshTimer = null;
  var banListTimer = null;
  var banListCountdownVal = 10;
  var pageState = { bans: 1, ipbans: 1, mutes: 1, reports: 1 };
  var ctxTarget = '';
  var lastFocus = null;

  var TABS = {
    dashboard: { title: '概览', desc: '服务器运行状态与实时数据' },
    players: { title: '玩家查询', desc: '按玩家名或 IP 查询关联账号与 IP 记录' },
    ban: { title: '封禁管理', desc: '执行封禁与解封操作' },
    banlist: { title: '封禁名单', desc: '当前生效的玩家与 IP 封禁记录' },
    mutelist: { title: '禁言管理', desc: '执行禁言、解除禁言，并查看禁言名单' },
    reports: { title: '举报管理', desc: '处理玩家提交的举报' },
    history: { title: '处罚历史', desc: '按玩家查询封禁 / 禁言 / 警告记录' },
    audit: { title: '审计日志', desc: '管理操作的完整审计流水' },
    actions: { title: '快捷操作', desc: '常用维护动作' },
    settings: { title: '面板设置', desc: '背景壁纸与功能按钮显隐' }
  };

  var BTN_LABELS = {
    ban: '封禁操作', unban: '解封操作', mute: '禁言操作', unmute: '解除禁言',
    warn: '警告操作', report: '举报管理', audit: '审计日志', player: '玩家查询',
    ip: 'IP 查询', history: '处罚历史', alts: '小号查询', broadcast: '全服广播'
  };

  var HIDDEN_BUTTON_TABS = {
    ban: 'ban', unban: 'ban', warn: 'ban',
    mute: 'mutelist', unmute: 'mutelist',
    report: 'reports', audit: 'audit',
    player: 'players', alts: 'players',
    history: 'history', ip: 'banlist',
    broadcast: 'actions'
  };

  var KPI_CARDS = [
    { k: 'online_players', label: '在线人数', tab: 'players', icon: 'ic-players' },
    { k: 'total_bans', label: '总封禁', tab: 'banlist', icon: 'ic-ban' },
    { k: 'active_bans', label: '活跃封禁', tab: 'banlist', icon: 'ic-banlist' },
    { k: 'ip_bans', label: 'IP封禁', tab: 'banlist', icon: 'ic-ban' },
    { k: 'mutes', label: '禁言', tab: 'mutelist', icon: 'ic-mute' },
    { k: 'pending_reports', label: '待处理举报', tab: 'reports', icon: 'ic-reports' }
  ];

  /* ================= 基础工具 ================= */

  function $(id) { return document.getElementById(id); }

  function esc(value) {
    if (value === null || value === undefined || value === '') return '';
    return String(value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  function setText(el, text) { if (el) el.textContent = text === null || text === undefined ? '' : String(text); }

  function setHidden(el, on) { if (el) el.classList.toggle('hidden', !!on); }

  function setInert(el, on) {
    if (!el) return;
    if (on) { el.setAttribute('inert', ''); el.setAttribute('aria-hidden', 'true'); }
    else { el.removeAttribute('inert'); el.removeAttribute('aria-hidden'); }
  }

  function svgIcon(name, cls) {
    return '<svg class="icon' + (cls ? ' ' + cls : '') + '" aria-hidden="true"><use href="#' + name + '"/></svg>';
  }

  function skeleton(cols, rows) {
    var html = '<div class="skeleton" aria-hidden="true">';
    for (var r = 0; r < rows; r++) {
      html += '<div class="sk-row">';
      for (var c = 0; c < cols; c++) html += '<span class="sk-bar"></span>';
      html += '</div>';
    }
    return html + '</div>';
  }

  function panelLoading(id, cols, rows) {
    var el = $(id);
    if (!el) return;
    el.setAttribute('aria-busy', 'true');
    el.innerHTML = skeleton(cols, rows);
  }

  function panelRender(id, html) {
    var el = $(id);
    if (!el) return;
    el.removeAttribute('aria-busy');
    el.innerHTML = html;
  }

  function emptyState(title, hint) {
    return '<div class="empty">' +
      '<span class="empty-icon">' + svgIcon('ic-empty') + '</span>' +
      '<p class="empty-title">' + esc(title) + '</p>' +
      (hint ? '<p class="empty-hint">' + esc(hint) + '</p>' : '') +
      '</div>';
  }

  function errorState(message, retryAction) {
    return '<div class="empty is-error">' +
      '<span class="empty-icon">' + svgIcon('ic-alert') + '</span>' +
      '<p class="empty-title">' + esc(message || '加载失败') + '</p>' +
      '<p class="empty-hint">数据没能取回来，稍后再试或点击重试</p>' +
      (retryAction ? '<button class="btn btn-ghost btn-sm" type="button" data-action="' + retryAction + '">' + svgIcon('ic-refresh') + '重试</button>' : '') +
      '</div>';
  }

  function tableWrap(headers, body) {
    return '<div class="table-wrap"><table class="table"><thead><tr>' +
      headers.map(function (h) { return '<th>' + esc(h) + '</th>'; }).join('') +
      '</tr></thead><tbody>' + body + '</tbody></table></div>';
  }

  function cell(value, cls) {
    return '<td' + (cls ? ' class="' + cls + '"' : '') + '>' + esc(value) + '</td>';
  }

  function badge(text, tone) {
    return '<span class="badge badge-' + tone + '">' + esc(text) + '</span>';
  }

  function ctxRow(kind, value, cells) {
    return '<tr class="row-ctx" data-ctx-kind="' + esc(kind) + '" data-ctx-value="' + esc(value) + '">' + cells + '</tr>';
  }

  function moreCell(value) {
    return '<td class="ta-right"><button class="row-more" type="button" data-action="row-menu" aria-label="' + esc(value) + ' 的更多操作">' + svgIcon('ic-more') + '</button></td>';
  }

  function backToLogin() {
    setInert($('mainApp'), true);
    setInert($('loginOverlay'), false);
    setHidden($('eulaOverlay'), true);
    setHidden($('loginOverlay'), false);
  }

  function enterApp() {
    authed = true;
    setText($('headerInfo'), currentUser || '已登录');
    setHidden($('eulaOverlay'), true);
    setHidden($('loginOverlay'), true);
    setInert($('loginOverlay'), true);
    setInert($('mainApp'), false);
    // 登录后立即拉取背景与按钮显隐配置，跟旧版一致
    loadThemeSettings();
  }

  /* ================= 接口层 ================= */

  async function api(path, opts) {
    opts = opts || {};
    var headers = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    try {
      var res = await fetch(BASE + path, Object.assign({}, opts, { headers: headers }));
      if (res.status === 429) {
        return { ok: false, data: { error: '请求过于频繁，请稍后再试' } };
      }
      var text = await res.text();
      try { return { ok: res.ok, data: JSON.parse(text) }; } catch (e) { return { ok: false, data: text }; }
    } catch (e) {
      return { ok: false, data: { error: '网络错误' } };
    }
  }

  function errText(res, fallback) {
    if (res && res.data && res.data.error) return res.data.error;
    return fallback;
  }

  /* ================= 轻提示 ================= */

  function removeToast(el) {
    if (!el || !el.parentNode) return;
    el.classList.add('is-out');
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 220);
  }

  function toast(message, tone) {
    var host = $('toastHost');
    if (!host) return;
    tone = tone || 'info';
    var icon = tone === 'ok' ? 'ic-check' : tone === 'err' ? 'ic-alert' : tone === 'warn' ? 'ic-alert' : 'ic-info';
    var el = document.createElement('div');
    el.className = 'toast toast-' + tone;
    el.innerHTML = svgIcon(icon, 'toast-icon') +
      '<span class="toast-text"></span>' +
      '<button class="toast-close" type="button" aria-label="关闭提示">' + svgIcon('ic-close') + '</button>' +
      '<i class="toast-bar"></i>';
    setText(el.querySelector('.toast-text'), message);
    host.appendChild(el);
    var timer = setTimeout(function () { removeToast(el); }, 4000);
    el.querySelector('.toast-close').addEventListener('click', function () {
      clearTimeout(timer);
      removeToast(el);
    });
    while (host.children.length > 4) removeToast(host.firstChild);
  }

  function toastOk(message) { toast(message, 'ok'); }
  function toastErr(message) { toast(message, 'err'); }
  function toastWarn(message) { toast(message, 'warn'); }

  /* ================= 对话框（危险操作二次确认 / 原因输入） ================= */

  function openDialog(cfg) {
    return new Promise(function (resolve) {
      var host = $('dialogHost');
      if (!host) { resolve(null); return; }
      var tone = cfg.tone || 'info';
      var markIcon = tone === 'danger' ? 'ic-alert' : tone === 'warn' ? 'ic-alert' : 'ic-info';
      var detail = '';
      if (cfg.detail && cfg.detail.length) {
        detail = '<dl class="dialog-detail">' + cfg.detail.map(function (row) {
          return '<dt>' + esc(row[0]) + '</dt><dd>' + esc(row[1]) + '</dd>';
        }).join('') + '</dl>';
      }
      var inputHtml = cfg.input
        ? '<div class="field"><label class="field-label" for="dialogInput">' + esc(cfg.input.label) + '</label>' +
          '<input class="input" type="text" id="dialogInput" autocomplete="off" spellcheck="false"></div>'
        : '';
      host.innerHTML = '<div class="dialog-veil" data-dialog="veil"></div>' +
        '<div class="dialog dialog-' + tone + '" role="dialog" aria-modal="true" aria-labelledby="dialogTitle">' +
        '<div class="dialog-head"><span class="dialog-mark">' + svgIcon(markIcon) + '</span>' +
        '<div><h2 class="dialog-title" id="dialogTitle">' + esc(cfg.title) + '</h2>' +
        (cfg.sub ? '<p class="dialog-sub">' + esc(cfg.sub) + '</p>' : '') + '</div></div>' +
        '<div class="dialog-body">' + detail + inputHtml + '</div>' +
        '<div class="dialog-foot">' +
        '<button class="btn btn-ghost" type="button" data-dialog="cancel">取消</button>' +
        '<button class="btn ' + (tone === 'danger' ? 'btn-danger' : 'btn-primary') + '" type="button" data-dialog="ok">' + esc(cfg.confirmText || '确认') + '</button>' +
        '</div></div>';
      host.classList.remove('hidden');

      var input = $('dialogInput');
      if (input) input.value = cfg.input.value || '';
      var okBtn = host.querySelector('[data-dialog="ok"]');
      var cancelBtn = host.querySelector('[data-dialog="cancel"]');
      var closed = false;
      lastFocus = document.activeElement;

      function close(result) {
        if (closed) return;
        closed = true;
        host.classList.add('hidden');
        host.innerHTML = '';
        document.removeEventListener('keydown', onKey);
        if (lastFocus && lastFocus.focus) { try { lastFocus.focus(); } catch (e) { /* 元素可能已被重绘 */ } }
        resolve(result);
      }
      function onKey(e) {
        if (e.key === 'Escape') { e.preventDefault(); close(null); }
      }
      document.addEventListener('keydown', onKey);
      host.querySelector('[data-dialog="veil"]').addEventListener('click', function () { close(null); });
      cancelBtn.addEventListener('click', function () { close(null); });
      okBtn.addEventListener('click', function () { close(input ? input.value.trim() : true); });
      host.querySelector('.dialog').addEventListener('keydown', function (e) {
        if (e.key === 'Enter' && e.target !== okBtn && e.target !== cancelBtn) {
          e.preventDefault();
          okBtn.click();
        }
      });
      if (input) { input.focus(); input.select(); } else { cancelBtn.focus(); }
    });
  }

  /* ================= 主题 ================= */

  function paintThemeIcon(isLight) {
    var use = $('themeIconUse');
    if (use) {
      var href = isLight ? '#ic-moon' : '#ic-sun';
      use.setAttribute('href', href);
      use.setAttributeNS('http://www.w3.org/1999/xlink', 'xlink:href', href);
    }
    var btn = $('themeToggle');
    if (btn) {
      var label = isLight ? '当前浅色主题，点击切换为深色' : '当前深色主题，点击切换为浅色';
      btn.setAttribute('title', label);
      btn.setAttribute('aria-label', label);
    }
  }

  function initTheme() {
    var saved = null;
    try { saved = localStorage.getItem(THEME_KEY); } catch (e) { saved = null; }
    var isLight = saved === 'light';
    document.documentElement.classList.toggle('light', isLight);
    paintThemeIcon(isLight);
  }

  function toggleTheme() {
    var isLight = !document.documentElement.classList.contains('light');
    document.documentElement.classList.toggle('light', isLight);
    try { localStorage.setItem(THEME_KEY, isLight ? 'light' : 'dark'); } catch (e) { /* 隐私模式下忽略 */ }
    paintThemeIcon(isLight);
  }

  /* ================= 鉴权 ================= */

  function jwtDecode(t) {
    try {
      var part = String(t).split('.')[1] || '';
      part = part.replace(/-/g, '+').replace(/_/g, '/');
      while (part.length % 4) part += '=';
      var bytes = atob(part);
      var utf8 = '';
      for (var i = 0; i < bytes.length; i++) {
        utf8 += '%' + ('00' + bytes.charCodeAt(i).toString(16)).slice(-2);
      }
      return JSON.parse(decodeURIComponent(utf8));
    } catch (e) { return {}; }
  }

  function showLoginError(message) {
    var el = $('loginError');
    if (!el) return;
    el.textContent = message;
    setHidden(el, false);
  }

  function setBusy(btn, busy) {
    if (!btn) return;
    btn.disabled = !!busy;
    btn.classList.toggle('is-busy', !!busy);
  }

  async function doLogin() {
    var submit = $('loginSubmit');
    if (submit && submit.disabled) return;
    var user = $('loginUser').value.trim();
    var pass = $('loginPass').value;
    if (!user || !pass) { showLoginError('请输入用户名和密码'); return; }
    setHidden($('loginError'), true);
    setBusy(submit, true);
    var res = await api('/api/login', { method: 'POST', body: JSON.stringify({ username: user, password: pass }) });
    setBusy(submit, false);
    if (res.ok && res.data && res.data.token) {
      token = res.data.token;
      currentUser = res.data.username || user;
      try { localStorage.setItem(TOKEN_KEY, token); } catch (e) { /* 忽略 */ }
      $('loginPass').value = '';
      enterApp();
      switchTab('dashboard');
      toast('欢迎回来，' + currentUser, 'ok');
    } else {
      showLoginError(errText(res, '登录失败'));
    }
  }

  function logout() {
    token = '';
    currentUser = '';
    authed = false;
    try { localStorage.removeItem(TOKEN_KEY); } catch (e) { /* 忽略 */ }
    stopAutoRefresh();
    stopBanListRefresh();
    setText($('headerInfo'), '-');
    if ($('loginUser')) $('loginUser').value = '';
    if ($('loginPass')) $('loginPass').value = '';
    setHidden($('loginError'), true);
    backToLogin();
    toast('已退出登录', 'info');
  }

  function acceptEula() {
    try { localStorage.setItem(EULA_KEY, 'accepted'); } catch (e) { /* 忽略 */ }
    setHidden($('eulaOverlay'), true);
    if (authed) {
      // 从面板底部重新查看协议：直接回到面板，不要闪一下登录页
      setInert($('mainApp'), false);
      if (lastFocus && lastFocus.focus) { try { lastFocus.focus(); } catch (e) { /* 元素可能已被重绘 */ } }
      return;
    }
    backToLogin();
    if (token) {
      api('/api/stats').then(function (res) {
        if (res.ok) {
          enterApp();
          switchTab('dashboard');
        }
      });
    }
  }

  function openEula() {
    lastFocus = document.activeElement;
    setInert($('mainApp'), true);
    setHidden($('eulaOverlay'), false);
    var btn = $('eulaOverlay') && $('eulaOverlay').querySelector('[data-action="accept-eula"]');
    if (btn) btn.focus();
  }

  function bootstrap() {
    initTheme();
    var accepted = false;
    try { accepted = !!localStorage.getItem(EULA_KEY); } catch (e) { accepted = true; }
    if (!accepted) {
      setInert($('mainApp'), true);
      setInert($('loginOverlay'), true);
      setHidden($('loginOverlay'), false);
      setHidden($('eulaOverlay'), false);
    } else {
      backToLogin();
    }
    if (token) {
      currentUser = jwtDecode(token).sub || '';
      api('/api/stats').then(function (res) {
        if (res.ok) {
          enterApp();
          switchTab('dashboard');
        } else {
          token = '';
          try { localStorage.removeItem(TOKEN_KEY); } catch (e) { /* 忽略 */ }
        }
      });
    }
  }

  /* ================= 导航 ================= */

  function switchTab(name) {
    var cfg = TABS[name];
    if (!cfg) return;
    refreshTab = name;
    var bar = $('tabBar');
    if (bar) {
      Array.prototype.forEach.call(bar.querySelectorAll('.tab'), function (t) {
        var on = t.getAttribute('data-tab') === name;
        t.classList.toggle('is-active', on);
        t.setAttribute('aria-selected', on ? 'true' : 'false');
      });
    }
    Array.prototype.forEach.call(document.querySelectorAll('.tab-content'), function (p) {
      p.classList.remove('is-active');
    });
    var panel = $('tab' + name.charAt(0).toUpperCase() + name.slice(1));
    if (panel) panel.classList.add('is-active');
    setText($('pageTitle'), cfg.title);
    setText($('pageDesc'), cfg.desc);

    stopAutoRefresh();
    stopBanListRefresh();
    if (name === 'dashboard') { loadDashboard(); startAutoRefresh(); }
    else if (name === 'banlist') { loadBanLists(); startBanListRefresh(); }
    else if (name === 'mutelist') { loadMuteList(); }
    else if (name === 'reports') { loadReports(); }
    else if (name === 'audit') { loadAudit(); }
    else if (name === 'settings') { loadThemeSettings(); }
  }

  /* ================= 概览 ================= */

  function dbTone(status) {
    var v = String(status || '');
    if (!v || v === '-') return '';
    if (/未|失败|错误|断开|error|fail|down/i.test(v)) return 'danger';
    if (/已连接|正常|成功|ok|up|connected|healthy/i.test(v)) return 'ok';
    return '';
  }

  function renderKpis(s) {
    var host = $('statsCards');
    if (!host) return;
    host.innerHTML = KPI_CARDS.map(function (c) {
      var value = c.k === 'online_players'
        ? esc(String(s.online_players || 0)) + '<span class="kpi-unit">/' + esc(String(s.max_players || 0)) + '</span>'
        : esc(String(s[c.k] || 0));
      return '<button class="kpi" type="button" data-action="tab" data-tab="' + c.tab + '">' +
        '<span class="kpi-icon">' + svgIcon(c.icon) + '</span>' +
        '<span class="kpi-value">' + value + '</span>' +
        '<span class="kpi-label">' + esc(c.label) + '</span>' +
        '</button>';
    }).join('');
    var pending = Number(s.pending_reports || 0);
    var badgeEl = $('reportsBadge');
    if (badgeEl) {
      badgeEl.textContent = String(pending);
      setHidden(badgeEl, !pending);
    }
  }

  function renderRecentBans(bans) {
    var head = '<h3 class="section-title">最近封禁记录</h3>';
    if (!bans.length) {
      return head + '<p class="hint">暂无封禁记录</p>';
    }
    var rows = bans.map(function (b) {
      return '<tr>' + cell(b.target, 'cell-strong') + cell(b.staff) + cell(b.reason) +
        cell(b.end_time, 'cell-mono') + cell(b.remaining, 'cell-mono') +
        '<td>' + badge(b.active ? '封禁中' : '已失效', b.active ? 'danger' : 'ok') + '</td></tr>';
    }).join('');
    return head + tableWrap(['玩家', '处理人', '原因', '到期时间', '剩余', '状态'], rows);
  }

  function renderStatsDetail(s) {
    var el = $('statsDetail');
    if (!el) return;
    var fields = [
      { v: s.plugin_version || '-', l: '插件版本' },
      { v: s.database_status || '-', l: '数据库状态', tone: dbTone(s.database_status) },
      { v: s.database_type || '-', l: '数据库类型' },
      { v: (s.online_players || 0) + ' / ' + (s.max_players || 0), l: '在线人数' },
      { v: s.total_bans || 0, l: '总封禁数' },
      { v: s.active_bans || 0, l: '活跃封禁' },
      { v: s.ip_bans || 0, l: 'IP封禁' },
      { v: s.pending_reports || 0, l: '待处理举报' }
    ];
    el.removeAttribute('aria-busy');
    var html = '<div class="tile-grid">' + fields.map(function (f, i) {
      var cls = 'tile' + (i >= 3 ? ' is-mono' : '') + (f.tone ? ' is-' + f.tone : '');
      return '<div class="' + cls + '"><span class="tile-value">' + esc(String(f.v)) + '</span>' +
        '<span class="tile-label">' + esc(f.l) + '</span></div>';
    }).join('') + '</div>';
    el.innerHTML = html + renderRecentBans(s.recent_bans || []);
  }

  function updateStatsUI(s) {
    renderKpis(s);
    renderStatsDetail(s);
    setText($('lastUpdate'), new Date().toLocaleTimeString());
    loadOnlinePlayers();
  }

  async function loadDashboard() {
    if (!$('statsDetail').innerHTML.trim()) panelLoading('statsDetail', 4, 3);
    var res = await api('/api/stats');
    if (!res.ok) {
      if (!$('statsDetail').innerHTML.trim() || $('statsDetail').querySelector('.skeleton')) {
        panelRender('statsDetail', errorState(errText(res, '加载失败'), 'retry-stats'));
      }
      return;
    }
    updateStatsUI(res.data);
  }

  function startAutoRefresh() {
    stopAutoRefresh();
    refreshTimer = setInterval(function () {
      if (refreshTab === 'dashboard') {
        api('/api/stats').then(function (res) {
          if (res.ok) updateStatsUI(res.data);
        });
      }
    }, 10000);
  }

  function stopAutoRefresh() {
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
  }

  async function refreshKpis() {
    var res = await api('/api/stats');
    if (!res.ok) return;
    if (refreshTab === 'dashboard') updateStatsUI(res.data);
    else renderKpis(res.data);
  }

  async function loadOnlinePlayers() {
    var el = $('onlinePlayersResult');
    if (!el) return;
    panelLoading('onlinePlayersResult', 3, 3);
    var res = await api('/api/online');
    if (!res.ok) { panelRender('onlinePlayersResult', errorState(errText(res, '加载失败'), 'refresh-online')); return; }
    var d = res.data || {};
    var players = d.players || [];
    if (!players.length) {
      panelRender('onlinePlayersResult', emptyState('暂无玩家在线', '服务器当前没有玩家，刷新可查看最新状态'));
      return;
    }
    var rows = players.map(function (p) {
      return '<tr>' + cell(p.name, 'cell-strong') + cell(String(p.ping), 'cell-mono') +
        '<td class="ta-right"><button class="btn btn-danger btn-sm" type="button" data-action="kick" data-player="' + esc(p.name) + '">' +
        svgIcon('ic-kick') + '踢出</button></td></tr>';
    }).join('');
    panelRender('onlinePlayersResult',
      '<div class="toolbar"><span>在线 <strong>' + esc(String(d.total)) + '</strong> 人</span></div>' +
      tableWrap(['玩家', 'Ping', '操作'], rows));
  }

  /* ================= 玩家查询 ================= */

  async function searchPlayer() {
    var q = $('playerSearchInput').value.trim();
    if (!q) { toastWarn('请输入玩家名或 IP'); return; }
    panelLoading('playerResult', 3, 3);
    var res = await api('/api/players?q=' + encodeURIComponent(q));
    if (!res.ok) { panelRender('playerResult', errorState(errText(res, '查询失败'), 'retry-search')); return; }
    var d = res.data || {};
    var html = '<div class="note note-info">查询：<strong>' + esc(d.query) + '</strong></div>';
    if (d.type === 'ip') {
      var related = d.players || [];
      html += '<h3 class="section-title">关联玩家</h3>';
      html += related.length
        ? tableWrap(['关联玩家'], related.map(function (p) { return '<tr>' + cell(p, 'cell-strong') + '</tr>'; }).join(''))
        : '<p class="hint">该 IP 暂无关联玩家</p>';
    } else {
      var alts = d.associated_players || [];
      if (alts.length > 0) {
        html += '<div class="note note-warn">该玩家与其他账号共享 IP</div>' +
          '<h3 class="section-title">关联账号</h3><div class="chips">' +
          alts.map(function (p) { return '<span class="tag">' + esc(p) + '</span>'; }).join('') + '</div>';
      } else {
        html += '<div class="note note-ok">无关联账号</div>';
      }
      var ips = d.ips || [];
      html += '<h3 class="section-title">IP 历史记录</h3>';
      html += ips.length
        ? tableWrap(['IP 地址', '首次使用', '最近使用'], ips.map(function (item) {
          return '<tr>' + cell(item.ip, 'cell-mono cell-strong') + cell(item.first_seen, 'cell-mono') + cell(item.last_seen, 'cell-mono') + '</tr>';
        }).join(''))
        : '<p class="hint">暂无 IP 记录</p>';
    }
    panelRender('playerResult', html);
  }

  /* ================= 封禁 / 解封 ================= */

  function onDurationChange() {
    var sel = $('banDurationSelect');
    if (!sel) return;
    setHidden($('banDurationCustom'), sel.value !== '__custom__');
  }

  function getDuration() {
    var sel = $('banDurationSelect');
    if (sel.value === '__custom__') {
      return $('banDurationInput').value.trim() || '7d';
    }
    return sel.value;
  }

  async function doBan() {
    var target = $('banTarget').value.trim();
    if (!target) { toastWarn('请输入目标'); return; }
    var duration = getDuration();
    var reason = $('banReason').value.trim() || '管理员操作';
    var ok = await openDialog({
      title: '确认封禁',
      tone: 'danger',
      sub: '封禁后该目标将立即无法进入服务器',
      detail: [['目标', target], ['时长', duration], ['原因', reason]],
      confirmText: '执行封禁'
    });
    if (!ok) return;
    var res = await api('/api/ban', { method: 'POST', body: JSON.stringify({ target: target, duration: duration, reason: reason }) });
    if (res.ok) {
      toastOk((res.data && res.data.message) || '封禁成功');
      $('banTarget').value = '';
      loadBanLists();
      refreshKpis();
    } else {
      toastErr(errText(res, '封禁失败'));
    }
  }

  async function doUnban() {
    var target = $('unbanTarget').value.trim();
    if (!target) { toastWarn('请输入目标'); return; }
    var ok = await openDialog({
      title: '确认解封',
      tone: 'warn',
      sub: '解封后该目标可立即重新进入服务器',
      detail: [['目标', target]],
      confirmText: '执行解封'
    });
    if (!ok) return;
    var res = await api('/api/unban', { method: 'POST', body: JSON.stringify({ target: target }) });
    if (res.ok) {
      toastOk((res.data && res.data.message) || '解封成功');
      $('unbanTarget').value = '';
      loadBanLists();
      refreshKpis();
    } else {
      toastErr(errText(res, '解封失败'));
    }
  }

  /* ================= 封禁名单 ================= */

  async function loadBanLists() {
    panelLoading('banListResult', 6, 4);
    panelLoading('ipBanListResult', 5, 3);

    var bansRes = await api('/api/bans');
    if (bansRes.ok) {
      var d = bansRes.data || {};
      var bans = d.bans || [];
      if (bans.length > 0) {
        var pageItems = paginate('bans', bans);
        var rows = pageItems.map(function (b) {
          return ctxRow('ban', b.target,
            cell(b.target, 'cell-strong') + cell(b.staff) + cell(b.reason) +
            cell(b.end_time, 'cell-mono') + cell(b.remaining, 'cell-mono') +
            '<td>' + badge(b.auto ? '自动' : '手动', b.auto ? 'warn' : 'danger') + '</td>' + moreCell(b.target));
        }).join('');
        panelRender('banListResult',
          '<div class="toolbar"><span>共 <strong>' + esc(String(d.total)) + '</strong> 条</span></div>' +
          tableWrap(['玩家', '处理人', '原因', '到期时间', '剩余', '类型', ''], rows) +
          renderPagination('bans', bans.length));
      } else {
        panelRender('banListResult', emptyState('暂无封禁记录', '服务器当前没有生效中的封禁'));
      }
    } else {
      panelRender('banListResult', errorState('加载失败', 'refresh-bans'));
    }

    var ipRes = await api('/api/ipbans');
    if (ipRes.ok) {
      var d2 = ipRes.data || {};
      var ipBans = d2.bans || [];
      if (ipBans.length > 0) {
        var pageIps = paginate('ipbans', ipBans);
        var ipRows = pageIps.map(function (b) {
          return ctxRow('ipban', b.ip,
            cell(b.ip, 'cell-mono cell-strong') + cell(b.staff) + cell(b.reason) +
            cell(b.end_time, 'cell-mono') + cell(b.remaining, 'cell-mono') + moreCell(b.ip));
        }).join('');
        panelRender('ipBanListResult',
          '<div class="toolbar"><span>共 <strong>' + esc(String(d2.total)) + '</strong> 条</span></div>' +
          tableWrap(['IP', '处理人', '原因', '到期时间', '剩余', ''], ipRows) +
          renderPagination('ipbans', ipBans.length));
      } else {
        panelRender('ipBanListResult', emptyState('暂无 IP 封禁记录', '服务器当前没有生效中的 IP 封禁'));
      }
    } else {
      panelRender('ipBanListResult', errorState('加载失败', 'refresh-bans'));
    }
  }

  function startBanListRefresh() {
    stopBanListRefresh();
    banListCountdownVal = 10;
    updateBanListCountdown();
    banListTimer = setInterval(function () {
      banListCountdownVal--;
      if (banListCountdownVal <= 0) {
        banListCountdownVal = 10;
        loadBanLists();
      }
      updateBanListCountdown();
    }, 1000);
  }

  function stopBanListRefresh() {
    if (banListTimer) { clearInterval(banListTimer); banListTimer = null; }
  }

  function updateBanListCountdown() {
    setText($('banListCountdown'), banListCountdownVal);
  }

  /* ================= 禁言 ================= */

  async function loadMuteList() {
    panelLoading('muteListResult', 4, 4);
    var res = await api('/api/mutes');
    if (!res.ok) { panelRender('muteListResult', errorState('加载失败', 'refresh-mutes')); return; }
    var d = res.data || {};
    var mutes = d.mutes || [];
    if (!mutes.length) {
      panelRender('muteListResult', emptyState('暂无禁言记录', '服务器当前没有被禁言的玩家'));
      return;
    }
    var pageItems = paginate('mutes', mutes);
    var rows = pageItems.map(function (m) {
      return ctxRow('mute', m.target,
        cell(m.target, 'cell-strong') + cell(m.staff) + cell(m.reason) + cell(m.time, 'cell-mono') + moreCell(m.target));
    }).join('');
    panelRender('muteListResult',
      '<div class="toolbar"><span>共 <strong>' + esc(String(d.total)) + '</strong> 条</span></div>' +
      tableWrap(['玩家', '处理人', '原因', '禁言时间', ''], rows) +
      renderPagination('mutes', mutes.length));
  }

  async function doMute() {
    var target = $('muteTarget').value.trim();
    if (!target) { toastWarn('请输入玩家名'); return; }
    var reason = $('muteReason').value.trim() || '管理员操作';
    var res = await api('/api/mute', { method: 'POST', body: JSON.stringify({ target: target, reason: reason }) });
    if (res.ok) {
      toastOk((res.data && res.data.message) || '禁言成功');
      $('muteTarget').value = '';
      loadMuteList();
      refreshKpis();
    } else {
      toastErr(errText(res, '禁言失败'));
    }
  }

  async function doUnmute() {
    var target = $('unmuteTarget').value.trim();
    if (!target) { toastWarn('请输入玩家名'); return; }
    var res = await api('/api/unmute', { method: 'POST', body: JSON.stringify({ target: target }) });
    if (res.ok) {
      toastOk((res.data && res.data.message) || '解除禁言成功');
      $('unmuteTarget').value = '';
      loadMuteList();
      refreshKpis();
    } else {
      toastErr(errText(res, '解除禁言失败'));
    }
  }

  /* ================= 举报 ================= */

  async function loadReports() {
    panelLoading('reportsResult', 6, 4);
    var res = await api('/api/reports');
    if (!res.ok) { panelRender('reportsResult', errorState('加载失败', 'refresh-reports')); return; }
    var d = res.data || {};
    var reports = d.reports || [];
    if (!reports.length) {
      panelRender('reportsResult', emptyState('暂无待处理举报', '所有举报都已处理完毕'));
      return;
    }
    var pageItems = paginate('reports', reports);
    var rows = pageItems.map(function (r) {
      return ctxRow('report', r.id,
        cell(r.id, 'cell-mono') + cell(r.target, 'cell-strong') + cell(r.reporter) + cell(r.reason) + cell(r.timestamp, 'cell-mono') +
        '<td class="ta-right"><button class="btn btn-warn btn-sm" type="button" data-action="report-accept" data-id="' + esc(r.id) + '">受理</button>' +
        '<button class="btn btn-ghost btn-sm" type="button" data-action="report-close" data-id="' + esc(r.id) + '">关闭</button></td>');
    }).join('');
    panelRender('reportsResult',
      '<div class="toolbar"><span>共 <strong>' + esc(String(d.total)) + '</strong> 条待处理</span></div>' +
      tableWrap(['编号', '被举报人', '举报人', '原因', '时间', '操作'], rows) +
      renderPagination('reports', reports.length));
  }

  async function reportAction(id, action) {
    var isAccept = action === 'accept';
    var ok = await openDialog({
      title: isAccept ? '确认受理举报' : '确认关闭举报',
      tone: isAccept ? 'warn' : 'danger',
      sub: isAccept ? '受理后该举报将标记为已处理' : '关闭后该举报不再出现在待处理列表',
      detail: [['举报编号', id]],
      confirmText: isAccept ? '受理' : '关闭'
    });
    if (!ok) return;
    var res = await api('/api/report/action', { method: 'POST', body: JSON.stringify({ id: id, action: action }) });
    if (res.ok) {
      toastOk(isAccept ? '已受理举报 ' + id : '已关闭举报 ' + id);
      loadReports();
      refreshKpis();
    } else {
      toastErr(errText(res, '操作失败'));
    }
  }

  /* ================= 处罚历史 ================= */

  async function loadHistory() {
    var player = $('historyInput').value.trim();
    if (!player) { toastWarn('请输入玩家名'); return; }
    panelLoading('historyResult', 5, 3);
    var res = await api('/api/history?player=' + encodeURIComponent(player));
    if (!res.ok) { panelRender('historyResult', errorState(errText(res, '查询失败'), 'retry-history')); return; }
    var d = res.data || {};
    var html = '<div class="note note-info">玩家：<strong>' + esc(d.player) + '</strong></div>';

    html += '<h3 class="section-title">封禁记录</h3>';
    if (d.bans && d.bans.length) {
      html += tableWrap(['目标', '处理人', '原因', '到期', '状态'], d.bans.map(function (b) {
        return '<tr>' + cell(b.target, 'cell-strong') + cell(b.staff) + cell(b.reason) + cell(b.end_time, 'cell-mono') +
          '<td>' + badge(b.active ? '封禁中' : '已解封', b.active ? 'danger' : 'ok') + '</td></tr>';
      }).join(''));
    } else {
      html += '<p class="hint">无封禁记录</p>';
    }

    html += '<h3 class="section-title">禁言记录</h3>';
    if (d.mutes && d.mutes.length) {
      html += tableWrap(['处理人', '原因', '到期'], d.mutes.map(function (m) {
        return '<tr>' + cell(m.staff) + cell(m.reason) + cell(m.end_time, 'cell-mono') + '</tr>';
      }).join(''));
    } else {
      html += '<p class="hint">无禁言记录</p>';
    }

    html += '<h3 class="section-title">警告记录</h3>';
    if (d.warnings && d.warnings.length) {
      html += tableWrap(['处理人', '原因', '时间', '状态'], d.warnings.map(function (w) {
        return '<tr>' + cell(w.staff) + cell(w.reason) + cell(w.warn_time, 'cell-mono') +
          '<td>' + badge(w.revoked ? '已撤销' : '有效', w.revoked ? 'ok' : 'warn') + '</td></tr>';
      }).join(''));
    } else {
      html += '<p class="hint">无警告记录</p>';
    }
    panelRender('historyResult', html);
  }

  /* ================= 审计日志 ================= */

  async function loadAudit() {
    panelLoading('auditResult', 7, 5);
    var filter = $('auditFilter').value.trim();
    var url = '/api/audit?limit=50';
    if (filter) url += '&player=' + encodeURIComponent(filter);
    var res = await api(url);
    if (!res.ok) { panelRender('auditResult', errorState(errText(res, '加载失败'), 'retry-audit')); return; }
    var d = res.data || {};
    var logs = d.logs || [];
    if (!logs.length) {
      panelRender('auditResult', emptyState('暂无审计记录', filter ? '换个操作人或目标再试试' : '管理操作发生后会记录在这里'));
      return;
    }
    var rows = logs.map(function (e) {
      return '<tr>' + cell(e.timestamp, 'cell-mono') + cell(e.action, 'cell-strong') + cell(e.actor) +
        cell(e.server || '-') + cell(e.target) + cell(e.reason) +
        '<td>' + badge(e.success ? '成功' : '失败', e.success ? 'ok' : 'danger') + '</td></tr>';
    }).join('');
    panelRender('auditResult',
      '<div class="toolbar"><span>共 <strong>' + esc(String(d.total)) + '</strong> 条</span></div>' +
      tableWrap(['时间', '操作', '操作人', '服务器', '目标', '原因', '结果'], rows));
  }

  /* ================= 分页 ================= */

  function renderPagination(key, total) {
    var totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
    if (pageState[key] > totalPages) pageState[key] = totalPages;
    var prevOff = pageState[key] <= 1;
    var nextOff = pageState[key] >= totalPages;
    return '<div class="pager">' +
      '<button class="btn btn-ghost btn-sm" type="button" data-action="page" data-key="' + key + '" data-delta="-1"' + (prevOff ? ' disabled' : '') + '>' +
      svgIcon('ic-left') + '上一页</button>' +
      '<span class="pager-info">第 ' + pageState[key] + ' / ' + totalPages + ' 页</span>' +
      '<button class="btn btn-ghost btn-sm" type="button" data-action="page" data-key="' + key + '" data-delta="1"' + (nextOff ? ' disabled' : '') + '>' +
      '下一页' + svgIcon('ic-right') + '</button>' +
      '</div>';
  }

  function paginate(key, items) {
    items = items || [];
    var totalPages = Math.max(1, Math.ceil(items.length / PAGE_SIZE));
    if (pageState[key] > totalPages) pageState[key] = totalPages;
    var start = (pageState[key] - 1) * PAGE_SIZE;
    return items.slice(start, start + PAGE_SIZE);
  }

  function changePage(key, delta) {
    pageState[key] = Math.max(1, pageState[key] + delta);
    if (key === 'bans' || key === 'ipbans') loadBanLists();
    else if (key === 'mutes') loadMuteList();
    else if (key === 'reports') loadReports();
  }

  /* ================= 右键菜单 ================= */

  function hideContextMenu() {
    setHidden($('contextMenu'), true);
  }

  function showContextMenu(x, y, kind, value) {
    var menu = $('contextMenu');
    if (!menu) return;
    ctxTarget = value;
    Array.prototype.forEach.call(menu.querySelectorAll('[data-ctx]'), function (item) {
      var action = item.getAttribute('data-ctx');
      var visible = (action === 'unban' && (kind === 'ban' || kind === 'ipban')) ||
        (action === 'unmute' && kind === 'mute') ||
        ((action === 'accept-report' || action === 'close-report') && kind === 'report');
      item.classList.toggle('hidden', !visible);
    });
    menu.classList.remove('hidden');
    var width = menu.offsetWidth;
    var height = menu.offsetHeight;
    var left = Math.max(8, Math.min(x, window.innerWidth - width - 8));
    var top = Math.max(8, Math.min(y, window.innerHeight - height - 8));
    menu.style.left = left + 'px';
    menu.style.top = top + 'px';
  }

  async function contextMenuAction(action) {
    var value = ctxTarget;
    if (!value) return;
    if (action === 'unban') {
      var okUnban = await openDialog({
        title: '确认解封', tone: 'warn', sub: '解封后该目标可立即重新进入服务器',
        detail: [['目标', value]], confirmText: '执行解封'
      });
      if (!okUnban) return;
      var unbanRes = await api('/api/unban', { method: 'POST', body: JSON.stringify({ target: value }) });
      if (unbanRes.ok) { toastOk('已解封 ' + value); loadBanLists(); refreshKpis(); }
      else { toastErr(errText(unbanRes, '解封失败')); }
      return;
    }
    if (action === 'unmute') {
      var okUnmute = await openDialog({
        title: '确认解除禁言', tone: 'warn', sub: '解除后该玩家可立即重新发言',
        detail: [['玩家', value]], confirmText: '解除禁言'
      });
      if (!okUnmute) return;
      var unmuteRes = await api('/api/unmute', { method: 'POST', body: JSON.stringify({ target: value }) });
      if (unmuteRes.ok) { toastOk('已解除 ' + value + ' 的禁言'); loadMuteList(); refreshKpis(); }
      else { toastErr(errText(unmuteRes, '解除禁言失败')); }
      return;
    }
    if (action === 'accept-report') { reportAction(value, 'accept'); return; }
    if (action === 'close-report') { reportAction(value, 'close'); }
  }

  /* ================= 快捷操作 ================= */

  async function doReload() {
    var res = await api('/api/reload', { method: 'POST' });
    if (res.ok) toastOk('配置已重新加载');
    else toastErr(errText(res, '重载失败'));
  }

  async function doBroadcast() {
    var res = await api('/api/broadcast', { method: 'POST' });
    if (res.ok) toastOk('已广播封禁人数');
    else toastErr(errText(res, '广播失败'));
  }

  /* ================= 面板设置 ================= */

  function applyBackground(servedUrl) {
    var layer = $('bgWallpaper');
    if (!layer) return;
    var url = servedUrl || 'https://api.dujin.org/bing/1920.php';
    layer.style.backgroundImage = "url('" + url + "')";
  }

  function applyHiddenButtons(hidden) {
    var bar = $('tabBar');
    if (!bar) return;
    Array.prototype.forEach.call(bar.querySelectorAll('.tab'), function (t) {
      t.classList.remove('hidden');
    });
    (hidden || []).forEach(function (key) {
      var tabName = HIDDEN_BUTTON_TABS[key];
      if (!tabName) return;
      var btn = bar.querySelector('.tab[data-tab="' + tabName + '"]');
      if (btn) btn.classList.add('hidden');
    });
    var active = bar.querySelector('.tab.is-active');
    if (active && active.classList.contains('hidden')) {
      switchTab('dashboard');
    }
  }

  async function loadThemeSettings() {
    var res = await api('/api/theme');
    if (!res.ok) return;
    var t = res.data || {};
    var type = t.background_type || 'default';
    if ($('bgType')) $('bgType').value = type;
    if ($('bgUrl')) $('bgUrl').value = t.background_url || '';
    onBgTypeChange();
    applyBackground(t.served_url || '');

    var checks = $('btnChecks');
    var hidden = t.hidden_buttons || [];
    if (checks) {
      var all = t.all_buttons && t.all_buttons.length ? t.all_buttons : Object.keys(BTN_LABELS);
      checks.innerHTML = all.map(function (id) {
        var on = hidden.indexOf(id) >= 0;
        return '<label class="check"><input type="checkbox" value="' + esc(id) + '"' + (on ? ' checked' : '') + '>' +
          '<span class="check-box">' + svgIcon('ic-check') + '</span>' +
          '<span class="check-text">' + esc(BTN_LABELS[id] || id) + '</span></label>';
      }).join('');
    }
    applyHiddenButtons(hidden);
  }

  function onBgTypeChange() {
    var sel = $('bgType');
    if (!sel) return;
    setHidden($('bgUrlRow'), sel.value !== 'url');
    setHidden($('bgUploadRow'), sel.value !== 'upload');
  }

  async function saveThemeSettings() {
    var type = $('bgType').value;
    if (type === 'upload') {
      var fileInput = $('bgFile');
      if (!fileInput || !fileInput.files || fileInput.files.length === 0) {
        toastWarn('请先点击「选择文件」选取本地图片');
        return;
      }
      await uploadBackground();
      return;
    }
    var payload = {};
    if (type === 'url') {
      var url = $('bgUrl').value.trim();
      if (!url) { toastWarn('请填写图片 URL'); return; }
      payload.background_url = url;
    }
    var res = await api('/api/theme', { method: 'POST', body: JSON.stringify(payload) });
    if (res.ok) {
      toastOk('背景设置已保存');
      loadThemeSettings();
    } else {
      toastErr(errText(res, '保存失败'));
    }
  }

  async function resetBackground() {
    var res = await api('/api/theme', { method: 'POST', body: JSON.stringify({ reset_background: true }) });
    if (res.ok) {
      toastOk('已恢复默认背景');
      loadThemeSettings();
    } else {
      toastErr(errText(res, '重置失败'));
    }
  }

  function onFilePicked() {
    var input = $('bgFile');
    var nameSpan = $('bgFileName');
    if (!nameSpan || !input) return;
    nameSpan.textContent = input.files && input.files.length > 0 ? input.files[0].name : '未选择';
  }

  async function uploadBackground() {
    var input = $('bgFile');
    if (!input || !input.files || input.files.length === 0) return;
    var file = input.files[0];
    if (file.size > 5 * 1024 * 1024) { toastErr('文件超过 5MB 上限'); return; }

    var res;
    var data = {};
    try {
      res = await fetch(BASE + '/api/theme/upload', {
        method: 'POST',
        headers: {
          'Authorization': 'Bearer ' + token,
          'Content-Disposition': 'attachment; filename="' + encodeURIComponent(file.name) + '"'
        },
        body: file
      });
      var text = await res.text();
      try { data = JSON.parse(text); } catch (e) { data = {}; }
    } catch (e) {
      toastErr('上传失败：网络错误');
      return;
    }
    if (res.ok && data.success) {
      var saveRes = await api('/api/theme', { method: 'POST', body: JSON.stringify({ background_file: data.filename }) });
      if (saveRes.ok) toastOk('上传成功，背景已切换为上传图片');
      else toastWarn('上传成功但应用失败');
      loadThemeSettings();
    } else {
      toastErr(data.error || '上传失败');
    }
  }

  async function saveHiddenButtons() {
    var checked = [];
    Array.prototype.forEach.call(document.querySelectorAll('#btnChecks input[type="checkbox"]:checked'), function (cb) {
      checked.push(cb.value);
    });
    var res = await api('/api/theme', { method: 'POST', body: JSON.stringify({ hidden_buttons: checked }) });
    if (res.ok) {
      toastOk('按钮显隐已保存');
      applyHiddenButtons(checked);
    } else {
      toastErr(errText(res, '保存失败'));
    }
  }

  /* ================= 踢出 ================= */

  function doKick(name) {
    openDialog({
      title: '踢出玩家',
      tone: 'danger',
      sub: '该玩家会立即被踢出当前服务器',
      detail: [['目标', name]],
      input: { label: '踢出原因', value: '管理员操作' },
      confirmText: '确认踢出'
    }).then(function (reason) {
      if (reason === null) return;
      return api('/api/kick', { method: 'POST', body: JSON.stringify({ target: name, reason: reason || '管理员操作' }) })
        .then(function (res) {
          if (res.ok) { toastOk('已踢出 ' + name); loadOnlinePlayers(); }
          else { toastErr(errText(res, '踢出失败')); }
        });
    });
  }

  /* ================= 事件委托 ================= */

  var ACTIONS = {
    'tab': function (el) { switchTab(el.getAttribute('data-tab')); },
    'toggle-theme': function () { toggleTheme(); },
    'logout': function () { logout(); },
    'open-eula': function () { openEula(); },
    'accept-eula': function () { acceptEula(); },
    'retry-stats': function () { loadDashboard(); },
    'refresh-online': function () { loadOnlinePlayers(); },
    'refresh-bans': function () { loadBanLists(); },
    'refresh-mutes': function () { loadMuteList(); },
    'refresh-reports': function () { loadReports(); },
    'retry-search': function () { searchPlayer(); },
    'retry-history': function () { loadHistory(); },
    'retry-audit': function () { loadAudit(); },
    'do-reload': function () { doReload(); },
    'do-broadcast': function () { doBroadcast(); },
    'save-theme': function () { saveThemeSettings(); },
    'reset-bg': function () { resetBackground(); },
    'pick-file': function () { if ($('bgFile')) $('bgFile').click(); },
    'save-hidden': function () { saveHiddenButtons(); },
    'page': function (el) { changePage(el.getAttribute('data-key'), Number(el.getAttribute('data-delta'))); },
    'kick': function (el) { doKick(el.getAttribute('data-player')); },
    'report-accept': function (el) { reportAction(el.getAttribute('data-id'), 'accept'); },
    'report-close': function (el) { reportAction(el.getAttribute('data-id'), 'close'); },
    'row-menu': function (el) {
      var row = el.closest('[data-ctx-kind]');
      if (!row) return;
      var rect = el.getBoundingClientRect();
      showContextMenu(rect.left, rect.bottom + 4, row.getAttribute('data-ctx-kind'), row.getAttribute('data-ctx-value'));
    }
  };

  var SUBMITS = {
    'do-login': function () { doLogin(); },
    'search-player': function () { searchPlayer(); },
    'do-ban': function () { doBan(); },
    'do-unban': function () { doUnban(); },
    'do-mute': function () { doMute(); },
    'do-unmute': function () { doUnmute(); },
    'load-history': function () { loadHistory(); },
    'load-audit': function () { loadAudit(); }
  };

  var CHANGES = {
    'ban-duration': function () { onDurationChange(); },
    'bg-type': function () { onBgTypeChange(); },
    'file-picked': function () { onFilePicked(); }
  };

  function wire() {
    document.addEventListener('click', function (e) {
      var item = e.target.closest ? e.target.closest('[data-ctx]') : null;
      var el = e.target.closest ? e.target.closest('[data-action]') : null;
      hideContextMenu();
      if (item) { contextMenuAction(item.getAttribute('data-ctx')); return; }
      if (!el) return;
      var handler = ACTIONS[el.getAttribute('data-action')];
      if (!handler) return;
      e.preventDefault();
      handler(el, e);
    });

    document.addEventListener('submit', function (e) {
      var form = e.target.closest ? e.target.closest('[data-submit]') : null;
      if (!form) return;
      e.preventDefault();
      var handler = SUBMITS[form.getAttribute('data-submit')];
      if (handler) handler(form);
    });

    document.addEventListener('change', function (e) {
      var el = e.target.closest ? e.target.closest('[data-change]') : null;
      if (!el) return;
      var handler = CHANGES[el.getAttribute('data-change')];
      if (handler) handler(el);
    });

    document.addEventListener('contextmenu', function (e) {
      var row = e.target.closest ? e.target.closest('[data-ctx-kind]') : null;
      if (!row) { hideContextMenu(); return; }
      e.preventDefault();
      showContextMenu(e.clientX, e.clientY, row.getAttribute('data-ctx-kind'), row.getAttribute('data-ctx-value'));
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') hideContextMenu();
    });

    window.addEventListener('resize', hideContextMenu);
    window.addEventListener('scroll', hideContextMenu, true);
  }

  wire();
  bootstrap();
})();
