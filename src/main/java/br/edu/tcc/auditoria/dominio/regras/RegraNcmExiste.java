package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;

import java.util.List;
import java.util.Optional;

// Regra R06: o NCM do item existe no catálogo na data da nota? Gravidade: crítica. Só vira achado se a data estiver dentro do período que a tabela carregada cobre.
public final class RegraNcmExiste extends RegraDeItem {

    public static final String ID = "R06";
    public static final String VERSAO = "1.0.0";

    static final String TABELA = "catalogo:ncm";

    private final ProcedenciaNormativa cobertura;

    // Construtor que recebe o período coberto pela tabela de NCM.
    public RegraNcmExiste(ProcedenciaNormativa coberturaDaTabela) {
        this.cobertura = exigirCobertura(coberturaDaTabela, "NCM");
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

    // Aplica a regra: sem NCM ou fora do período coberto, NAO_AVALIADO; achou no catálogo, CONFORME; não achou, gera achado.
    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<Ncm> ncm = item.ncm();
        if (ncm.isEmpty()) {
            return naoAvaliada(item, documento, "O item não declarou NCM; não há código a procurar no catálogo.");
        }
        if (!cobertura.vigenteEm(documento.dataEmissao())) {
            return naoAvaliada(item, documento, motivoDeCoberturaInsuficiente(documento));
        }
        if (contexto.registroNcm(ncm.get()).isPresent()) {
            return conforme(item, documento);
        }

        return comAchado(
                item,
                documento,
                List.of(
                        doDocumento("ncm", item, ncm.get().valor()),
                        daTabela("ncm", TABELA, cobertura.fonteNormativa(), Optional.empty(), Optional.empty())),
                cobertura.fonteNormativa(),
                cobertura.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "NCM não reconhecido não permite chegar a tratamento de referência nem a diferença de valor."));
    }

    // Método auxiliar que monta a mensagem para quando a tabela não cobre a data da nota.
    private String motivoDeCoberturaInsuficiente(Documento documento) {
        return ("A tabela de NCM carregada cobre a partir de %s%s e não alcança a data de emissão %s. "
                + "Sem cobertura, silêncio do catálogo é falta de dado, não ausência do código.").formatted(
                cobertura.vigenciaInicio(),
                cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                documento.dataEmissao());
    }
}
