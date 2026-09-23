package br.edu.tcc.auditoria.infraestrutura.sal;

import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

// Classe que resolve o sal nesta ordem: propriedade auditoria.pseudonimizacao.sal, variável AUDITORIA_PSEUDONIMIZACAO_SAL, arquivo local e, por último, um sal novo de 256 bits gravado nesse arquivo. As duas primeiras seguem a ordem da Etapa 4, e o que continua proibido é sal fixo no código.
public final class ResolvedorDeSal {

    private static final Logger LOG = LoggerFactory.getLogger(ResolvedorDeSal.class);

    // Tamanho do sal gerado: 256 bits.
    static final int BYTES_DO_SAL_GERADO = 32;

    // Nome da propriedade do sal, repetido aqui porque a constante original é interna ao pacote xml.
    public static final String PROPRIEDADE = "auditoria.pseudonimizacao.sal";

    // Nome da variável de ambiente do sal, repetido pelo mesmo motivo.
    public static final String VARIAVEL_DE_AMBIENTE = "AUDITORIA_PSEUDONIMIZACAO_SAL";

    private final ArquivoDeSalLocal arquivo;
    private final SecureRandom aleatorio;

    // Construtor que recebe o arquivo local e usa um gerador aleatório seguro.
    public ResolvedorDeSal(ArquivoDeSalLocal arquivo) {
        this(arquivo, new SecureRandom());
    }

    // Construtor que recebe o arquivo local e o gerador aleatório.
    ResolvedorDeSal(ArquivoDeSalLocal arquivo, SecureRandom aleatorio) {
        if (arquivo == null) {
            throw new SalTrocado("O resolvedor precisa saber em que arquivo guardar o sal.");
        }
        this.arquivo = arquivo;
        this.aleatorio = aleatorio;
    }

    // Resolve o sal. Recebe os dois valores configurados, em vez de ler o ambiente, para o teste não depender da máquina de quem roda.
    public SalResolvido resolver(String daPropriedadeDeConfiguracao, String daVariavelDeAmbiente) {
        if (preenchido(daPropriedadeDeConfiguracao)) {
            // O Spring também mostra a variável de ambiente como propriedade; se os dois valores são iguais, a origem informada é a variável, para ninguém procurar no application.properties.
            OrigemDoSal origem = daPropriedadeDeConfiguracao.equals(daVariavelDeAmbiente)
                    ? OrigemDoSal.VARIAVEL_DE_AMBIENTE
                    : OrigemDoSal.PROPRIEDADE_DE_CONFIGURACAO;
            return semArquivo(daPropriedadeDeConfiguracao, origem);
        }
        if (preenchido(daVariavelDeAmbiente)) {
            return semArquivo(daVariavelDeAmbiente, OrigemDoSal.VARIAVEL_DE_AMBIENTE);
        }

        Optional<String> gravado = arquivo.ler();
        if (gravado.isPresent()) {
            return new SalResolvido(
                    new SalDeInstalacao(gravado.get()),
                    OrigemDoSal.ARQUIVO_LOCAL,
                    Optional.of(arquivo.caminho()),
                    Optional.empty());
        }
        return gerarEGravar();
    }

    // Método auxiliar que sorteia um sal novo, grava no arquivo e avisa no log.
    private SalResolvido gerarEGravar() {
        byte[] bytes = new byte[BYTES_DO_SAL_GERADO];
        aleatorio.nextBytes(bytes);
        String novo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ArquivoDeSalLocal.Restricao restricao = arquivo.gravar(novo);
        SalResolvido resolvido = new SalResolvido(
                new SalDeInstalacao(novo),
                OrigemDoSal.GERADO_AGORA,
                Optional.of(arquivo.caminho()),
                Optional.of(restricao));

        anunciar(resolvido, restricao);
        return resolvido;
    }

    // Método auxiliar que avisa no log, em nível visível, que um sal novo foi criado e onde ele está, para quem subiu guardar o arquivo.
    private static void anunciar(SalResolvido resolvido, ArquivoDeSalLocal.Restricao restricao) {
        LOG.warn("Não havia sal de pseudonimização configurado: um sal novo foi gerado e gravado.");
        LOG.warn("  arquivo ............ {}", resolvido.arquivo().orElseThrow());
        LOG.warn("  impressão digital .. {}", resolvido.impressaoDigital().valor());
        LOG.warn("  permissão .......... {}", restricao.comoFoiFeita());
        LOG.warn("Guarde esse arquivo. Sem ele, os pseudônimos deste acervo não se reproduzem, e "
                + "o mesmo participante passaria a aparecer sob dois pseudônimos.");
    }

    // Método auxiliar que monta o resultado para sal que não veio de arquivo.
    private static SalResolvido semArquivo(String valor, OrigemDoSal origem) {
        return new SalResolvido(
                new SalDeInstalacao(valor), origem, Optional.empty(), Optional.empty());
    }

    // Método auxiliar que diz se o valor está preenchido.
    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
