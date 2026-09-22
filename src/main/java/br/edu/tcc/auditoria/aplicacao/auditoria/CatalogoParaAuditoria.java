package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativoNaData;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioAliquota;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RepositorioNcm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

/**
 * O catálogo carregado para uma rodada de auditoria, capaz de se resolver na
 * data de cada documento.
 *
 * <p>É aqui que a resolução por vigência acontece na prática: cada documento
 * recebe um {@link ContextoNormativo} montado na <strong>sua</strong> data de
 * emissão, e não na data em que a auditoria está rodando. Auditar hoje um
 * documento emitido há dois anos tem de confrontá-lo com a tabela que valia
 * quando ele foi emitido; qualquer outra coisa apontaria incoerência inventada
 * pela passagem do tempo.</p>
 *
 * <h2>A procedência vem junto, e não é enfeite</h2>
 *
 * <p>{@code natureza} diz, por tabela, se o conteúdo é transcrição de fonte
 * normativa ou dado de demonstração. Ela viaja com o catálogo porque a tela que
 * exibe o tratamento precisa avisar quem lê — e porque um aviso que dependesse de
 * configuração separada seria esquecido exatamente na instalação em que importa.
 * Carga anterior à declaração vem com {@link NaturezaDaCarga#naoDeclarada()}, que
 * <strong>não</strong> é sinônimo de normativa.</p>
 *
 * @param versao    versão da carga de catálogo, registrada na execução
 * @param cobertura o que esta carga declara cobrir, por tabela
 * @param natureza  a procedência declarada de cada tabela desta carga
 */
public record CatalogoParaAuditoria(
        String versao,
        CoberturaDoCatalogo cobertura,
        NaturezaDaCarga natureza,
        RepositorioClassificacaoTributaria classificacoesTributarias,
        RepositorioNcm registrosDeNcm,
        RepositorioItemAnexo itensDeAnexo,
        RepositorioAliquota aliquotas) implements ProvedorDeContextoNormativo {

    public CatalogoParaAuditoria {
        if (versao == null || versao.isBlank()) {
            throw new AuditoriaInvalida(
                    "O catálogo carregado precisa de versão: a execução a registra para que o relatório "
                            + "diga contra qual catálogo foi produzido.");
        }
        exigir(cobertura, "a cobertura declarada da carga");
        exigir(natureza, "a procedência declarada da carga");
        exigir(classificacoesTributarias, "o repositório de classificações tributárias");
        exigir(registrosDeNcm, "o repositório de NCM");
        exigir(itensDeAnexo, "o repositório de itens de anexo");
        exigir(aliquotas, "o repositório de alíquotas");
    }

    @Override
    public ContextoNormativo contextoPara(Documento documento) {
        if (documento == null) {
            throw new AuditoriaInvalida("Não há documento para o qual montar contexto normativo.");
        }
        return new ContextoNormativoNaData(
                documento.dataEmissao(),
                classificacoesTributarias,
                registrosDeNcm,
                itensDeAnexo,
                aliquotas);
    }

    private static void exigir(Object valor, String oQueFalta) {
        if (valor == null) {
            throw new AuditoriaInvalida("O catálogo carregado precisa de %s.".formatted(oQueFalta));
        }
    }
}
