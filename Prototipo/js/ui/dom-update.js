// Refresca cada ~350ms los textos/KPIs del DOM (reloj, turno, indicadores, pedidos por tipo, flota, almacenes) a partir del estado.
// Depende de: core/*, core/time.js
"use strict";
  /* =================== DOM UPDATE =================== */
  const dayLabel = document.getElementById('dayLabel');
  const dateLabel = document.getElementById('dateLabel');
  const durationLabel = document.getElementById('durationLabel');
  const timeLabel = document.getElementById('timeLabel');
  const simElapsedLabel = document.getElementById('simElapsedLabel');
  const realTimeLabel = document.getElementById('realTimeLabel');
  const legCarCount = document.getElementById('legCarCount');
  const legMotoCount = document.getElementById('legMotoCount');
  const legBikeCount = document.getElementById('legBikeCount');
  const legPackages = document.getElementById('legPackages');
  const shiftLabel = document.getElementById('shiftLabel');
  const shiftSwatch = document.getElementById('shiftSwatch');
  const mealTag = document.getElementById('mealTag');
  const kpiActive = document.getElementById('kpiActive');
  const kpiActiveSub = document.getElementById('kpiActiveSub');
  const kpiOnTime = document.getElementById('kpiOnTime');
  const kpiOnTimeSub = document.getElementById('kpiOnTimeSub');
  const kpiToday = document.getElementById('kpiToday');
  const kpiIncidents = document.getElementById('kpiIncidents');
  const kpiIncidentsSub = document.getElementById('kpiIncidentsSub');
  const prioRows = document.getElementById('prioRows');
  const pedTipoRows = document.getElementById('pedTipoRows');
  const flotaRows = document.getElementById('flotaRows');
  const whNoroesteVal = document.getElementById('whNoroesteVal');
  const whNoroesteBar = document.getElementById('whNoroesteBar');
  const whEsteVal = document.getElementById('whEsteVal');
  const whEsteBar = document.getElementById('whEsteBar');
  document.getElementById('whNoroesteName').textContent = WAREHOUSES.noroeste.name.replace('Almacén ','');
  document.getElementById('whEsteName').textContent = WAREHOUSES.este.name.replace('Almacén ','');

  function updateDom(){
    dayLabel.textContent = scenario==='5d' ? `Día ${Math.min(cycleDay,CYCLE_DAYS_5D)} / ${CYCLE_DAYS_5D}` : `Día ${cycleDay}`;
    timeLabel.textContent = fmtTimeS(simMin);
    const simDate = new Date(simEpochDate.getTime() + simMin*60000);
    dateLabel.textContent = simDate.toLocaleDateString('es-PE', {day:'2-digit', month:'2-digit', year:'numeric'});
    const runMs = running ? accumulatedRunMs + (performance.now()-startedAtReal) : accumulatedRunMs;
    const totalSec = Math.floor(runMs/1000);
    durationLabel.textContent = [Math.floor(totalSec/3600), Math.floor(totalSec/60)%60, totalSec%60]
      .map(x=>String(x).padStart(2,'0')).join(':');
    simElapsedLabel.textContent = fmtElapsedSim(Math.max(0, simMin - runStartSimMin));
    realTimeLabel.textContent = new Date().toLocaleTimeString('es-PE', {hour12:false});

    const shift = currentShift();
    shiftLabel.textContent = shift.label.split(' · ')[0] + ' · ' + shift.label.split('· ')[1];
    const meal = inMeal(shift);
    mealTag.style.display = meal ? 'inline-block' : 'none';
    shiftSwatch.style.background = meal ? css('--warning') : css('--accent');

    const activeOrders = orders.filter(o=>o.status!=='delivered');
    kpiActive.textContent = activeOrders.length;
    kpiActiveSub.textContent = `${orders.filter(o=>o.status==='pending').length} por asignar`;

    // desglose de pedidos por tipo: programado (nuevo) / reprogramado (avería lo devolvió a la cola) /
    // en marcha (ya asignado a una unidad) / entregados hoy
    const programados = orders.filter(o=>o.status==='pending' && !o.reprogramado).length;
    const reprogramados = orders.filter(o=>o.status==='pending' && o.reprogramado).length;
    const enMarcha = orders.filter(o=>o.status==='assigned').length;
    const tipoTotal = programados + reprogramados + enMarcha + stats.deliveredToday;
    const tipoBucket = (label, count, color)=>{
      const pct = tipoTotal ? Math.round(count/tipoTotal*100) : 0;
      return `<div class="prio-row">
        <span class="prio-badge" style="width:auto;flex:1 1 auto;color:var(--ink-2);">${label}</span>
        <span class="prio-bar"><i style="width:${pct}%;background:var(--${color})"></i></span>
        <span class="prio-count">${count}</span>
      </div>`;
    };
    pedTipoRows.innerHTML =
      tipoBucket('Programados', programados, 'accent') +
      tipoBucket('Reprogramados', reprogramados, 'warning') +
      tipoBucket('En marcha', enMarcha, 'accent-2') +
      tipoBucket('Entregados hoy', stats.deliveredToday, 'good');

    const totalDelivered = stats.onTime + stats.late;
    const onTimePct = totalDelivered ? Math.round(stats.onTime/totalDelivered*100) : null;
    kpiOnTime.textContent = onTimePct===null ? '—' : onTimePct+'%';
    kpiOnTime.className = 'kpi-value ' + (onTimePct===null ? '' : riskLevel(onTimePct));
    kpiOnTimeSub.textContent = totalDelivered ? `${stats.onTime}/${totalDelivered} entregas` : 'sin entregas aún';

    kpiToday.textContent = stats.deliveredToday;

    const blq = incidents.filter(i=>i.type==='bloqueo').length;
    const flt = incidents.filter(i=>i.type==='falla').length;
    kpiIncidents.textContent = blq + flt; // el mantenimiento es planificado, no cuenta como incidencia
    kpiIncidentsSub.textContent = `${blq} bloqueo(s) · ${flt} avería(s)`;

    // conteo de flota por tipo, para la leyenda siempre visible del mapa y para el panel de indicadores —
    // coloreado por disponibilidad (semáforo), con el mismo desglose en ambos lugares
    const legEls = { auto:legCarCount, moto:legMotoCount, bici:legBikeCount };
    flotaRows.innerHTML = '';
    Object.values(VEHICLE_TYPES).forEach(t=>{
      const list = vehicles.filter(v=>v.type===t.key);
      const busy = list.filter(v=>v.state!=='idle' && v.state!=='break').length;
      const available = list.length - busy;
      const pct = list.length ? available/list.length*100 : 100;
      const busyColor = css('--'+riskLevel(pct));
      const enRuta = list.filter(v=>v.state==='toClient').length;
      const enCliente = list.filter(v=>v.state==='atClient').length;
      const retornando = list.filter(v=>v.state==='returning').length;
      const enAveria = list.filter(v=>v.state==='broken').length;
      const enManten = list.filter(v=>v.state==='maintenance').length;

      const el = legEls[t.key];
      if(el){
        el.textContent = `${busy}/${list.length}`;
        el.style.color = busyColor;
        // mismo desglose, disponible también al pasar el mouse sobre el contador del mapa
        el.parentElement.title =
          `${t.label}: ${busy} ocupadas de ${list.length}\n` +
          `En ruta al cliente: ${enRuta}\nEn el cliente: ${enCliente}\nRetornando a almacén: ${retornando}\n` +
          `Avería: ${enAveria}\nMantenimiento: ${enManten}\nDisponibles: ${available}`;
      }

      const row = document.createElement('div');
      row.style.marginBottom = '10px';
      row.innerHTML = `
        <div style="display:flex;align-items:center;gap:6px;font-size:12px;margin-bottom:3px;">
          <span style="width:9px;height:9px;border-radius:3px;background:${css(t.color)};display:inline-block;flex:0 0 auto;"></span>
          <span style="color:var(--ink);font-weight:600;">${t.label}</span>
          <span style="margin-left:auto;font-family:'IBM Plex Mono',monospace;color:${busyColor};">${busy}/${list.length}</span>
        </div>
        <div style="font-size:10.5px;color:var(--ink-3);padding-left:15px;">
          En ruta ${enRuta} · En cliente ${enCliente} · Retornando ${retornando} · Avería ${enAveria} · Manten. ${enManten}
        </div>`;
      flotaRows.appendChild(row);
    });
    legPackages.textContent = orders.filter(o=>o.status==='assigned').reduce((s,o)=>s+o.qty,0);

    // priority-bucket compliance rows (4h/8h/12h/18h prioritized + 36h standard)
    prioRows.innerHTML = '';
    PRIORITY_BUCKETS.forEach(p=>{
      const b = stats.byPriority[p] || {delivered:0, onTime:0};
      const pct = b.delivered ? Math.round(b.onTime/b.delivered*100) : null;
      const level = pct===null ? null : riskLevel(pct);
      const row = document.createElement('div');
      row.className = 'prio-row';
      const barColor = level ? css('--'+level) : css('--border');
      row.innerHTML = `<span class="prio-badge">${p}h</span>
        <span class="prio-bar"><i style="width:${pct||0}%;background:${barColor}"></i></span>
        <span class="prio-count">${b.onTime}/${b.delivered}${pct===null?'':' · '+pct+'%'}</span>`;
      prioRows.appendChild(row);
    });

    whNoroesteVal.textContent = `${WAREHOUSES.noroeste.stock} / ${WAREHOUSES.noroeste.capacity}`;
    whNoroesteBar.style.width = (WAREHOUSES.noroeste.stock/WAREHOUSES.noroeste.capacity*100)+'%';
    whNoroesteBar.style.background = css('--'+warehouseLevel(WAREHOUSES.noroeste));
    whEsteVal.textContent = `${WAREHOUSES.este.stock} / ${WAREHOUSES.este.capacity}`;
    whEsteBar.style.width = (WAREHOUSES.este.stock/WAREHOUSES.este.capacity*100)+'%';
    whEsteBar.style.background = css('--'+warehouseLevel(WAREHOUSES.este));

    if(document.getElementById('modIncidencias').classList.contains('active')) renderIncidenciasModule();
    if(document.getElementById('modAverias').classList.contains('active')) renderAveriasModule();
    if(document.getElementById('modFlota').classList.contains('active')) renderFlotaModule();
    if(document.getElementById('modMantenimiento').classList.contains('active')) renderMantenimientoModule();
  }
