// Panel de indicadores: abrir/cerrar, y sus pestañas Indicadores / Registro de eventos con el badge de eventos nuevos.
// Depende de: core/log.js
"use strict";
  /* =================== PANEL LATERAL / BUSCAR: desplegables ---------- */
  const sidebarPanel = document.getElementById('sidebarPanel');
  document.getElementById('sidebarToggle').addEventListener('click', e=>{
    e.stopPropagation();
    sidebarPanel.classList.toggle('open');
    document.getElementById('searchCtl').classList.remove('open');
  });
  document.getElementById('sidebarClose').addEventListener('click', ()=> sidebarPanel.classList.remove('open'));

  // --- pestañas del panel de indicadores: Indicadores <-> Registro de eventos ---
  const sbTabMetrics = document.getElementById('sbTabMetrics');
  const sbTabLog = document.getElementById('sbTabLog');
  const sbPanelMetrics = document.querySelector('.sidebar-scroll[data-tabpanel="metrics"]');
  const sbPanelLog = document.querySelector('.sidebar-log[data-tabpanel="log"]');
  const logBadge = document.getElementById('logBadge');
  let sidebarActiveTab = 'metrics';
  let unseenLogCount = 0;

  function setSidebarTab(tab){
    sidebarActiveTab = tab;
    const onLog = tab==='log';
    sbTabMetrics.classList.toggle('active', !onLog);
    sbTabLog.classList.toggle('active', onLog);
    sbTabMetrics.setAttribute('aria-selected', String(!onLog));
    sbTabLog.setAttribute('aria-selected', String(onLog));
    sbPanelMetrics.classList.toggle('active', !onLog);
    sbPanelLog.classList.toggle('active', onLog);
    if(onLog){ unseenLogCount = 0; logBadge.style.display = 'none'; }
  }
  sbTabMetrics.addEventListener('click', ()=> setSidebarTab('metrics'));
  sbTabLog.addEventListener('click', ()=> setSidebarTab('log'));
