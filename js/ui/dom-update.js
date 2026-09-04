// Refresca cada ~350ms los textos/KPIs del DOM (reloj, turno, indicadores, almacenes) a partir del estado.
// Depende de: core/*, core/time.js
"use strict";
  /* =================== DOM UPDATE =================== */
  const dayLabel = document.getElementById('dayLabel');
  const dateLabel = document.getElementById('dateLabel');
  const durationLabel = document.getElementById('durationLabel');
  const timeLabel = document.getElementById('timeLabel');
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
  const whNoroesteVal = document.getElementById('whNoroesteVal');
  const whNoroesteBar = document.getElementById('whNoroesteBar');
  const whEsteVal = document.getElementById('whEsteVal');
  const whEsteBar = document.getElementById('whEsteBar');
  document.getElementById('whNoroesteName').textContent = WAREHOUSES.noroeste.name.replace('Almacén ','');
  document.getElementById('whEsteName').textContent = WAREHOUSES.este.name.replace('Almacén ','');

  function updateDom(){
    dayLabel.textContent = scenario==='5d' ? `Día ${Math.min(cycleDay,CYCLE_DAYS_5D)} / ${CYCLE_DAYS_5D}` : `Día ${cycleDay}`;
    timeLabel.textContent = fmtTime(simMin);
    const simDate = new Date(simEpochDate.getTime() + simMin*60000);
    dateLabel.textContent = simDate.toLocaleDateString('es-PE', {day:'2-digit', month:'2-digit', year:'numeric'});
    const runMs = running ? accumulatedRunMs + (performance.now()-startedAtReal) : accumulatedRunMs;
    const totalSec = Math.floor(runMs/1000);
    durationLabel.textContent = [Math.floor(totalSec/3600), Math.floor(totalSec/60)%60, totalSec%60]
      .map(x=>String(x).padStart(2,'0')).join(':');

    const shift = currentShift();
    shiftLabel.textContent = shift.label.split(' · ')[0] + ' · ' + shift.label.split('· ')[1];
    const meal = inMeal(shift);
    mealTag.style.display = meal ? 'inline-block' : 'none';
    shiftSwatch.style.background = meal ? css('--warning') : css('--accent');

    const activeOrders = orders.filter(o=>o.status!=='delivered');
    kpiActive.textContent = activeOrders.length;
    kpiActiveSub.textContent = `${orders.filter(o=>o.status==='pending').length} por asignar`;

    const totalDelivered = stats.onTime + stats.late;
    const onTimePct = totalDelivered ? Math.round(stats.onTime/totalDelivered*100) : null;
    kpiOnTime.textContent = onTimePct===null ? '—' : onTimePct+'%';
    kpiOnTime.className = 'kpi-value ' + (onTimePct===null ? '' : riskLevel(onTimePct));
    kpiOnTimeSub.textContent = totalDelivered ? `${stats.onTime}/${totalDelivered} entregas` : 'sin entregas aún';

    kpiToday.textContent = stats.deliveredToday;

    kpiIncidents.textContent = incidents.length;
    const blq = incidents.filter(i=>i.type==='bloqueo').length;
    const flt = incidents.filter(i=>i.type==='falla').length;
    kpiIncidentsSub.textContent = `${blq} bloqueo(s) · ${flt} avería(s)`;

    // conteo de flota por tipo, para la leyenda siempre visible del mapa
    Object.values(VEHICLE_TYPES).forEach(t=>{
      const list = vehicles.filter(v=>v.type===t.key);
      const busy = list.filter(v=>v.state!=='idle' && v.state!=='break').length;
      if(t.key==='auto') legCarCount.textContent = `${busy}/${list.length}`;
      if(t.key==='moto') legMotoCount.textContent = `${busy}/${list.length}`;
      if(t.key==='bici') legBikeCount.textContent = `${busy}/${list.length}`;
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
  }

