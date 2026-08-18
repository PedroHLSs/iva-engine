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

/**
 * R06 — o NCM declarado no item consta do catálogo de NCM vigente na data de
 * emissão do documento?
 *
 * <p>A pergunta só faz sentido datada: a tabela de NCM muda com o tempo, e um
 * código que existe hoje pode não existir na data em que o documento foi
 * emitido — e vice-versa. Quem resolve a data é o {@link ContextoNormativo}; a
 * regra apenas pergunta.</p>
 *
 * <p>O construtor validou a forma do NCM (oito dígitos) lá atrás; aqui se
 * verifica a existência, que é outra coisa. Um NCM com forma válida e sem
 * registro no catálogo deixa o item sem âncora para as regras que dependem de
 * anexo, daí a severidade crítica.</p>
 *
 * <p>Sobre por que "o catálogo nada diz" vira achado nesta regra e não nas
 * outras, ver {@link CoberturaDoCatalogo}.</p>
 */
public final class RegraNcmExiste extends RegraDeItem {

    public static final String ID = "R06";
    public static final String VERSAO = "1.0.0";

    static final String TABELA = "catalogo:ncm";

    private final ProcedenciaNormativa cobertura;

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

    private String motivoDeCoberturaInsuficiente(Documento documento) {
        return ("A tabela de NCM carregada cobre a partir de %s%s e não alcança a data de emissão %s. "
                + "Sem cobertura, silêncio do catálogo é falta de dado, não ausência do código.").formatted(
                cobertura.vigenciaInicio(),
                cobertura.vigenciaFim().map(" até %s"::formatted).orElse(""),
                documento.dataEmissao());
    }
}
