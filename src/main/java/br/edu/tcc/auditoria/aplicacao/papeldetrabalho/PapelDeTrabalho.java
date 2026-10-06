package br.edu.tcc.auditoria.aplicacao.papeldetrabalho;

import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.auditoria.ToleranciaDaExecucao;
import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.execucao.ExecucaoAuditoria;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Representa o papel de trabalho de uma execução, pronto para virar planilha, sempre com a identificação da execução.
// Emenda de 04/10/2026 (D018): carrega também os arquivos que a execução não conseguiu ler, vazio quando a leitura não foi registrada. Até essa data a planilha dizia "Documentos auditados" e não mencionava os arquivos que ficaram de fora.
public record PapelDeTrabalho(
        ExecucaoAuditoria execucao,
        List<LinhaDeAchado> achados,
        List<LinhaNaoAvaliada> naoAvaliados,
        List<MotivoAgrupado> motivosAgrupados,
        int itensNaoAvaliados,
        Optional<List<ArquivoIlegivel>> arquivosNaoLidos,
        Optional<Integer> documentosRepetidosDescartados,
        NaturezaDaCarga natureza,
        Optional<ToleranciaDaExecucao> tolerancia) {

    // Emenda de 04/10/2026 (D023): carrega a tolerância de valor da R05 que a execução usou, com a origem; vazia quando a execução não a registrou.

    // Emenda de 04/10/2026 (D021): carrega a natureza do catálogo da execução, para a planilha marcar o catálogo fictício tão visivelmente quanto a tela. Até essa data uma planilha gerada com catálogo inteiramente fictício não dizia isso em lugar nenhum.

    // Emenda de 04/10/2026 (D019): carrega quantos documentos repetidos, com o mesmo conteúdo, o lote descartou; vazio quando a execução não registrou essa contagem.

    // Construtor na aridade anterior à D018: a leitura fica não registrada, que é o que se pode dizer sem ela.
    public PapelDeTrabalho(
            ExecucaoAuditoria execucao,
            List<LinhaDeAchado> achados,
            List<LinhaNaoAvaliada> naoAvaliados,
            List<MotivoAgrupado> motivosAgrupados,
            int itensNaoAvaliados) {
        this(execucao, achados, naoAvaliados, motivosAgrupados, itensNaoAvaliados, Optional.empty(),
                Optional.empty(), NaturezaDaCarga.naoDeclarada(), Optional.empty());
    }

    // Valida o papel de trabalho e confere que os motivos agrupados e os achados batem com o detalhe e com o recibo da execução.
    public PapelDeTrabalho {
        if (execucao == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O papel de trabalho precisa da identificação da execução. Sem ela a planilha não "
                            + "diz contra qual catálogo nem com que regras foi produzida, e deixa de ser "
                            + "conferível depois.");
        }
        achados = copiar(achados, "achados");
        naoAvaliados = copiar(naoAvaliados, "não avaliados");
        motivosAgrupados = copiar(motivosAgrupados, "motivos agrupados");
        if (arquivosNaoLidos == null) {
            throw new PapelDeTrabalhoInvalido(
                    "Os arquivos não lidos vêm vazios quando a leitura não foi registrada, nunca nulos.");
        }
        arquivosNaoLidos = arquivosNaoLidos.map(lista -> copiar(lista, "arquivos não lidos"));
        if (tolerancia == null) {
            throw new PapelDeTrabalhoInvalido("A tolerância vem vazia quando não foi registrada, nunca nula.");
        }
        if (natureza == null) {
            throw new PapelDeTrabalhoInvalido(
                    "O papel de trabalho precisa da natureza do catálogo: planilha de catálogo fictício sem "
                            + "aviso é afirmação falsa sobre a lei. Carga sem declaração é "
                            + "NaturezaDaCarga.naoDeclarada(), nunca nula.");
        }
        if (documentosRepetidosDescartados == null) {
            throw new PapelDeTrabalhoInvalido(
                    "A contagem de documentos repetidos vem vazia quando não foi registrada, nunca nula.");
        }
        if (documentosRepetidosDescartados.isPresent() && documentosRepetidosDescartados.get() < 0) {
            throw new PapelDeTrabalhoInvalido("A contagem de documentos repetidos não pode ser negativa.");
        }

        if (itensNaoAvaliados < 0) {
            throw new PapelDeTrabalhoInvalido("A contagem de itens não avaliados não pode ser negativa.");
        }
        int somaDosAgrupados = motivosAgrupados.stream()
                .mapToInt(MotivoAgrupado::quantidade)
                .sum();
        if (somaDosAgrupados != naoAvaliados.size()) {
            throw new PapelDeTrabalhoInvalido(
                    ("Os motivos agrupados somam %d e há %d avaliações não concluídas. O resumo não pode "
                            + "divergir do detalhe: é a primeira coisa que quem confere confere.")
                            .formatted(somaDosAgrupados, naoAvaliados.size()));
        }
        if (execucao.quantidadeDeAchados() != achados.size()) {
            // Emenda de 04/10/2026 (D019): a mensagem passou a dizer a causa conhecida, além da genérica.
            throw new PapelDeTrabalhoInvalido(
                    ("O recibo da execução conta %d apontamentos e a planilha traria %d linhas. Isso "
                            + "significa que os apontamentos carregados não são os daquela execução. A causa "
                            + "conhecida é execução gravada antes de 04/10/2026 a partir de um lote com o "
                            + "mesmo documento repetido — por exemplo o -nfe.xml e o -procNFe.xml da mesma "
                            + "nota: o recibo contou as cópias, e o banco guardou um apontamento por item. "
                            + "A planilha não é emitida, porque teria de afirmar um dos dois números; "
                            + "audite o lote de novo, que a cópia passa a ser contada uma vez.")
                            .formatted(execucao.quantidadeDeAchados(), achados.size()));
        }
    }

    // Indica se a rodada não produziu apontamento nenhum.
    public boolean semAchados() {
        return achados.isEmpty();
    }

    public int quantidadeDeNaoAvaliados() {
        return naoAvaliados.size();
    }

    // Método auxiliar que copia a lista, recusando lista nula ou com elemento nulo.
    private static <T> List<T> copiar(List<T> linhas, String oQue) {
        if (linhas == null) {
            throw new PapelDeTrabalhoInvalido(
                    "A lista de %s deve ser vazia quando não há nenhum, nunca nula.".formatted(oQue));
        }
        if (linhas.stream().anyMatch(Objects::isNull)) {
            throw new PapelDeTrabalhoInvalido(
                    "A lista de %s não pode conter elemento nulo.".formatted(oQue));
        }
        return List.copyOf(linhas);
    }
}
