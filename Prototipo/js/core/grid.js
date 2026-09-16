// Geometría de la retícula y pathfinding (BFS) sobre los nodos de 1 km, respetando bloqueos.
// Depende de: core/config.js, core/state.js
"use strict";
  /* =================== GRID GEOMETRY & PATHFINDING =================== */
  function dist(a,b){ return Math.hypot(a.x-b.x, a.y-b.y); }
  function roundNode(pos){ return { x: Math.round(pos.x), y: Math.round(pos.y) }; }

  function edgeKey(a,b){
    if(a.x>b.x || (a.x===b.x && a.y>b.y)){ const t=a; a=b; b=t; }
    return a.x+','+a.y+'-'+b.x+','+b.y;
  }
  function neighborsOf(n){
    const out=[];
    if(n.x>0) out.push({x:n.x-1,y:n.y});
    if(n.x<GRID_W) out.push({x:n.x+1,y:n.y});
    if(n.y>0) out.push({x:n.x,y:n.y-1});
    if(n.y<GRID_H) out.push({x:n.x,y:n.y+1});
    return out;
  }

  // shortest path over the node grid (BFS — every edge is 1km and two-way; blocked edges are impassable
  // and cannot be "turned into" either, matching the U-turn rule for blocked nodes)
  function computeRoute(from, to){
    from = roundNode(from); to = roundNode(to);
    const startKey = from.x+','+from.y, goalKey = to.x+','+to.y;
    if(startKey===goalKey) return [from];
    const cameFrom = new Map();
    const visited = new Set([startKey]);
    const queue = [from];
    let qi = 0;
    while(qi < queue.length){
      const cur = queue[qi++];
      if(cur.x+','+cur.y === goalKey) break;
      for(const nb of neighborsOf(cur)){
        const nbKey = nb.x+','+nb.y;
        if(visited.has(nbKey)) continue;
        if(blockedEdges.has(edgeKey(cur,nb))) continue;
        visited.add(nbKey);
        cameFrom.set(nbKey, cur);
        queue.push(nb);
      }
    }
    if(!visited.has(goalKey)) return [from, to]; // no debería ocurrir con bloqueos tipo polígono abierto
    const path = [to];
    let curKey = goalKey;
    while(curKey !== startKey){
      const prev = cameFrom.get(curKey);
      path.unshift(prev);
      curKey = prev.x+','+prev.y;
    }
    return path;
  }
  function pathLength(path){
    let d=0; for(let i=1;i<path.length;i++) d+=dist(path[i-1],path[i]); return d;
  }
  function pathUsesEdges(path, fromIdx, edgeKeysArr){
    for(let i=Math.max(0,fromIdx); i<path.length-1; i++){
      if(edgeKeysArr.includes(edgeKey(path[i],path[i+1]))) return true;
    }
    return false;
  }

  function randomOrderPos(){ return { x: Math.floor(Math.random()*(GRID_W+1)), y: Math.floor(Math.random()*(GRID_H+1)) }; }
  function randomClientId(){ return 'c'+(1000+Math.floor(Math.random()*9000)); }
  function sectorOf(pos){
    const sx = Math.floor(pos.x/10)*10, sy = Math.floor(pos.y/10)*10;
    return `Sector (${sx}-${sx+10}, ${sy}-${sy+10})`;
  }
