package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

/**
 * Caso de uso: importar um catálogo normativo.
 *
 * <p>A validação relevante já aconteceu quando a {@link CargaDeCatalogo} foi
 * construída — formato dos registros, cobertura declarada e ausência de
 * vigências sobrepostas. Aqui a carga só é gravada. O serviço existe para que o
 * ponto de entrada (a linha de comando) não fale com o repositório direto e para
 * que o resumo devolvido ao usuário seja o mesmo qualquer que seja a origem dos
 * arquivos.</p>
 */
public final class ServicoDeImportacaoDeCatalogo {

    private final RepositorioDeCargaDeCatalogo repositorio;

    public ServicoDeImportacaoDeCatalogo(RepositorioDeCargaDeCatalogo repositorio) {
        if (repositorio == null) {
            throw new CatalogoInvalido("A importação de catálogo precisa de um repositório onde gravar.");
        }
        this.repositorio = repositorio;
    }

    /** Grava a carga e devolve o resumo do que entrou. */
    public ResumoDaImportacao importar(CargaDeCatalogo carga) {
        if (carga == null) {
            throw new CatalogoInvalido("Não há carga de catálogo a importar.");
        }
        repositorio.salvar(carga);
        return new ResumoDaImportacao(
                carga.versao(),
                carga.classificacoesTributarias().size(),
                carga.registrosDeNcm().size(),
                carga.itensDeAnexo().size(),
                carga.aliquotas().size());
    }

    /** O que foi gravado, por tabela. */
    public record ResumoDaImportacao(
            String versao,
            int classificacoesTributarias,
            int registrosDeNcm,
            int itensDeAnexo,
            int aliquotas) {

        /** Total de registros gravados. */
        public int total() {
            return classificacoesTributarias + registrosDeNcm + itensDeAnexo + aliquotas;
        }
    }
}
