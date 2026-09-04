// Dibuja el mapa en <canvas> (fondo, calles, almacenes, vehículos, pedidos, bloqueos, selección).
// Depende de: core/*, sim/*
"use strict";
  /* =================== CANVAS RENDER =================== */
  const canvas = document.getElementById('mapCanvas');
  const ctx = canvas.getContext('2d');
  const bgCanvas = document.createElement('canvas');
  const bgCtx = bgCanvas.getContext('2d');
  let W=0,H=0,DPR=1;

  function resizeCanvas(){
    const rect = canvas.parentElement.getBoundingClientRect();
    DPR = window.devicePixelRatio || 1;
    W = rect.width; H = rect.height;
    canvas.width = Math.round(W*DPR);
    canvas.height = Math.round(H*DPR);
    ctx.setTransform(DPR,0,0,DPR,0,0);
    bgCanvas.width = canvas.width; bgCanvas.height = canvas.height;
    bgCtx.setTransform(DPR,0,0,DPR,0,0);
    renderBackground();
    clampCamera();
  }
  window.addEventListener('resize', resizeCanvas);
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', ()=> renderBackground());

  // pos está en km (0..70, 0..50); el origen (0,0) es la esquina inferior izquierda, por eso se invierte Y al dibujar
  function P(pos){ return { x: pos.x/GRID_W*W, y: (1 - pos.y/GRID_H)*H }; }

  /* ---- camera (zoom / pan) — pure map navigation, independent of the simulation ---- */
  function clampCamera(){
    if(W<=0 || H<=0) return; // container not laid out yet — leave camera untouched
    camera.scale = Math.min(6, Math.max(1, camera.scale));
    const vw = W/camera.scale, vh = H/camera.scale;
    const halfW = Math.min(0.5, vw/(2*W)), halfH = Math.min(0.5, vh/(2*H));
    camera.cx = Math.min(1-halfW, Math.max(halfW, camera.cx));
    camera.cy = Math.min(1-halfH, Math.max(halfH, camera.cy));
  }
  function viewRect(){
    const vw = W/camera.scale, vh = H/camera.scale;
    return { vw, vh, srcX: camera.cx*W - vw/2, srcY: camera.cy*H - vh/2 };
  }
  function S(pos){
    const nx = pos.x/GRID_W, ny = 1 - pos.y/GRID_H;
    const v = viewRect();
    return { x: (nx*W - v.srcX)*camera.scale, y: (ny*H - v.srcY)*camera.scale };
  }
  function screenToWorld(sx, sy){
    const v = viewRect();
    const nx = (v.srcX + sx/camera.scale)/W;
    const ny = (v.srcY + sy/camera.scale)/H;
    return { x: nx*GRID_W, y: (1-ny)*GRID_H };
  }
  function zoomAt(sx, sy, factor){
    const worldPt = screenToWorld(sx, sy);
    camera.scale = Math.min(6, Math.max(1, camera.scale*factor));
    camera.cx = (worldPt.x/GRID_W) + (W/2 - sx)/(camera.scale*W);
    camera.cy = (1 - worldPt.y/GRID_H) + (H/2 - sy)/(camera.scale*H);
    clampCamera();
  }

  function drawGrid(c){
    // calles locales, cada 1 km, transitables en ambos sentidos
    c.strokeStyle = css('--road-local-edge'); c.lineWidth = 0.6;
    for(let x=0;x<=GRID_W;x++){ const p1=P({x,y:0}), p2=P({x,y:GRID_H}); c.beginPath(); c.moveTo(p1.x,p1.y); c.lineTo(p2.x,p2.y); c.stroke(); }
    for(let y=0;y<=GRID_H;y++){ const p1=P({x:0,y}), p2=P({x:GRID_W,y}); c.beginPath(); c.moveTo(p1.x,p1.y); c.lineTo(p2.x,p2.y); c.stroke(); }

    // avenidas cada 10 km
    c.strokeStyle = css('--road-arterial'); c.lineWidth = 2.4;
    for(let x=0;x<=GRID_W;x+=10){ const p1=P({x,y:0}), p2=P({x,y:GRID_H}); c.beginPath(); c.moveTo(p1.x,p1.y); c.lineTo(p2.x,p2.y); c.stroke(); }
    for(let y=0;y<=GRID_H;y+=10){ const p1=P({x:0,y}), p2=P({x:GRID_W,y}); c.beginPath(); c.moveTo(p1.x,p1.y); c.lineTo(p2.x,p2.y); c.stroke(); }

    // etiquetas de eje, en km
    c.font = "500 10px 'IBM Plex Mono', monospace";
    c.fillStyle = css('--ink-3');
    c.textAlign = 'center';
    for(let x=0;x<=GRID_W;x+=10){ const p=P({x,y:0}); c.fillText(String(x), p.x, H-6); }
    c.textAlign = 'left';
    for(let y=0;y<=GRID_H;y+=10){ const p=P({x:0,y}); c.fillText(String(y), p.x+4, p.y+3); }
  }

  function renderBackground(){
    bgCtx.clearRect(0,0,W,H);
    bgCtx.fillStyle = css('--land'); bgCtx.fillRect(0,0,W,H);
    drawGrid(bgCtx);
  }

  function roundedRect(x,y,w,h,r){
    ctx.beginPath();
    ctx.moveTo(x+r,y);
    ctx.arcTo(x+w,y,x+w,y+h,r);
    ctx.arcTo(x+w,y+h,x,y+h,r);
    ctx.arcTo(x,y+h,x,y,r);
    ctx.arcTo(x,y,x+w,y,r);
    ctx.closePath();
  }

  function pinPath(c, x, y, r){
    // teardrop map-pin: circle head tapering to a point below (x,y)
    c.beginPath();
    c.arc(x, y-r*1.7, r, Math.PI*0.18, Math.PI*0.82, true);
    c.lineTo(x, y);
    c.closePath();
  }

  function warehouseLevel(wh){
    if(wh.infinite) return 'good';
    return riskLevel(wh.stock/wh.capacity*100);
  }

  function drawWarehouses(){
    Object.values(WAREHOUSES).forEach(wh=>{
      const p = S(wh.pos);
      const r = wh.infinite ? 11 : 9.5;

      ctx.save();
      ctx.shadowColor = 'rgba(0,0,0,.28)'; ctx.shadowBlur = 5; ctx.shadowOffsetY = 2;
      pinPath(ctx, p.x, p.y, r);
      ctx.fillStyle = css('--accent');
      ctx.fill();
      ctx.restore();

      ctx.strokeStyle = css('--surface'); ctx.lineWidth = 1.6;
      pinPath(ctx, p.x, p.y, r);
      ctx.stroke();

      // package glyph inside the pin head
      ctx.strokeStyle = css('--surface'); ctx.lineWidth = 1.3;
      ctx.beginPath();
      const cy = p.y - r*1.7;
      ctx.moveTo(p.x-3.5, cy-2.4); ctx.lineTo(p.x, cy-4.2); ctx.lineTo(p.x+3.5, cy-2.4);
      ctx.lineTo(p.x+3.5, cy+2); ctx.lineTo(p.x, cy+3.6); ctx.lineTo(p.x-3.5, cy+2); ctx.closePath();
      ctx.stroke();

      if(!wh.infinite){
        const pct = wh.stock/wh.capacity;
        const gw = 26, gx = p.x-gw/2, gy = p.y+7;
        ctx.fillStyle = css('--surface');
        roundedRect(gx, gy, gw, 4, 2); ctx.fill();
        ctx.fillStyle = css('--'+warehouseLevel(wh));
        roundedRect(gx, gy, gw*pct, 4, 2); ctx.fill();
      }

      ctx.font = "600 11px 'Sora', sans-serif";
      ctx.fillStyle = css('--ink');
      ctx.textAlign = 'center';
      ctx.fillText(wh.name, p.x, p.y - r*3.1);

      if(selected && selected.type==='warehouse' && selected.id===wh.id){
        ctx.strokeStyle = css('--accent'); ctx.lineWidth = 2; ctx.setLineDash([3,3]);
        ctx.beginPath(); ctx.arc(p.x, p.y-r*1.7, r+7, 0, Math.PI*2); ctx.stroke();
        ctx.setLineDash([]);
      }
    });
  }

  function priorityColor(o){
    if(o.priority<=8) return css('--critical');
    if(o.priority<=18) return css('--accent-2');
    return css('--ink-3');
  }

  function drawOrders(){
    orders.forEach(o=>{
      const p = S(o.pos);
      const r = 3 + Math.min(o.qty,20)*0.18;
      const col = riskColorOf(o);
      if(o.status==='pending'){
        ctx.strokeStyle = col;
        ctx.setLineDash([2,3]);
        ctx.lineWidth=1.5;
        ctx.beginPath(); ctx.arc(p.x,p.y,r,0,Math.PI*2); ctx.stroke();
        ctx.setLineDash([]);
      } else {
        ctx.strokeStyle = col; ctx.lineWidth=1.8;
        ctx.beginPath(); ctx.arc(p.x,p.y,r,0,Math.PI*2); ctx.stroke();
      }
      if(o.priority < 36){
        ctx.fillStyle = priorityColor(o);
        ctx.beginPath(); ctx.arc(p.x, p.y-r-4, 2.1, 0, Math.PI*2); ctx.fill();
      }
    });
  }

  function drawFlashes(){
    flashes = flashes.filter(f=> (simMin - f.born) < 22);
    flashes.forEach(f=>{
      const age = (simMin - f.born)/22;
      const p = S(f.pos);
      ctx.globalAlpha = Math.max(0, 1-age);
      ctx.strokeStyle = f.color; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.arc(p.x,p.y, 4 + age*16, 0, Math.PI*2); ctx.stroke();
      ctx.globalAlpha = 1;
    });
  }

  function drawBlockages(){
    incidents.forEach(inc=>{
      if(inc.type!=='bloqueo') return;
      ctx.strokeStyle = css('--critical'); ctx.lineWidth = 4; ctx.lineCap = 'round';
      ctx.beginPath();
      inc.nodes.forEach((n,i)=>{ const p=S(n); if(i===0) ctx.moveTo(p.x,p.y); else ctx.lineTo(p.x,p.y); });
      ctx.stroke();
      ctx.fillStyle = css('--critical');
      [inc.nodes[0], inc.nodes[inc.nodes.length-1]].forEach(n=>{
        const p = S(n);
        ctx.beginPath(); ctx.arc(p.x,p.y,3.5,0,Math.PI*2); ctx.fill();
      });
    });
  }

  function drawTrails(){
    vehicles.forEach(v=>{
      if(!v.trail || v.trail.length < 2 || !typeFilter[v.type]) return;
      const col = css(VEHICLE_TYPES[v.type].color);
      ctx.lineCap = 'round'; ctx.lineJoin = 'round';
      for(let i=1;i<v.trail.length;i++){
        const a = S(v.trail[i-1]), b = S(v.trail[i]);
        ctx.globalAlpha = 0.08 + 0.32*(i/v.trail.length);
        ctx.strokeStyle = col;
        ctx.lineWidth = 3;
        ctx.beginPath(); ctx.moveTo(a.x,a.y); ctx.lineTo(b.x,b.y); ctx.stroke();
      }
      ctx.globalAlpha = 1;
    });
  }

  // ruta completa asignada a cada pedido en curso (desde la posición actual hasta el destino),
  // distinta de la estela: la estela muestra por dónde pasó, esto muestra hacia dónde va.
  function drawRoutes(){
    vehicles.forEach(v=>{
      if(!v.path || v.pathIdx>=v.path.length-1 || !typeFilter[v.type]) return;
      if(v.state!=='toClient' && v.state!=='returning') return;
      const col = css(VEHICLE_TYPES[v.type].color);
      ctx.strokeStyle = col; ctx.lineWidth = 1.4; ctx.globalAlpha = 0.45;
      ctx.setLineDash([1,5]); ctx.lineCap = 'round';
      ctx.beginPath();
      const p0 = S(v.pos); ctx.moveTo(p0.x,p0.y);
      for(let i=v.pathIdx+1; i<v.path.length; i++){ const p=S(v.path[i]); ctx.lineTo(p.x,p.y); }
      ctx.stroke();
      ctx.setLineDash([]); ctx.globalAlpha = 1;
    });
  }

  function drawVehicles(){
    vehicles.forEach(v=>{
      if(v.state==='idle' || v.state==='break') return;
      if(!typeFilter[v.type]) return;
      const p = S(v.pos);
      const t = VEHICLE_TYPES[v.type];
      const col = css(t.color);

      if((selected && selected.type==='vehicle' && selected.id===v.id) || v.state==='broken'){
        ctx.strokeStyle = css(v.state==='broken' ? '--critical' : '--accent');
        ctx.lineWidth = 2; ctx.setLineDash([3,3]);
        ctx.beginPath(); ctx.arc(p.x, p.y, 14, 0, Math.PI*2); ctx.stroke();
        ctx.setLineDash([]);
      }

      ctx.save();
      ctx.shadowColor='rgba(0,0,0,.28)'; ctx.shadowBlur=3; ctx.shadowOffsetY=1;
      ctx.font = "16px sans-serif";
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillText(t.emoji, p.x, p.y);
      ctx.restore();

      if(v.order){
        const order = orders.find(o=>o.id===v.order);
        if(order){
          const frac = Math.min(1, order.qty / v.capacity);
          ctx.fillStyle = css('--surface');
          ctx.fillRect(p.x-8, p.y+9, 16, 3);
          ctx.fillStyle = col;
          ctx.fillRect(p.x-8, p.y+9, 16*frac, 3);
        }
      }
    });
  }

  const scaleBarLine = document.getElementById('scaleBarLine');
  function updateScaleBar(){
    scaleBarLine.style.width = (5 * (W/GRID_W) * camera.scale) + 'px';
  }

  function render(){
    if(W<=0 || H<=0 || bgCanvas.width<=0 || bgCanvas.height<=0) return; // container not laid out yet
    ctx.clearRect(0,0,W,H);
    const v = viewRect();
    ctx.drawImage(bgCanvas, v.srcX*DPR, v.srcY*DPR, v.vw*DPR, v.vh*DPR, 0, 0, W, H);
    drawBlockages();
    drawRoutes();
    drawTrails();
    drawWarehouses();
    drawOrders();
    drawFlashes();
    drawVehicles();
    updateScaleBar();
    if(selected) refreshSelectionPanel();
  }

