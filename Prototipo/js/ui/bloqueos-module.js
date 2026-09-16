// Módulo Bloqueos: registro manual + carga por archivo + tabla en vivo de bloqueos.
// Depende de: sim/blockages.js, ui/file-upload.js
"use strict";
  /* =================== MÓDULO: INCIDENCIAS (bloqueos) =================== */
  const incTbody = document.getElementById('incTbody');

  function renderIncidenciasModule(){
    const list = incidents.filter(i=>i.type==='bloqueo');
    incTbody.innerHTML = !list.length
      ? `<tr><td colspan="3" class="inc-empty">Sin bloqueos registrados por ahora.</td></tr>`
      : list.slice().reverse().map(inc=>{
          const a=inc.nodes[0], b=inc.nodes[inc.nodes.length-1];
          return `<tr><td>(${a.x},${a.y}) → (${b.x},${b.y})</td><td>${fmtTime(inc.since)}</td><td>${fmtTime(inc.until)}</td></tr>`;
        }).join('');
  }

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
    addLog(`<b>Bloqueo</b> registrado manualmente (${nodes.length} nodo(s), ${horas} h).`, 'warning');
    document.getElementById('incBloqueoNodos').value = '';
    renderIncidenciasModule();
  });
