package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ComparacaoDeclaradoEIndicado;

import java.util.List;

// Representa o quadro que põe o que a nota declarou ao lado do que a base indica. O quadro não diz se confere; quem julga são as sete regras, e o campo quemJulga lembra isso na tela.
public record ComparacaoExposta(String quemJulga, List<LinhaExposta> linhas) {

    // Valida que o quadro tenha a frase de quem julga e pelo menos uma linha.
    public ComparacaoExposta {
        if (quemJulga == null || quemJulga.isBlank()) {
            throw new RespostaInvalida(
                    "O quadro precisa dizer quem responde pelo veredito. Sem isso ele vira um veredito.");
        }
        if (linhas == null || linhas.isEmpty()) {
            throw new RespostaInvalida("O quadro precisa de linhas.");
        }
        linhas = List.copyOf(linhas);
    }

    // Método estático que converte o quadro da aplicação para a resposta.
    static ComparacaoExposta de(ComparacaoDeclaradoEIndicado comparacao) {
        return new ComparacaoExposta(
                ComparacaoDeclaradoEIndicado.QUEM_JULGA,
                comparacao.linhas().stream().map(LinhaExposta::de).toList());
    }

    // Representa uma linha do quadro: o campo, o que a nota declarou (ou o motivo de não ter declarado) e o que a base indica.
    public record LinhaExposta(
            String campo,
            String declarado,
            String motivoDoNaoDeclarado,
            LeituraExposta<String> indicado) {

        // Valida que a linha tenha o campo, o valor declarado ou o motivo de faltar, e o lado da base.
        public LinhaExposta {
            if (campo == null || campo.isBlank()) {
                throw new RespostaInvalida("A linha do quadro precisa nomear o campo.");
            }
            if ((declarado == null) == (motivoDoNaoDeclarado == null)) {
                throw new RespostaInvalida(
                        ("A linha \"%s\" precisa ou do valor declarado, ou do motivo de ele não estar "
                                + "lá. Célula em branco numa comparação é lida como igualdade.")
                                .formatted(campo));
            }
            if (indicado == null) {
                throw new RespostaInvalida(
                        "A linha \"%s\" precisa do lado da base normativa.".formatted(campo));
            }
        }

        // Método estático que converte uma linha da aplicação para a resposta.
        static LinhaExposta de(ComparacaoDeclaradoEIndicado.Linha linha) {
            return new LinhaExposta(
                    linha.campo(),
                    linha.declarado().orElse(null),
                    linha.motivoDoNaoDeclarado().orElse(null),
                    LeituraExposta.de(linha.indicado(), texto -> texto));
        }
    }
}
