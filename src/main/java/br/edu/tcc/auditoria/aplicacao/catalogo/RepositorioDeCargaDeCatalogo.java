package br.edu.tcc.auditoria.aplicacao.catalogo;

import java.util.Optional;

/**
 * Porta de gravação do catálogo normativo importado.
 *
 * <p>A aplicação não sabe se do outro lado há banco, arquivo ou memória, e não
 * deve saber: quem implementa é a infraestrutura.</p>
 */
public interface RepositorioDeCargaDeCatalogo {

    /**
     * Grava a carga inteira.
     *
     * <p>Cada carga é gravada como um conjunto próprio, identificado pela sua
     * versão. Importar de novo não sobrescreve a carga anterior: a auditoria usa
     * a mais recente, e as antigas permanecem para que um relatório produzido
     * contra elas continue conferível.</p>
     *
     * @throws br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido se já houver
     *         carga com a mesma versão
     */
    void salvar(CargaDeCatalogo carga);

    /** Versão da carga mais recente, vazio se nenhum catálogo foi importado ainda. */
    Optional<String> versaoDaCargaMaisRecente();
}
