// Módulo Averías: registro manual (disponible en todo escenario) + carga por archivo en 5D/Colapso + tabla filtrable por tipo.
// Depende de: sim/incidents.js, ui/file-upload.js, ui/selection-panel.js
"use strict";
  /* =================== MÓDULO: AVERÍAS =================== */
  const incVehSelect = document.getElementById('incVehSelect');
  const incTipoSelect = document.getElementById('incTipoSelect');
  const averiasTbody = document.getElementById('averiasTbody');
  FALLA_TYPES.forEach(f=>{
    const o = document.createElement('option'); o.value=f.tipo; o.textContent = `Avería ${f.label}`;
    incTipoSelect.appendChild(o);
  });

  // el registro manual de averías está disponible siempre (herramienta operativa);
  // en 5D y colapso se suma, además, la carga en lote desde un archivo programado
  function updateAveriasInputMode(){
    const isDiaria = scenario==='diaria';
    document.getElementById('averiasManualSection').style.display = '';
    document.getElementById('averiasArchivoSection').style.display = isDiaria ? 'none' : 'flex';
    document.getElementById('averiasMsub').textContent = isDiaria
      ? 'Registrar una avería manualmente sobre una unidad en ruta, y ver el historial de averías de la ejecución.'
      : 'Registra una avería a mano sobre una unidad en ruta, o carga un archivo de averías programadas para el escenario.';
  }

  function renderAveriasModule(){
    updateAveriasInputMode();
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

    const tipoFiltro = document.getElementById('averiasFiltroTipo').value;
    let list = incidents.filter(i=>i.type==='falla');
    if(tipoFiltro) list = list.filter(i=>String(i.falla.tipo)===tipoFiltro);
    averiasTbody.innerHTML = !list.length
      ? `<tr><td colspan="4" class="inc-empty">Sin averías registradas por ahora.</td></tr>`
      : list.slice().reverse().map(inc=>
          `<tr data-vehicle="${inc.vehicleId}" style="cursor:pointer;" title="Ver esta unidad en el mapa"><td>${inc.vehicleId}</td><td><span style="display:inline-flex;align-items:center;gap:6px;"><i style="width:7px;height:7px;border-radius:50%;background:var(${inc.falla.color});display:inline-block;flex:0 0 auto;"></i>${inc.falla.label}</span></td><td>${fmtTime(inc.since)}</td><td>${fmtTime(inc.until)}</td></tr>`
        ).join('');

    averiasTbody.querySelectorAll('tr[data-vehicle]').forEach(tr=>{
      tr.addEventListener('click', ()=> selectVehicleAndCenter(tr.dataset.vehicle));
    });
  }

  document.getElementById('averiasFiltroTipo').addEventListener('change', renderAveriasModule);

  document.getElementById('btnRegistrarAveria').addEventListener('click', ()=>{
    const vid = incVehSelect.value;
    if(!vid) return;
    triggerAveriaManual(vid, +incTipoSelect.value);
    renderAveriasModule();
  });
