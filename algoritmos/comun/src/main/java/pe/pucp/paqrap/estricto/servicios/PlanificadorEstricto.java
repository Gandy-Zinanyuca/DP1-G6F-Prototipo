package pe.pucp.paqrap.estricto.servicios;

import pe.pucp.paqrap.estricto.modelo.*;

public interface PlanificadorEstricto {
    ResultadoPlanificacion planificar(EstadoOperacion estado, ParametrosOperacion parametros);
}
