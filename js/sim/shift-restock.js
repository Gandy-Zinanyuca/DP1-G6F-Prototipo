// Aplicación de refrigerios y recarga diaria de almacenes intermedios a las 23:59:59.
// Depende de: core/time.js, core/state.js
"use strict";
  /* =================== SHIFT / MEAL / RESTOCK =================== */
  let lastRestockDay = -1;
  function handleRestock(){
    const t = todMin();
    if(t >= 23*60+59 && lastRestockDay !== cycleDay){
      WAREHOUSES.noroeste.stock = WAREHOUSES.noroeste.capacity;
      WAREHOUSES.este.stock = WAREHOUSES.este.capacity;
      lastRestockDay = cycleDay;
      addLog(`Recarga de almacenes intermedios completada (23:59:59).`, 'accent');
    }
  }

  function applyMealBreaks(){
    const shift = currentShift();
    const meal = inMeal(shift);
    vehicles.forEach(v=>{
      if(v.state==='idle' && meal && Math.random()<0.003) v.state='break';
      if(v.state==='break' && !meal) v.state='idle';
    });
  }

