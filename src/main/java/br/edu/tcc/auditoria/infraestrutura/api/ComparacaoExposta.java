package br.edu.tcc.auditoria.infraestrutura.api;

import br.edu.tcc.auditoria.aplicacao.conferencia.ComparacaoDeclaradoEIndicado;

import java.util.List;

/**
 * O declarado ao lado do indicado, como a tela recebe o quadro.
 *
 * <p>{@code quemJulga} não é aviso legal: é a frase que impede a tela de ser lida
 * como um segundo veredito. O quadro não escreve "confere" nem "não confere", e o
 * motivo está em {@link ComparacaoDeclaradoEIndicado} — quem julga são as sete
 * regras, e o julgamento delas já está na situação do produto.</p>
 */
public record ComparacaoExposta(String quemJulga, List<LinhaExposta> linhas) {

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

    static ComparacaoExposta de(ComparacaoDeclaradoEIndicado comparacao) {
        return new ComparacaoExposta(
                ComparacaoDeclaradoEIndicado.QUEM_JULGA,
                comparacao.linhas().stream().map(LinhaExposta::de).toList());
    }

    /** Um campo, o que o documento declarou e o que a carga indica. */
    public record LinhaExposta(
            String campo,
            String declarado,
            String motivoDoNaoDeclarado,
            LeituraExposta<String> indicado) {

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

        static LinhaExposta de(ComparacaoDeclaradoEIndicado.Linha linha) {
            return new LinhaExposta(
                    linha.campo(),
                    linha.declarado().orElse(null),
                    linha.motivoDoNaoDeclarado().orElse(null),
                    LeituraExposta.de(linha.indicado(), texto -> texto));
        }
    }
}
