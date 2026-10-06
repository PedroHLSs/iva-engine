package br.edu.tcc.auditoria.dominio.catalogo;

// Diz sobre o que incide a redução que o catálogo declara para um cClassTrib: a alíquota ou a base de cálculo. Os valores são categorias, e não código da norma; qual código tem qual incidência entra pela carga (coluna reducaoIncideSobre). Acrescentado em 03/10/2026 (D017), no lugar dos CST que a R05 trazia escritos em código.
public enum IncidenciaDaReducao {
    ALIQUOTA,
    BASE
}
