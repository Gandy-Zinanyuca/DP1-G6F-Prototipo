// Panel de detalle al hacer clic en un vehículo/almacén sobre el mapa.
// Depende de: core/*, ui/render.js
"use strict";
  /* =================== SELECTION / DETAIL PANEL =================== */
  const selectionPanel = document.getElementById('selectionPanel');
  const selSwatch = document.getElementById('selSwatch');
  const selName = document.getElementById('selName');
  const selSub = document.getElementById('selSub');
  const selBody = document.getElementById('selBody');
  document.getElementById('selClose').addEventListener('click', ()=>{ selected=null; hideSelection(); });
  selBody.addEventListener('click', e=>{
    const btn = e.target.closest('button[data-act="registrar-averia"]');
    if(!btn) return;
    const tipo = +document.getElementById('selAveriaTipo').value;
    triggerAveriaManual(btn.dataset.id, tipo);
    refreshSelectionPanel();
  });

  function hideSelection(){ selectionPanel.classList.remove('open'); }

  function remainingDistance(v){
    if(!v.path) return 0;
    let remain = 0;
    if(v.pathIdx < v.path.length-1) remain += dist(v.pos, v.path[v.pathIdx+1]);
    for(let i=v.pathIdx+1; i<v.path.length-1; i++) remain += dist(v.path[i], v.path[i+1]);
    return remain;
  }
  function etaText(v){
    if(v.state==='atClient') return `Entregando (${Math.max(0,Math.round(v.timer))} min)`;
    if(!v.path) return '—';
    const mins = remainingDistance(v)/v.speed*60;
    return `${Math.round(mins)} min · llega ${fmtTime(simMin+mins)}`;
  }
  function vehicleStateLabel(v){
    switch(v.state){
      case 'toClient': return 'En ruta al cliente';
      case 'atClient': return 'Entregando en destino';
      case 'returning': return 'Retornando a recargar';
      case 'broken': return 'Avería — en espera de reasignación';
      default: return 'Disponible';
    }
  }

  function renderVehicleDetail(v){
    const t = VEHICLE_TYPES[v.type];
    selSwatch.style.background = css(t.color);
    selSwatch.style.borderRadius = '3px';
    selName.textContent = `${t.label} ${v.id}`;
    selSub.textContent = vehicleStateLabel(v);
    const order = v.order ? orders.find(o=>o.id===v.order) : null;
    let rows = `<div class="sel-row"><span>Capacidad</span><span>${t.capacity} paq.</span></div>
      <div class="sel-row"><span>Velocidad</span><span>${t.speed} km/h</span></div>
      <div class="sel-row"><span>Tarifa</span><span>S/ ${t.cost.toFixed(2)}/km</span></div>
      <div class="sel-row"><span>Posición</span><span>(${Math.round(v.pos.x)}, ${Math.round(v.pos.y)})</span></div>`;
    if(v.state==='broken'){
      const inc = incidents.find(i=>i.type==='falla' && i.vehicleId===v.id);
      rows += `<div class="sel-row"><span>Tipo de avería</span><span>${inc ? inc.falla.label : '—'}</span></div>
        <div class="sel-row"><span>Se repara</span><span>${inc ? fmtTime(inc.until) : '—'}</span></div>`;
    }
    if(order){
      rows += `<div class="sel-row"><span>Pedido</span><span>#${order.id}</span></div>
        <div class="sel-row"><span>Cliente</span><span>${order.clientId} (${order.pos.x},${order.pos.y})</span></div>
        <div class="sel-row"><span>Carga</span><span>${order.qty}/${t.capacity} paq.</span></div>
        <div class="sel-row"><span>Prioridad</span><span>${order.priority} h</span></div>
        <div class="sel-row"><span>ETA</span><span>${etaText(v)}</span></div>`;
    } else if(v.state==='returning'){
      rows += `<div class="sel-row"><span>Regreso a</span><span>${WAREHOUSES[v.returnTarget||v.home].name}</span></div>
        <div class="sel-row"><span>ETA</span><span>${etaText(v)}</span></div>`;
    } else {
      rows += `<div class="sel-row"><span>Base actual</span><span>${WAREHOUSES[v.home].name}</span></div>`;
    }
    if(v.state==='toClient' || v.state==='returning'){
      rows += `<div class="sel-actions">
        <select id="selAveriaTipo">
          ${FALLA_TYPES.map(f=>`<option value="${f.tipo}">Avería ${f.label}</option>`).join('')}
        </select>
        <button data-act="registrar-averia" data-id="${v.id}">Registrar</button>
      </div>`;
    }
    selBody.innerHTML = rows;
  }

  function renderWarehouseDetail(wh){
    selSwatch.style.background = css('--accent');
    selSwatch.style.borderRadius = '50%';
    selName.textContent = wh.name;
    selSub.textContent = wh.infinite ? 'Almacén central' : 'Almacén intermedio';
    const homeCount = vehicles.filter(v=>v.home===wh.id).length;
    let rows = `<div class="sel-row"><span>Nodo</span><span>(${wh.pos.x}, ${wh.pos.y})</span></div>`;
    rows += wh.infinite
      ? `<div class="sel-row"><span>Stock</span><span>Ilimitado</span></div>`
      : `<div class="sel-row"><span>Stock</span><span>${wh.stock} / ${wh.capacity}</span></div>
         <div class="sel-row"><span>Ocupación</span><span>${Math.round(wh.stock/wh.capacity*100)}%</span></div>`;
    rows += `<div class="sel-row"><span>Vehículos con base aquí</span><span>${homeCount}</span></div>
      <div class="sel-row"><span>Despachados hoy</span><span>${wh.dispatchedToday||0}</span></div>`;
    selBody.innerHTML = rows;
  }

  function renderZoneDetail(name){
    selSwatch.style.background = css('--ink-3');
    selSwatch.style.borderRadius = '50%';
    selName.textContent = name;
    selSub.textContent = 'Sector de la retícula';
    const activeHere = orders.filter(o=>sectorOf(o.pos)===name && o.status!=='delivered').length;
    const d = stats.bySector[name] || {delivered:0, onTime:0};
    const pct = d.delivered ? Math.round(d.onTime/d.delivered*100) : null;
    selBody.innerHTML = `<div class="sel-row"><span>Pedidos activos</span><span>${activeHere}</span></div>
      <div class="sel-row"><span>Entregados (ciclo)</span><span>${d.delivered}</span></div>
      <div class="sel-row"><span>A tiempo</span><span>${pct===null?'—':pct+'%'}</span></div>`;
  }

  function refreshSelectionPanel(){
    if(!selected) return;
    if(selected.type==='vehicle'){
      const v = vehicles.find(vv=>vv.id===selected.id);
      if(!v || v.state==='idle' || v.state==='break'){ selected=null; hideSelection(); return; }
      renderVehicleDetail(v);
    } else if(selected.type==='warehouse'){
      renderWarehouseDetail(WAREHOUSES[selected.id]);
    } else if(selected.type==='zone'){
      renderZoneDetail(selected.name);
    }
    selectionPanel.classList.add('open');
  }

  function handleMapClick(sx, sy){
    let hit=null;
    for(const v of vehicles){
      if(v.state==='idle' || v.state==='break' || !typeFilter[v.type]) continue;
      const p = S(v.pos);
      if(Math.hypot(p.x-sx,p.y-sy) < 13){ hit={type:'vehicle', id:v.id}; break; }
    }
    if(!hit){
      for(const wh of Object.values(WAREHOUSES)){
        const p = S(wh.pos);
        const r = wh.infinite ? 11 : 9.5;
        const headY = p.y - r*1.7;
        if(Math.hypot(p.x-sx, headY-sy) < r+5){ hit={type:'warehouse', id:wh.id}; break; }
      }
    }
    if(!hit){
      const world = screenToWorld(sx, sy);
      if(world.x>=0 && world.x<=GRID_W && world.y>=0 && world.y<=GRID_H){
        hit = {type:'zone', name: sectorOf(world)};
      }
    }
    if(hit){ selected = hit; refreshSelectionPanel(); } else { selected=null; hideSelection(); }
  }

