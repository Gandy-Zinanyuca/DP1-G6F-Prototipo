// Zoom, paneo y clic sobre el canvas del mapa (solo navegación, no controla la simulación).
// Depende de: ui/render.js, ui/selection-panel.js
"use strict";
  /* =================== MAP INPUT: zoom / pan / click (navigation only — never simulation controls) =================== */
  let dragging=false, dragMoved=false, dragStartScreen=null, dragStartCam=null;

  canvas.addEventListener('wheel', e=>{
    e.preventDefault();
    const rect = canvas.getBoundingClientRect();
    zoomAt(e.clientX-rect.left, e.clientY-rect.top, e.deltaY<0 ? 1.18 : 1/1.18);
  }, {passive:false});

  canvas.addEventListener('mousedown', e=>{
    dragging=true; dragMoved=false;
    const rect = canvas.getBoundingClientRect();
    dragStartScreen = {x:e.clientX-rect.left, y:e.clientY-rect.top};
    dragStartCam = {cx:camera.cx, cy:camera.cy};
    canvas.classList.add('dragging');
  });
  window.addEventListener('mousemove', e=>{
    if(!dragging) return;
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX-rect.left, sy = e.clientY-rect.top;
    const dx = sx-dragStartScreen.x, dy = sy-dragStartScreen.y;
    if(Math.hypot(dx,dy)>4) dragMoved=true;
    camera.cx = dragStartCam.cx - dx/(camera.scale*W);
    camera.cy = dragStartCam.cy - dy/(camera.scale*H);
    clampCamera();
  });
  window.addEventListener('mouseup', e=>{
    if(!dragging) return;
    dragging=false; canvas.classList.remove('dragging');
    const rect = canvas.getBoundingClientRect();
    const sx = e.clientX-rect.left, sy = e.clientY-rect.top;
    if(sx>=0 && sy>=0 && sx<=rect.width && sy<=rect.height && !dragMoved) handleMapClick(sx, sy);
  });
  canvas.addEventListener('dblclick', e=>{
    const rect = canvas.getBoundingClientRect();
    zoomAt(e.clientX-rect.left, e.clientY-rect.top, 1.6);
  });

  document.getElementById('zoomIn').addEventListener('click', ()=> zoomAt(W/2,H/2,1.4));
  document.getElementById('zoomOut').addEventListener('click', ()=> zoomAt(W/2,H/2,1/1.4));
  document.getElementById('zoomReset').addEventListener('click', ()=>{ camera.scale=1; camera.cx=0.5; camera.cy=0.5; });
