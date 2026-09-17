// Módulo Mantenimiento: programación manual (solo día a día) + plan de mantenimiento preventivo por archivo (aaaammdd:TTNN) + tabla.
// Depende de: sim/incidents.js, ui/file-upload.js
"use strict";
  /* =================== MÓDULO: MANTENIMIENTO =================== */
  const manVehSelect = document.getElementById('manVehSelect');
  const manTbody = document.getElementById('manTbody');
  function updateMantenimientoInputMode(){
    const isDiaria = scenario==='diaria';
    document.getElementById('manManualSection').style.display = isDiaria ? '' : 'none';
    document.getElementById('manArchivoSection').style.display = isDiaria ? 'none' : 'flex';
    document.getElementById('manMsub').textContent = isDiaria
      ? 'Programar mantenimiento preventivo para una unidad disponible, y ver las ventanas de mantenimiento de la ejecución.'
      : 'Este escenario recibe los mantenimientos desde un archivo programado — el registro manual es solo para la Operación día a día.';
  }

  function renderMantenimientoModule(){
    updateMantenimientoInputMode();
    manVehSelect.innerHTML = '';
    const eligible = vehicles.filter(v=>v.state==='idle' || v.state==='break');
    if(!eligible.length){
      const o = document.createElement('option'); o.textContent = 'Ninguna unidad disponible ahora mismo'; o.disabled=true;
      manVehSelect.appendChild(o);
    } else {
      eligible.forEach(v=>{
        const o = document.createElement('option'); o.value=v.id;
        o.textContent = `${v.id} (${VEHICLE_TYPES[v.type].label}) — disponible`;
        manVehSelect.appendChild(o);
      });
    }

    const list = incidents.filter(i=>i.type==='mantenimiento');
    manTbody.innerHTML = !list.length
      ? `<tr><td colspan="4" class="inc-empty">Sin mantenimientos programados por ahora.</td></tr>`
      : list.slice().reverse().map(inc=>
          `<tr><td>${inc.vehicleId}</td><td>${inc.horas} h</td><td>${fmtTime(inc.since)}</td><td>${fmtTime(inc.until)}</td></tr>`
        ).join('');
  }

  document.getElementById('btnRegistrarMantenimiento').addEventListener('click', ()=>{
    const vid = manVehSelect.value;
    if(!vid) return;
    const horas = +document.getElementById('manHoras').value || 2;
    triggerMantenimientoManual(vid, horas);
    renderMantenimientoModule();
  });
