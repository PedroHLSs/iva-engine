package br.edu.tcc.auditoria.infraestrutura.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Opções de um comando, no formato {@code --nome=valor}.
 *
 * <p>Formato único e sem abreviação: {@code --origem=dados/agosto}. Não há
 * {@code -o}, não há {@code --origem dados} separado por espaço, e não há
 * ordem posicional. A rigidez é intencional — o comando que apaga um dia de
 * trabalho por causa de um espaço mal colocado não é um comando bom.</p>
 *
 * <p>Opção sem valor ({@code --apenas-abertos}) é sinalizador e vale
 * verdadeiro.</p>
 */
record Argumentos(String comando, Map<String, String> opcoes) {

    private static final String PREFIXO = "--";

    Argumentos {
        opcoes = Map.copyOf(opcoes);
    }

    /** Interpreta os argumentos brutos recebidos na linha de comando. */
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

    /** Valor da opção, vazio se ela não foi informada ou veio em branco. */
    Optional<String> texto(String nome) {
        String valor = opcoes.get(nome);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor.strip());
    }

    /** Valor da opção; erro de uso se ela não foi informada. */
    String textoObrigatorio(String nome) {
        return texto(nome).orElseThrow(() -> new UsoInvalido(
                "A opção \"--%s\" é obrigatória.".formatted(nome)));
    }

    /** Caminho da opção; erro de uso se ela não foi informada. */
    Path caminhoObrigatorio(String nome) {
        return Path.of(textoObrigatorio(nome));
    }

    /** Caminho da opção, se informada. */
    Optional<Path> caminho(String nome) {
        return texto(nome).map(Path::of);
    }

    /** Identificador da opção; erro de uso se ausente ou malformado. */
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

    /** Número inteiro da opção, ou o padrão se ela não foi informada. */
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

    /** Indica se o sinalizador foi informado. */
    boolean sinalizador(String nome) {
        return opcoes.containsKey(nome);
    }

    /** Recusa opções que o comando não conhece, em vez de ignorá-las em silêncio. */
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
