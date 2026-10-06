package br.edu.tcc.auditoria.aplicacao.historico;

import java.util.List;

// Representa uma página do histórico: as linhas, o total que o filtro alcança, e os executores que aparecem no histórico, para a tela oferecer no filtro.
// Emenda de 04/10/2026 (D020): traz quantas execuções do resultado não têm contagem medida. Nenhum filtro de quantidade ou de situação as exclui, e a tela precisa dizer por que estão ali.
public record PaginaDoHistorico(
        List<LinhaDoHistorico> linhas,
        long totalDeLinhas,
        int pagina,
        int tamanho,
        List<LinhaDoHistorico.ExecutorRegistrado> executoresConhecidos,
        long execucoesNaoMedidasNoResultado) {

    // Guarda cópias imutáveis das listas.
    public PaginaDoHistorico {
        if (linhas == null || executoresConhecidos == null || totalDeLinhas < 0) {
            throw new HistoricoInvalido("A página do histórico veio incompleta.");
        }
        if (execucoesNaoMedidasNoResultado < 0 || execucoesNaoMedidasNoResultado > totalDeLinhas) {
            throw new HistoricoInvalido(
                    "A quantidade de execuções não medidas tem de estar entre zero e o total do resultado.");
        }
        linhas = List.copyOf(linhas);
        executoresConhecidos = List.copyOf(executoresConhecidos);
    }
}
