// Parseo de los archivos de entrada del curso: ventas (##d##h##m:...) y bloqueos (rango de fechas:...).
// Depende de: core/state.js, sim/blockages.js
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

  function loadVentasFile(text){
    const parsed = parseVentas(text);
    if(!parsed.length) return {ok:false};
    ventasQueue = parsed; ventasPtr = 0;
    let historicos = 0;
    while(ventasPtr < ventasQueue.length && ventasQueue[ventasPtr].arriveMin <= simMin){
      pushOrderFromRecord(ventasQueue[ventasPtr++]);
      historicos++;
    }
    addLog(`Archivo de pedidos cargado: <b>${parsed.length}</b> registros (${historicos} históricos registrados de inmediato, ${parsed.length-historicos} proyectados en cola).`, 'accent');
    return {ok:true, count:parsed.length, historicos};
  }

  function loadBloqueosFile(text){
    const parsed = parseBloqueos(text);
    if(!parsed.length) return {ok:false};
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
    return {ok:true, count:parsed.length, vigentes};
  }

