package br.edu.tcc.auditoria.infraestrutura.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// Representa o comando e as opções da linha de comando, sempre no formato --nome=valor, sem abreviação nem posição. Opção sem valor, como --apenas-abertos, é um sinalizador.
record Argumentos(String comando, Map<String, String> opcoes) {

    private static final String PREFIXO = "--";

    // Guarda uma cópia imutável das opções.
    Argumentos {
        opcoes = Map.copyOf(opcoes);
    }

    // Método estático que interpreta os argumentos: o primeiro é o comando e os outros são opções; recusa opção sem --, sem nome ou repetida.
    static Argumentos de(String... brutos) {
        if (brutos == null || brutos.length == 0 || brutos[0].startsWith(PREFIXO)) {
            throw new UsoInvalido(
                    "O primeiro argumento precisa ser o nome do comando, antes de qualquer opção.");
        }

        Map<String, String> opcoes = new LinkedHashMap<>();
        for (int posicao = 1; posicao < brutos.length; posicao++) {
            String bruto = brutos[posicao];
            if (!bruto.startsWith(PREFIXO)) {
                throw new UsoInvalido(
                        ("Argumento \"%s\" não é uma opção. Toda opção começa com \"%s\" e leva o valor "
                                + "junto, como \"--origem=dados\".").formatted(bruto, PREFIXO));
            }
            String semPrefixo = bruto.substring(PREFIXO.length());
            int igual = semPrefixo.indexOf('=');
            String nome = igual < 0 ? semPrefixo : semPrefixo.substring(0, igual);
            String valor = igual < 0 ? "" : semPrefixo.substring(igual + 1);

            if (nome.isBlank()) {
                throw new UsoInvalido("Há uma opção sem nome em \"%s\".".formatted(bruto));
            }
            if (opcoes.put(nome, valor) != null) {
                throw new UsoInvalido(
                        ("A opção \"--%s\" foi informada mais de uma vez. Qual delas valeria não é "
                                + "óbvio, então nenhuma vale.").formatted(nome));
            }
        }
        return new Argumentos(brutos[0], opcoes);
    }

    // Devolve o valor da opção, ou vazio se ela não veio ou veio em branco.
    Optional<String> texto(String nome) {
        String valor = opcoes.get(nome);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }

    // Devolve o valor da opção; recusa se ela não foi informada.
    String textoObrigatorio(String nome) {
        return texto(nome).orElseThrow(() -> new UsoInvalido(
                "A opção \"--%s\" é obrigatória.".formatted(nome)));
    }

    // Devolve a opção como caminho; recusa se ela não foi informada.
    Path caminhoObrigatorio(String nome) {
        return Path.of(textoObrigatorio(nome));
    }

    // Devolve a opção como caminho, se ela foi informada.
    Optional<Path> caminho(String nome) {
        return texto(nome).map(Path::of);
    }

    // Devolve a opção como identificador; recusa se faltar ou estiver malformada.
    UUID identificadorObrigatorio(String nome) {
        String valor = textoObrigatorio(nome);
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException malformado) {
            throw new UsoInvalido(
                    "A opção \"--%s\" deve ser o identificador do apontamento, como aparece em "
                            .formatted(nome)
                            + "\"listar-achados\", mas veio \"%s\".".formatted(valor));
        }
    }

    // Devolve a opção como número inteiro, ou o padrão se ela não foi informada.
    int inteiro(String nome, int padrao) {
        return texto(nome).map(valor -> {
            try {
                return Integer.parseInt(valor);
            } catch (NumberFormatException naoENumero) {
                throw new UsoInvalido(
                        "A opção \"--%s\" deve ser um número inteiro, mas veio \"%s\"."
                                .formatted(nome, valor));
            }
        }).orElse(padrao);
    }

    // Diz se o sinalizador foi informado.
    boolean sinalizador(String nome) {
        return opcoes.containsKey(nome);
    }

    // Recusa opção que o comando não conhece, em vez de ignorá-la.
    void exigirSomente(List<String> conhecidas) {
        for (String informada : opcoes.keySet()) {
            if (!conhecidas.contains(informada)) {
                throw new UsoInvalido(
                        ("O comando \"%s\" não conhece a opção \"--%s\". Opções aceitas: %s.")
                                .formatted(comando, informada, String.join(", ", conhecidas)));
            }
        }
    }
}
