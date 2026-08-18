package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.util.List;
import java.util.Optional;

/**
 * R01 — o {@code cClassTrib} declarado no item consta do catálogo vigente na
 * data de emissão do documento?
 *
 * <p>É a verificação mais elementar do conjunto, e a que sustenta as demais:
 * enquanto o código não for reconhecido, nada mais sobre ele pode ser afirmado.
 * Daí a severidade crítica — o grupo de IBS/CBS do item fica ininterpretável.</p>
 *
 * <p>Repare que a regra não sabe quais códigos existem, e não é isso que ela
 * verifica. Ela pergunta ao catálogo e reporta o que ouviu. A lista de códigos
 * válidos é conteúdo normativo e mora nos CSVs importados.</p>
 *
 * <h2>Vazio do catálogo, aqui, é resposta — mas só dentro da cobertura</h2>
 *
 * <p>Esta é a única regra do conjunto para a qual "o catálogo nada diz" é o
 * próprio achado. Se ela devolvesse {@code NAO_AVALIADO} sempre que o código não
 * fosse encontrado, jamais apontaria nada. Por outro lado, um catálogo não
 * carregado responderia vazio para todo código, e a regra acusaria o documento
 * inteiro sem base. A {@link CoberturaDoCatalogo} separa os dois casos: fora da
 * vigência coberta pela carga, o resultado é {@code NAO_AVALIADO}.</p>
 */
public final class RegraClassificacaoTributariaExiste extends RegraDeItem {

    public static final String ID = "R01";
    public static final String VERSAO = "1.0.0";

    static final String TABELA = "catalogo:classificacaoTributaria";

    private final ProcedenciaNormativa cobertura;

    public RegraClassificacaoTributariaExiste(ProcedenciaNormativa coberturaDaTabela) {
        this.cobertura = exigirCobertura(coberturaDaTabela, "classificações tributárias");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String versao() {
        return VERSAO;
    }

    @Override
    public Severidade severidade() {
        return Severidade.CRITICA;
    }

    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou cClassTrib; não há código a procurar no catálogo.");
        }
        if (!cobertura.vigenteEm(documento.dataEmissao())) {
            return naoAvaliada(item, documento, motivoDeCoberturaInsuficiente(documento));
        }
        if (contexto.classificacaoTributaria(codigo.get()).isPresent()) {
            return conforme(item, documento);
        }

        return comAchado(
                item,
                documento,
                List.of(
                        doDocumento("cClassTrib", item, codigo.get().valor()),
                        daTabela(
                                "cClassTrib",
                                TABELA,
                                cobertura.fonteNormativa(),
                                Optional.empty(),
                                Optional.empty())),
                cobertura.fonteNormativa(),
                cobertura.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "O cClassTrib não foi reconhecido, então não há tratamento de referência "
                                + "com que comparar o valor declarado."));
    }

    private String motivoDeCoberturaInsuficiente(Documento documento) {
        return ("A tabela de classificações tributárias carregada cobre a partir de %s%s e não alcança a "
                + "data de emissão %s. Sem cobertura, silêncio do catálogo é falta de dado, não ausência "
                + "do código.").formatted(
                cobertura.vigenciaInicio(),
                cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                documento.dataEmissao());
    }
}
