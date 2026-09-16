// Bloqueos de tramos de calle (cadenas de nodos), su efecto sobre blockedEdges y la activación de los programados por archivo.
// Depende de: core/state.js, core/grid.js
"use strict";
  /* =================== BLOQUEOS (segmentos entre nodos, tipo polígono abierto) =================== */
  function randomBlockageChain(){
    let cur = { x: 5+Math.floor(Math.random()*(GRID_W-10)), y: 5+Math.floor(Math.random()*(GRID_H-10)) };
    const nodes = [cur];
    const steps = 3 + Math.floor(Math.random()*4);
    let dir = Math.random()<0.5 ? 'h' : 'v';
    for(let i=0;i<steps;i++){
      const dx = dir==='h' ? (Math.random()<0.5?1:-1) : 0;
      const dy = dir==='v' ? (Math.random()<0.5?1:-1) : 0;
      const next = {x:cur.x+dx, y:cur.y+dy};
      if(next.x<0||next.x>GRID_W||next.y<0||next.y>GRID_H) break;
      nodes.push(next);
      cur = next;
      if(Math.random()<0.3) dir = dir==='h' ? 'v' : 'h';
    }
    return nodes;
  }

  function addBlockageChain(nodes, until){
    if(nodes.length<2) return;
    const edges = [];
    for(let i=0;i<nodes.length-1;i++){ const k = edgeKey(nodes[i],nodes[i+1]); edges.push(k); blockedEdges.add(k); }
    incidents.push({type:'bloqueo', nodes, edges, until, since:simMin});
    vehicles.forEach(v=>{
      if((v.state==='toClient' || v.state==='returning') && v.path && pathUsesEdges(v.path, v.pathIdx, edges)){
        const dest = v.path[v.path.length-1];
        v.path = computeRoute(v.pos, dest);
        v.pathIdx = 0;
        addLog(`Ruta de ${v.id} recalculada: tramo bloqueado por delante.`, 'warning');
      }
    });
  }
  function removeBlockageChain(inc){ inc.edges.forEach(k=>blockedEdges.delete(k)); }

  // activa los bloqueos que ya cargaron desde archivo y cuya ventana [startMin,endMin) esté vigente
  function activateScheduledBlockages(){
    if(!bloqueosQueue) return;
    bloqueosQueue.forEach(rec=>{
      const already = incidents.some(inc=>inc.fromFile===rec);
      if(!already && rec.startMin<=simMin && simMin<rec.endMin){
        addBlockageChain(rec.nodes, rec.endMin);
        incidents[incidents.length-1].fromFile = rec;
      }
    });
  }
