// Parseo de los archivos de entrada del curso: ventas, bloqueos, averías y plan de mantenimiento preventivo (todos ##d##h##m o aaaammdd), + normalización de códigos de unidad TTNN.
// Depende de: core/state.js, core/time.js
"use strict";
  /* =================== ARCHIVOS DE ENTRADA (formato dado por el curso) =================== */
  // ##d##h##m -> minutos desde el inicio del día 1 de la simulación
  function parseDDHHMM(s){
    const m = s.match(/^(\d+)d(\d+)h(\d+)m$/);
    if(!m) return null;
    return (parseInt(m[1],10)-1)*1440 + parseInt(m[2],10)*60 + parseInt(m[3],10);
  }

  // Registro: ##d##h##m:posx,posY,cIdCliente,qq,hl  (p.ej. 11d13h31m:45,43,c9167,12,36)
  function parseVentas(text){
    const out = [];
    text.split(/\r?\n/).forEach(raw=>{
      const line = raw.trim();
      if(!line) return;
      const m = line.match(/^(\d+d\d+h\d+m):(-?\d+),(-?\d+),(\w+),(\d+),(\d+)$/);
      if(!m) return;
      const arriveMin = parseDDHHMM(m[1]);
      if(arriveMin===null) return;
      out.push({ arriveMin, pos:{x:+m[2], y:+m[3]}, clientId:m[4], qty:+m[5], hourLimit:+m[6] });
    });
    out.sort((a,b)=>a.arriveMin-b.arriveMin);
    return out;
  }

  // Registro: ##d##h##m-##d##h##m:x1,y1,x2,y2,...  (p.ej. 01d06h00m-01d15h00m:31,21,34,21)
  function parseBloqueos(text){
    const out = [];
    text.split(/\r?\n/).forEach(raw=>{
      const line = raw.trim();
      if(!line) return;
      const m = line.match(/^(\d+d\d+h\d+m)-(\d+d\d+h\d+m):(.+)$/);
      if(!m) return;
      const startMin = parseDDHHMM(m[1]), endMin = parseDDHHMM(m[2]);
      if(startMin===null || endMin===null) return;
      const coords = m[3].split(',').map(n=>parseInt(n,10));
      const nodes = [];
      for(let i=0;i+1<coords.length;i+=2) nodes.push({x:coords[i], y:coords[i+1]});
      if(nodes.length>=2) out.push({startMin, endMin, nodes});
    });
    return out;
  }

  // Guarda un archivo de ventas YA parseado y confirmado por el usuario en el modal de carga.
  function commitVentasFile(parsed){
    ventasQueue = parsed; ventasPtr = 0;
    let historicos = 0;
    while(ventasPtr < ventasQueue.length && ventasQueue[ventasPtr].arriveMin <= simMin){
      pushOrderFromRecord(ventasQueue[ventasPtr++]);
      historicos++;
    }
    addLog(`Archivo de pedidos cargado: <b>${parsed.length}</b> registros (${historicos} históricos registrados de inmediato, ${parsed.length-historicos} proyectados en cola).`, 'accent');
    return {count:parsed.length, historicos};
  }

  // Vista previa (hasta 6 filas) del archivo de ventas, para el modal de confirmación antes de cargarlo.
  function previewVentas(parsed){
    const rows = parsed.slice(0,6).map(r=>{
      const day = Math.floor(r.arriveMin/1440)+1;
      return `D${day} ${fmtTime(r.arriveMin)} · ${r.clientId} · ${r.qty} paq · (${r.pos.x},${r.pos.y}) · límite ${r.hourLimit}h`;
    }).join('<br>');
    return rows + (parsed.length>6 ? `<br>… y ${parsed.length-6} registro(s) más` : '');
  }

  // Guarda un archivo de bloqueos YA parseado y confirmado por el usuario en el modal de carga.
  function commitBloqueosFile(parsed){
    bloqueosQueue = parsed;
    let vigentes = 0;
    parsed.forEach(rec=>{
      if(rec.startMin<=simMin && simMin<rec.endMin){
        addBlockageChain(rec.nodes, rec.endMin);
        incidents[incidents.length-1].fromFile = rec;
        vigentes++;
      }
    });
    addLog(`Archivo de bloqueos cargado: <b>${parsed.length}</b> registros (${vigentes} vigentes desde ya).`, 'accent');
    return {count:parsed.length, vigentes};
  }

  // Vista previa (hasta 6 filas) del archivo de bloqueos, para el modal de confirmación antes de cargarlo.
  function previewBloqueos(parsed){
    const rows = parsed.slice(0,6).map(r=>{
      const d1 = Math.floor(r.startMin/1440)+1, d2 = Math.floor(r.endMin/1440)+1;
      return `D${d1} ${fmtTime(r.startMin)} → D${d2} ${fmtTime(r.endMin)} · ${r.nodes.length} nodos`;
    }).join('<br>');
    return rows + (parsed.length>6 ? `<br>… y ${parsed.length-6} registro(s) más` : '');
  }

  // normaliza un código de unidad tipo "tb3" -> "TB03" (2 letras de tipo + número de 2 dígitos)
  function normVehId(s){
    const m = String(s).trim().toUpperCase().match(/^([A-Z]{2})(\d{1,3})$/);
    return m ? m[1] + m[2].padStart(2,'0') : String(s).trim().toUpperCase();
  }

  // Registro de avería programada: ##d##h##m:TTNN,tipo   (p.ej. 01d09h30m:TA01,2 — tipo 1/2/3)
  function parseAveriasFile(text){
    const out = [];
    text.split(/\r?\n/).forEach(raw=>{
      const line = raw.trim();
      if(!line) return;
      const m = line.match(/^(\d+d\d+h\d+m):([A-Za-z]{1,3}\d{1,3}),([123])$/);
      if(!m) return;
      const atMin = parseDDHHMM(m[1]);
      if(atMin===null) return;
      out.push({ atMin, vehicleId:normVehId(m[2]), tipo:+m[3], done:false });
    });
    out.sort((a,b)=>a.atMin-b.atMin);
    return out;
  }
  function previewAverias(parsed){
    const T = {1:'🟡 leve', 2:'🟠 moderada', 3:'🔴 grave'};
    const rows = parsed.slice(0,6).map(r=>{
      const day = Math.floor(r.atMin/1440)+1;
      return `D${day} ${fmtTime(r.atMin)} · ${r.vehicleId} · ${T[r.tipo] || ('tipo '+r.tipo)}`;
    }).join('<br>');
    return rows + (parsed.length>6 ? `<br>… y ${parsed.length-6} registro(s) más` : '');
  }
  function commitAveriasFile(parsed){
    averiasQueue = parsed;
    const before = incidents.filter(i=>i.type==='falla').length;
    activateScheduledAverias();
    const inmediatas = incidents.filter(i=>i.type==='falla').length - before;
    addLog(`Archivo de averías cargado: <b>${parsed.length}</b> registros (${inmediatas} aplicadas de inmediato, el resto en cola).`, 'accent');
    return {count:parsed.length, inmediatas};
  }

  // Plan de mantenimiento preventivo (archivo mant.preventivo.m1.m2). Registro: aaaammdd:TTNN
  // p.ej. 20260908:TB03 — la unidad queda fuera de ruta desde las 00:00 hasta las 23:59 de esa fecha.
  function parseMantenimientoFile(text){
    const out = [];
    text.split(/\r?\n/).forEach(raw=>{
      const line = raw.trim();
      if(!line || /^plan$/i.test(line)) return;
      const m = line.match(/^(\d{4})(\d{2})(\d{2}):([A-Za-z]{2})(\d{1,3})$/);
      if(!m) return;
      const y=+m[1], mo=+m[2], d=+m[3];
      if(mo<1 || mo>12 || d<1 || d>31) return;
      out.push({ y, mo, d, dateStr:`${m[1]}-${m[2]}-${m[3]}`, vehicleId:normVehId(m[4]+m[5]), done:false, warned:false });
    });
    out.sort((a,b)=> (a.y-b.y)||(a.mo-b.mo)||(a.d-b.d));
    return out;
  }
  function previewMantenimiento(parsed){
    const rows = parsed.slice(0,6).map(r=> `${r.dateStr} · ${r.vehicleId} · día completo (00:00–23:59)`).join('<br>');
    return rows + (parsed.length>6 ? `<br>… y ${parsed.length-6} registro(s) más` : '');
  }
  function commitMantenimientoFile(parsed){
    mantenimientoQueue = parsed;
    const before = incidents.filter(i=>i.type==='mantenimiento').length;
    activateScheduledMantenimiento();
    const inmediatos = incidents.filter(i=>i.type==='mantenimiento').length - before;
    addLog(`Plan de mantenimiento preventivo cargado: <b>${parsed.length}</b> registros (${inmediatos} aplicados de inmediato, el resto en cola).`, 'accent');
    return {count:parsed.length, inmediatos};
  }
