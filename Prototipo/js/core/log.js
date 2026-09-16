// Bitácora de eventos in-app (addLog) que alimenta el panel de indicadores y dispara el badge de la pestaña Registro.
// Depende de: core/state.js, core/time.js
"use strict";
  /* =================== LOG =================== */
  const logListEl = document.getElementById('logList');
  function addLog(text, kind){
    const color = kind==='good' ? css('--good') : kind==='warning' ? css('--warning') : kind==='critical' ? css('--critical') : css('--accent');
    logs.push({t: simMin, text, color});
    const item = document.createElement('div');
    item.className = 'log-item';
    item.innerHTML = `<span class="log-dot" style="background:${color}"></span><span class="log-time">D${cycleDay} · ${fmtTime(simMin)}</span><span class="log-text">${text}</span>`;
    logListEl.appendChild(item);
    while(logListEl.children.length > 60){ logListEl.removeChild(logListEl.firstChild); }
    if(typeof noteNewLog === 'function') noteNewLog();
  }
