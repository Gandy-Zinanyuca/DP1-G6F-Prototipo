// Dibuja el mapa en <canvas> (fondo, calles, almacenes, vehículos, pedidos, bloqueos, rutas, selección).
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
      const r = wh.infinite ? 13 : 11.5;
      const levelColor = css('--'+warehouseLevel(wh));
      const headCy = p.y - r*1.7;

      // halo de fondo, para que el almacén contraste con las calles/manzanas y se note desde lejos
      ctx.beginPath(); ctx.arc(p.x, headCy, r+7, 0, Math.PI*2);
      ctx.fillStyle = color_mix_fallback('--surface', 0.9); ctx.fill();

      // anillo semáforo: verde/ámbar/rojo según el nivel de stock (infinito = siempre verde)
      ctx.beginPath(); ctx.arc(p.x, headCy, r+4, 0, Math.PI*2);
      ctx.strokeStyle = levelColor; ctx.lineWidth = 3; ctx.stroke();

      ctx.save();
      ctx.shadowColor = 'rgba(0,0,0,.3)'; ctx.shadowBlur = 6; ctx.shadowOffsetY = 2;
      pinPath(ctx, p.x, p.y, r);
      ctx.fillStyle = css('--accent');
      ctx.fill();
      ctx.restore();

      ctx.strokeStyle = css('--surface'); ctx.lineWidth = 1.8;
      pinPath(ctx, p.x, p.y, r);
      ctx.stroke();

      // ícono de almacén (estantería) dentro de la cabeza del pin — distinto del glifo genérico de paquete
      ctx.strokeStyle = css('--surface'); ctx.lineWidth = 1.4; ctx.lineJoin='round';
      ctx.strokeRect(p.x-4.5, headCy-4.5, 9, 9);
      ctx.beginPath(); ctx.moveTo(p.x-4.5, headCy); ctx.lineTo(p.x+4.5, headCy); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(p.x, headCy-4.5); ctx.lineTo(p.x, headCy+4.5); ctx.stroke();

      ctx.font = "700 11px 'Sora', sans-serif";
      ctx.fillStyle = css('--ink');
      ctx.textAlign = 'center';
      ctx.fillText(wh.name, p.x, p.y - r*3.35);

      // etiqueta con el stock, visible siempre en el mapa (no solo al pasar el mouse)
      const stockLabel = wh.infinite ? 'Stock ∞' : `${wh.stock} / ${wh.capacity}`;
      ctx.font = "600 10px 'IBM Plex Mono', monospace";
      const stockY = p.y - r*2.15;
      const stockW = ctx.measureText(stockLabel).width + 10;
      ctx.fillStyle = levelColor;
      if(ctx.roundRect){ ctx.beginPath(); ctx.roundRect(p.x-stockW/2, stockY-8, stockW, 16, 4); ctx.fill(); }
      else ctx.fillRect(p.x-stockW/2, stockY-8, stockW, 16);
      ctx.fillStyle = '#fff'; ctx.textBaseline = 'middle';
      ctx.fillText(stockLabel, p.x, stockY);
      ctx.textBaseline = 'alphabetic';

      if(selected && selected.type==='warehouse' && selected.id===wh.id){
        ctx.strokeStyle = css('--accent'); ctx.lineWidth = 2; ctx.setLineDash([3,3]);
        ctx.beginPath(); ctx.arc(p.x, headCy, r+11, 0, Math.PI*2); ctx.stroke();
        ctx.setLineDash([]);
      }
    });
  }
  // aproximación de color-mix para <canvas> (color-mix() de CSS no aplica a fillStyle de canvas)
  function color_mix_fallback(varName, alpha){
    const hex = css(varName);
    return hex + Math.round(alpha*255).toString(16).padStart(2,'0');
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
      if(selected && selected.type==='bloqueo' && selected.ref===inc){
        ctx.save();
        ctx.strokeStyle = css('--accent'); ctx.lineWidth = 9; ctx.lineCap='round'; ctx.globalAlpha=0.35;
        ctx.beginPath();
        inc.nodes.forEach((n,i)=>{ const p=S(n); if(i===0) ctx.moveTo(p.x,p.y); else ctx.lineTo(p.x,p.y); });
        ctx.stroke();
        ctx.restore();
      }
      ctx.strokeStyle = css('--critical'); ctx.lineWidth = 5; ctx.lineCap = 'round';
      ctx.beginPath();
      inc.nodes.forEach((n,i)=>{ const p=S(n); if(i===0) ctx.moveTo(p.x,p.y); else ctx.lineTo(p.x,p.y); });
      ctx.stroke();
      // franja diagonal tipo "cinta de peligro", para que el tramo se lea como bloqueo y no solo como una calle roja
      ctx.save();
      ctx.strokeStyle = css('--surface'); ctx.lineWidth = 1.4; ctx.setLineDash([4,5]);
      ctx.beginPath();
      inc.nodes.forEach((n,i)=>{ const p=S(n); if(i===0) ctx.moveTo(p.x,p.y); else ctx.lineTo(p.x,p.y); });
      ctx.stroke();
      ctx.restore();
      ctx.fillStyle = css('--critical');
      [inc.nodes[0], inc.nodes[inc.nodes.length-1]].forEach(n=>{
        const p = S(n);
        ctx.beginPath(); ctx.arc(p.x,p.y,4,0,Math.PI*2); ctx.fill();
      });

      // etiqueta explícita "Bloqueo" en el punto medio del tramo — no solo un color, un texto
      const mid = inc.nodes[Math.floor(inc.nodes.length/2)];
      const pm = S(mid);
      const label = 'Bloqueo';
      ctx.save();
      ctx.font = "600 10.5px 'Sora', sans-serif";
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      const padX = 6, labelY = pm.y - 20;
      const labelW = ctx.measureText(label).width + padX*2;
      ctx.fillStyle = css('--critical');
      if(ctx.roundRect){ ctx.beginPath(); ctx.roundRect(pm.x-labelW/2, labelY-9, labelW, 18, 5); ctx.fill(); }
      else ctx.fillRect(pm.x-labelW/2, labelY-9, labelW, 18);
      ctx.fillStyle = '#fff';
      ctx.fillText(label, pm.x, labelY);
      ctx.restore();
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
      ctx.lineCap = 'round'; ctx.lineJoin = 'round';
      const p0 = S(v.pos);
      const pathPts = [p0];
      for(let i=v.pathIdx+1; i<v.path.length; i++) pathPts.push(S(v.path[i]));

      // halo claro debajo, para que la línea de color se distinga del fondo de la calle
      ctx.strokeStyle = css('--surface'); ctx.lineWidth = 4.5; ctx.globalAlpha = 0.85;
      ctx.beginPath();
      pathPts.forEach((p,i)=> i===0 ? ctx.moveTo(p.x,p.y) : ctx.lineTo(p.x,p.y));
      ctx.stroke();

      // línea de ruta en el color del tipo de vehículo, bien sólida y visible
      ctx.strokeStyle = col; ctx.lineWidth = 2.6; ctx.globalAlpha = 1;
      ctx.setLineDash([7,4]);
      ctx.beginPath();
      pathPts.forEach((p,i)=> i===0 ? ctx.moveTo(p.x,p.y) : ctx.lineTo(p.x,p.y));
      ctx.stroke();
      ctx.setLineDash([]);
    });
  }

  function drawVehicles(){
    vehicles.forEach(v=>{
      if(v.state==='idle' || v.state==='break') return;
      if(!typeFilter[v.type]) return;
      const p = S(v.pos);
      const t = VEHICLE_TYPES[v.type];
      const col = css(t.color);

      if((selected && selected.type==='vehicle' && selected.id===v.id) || v.state==='broken' || v.state==='maintenance'){
        let ringColor = '--accent';
        if(v.state==='broken'){
          const inc = incidents.find(i=>i.type==='falla' && i.vehicleId===v.id);
          ringColor = (inc && inc.falla.color) || '--critical'; // color propio de cada tipo de avería (1/2/3)
        } else if(v.state==='maintenance'){
          ringColor = '--ink-3';
        }
        ctx.strokeStyle = css(ringColor);
        ctx.lineWidth = 2; ctx.setLineDash([3,3]);
        ctx.beginPath(); ctx.arc(p.x, p.y, 14, 0, Math.PI*2); ctx.stroke();
        ctx.setLineDash([]);

        // insignia de tipo de avería, junto al vehículo, para diferenciar tipo 1/2/3 de un vistazo
        if(v.state==='broken'){
          const inc = incidents.find(i=>i.type==='falla' && i.vehicleId===v.id);
          if(inc){
            ctx.beginPath(); ctx.arc(p.x+12, p.y-12, 5, 0, Math.PI*2);
            ctx.fillStyle = css(inc.falla.color); ctx.fill();
            ctx.lineWidth = 1.5; ctx.strokeStyle = css('--surface'); ctx.stroke();
          }
        }
      }

      // disco de fondo siempre opaco detrás del vehículo: nada (rutas, bloqueos, otros vehículos) debe
      // restarle visibilidad — el vehículo en movimiento es el elemento más importante del mapa
      ctx.beginPath(); ctx.arc(p.x, p.y, 9.5, 0, Math.PI*2);
      ctx.fillStyle = css('--surface'); ctx.fill();

      ctx.save();
      ctx.shadowColor='rgba(0,0,0,.28)'; ctx.shadowBlur=3; ctx.shadowOffsetY=1;
      ctx.font = "16px sans-serif";
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      // alinea el ícono con el tramo que recorre, sin dejarlo nunca "al revés": si la dirección apunta
      // hacia la izquierda de la pantalla, se refleja en espejo en vez de rotar más allá de ±90°
      if((v.state==='toClient' || v.state==='returning') && typeof v.heading==='number'){
        let dx = Math.cos(v.heading), dy = -Math.sin(v.heading); // mundo (Y arriba) -> pantalla (Y abajo)
        const flip = dx < 0;
        if(flip) dx = -dx;
        const screenAngle = Math.atan2(dy, dx); // siempre queda en (-90°, 90°]
        ctx.translate(p.x, p.y);
        if(flip) ctx.scale(-1, 1);
        ctx.rotate(screenAngle);
        ctx.fillText(t.emoji, 0, 0);
      } else {
        ctx.fillText(t.emoji, p.x, p.y);
      }
      ctx.restore();

      if(v.order){
        const order = orders.find(o=>o.id===v.order);
        if(order){
          const frac = Math.min(1, order.qty / v.capacity);
          const loadColor = css('--'+riskLevel(frac*100)); // semáforo de utilización de carga
          ctx.fillStyle = css('--surface');
          ctx.fillRect(p.x-8, p.y+9, 16, 3);
          ctx.fillStyle = loadColor;
          ctx.fillRect(p.x-8, p.y+9, 16*frac, 3);
          // lectura numérica de la carga (paquetes / capacidad)
          ctx.save();
          ctx.font = "8px 'IBM Plex Mono', monospace";
          ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
          ctx.fillStyle = css('--ink-2');
          ctx.fillText(`${order.qty}/${v.capacity}`, p.x, p.y+18);
          ctx.restore();

          // código del pedido que se está atendiendo, visible encima del vehículo
          const codeLabel = '#'+order.id;
          ctx.save();
          ctx.font = "9px 'IBM Plex Mono', monospace";
          ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
          const labelY = p.y-15;
          const labelW = ctx.measureText(codeLabel).width + 8;
          ctx.fillStyle = css('--surface');
          ctx.globalAlpha = 0.92;
          if(ctx.roundRect){ ctx.beginPath(); ctx.roundRect(p.x-labelW/2, labelY-7, labelW, 14, 4); ctx.fill(); }
          else ctx.fillRect(p.x-labelW/2, labelY-7, labelW, 14);
          ctx.globalAlpha = 1;
          ctx.strokeStyle = css('--border'); ctx.lineWidth = 1;
          if(ctx.roundRect) ctx.stroke();
          ctx.fillStyle = css('--ink');
          ctx.fillText(codeLabel, p.x, labelY);
          ctx.restore();
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
    if(showRoutes) drawRoutes();
    drawTrails();
    drawWarehouses();
    drawOrders();
    drawFlashes();
    drawVehicles();
    updateScaleBar();
    if(selected) refreshSelectionPanel();
  }
