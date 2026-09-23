package br.edu.tcc.auditoria.aplicacao.catalogo;

import java.util.Optional;

// Repositório utilizado para gravar as cargas de catálogo normativo importadas.
public interface RepositorioDeCargaDeCatalogo {

    // Grava a carga inteira sem sobrescrever as anteriores; recusa se já houver carga com a mesma versão.
    void salvar(CargaDeCatalogo carga);

    // Retorna a versão da carga mais recente, ou vazio se nenhum catálogo foi importado ainda.
    Optional<String> versaoDaCargaMaisRecente();
}
