package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Documento;
import br.edu.tcc.auditoria.dominio.Evidencia;
import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.ValorEmRisco;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * R02 — o par formado pelo CST declarado e pelo {@code cClassTrib} declarado é
 * admitido pelo catálogo vigente na data do documento?
 *
 * <p>O item traz dois CSTs, um de IBS e um de CBS, e a regra confronta cada um
 * que tenha vindo contra o conjunto que o catálogo associa ao
 * {@code cClassTrib}. Quais pares existem é conteúdo normativo: a regra não
 * conhece nenhum, apenas compara o declarado com a lista importada.</p>
 *
 * <p>A severidade é moderada porque um par recusado é incoerência entre códigos
 * e, por si, não altera montante declarado. O que ele altera é o tratamento que
 * o documento afirma estar aplicando.</p>
 *
 * <h2>Duas ausências que não viram achado</h2>
 *
 * <ul>
 *   <li><strong>O catálogo não tem o {@code cClassTrib} na data.</strong> Sem o
 *       registro não há lista de referência, e portanto não há par a conferir.
 *       Quem aponta código inexistente é R01; R02 não repete o apontamento nem o
 *       disfarça de conformidade.</li>
 *   <li><strong>O registro existe e não lista nenhum CST compatível.</strong>
 *       Poderia ser lido como "nenhum CST é admitido", o que tornaria todo item
 *       um achado. Mas coluna preenchida em branco é indistinguível de coluna
 *       que ninguém preencheu, e transformar essa dúvida em acusação é o oposto
 *       do que uma auditoria deve fazer. Fica {@code NAO_AVALIADO}, com o motivo
 *       dizendo exatamente isso — visível no relatório, e corrigível na carga.</li>
 * </ul>
 *
 * <p>Quando só um dos dois CSTs veio, a regra julga o par que existe e diz
 * conforme sobre ele. O CST que não veio não forma par nenhum, e sua ausência é
 * assunto de R07, que sabe quais campos o catálogo exige para aquele código.</p>
 */
public final class RegraCstCompativelComClassificacao extends RegraDeItem {

    public static final String ID = "R02";
    public static final String VERSAO = "1.0.0";

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
        return Severidade.MODERADA;
    }

    @Override
    protected Avaliacao avaliarItem(ItemDocumento item, Documento documento, ContextoNormativo contexto) {
        Optional<CodigoClassificacaoTributaria> codigo = item.codigoClassificacaoTributaria();
        if (codigo.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou cClassTrib; não há par a conferir.");
        }

        Optional<ClassificacaoTributaria> registro = contexto.classificacaoTributaria(codigo.get());
        if (registro.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O catálogo nada diz sobre o cClassTrib \"%s\" na data de emissão, então não há lista "
                            + "de CSTs compatíveis a confrontar. A existência do código é verificada por %s.")
                            .formatted(codigo.get().valor(), RegraClassificacaoTributariaExiste.ID));
        }

        ClassificacaoTributaria classificacao = registro.get();
        List<String> compativeis = cstsCompativeisOrdenados(classificacao);
        if (compativeis.isEmpty()) {
            return naoAvaliada(item, documento,
                    ("O catálogo não lista nenhum CST compatível com o cClassTrib \"%s\" na vigência "
                            + "aplicável. Sem lista de referência não há par a julgar.")
                            .formatted(codigo.get().valor()));
        }

        List<CstDeclarado> declarados = declarados(item);
        if (declarados.isEmpty()) {
            return naoAvaliada(item, documento,
                    "O item não declarou CST de IBS nem de CBS; não há par a conferir.");
        }

        List<CstDeclarado> incompativeis = declarados.stream()
                .filter(declarado -> !classificacao.admiteCst(declarado.cst()))
                .toList();
        if (incompativeis.isEmpty()) {
            return conforme(item, documento);
        }

        String admitidos = String.join(", ", compativeis);
        List<Evidencia> evidencias = new ArrayList<>();
        evidencias.add(doDocumento("cClassTrib", item, codigo.get().valor()));
        for (CstDeclarado incompativel : incompativeis) {
            evidencias.add(daTabela(
                    incompativel.campo(),
                    RegraClassificacaoTributariaExiste.TABELA,
                    classificacao.fonteNormativa(),
                    Optional.of(incompativel.cst().valor()),
                    Optional.of(admitidos)));
        }

        return comAchado(
                item,
                documento,
                List.copyOf(evidencias),
                classificacao.dispositivoLegal(),
                classificacao.vigencia(),
                ValorEmRisco.naoCalculavel(
                        "Par de códigos recusado pelo catálogo não produz, por si, diferença de valor aferível."));
    }

    /**
     * CSTs efetivamente declarados, em ordem fixa.
     *
     * <p>A ordem é a de declaração no item — IBS e depois CBS — e não a de
     * iteração de nenhuma coleção, para que duas execuções sobre o mesmo
     * documento produzam as evidências na mesma sequência.</p>
     */
    private static List<CstDeclarado> declarados(ItemDocumento item) {
        List<CstDeclarado> declarados = new ArrayList<>();
        item.cstIbs().ifPresent(cst -> declarados.add(new CstDeclarado("cstIbs", cst)));
        item.cstCbs().ifPresent(cst -> declarados.add(new CstDeclarado("cstCbs", cst)));
        return List.copyOf(declarados);
    }

    /**
     * Os CSTs admitidos, em ordem alfabética.
     *
     * <p>O conjunto do registro é imutável e não promete ordem de iteração; ler
     * dele sem ordenar faria o texto da evidência variar entre execuções, e a
     * ordem determinística da saída é requisito do motor.</p>
     */
    private static List<String> cstsCompativeisOrdenados(ClassificacaoTributaria classificacao) {
        return classificacao.cstsCompativeis().stream().map(CodigoCst::valor).sorted().toList();
    }

    /** Um CST que veio no item, junto do nome do campo em que veio. */
    private record CstDeclarado(String campo, CodigoCst cst) {
    }
}
