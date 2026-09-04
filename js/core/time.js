// Helpers de tiempo: hora del día, formato HH:MM, turnos (buildShifts/currentShift) y detección de refrigerio.
// Depende de: core/state.js
"use strict";
  /* =================== TIME HELPERS =================== */
  function todMin(){ return ((simMin % 1440) + 1440) % 1440; }
  function fmtTime(m){
    const mm = ((m%1440)+1440)%1440;
    const h = Math.floor(mm/60), mi = Math.floor(mm%60);
    return String(h).padStart(2,'0')+':'+String(mi).padStart(2,'0');
  }
  // construye los 3 turnos (8h cada uno) a partir de shiftStarts; el refrigerio cae 4h-5h después del inicio de turno
  function buildShifts(){
    return shiftStarts.map((start,i)=>{
      const st = ((start%1440)+1440)%1440;
      return { start:st, dur:SHIFT_DUR, label:`Turno ${i+1} · ${fmtTime(st)}–${fmtTime(st+SHIFT_DUR)}` };
    });
  }
  let SHIFTS = buildShifts();
  function minutesIntoShift(t, s){ return ((t - s.start) % 1440 + 1440) % 1440; }
  function currentShift(){
    const t = todMin();
    for(const s of SHIFTS){ if(minutesIntoShift(t, s) < s.dur) return s; }
    return SHIFTS[0];
  }
  function inMeal(shift){
    const m = minutesIntoShift(todMin(), shift);
    return m >= 4*60 && m < 5*60;
  }

