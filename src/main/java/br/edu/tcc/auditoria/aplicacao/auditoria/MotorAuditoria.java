package br.edu.tcc.auditoria.aplicacao.auditoria;

import br.edu.tcc.auditoria.dominio.ItemDocumento;
import br.edu.tcc.auditoria.dominio.catalogo.ContextoNormativo;
import br.edu.tcc.auditoria.dominio.regras.Avaliacao;
import br.edu.tcc.auditoria.dominio.regras.ConjuntoRegras;
import br.edu.tcc.auditoria.dominio.regras.RegraAuditoria;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

//Aplicação das regras de auditoria a documentos
public final class MotorAuditoria {

    // Audita um documento contra o catálogo já resolvido na data de emissão dele, aplicando as regras na ordem em que estão declaradas.
    public List<Avaliacao> auditar(
            DocumentoComItens documento, ContextoNormativo contexto, ConjuntoRegras conjunto) {

        exigir(documento, "Não há documento a auditar.");
        exigir(contexto, "Não há contexto normativo: o catálogo precisa ser resolvido na data do documento.");
        exigir(conjunto, "Não há conjunto de regras a aplicar.");

        List<Avaliacao> avaliacoes = new ArrayList<>();
        for (ItemDocumento item : documento.itensOrdenados()) {
            for (RegraAuditoria regra : conjunto.regras()) {
                avaliacoes.add(regra.avaliar(item, documento.documento(), contexto));
            }
        }
        return List.copyOf(avaliacoes);
    }
    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção
    public List<Avaliacao> auditar(
            List<DocumentoComItens> documentos,
            ProvedorDeContextoNormativo provedor,
            ConjuntoRegras conjunto) {

        exigir(documentos, "Não há documentos a auditar.");
        exigir(provedor, "Não há provedor de contexto normativo.");
        exigir(conjunto, "Não há conjunto de regras a aplicar.");
        if (documentos.stream().anyMatch(Objects::isNull)) {
            throw new AuditoriaInvalida("A lista de documentos não pode conter elemento nulo.");
        }
        // Garante a não duplicidade para que não haja aleatoriedade na medição
        List<Avaliacao> avaliacoes = new ArrayList<>();
        for (DocumentoComItens documento : ordenadosPorChave(documentos)) {
            ContextoNormativo contexto = provedor.contextoPara(documento.documento());
            if (contexto == null) {
                throw new AuditoriaInvalida(
                        "O provedor não construiu contexto normativo para o documento emitido em %s."
                                .formatted(documento.documento().dataEmissao()));
            }
            avaliacoes.addAll(auditar(documento, contexto, conjunto));
        }
        return List.copyOf(avaliacoes);
    }
    // Método auxiliar para ordenar documentos por chave de acesso, garantindo consistência na ordem de processamento.
    private static List<DocumentoComItens> ordenadosPorChave(List<DocumentoComItens> documentos) {
        return documentos.stream()
                .sorted(Comparator.comparing(documento -> documento.documento().chaveAcesso().valor()))
                .toList();
    }
    // Método auxiliar para verificar se um valor é nulo e lançar uma exceção com uma mensagem apropriada.
    private static void exigir(Object valor, String mensagem) {
        if (valor == null) {
            throw new AuditoriaInvalida(mensagem);
        }
    }
}
