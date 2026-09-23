package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

// Serviço que importa um catálogo normativo, gravando a carga já validada e devolvendo o resumo do que entrou.
public final class ServicoDeImportacaoDeCatalogo {

    private final RepositorioDeCargaDeCatalogo repositorio;

    // Construtor do serviço de importação, que recebe o repositório onde a carga será gravada.
    public ServicoDeImportacaoDeCatalogo(RepositorioDeCargaDeCatalogo repositorio) {
        if (repositorio == null) {
            throw new CatalogoInvalido("A importação de catálogo precisa de um repositório onde gravar.");
        }
        this.repositorio = repositorio;
    }

    // Grava a carga de catálogo e devolve o resumo com a quantidade de registros por tabela.
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

    // Representa o resumo da importação, com a versão e a quantidade de registros gravados por tabela.
    public record ResumoDaImportacao(
            String versao,
            int classificacoesTributarias,
            int registrosDeNcm,
            int itensDeAnexo,
            int aliquotas) {

        public int total() {
            return classificacoesTributarias + registrosDeNcm + itensDeAnexo + aliquotas;
        }
    }
}
