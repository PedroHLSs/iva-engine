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

// Regra R01: o cClassTrib do item existe no catálogo na data da nota? Gravidade: crítica. Só vira achado se a data estiver dentro do período que a tabela carregada cobre.
public final class RegraClassificacaoTributariaExiste extends RegraDeItem {

    public static final String ID = "R01";
    public static final String VERSAO = "1.0.0";

    static final String TABELA = "catalogo:classificacaoTributaria";

    private final ProcedenciaNormativa cobertura;

    // Construtor que recebe o período coberto pela tabela de classificações tributárias.
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

    // Aplica a regra: sem cClassTrib ou fora do período coberto, NAO_AVALIADO; achou no catálogo, CONFORME; não achou, gera achado.
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

    // Método auxiliar que monta a mensagem para quando a tabela não cobre a data da nota.
    private String motivoDeCoberturaInsuficiente(Documento documento) {
        return ("A tabela de classificações tributárias carregada cobre a partir de %s%s e não alcança a "
                + "data de emissão %s. Sem cobertura, silêncio do catálogo é falta de dado, não ausência "
                + "do código.").formatted(
                cobertura.vigenciaInicio(),
                cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                documento.dataEmissao());
    }
}
