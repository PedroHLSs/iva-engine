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

// Regra R02: o CST do item combina com o cClassTrib, segundo o catálogo? Gravidade: moderada. Se o código não estiver no catálogo ou não tiver lista de CST, vira NAO_AVALIADO.
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

    // Aplica a regra: compara cada CST do item com a lista de CSTs aceitos para o cClassTrib e aponta os que não estão na lista.
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

    // Método auxiliar que junta os CSTs do item, sempre primeiro o do IBS e depois o da CBS, para as evidências saírem na mesma ordem.
    private static List<CstDeclarado> declarados(ItemDocumento item) {
        List<CstDeclarado> declarados = new ArrayList<>();
        item.cstIbs().ifPresent(cst -> declarados.add(new CstDeclarado("cstIbs", cst)));
        item.cstCbs().ifPresent(cst -> declarados.add(new CstDeclarado("cstCbs", cst)));
        return List.copyOf(declarados);
    }

    // Método auxiliar que devolve os CSTs aceitos em ordem alfabética, para a saída ser sempre igual.
    private static List<String> cstsCompativeisOrdenados(ClassificacaoTributaria classificacao) {
        return classificacao.cstsCompativeis().stream().map(CodigoCst::valor).sorted().toList();
    }

    // Guarda um CST que veio no item e o nome do campo onde ele veio.
    private record CstDeclarado(String campo, CodigoCst cst) {
    }
}
