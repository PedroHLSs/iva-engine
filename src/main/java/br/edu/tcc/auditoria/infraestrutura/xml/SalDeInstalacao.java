package br.edu.tcc.auditoria.infraestrutura.xml;

import java.nio.charset.StandardCharsets;

/**
 * Segredo de instalação usado na pseudonimização de CNPJ e CPF.
 *
 * <p><strong>Não tem valor padrão e não pode ter.</strong> Um sal fixo em código
 * seria público — está no repositório e no jar — e um pseudônimo calculado com
 * sal público é reversível por força bruta: são cerca de 10<sup>14</sup> CNPJ
 * possíveis, o que uma máquina comum percorre em minutos. O sal vem de fora,
 * é diferente em cada instalação, e não é versionado.</p>
 *
 * <p>Consequência operacional: o mesmo documento processado em duas instalações
 * com sais diferentes produz pseudônimos diferentes, e trocar o sal invalida a
 * comparação com tudo que foi processado antes. Isso é o esperado — o pseudônimo
 * serve para reconhecer o mesmo participante dentro de um acervo, não entre
 * acervos.</p>
 *
 * <p>De onde o valor é lido, nesta ordem:</p>
 *
 * <ol>
 *   <li>propriedade de sistema {@code auditoria.pseudonimizacao.sal};</li>
 *   <li>variável de ambiente {@code AUDITORIA_PSEUDONIMIZACAO_SAL}.</li>
 * </ol>
 *
 * <p>{@code toString} não devolve o valor: o sal é segredo, e objeto de
 * configuração costuma parar em log de inicialização.</p>
 */
public record SalDeInstalacao(String valor) {

    static final String PROPRIEDADE_DE_SISTEMA = "auditoria.pseudonimizacao.sal";
    static final String VARIAVEL_DE_AMBIENTE = "AUDITORIA_PSEUDONIMIZACAO_SAL";

    /**
     * Comprimento mínimo aceito.
     *
     * <p>Não é uma medida de entropia — não há como um construtor saber se o
     * texto é aleatório ou se é o nome da empresa. É só o piso que barra o
     * descuido óbvio, do tipo {@code "sal"} ou {@code "1234"}.</p>
     */
    static final int COMPRIMENTO_MINIMO = 32;

    public SalDeInstalacao {
        if (valor == null) {
            throw new SalDeInstalacaoInvalido("O sal de instalação não pode ser nulo.");
        }
        if (valor.length() < COMPRIMENTO_MINIMO) {
            // O valor recusado não entra na mensagem: é segredo.
            throw new SalDeInstalacaoInvalido(
                    ("O sal de instalação deve ter pelo menos %d caracteres, mas o valor configurado tem %d. "
                            + "Gere um valor aleatório longo e guarde-o fora do repositório.")
                            .formatted(COMPRIMENTO_MINIMO, valor.length()));
        }
        if (valor.isBlank()) {
            throw new SalDeInstalacaoInvalido("O sal de instalação não pode ser só espaço em branco.");
        }
    }

    /**
     * Lê o sal da configuração externa.
     *
     * @throws SalDeInstalacaoInvalido se não houver sal configurado, ou se o
     *                                 valor configurado for curto demais
     */
    public static SalDeInstalacao daConfiguracaoExterna() {
        return de(System.getProperty(PROPRIEDADE_DE_SISTEMA), System.getenv(VARIAVEL_DE_AMBIENTE));
    }

    /**
     * Resolve o sal a partir das duas fontes, na ordem de precedência.
     *
     * <p>Recebe os valores em vez de consultá-los porque estado global não se
     * testa: assim o teste do caso "não configurado" não depende de como está o
     * ambiente de quem roda a suíte.</p>
     */
    static SalDeInstalacao de(String daPropriedadeDeSistema, String daVariavelDeAmbiente) {
        String configurado = primeiroPreenchido(daPropriedadeDeSistema, daVariavelDeAmbiente);
        if (configurado == null) {
            throw new SalDeInstalacaoInvalido(
                    ("Não há sal de pseudonimização configurado. Defina a propriedade de sistema \"%s\" "
                            + "ou a variável de ambiente \"%s\" com um valor aleatório de pelo menos %d "
                            + "caracteres. O sistema não usa valor padrão: sal conhecido torna o "
                            + "pseudônimo reversível.")
                            .formatted(PROPRIEDADE_DE_SISTEMA, VARIAVEL_DE_AMBIENTE, COMPRIMENTO_MINIMO));
        }
        return new SalDeInstalacao(configurado);
    }

    byte[] bytes() {
        return valor.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "SalDeInstalacao[valor oculto]";
    }

    private static String primeiroPreenchido(String primeiro, String segundo) {
        if (primeiro != null && !primeiro.isBlank()) {
            return primeiro;
        }
        if (segundo != null && !segundo.isBlank()) {
            return segundo;
        }
        return null;
    }
}
