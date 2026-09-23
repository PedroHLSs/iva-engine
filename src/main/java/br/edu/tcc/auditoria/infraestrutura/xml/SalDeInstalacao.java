package br.edu.tcc.auditoria.infraestrutura.xml;

import java.nio.charset.StandardCharsets;

// Representa o segredo da instalação usado para pseudonimizar CNPJ e CPF. Nunca há sal fixo no código, porque sal público deixa descobrir o CNPJ por força bruta, e o toString não mostra o valor. Mudou na Etapa 10: quem monta o sal em produção passou a ser o ResolvedorDeSal, e sem sal configurado o sistema gera um, em vez de parar.
public record SalDeInstalacao(String valor) {

    static final String PROPRIEDADE_DE_SISTEMA = "auditoria.pseudonimizacao.sal";
    static final String VARIAVEL_DE_AMBIENTE = "AUDITORIA_PSEUDONIMIZACAO_SAL";

    // Tamanho mínimo aceito. Não mede se o sal é aleatório; só barra descuido óbvio, como "sal" ou "1234".
    static final int COMPRIMENTO_MINIMO = 32;

    // Valida que o sal exista, tenha pelo menos 32 caracteres e não seja só espaço.
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

    // Método estático que lê o sal da propriedade de sistema ou da variável de ambiente; recusa se não houver ou se for curto.
    public static SalDeInstalacao daConfiguracaoExterna() {
        return de(System.getProperty(PROPRIEDADE_DE_SISTEMA), System.getenv(VARIAVEL_DE_AMBIENTE));
    }

    // Método estático que escolhe o sal entre as duas fontes, na ordem. Recebe os valores, em vez de ler o ambiente, para o teste não depender da máquina.
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

    // Retorna o sal em bytes UTF-8.
    byte[] bytes() {
        return valor.getBytes(StandardCharsets.UTF_8);
    }

    // Não mostra o valor do sal, que é segredo.
    @Override
    public String toString() {
        return "SalDeInstalacao[valor oculto]";
    }

    // Método auxiliar que devolve o primeiro valor preenchido, ou null.
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
