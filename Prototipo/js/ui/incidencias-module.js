// Módulo de registro manual de averías y bloqueos + su tabla en vivo.
// Depende de: sim/incidents.js, sim/blockages.js
"use strict";
  /* =================== MÓDULO: INCIDENCIAS =================== */
  const incVehSelect = document.getElementById('incVehSelect');
  const incTipoSelect = document.getElementById('incTipoSelect');
  const incTbody = document.getElementById('incTbody');
  FALLA_TYPES.forEach(f=>{
    const o = document.createElement('option'); o.value=f.tipo; o.textContent = `Avería ${f.label}`;
    incTipoSelect.appendChild(o);
  });

  function renderIncidenciasModule(){
    incVehSelect.innerHTML = '';
    const eligible = vehicles.filter(v=>v.state==='toClient' || v.state==='returning');
    if(!eligible.length){
      const o = document.createElement('option'); o.textContent = 'Ninguna unidad en ruta ahora mismo'; o.disabled=true;
      incVehSelect.appendChild(o);
    } else {
      eligible.forEach(v=>{
        const o = document.createElement('option'); o.value=v.id;
        o.textContent = `${v.id} (${VEHICLE_TYPES[v.type].label}) — ${v.state==='toClient'?'en ruta al cliente':'retornando'}`;
        incVehSelect.appendChild(o);
      });
    }

    if(!incidents.length){
      incTbody.innerHTML = `<tr><td colspan="4" class="inc-empty">Sin incidencias registradas por ahora.</td></tr>`;
    } else {
      incTbody.innerHTML = incidents.slice().reverse().map(inc=>{
        if(inc.type==='falla'){
          return `<tr><td>Avería</td><td>${inc.vehicleId} — ${inc.falla.label}</td><td>${fmtTime(inc.since)}</td><td>${fmtTime(inc.until)}</td></tr>`;
        }
        const a=inc.nodes[0], b=inc.nodes[inc.nodes.length-1];
        return `<tr><td>Bloqueo</td><td>(${a.x},${a.y}) → (${b.x},${b.y})</td><td>${fmtTime(inc.since)}</td><td>${fmtTime(inc.until)}</td></tr>`;
      }).join('');
    }
  }

  document.getElementById('btnRegistrarAveria').addEventListener('click', ()=>{
    const vid = incVehSelect.value;
    if(!vid) return;
    triggerAveriaManual(vid, +incTipoSelect.value);
    renderIncidenciasModule();
  });

  document.getElementById('btnRegistrarBloqueo').addEventListener('click', ()=>{
    const raw = document.getElementById('incBloqueoNodos').value.trim();
    const horas = +document.getElementById('incBloqueoHoras').value || 4;
    const coords = raw.split(',').map(n=>parseInt(n.trim(),10));
    if(coords.length<4 || coords.some(isNaN)){
      addLog('No se pudo registrar el bloqueo: formato de nodos inválido.', 'critical');
      return;
    }
    const nodes = [];
    for(let i=0;i+1<coords.length;i+=2) nodes.push({x:coords[i], y:coords[i+1]});
    addBlockageChain(nodes, simMin + horas*60);
    document.getElementById('incBloqueoNodos').value = '';
    renderIncidenciasModule();
  });

