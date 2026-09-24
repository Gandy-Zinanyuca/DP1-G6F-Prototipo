package pe.pucp.paqrap.solucion;

import pe.pucp.paqrap.modelo.Almacen;

/**
 * Inventario de un almacén en un día simulado. Los intermedios se recargan a medianoche
 * (LE033), así que el stock que puede tomar una carga depende del día en que ocurre: lo que se
 * carga después de las 00:00 sale del inventario ya renovado.
 */
public record ClaveStock(Almacen almacen, int dia) {
}
