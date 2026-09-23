package br.edu.tcc.auditoria.aplicacao.conferencia;

// Interface selada que representa a versão da regra que produziu uma verificação, ou o motivo de não se saber.
public sealed interface VersaoDaRegra {

    // Retorna o texto para a tela: a versão, ou a frase que a substitui.
    String paraLeitura();

    // Cria uma versão registrada a partir do valor gravado.
    static VersaoDaRegra registrada(String valor) {
        return new Registrada(valor);
    }

    // Cria uma versão não registrada, com o motivo.
    static VersaoDaRegra naoRegistrada(String motivo) {
        return new NaoRegistrada(motivo);
    }

    // Representa a versão lida de uma linha gravada: um apontamento ou uma avaliação não concluída.
    record Registrada(String valor) implements VersaoDaRegra {

        // Valida que a versão registrada tenha valor.
        public Registrada {
            if (valor == null || valor.isBlank()) {
                throw new ConferenciaInvalida(
                        "Uma versão registrada precisa do valor. Se não há, use "
                                + "VersaoDaRegra.naoRegistrada(motivo).");
            }
        }

        @Override
        public String paraLeitura() {
            return valor;
        }
    }

    // Representa a versão ausente, quando não há linha gravada de onde tirá-la.
    record NaoRegistrada(String motivo) implements VersaoDaRegra {

        // Valida que a versão ausente traga o motivo.
        public NaoRegistrada {
            if (motivo == null || motivo.isBlank()) {
                throw new ConferenciaInvalida(
                        "Versão de regra ausente precisa registrar o motivo: campo em branco é "
                                + "indistinguível de campo que ninguém preencheu.");
            }
        }

        @Override
        public String paraLeitura() {
            return "(não registrada)";
        }
    }
}
