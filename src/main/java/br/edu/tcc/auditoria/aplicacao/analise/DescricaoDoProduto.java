package br.edu.tcc.auditoria.aplicacao.analise;

import java.util.Optional;
// Interface utilizada com o intuito de representar a descrição do produto
public sealed interface DescricaoDoProduto {

    String NAO_VEIO_NO_DOCUMENTO = "o documento não declarou descrição para este item";

    String NAO_LIDA_NESTA_ANALISE =
            "este item não foi registrado pela leitura desta análise. É incoerência interna, e não "
                    + "afirmação sobre o que o documento trouxe";

    String ANTERIOR_AO_REGISTRO =
            "esta análise é anterior ao registro da descrição do produto, e o sistema guarda o item, "
                    + "não versões dele: não há como recuperar o que ela leu";

    Optional<String> texto();

    Optional<String> motivoDaAusencia();

    record Declarada(String valor) implements DescricaoDoProduto {

        public Declarada {
            if (valor == null || valor.isBlank()) {
                throw new AnaliseInvalida(
                        "Descrição em branco é ausência, e ausência se representa com NaoDeclarada e o "
                                + "motivo. Texto vazio na tela seria indistinguível de campo que "
                                + "ninguém preencheu.");
            }
        }

        @Override
        public Optional<String> texto() {
            return Optional.of(valor);
        }

        @Override
        public Optional<String> motivoDaAusencia() {
            return Optional.empty();
        }
    }

    record NaoDeclarada(String motivo) implements DescricaoDoProduto {

        public NaoDeclarada {
            if (motivo == null || motivo.isBlank()) {
                throw new AnaliseInvalida(
                        "Ausência de descrição precisa do motivo: sem ele a tela mostra um espaço em "
                                + "branco, e espaço em branco não diz de quem é a falta.");
            }
        }

        @Override
        public Optional<String> texto() {
            return Optional.empty();
        }

        @Override
        public Optional<String> motivoDaAusencia() {
            return Optional.of(motivo);
        }
    }

    static DescricaoDoProduto de(Optional<String> texto) {
        if (texto == null) {
            throw new AnaliseInvalida(
                    "Ausência de descrição se representa com Optional.empty(), nunca com nulo.");
        }
        return texto.filter(lido -> !lido.isBlank())
                .<DescricaoDoProduto>map(Declarada::new)
                .orElseGet(() -> new NaoDeclarada(NAO_VEIO_NO_DOCUMENTO));
    }

    static DescricaoDoProduto anteriorAoRegistro() {
        return new NaoDeclarada(ANTERIOR_AO_REGISTRO);
    }

    static DescricaoDoProduto naoLidaNestaAnalise() {
        return new NaoDeclarada(NAO_LIDA_NESTA_ANALISE);
    }
}
