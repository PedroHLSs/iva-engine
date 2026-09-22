package br.edu.tcc.auditoria.infraestrutura.sal;

import br.edu.tcc.auditoria.infraestrutura.xml.SalDeInstalacao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Resolve o sal de instalação em cascata, sem exigir que alguém o declare à mão.
 *
 * <h2>A ordem, e por que é esta</h2>
 *
 * <ol>
 *   <li>propriedade de configuração {@code auditoria.pseudonimizacao.sal};</li>
 *   <li>variável de ambiente {@code AUDITORIA_PSEUDONIMIZACAO_SAL};</li>
 *   <li>arquivo local, fora do repositório;</li>
 *   <li>um sal aleatório novo, gravado no arquivo de (3).</li>
 * </ol>
 *
 * <p>As duas primeiras são exatamente as fontes que a Etapa 4 já lia, na mesma
 * precedência relativa. Isso é deliberado: quem já tem a variável definida
 * continua com o mesmo sal, e o comportamento de quem instalou antes não muda.
 * Inverter a ordem entre (1) e (2) faria uma instalação que declara as duas
 * passar a usar a outra — e, com o guarda de troca ligado, passar a recusar a
 * subida. Correto, mas gratuito.</p>
 *
 * <h2>Por que passou a existir um padrão, se a Etapa 4 dizia que não podia</h2>
 *
 * <p>O que a Etapa 4 proíbe é <em>sal fixo em código</em>: esse seria público,
 * está no repositório e no jar, e torna o pseudônimo reversível por força bruta.
 * Um sal sorteado de 256 bits na primeira subida e gravado fora do repositório
 * não tem nenhuma dessas propriedades — é tão secreto quanto um que a pessoa
 * escolhesse, e mais aleatório do que a maioria dos que ela escolheria.</p>
 *
 * <p>O que a geração automática troca é outra coisa: antes, esquecer de
 * configurar parava o sistema; agora, esquecer produz um segredo que só existe
 * naquela máquina. Por isso a criação é anunciada em nível visível, dizendo onde
 * o arquivo ficou — perder aquele arquivo é perder a continuidade dos
 * pseudônimos do acervo.</p>
 */
public final class ResolvedorDeSal {

    private static final Logger LOG = LoggerFactory.getLogger(ResolvedorDeSal.class);

    /** 256 bits, o mesmo tamanho do resumo que consome o sal. */
    static final int BYTES_DO_SAL_GERADO = 32;

    /**
     * Nome da propriedade que carrega o sal.
     *
     * <p>Repetido aqui porque a constante equivalente é interna ao pacote
     * {@code infraestrutura.xml} — o mesmo motivo, e o mesmo precedente, da
     * repetição que já existe em {@code ConfiguracaoDaAuditoria}.</p>
     */
    public static final String PROPRIEDADE = "auditoria.pseudonimizacao.sal";

    /** Nome da variável de ambiente que carrega o sal. Repetida pelo mesmo motivo. */
    public static final String VARIAVEL_DE_AMBIENTE = "AUDITORIA_PSEUDONIMIZACAO_SAL";

    private final ArquivoDeSalLocal arquivo;
    private final SecureRandom aleatorio;

    public ResolvedorDeSal(ArquivoDeSalLocal arquivo) {
        this(arquivo, new SecureRandom());
    }

    ResolvedorDeSal(ArquivoDeSalLocal arquivo, SecureRandom aleatorio) {
        if (arquivo == null) {
            throw new SalTrocado("O resolvedor precisa saber em que arquivo guardar o sal.");
        }
        this.arquivo = arquivo;
        this.aleatorio = aleatorio;
    }

    /**
     * Resolve o sal.
     *
     * <p>Recebe os dois valores configurados em vez de consultá-los, pelo mesmo
     * motivo que {@code SalDeInstalacao.de} recebe: estado global não se testa, e
     * o teste do caminho "nada configurado" não pode depender de como está o
     * ambiente de quem roda a suíte.</p>
     */
    public SalResolvido resolver(String daPropriedadeDeConfiguracao, String daVariavelDeAmbiente) {
        if (preenchido(daPropriedadeDeConfiguracao)) {
            // O Spring faz ligação relaxada: a variável de ambiente aparece TAMBÉM
            // como valor da propriedade. Quando os dois textos são o mesmo, quem
            // explica a instalação é a variável, e é ela que o diagnóstico informa —
            // dizer "propriedade de configuração" mandaria a pessoa procurar num
            // application.properties onde não há nada escrito.
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

    /**
     * Anuncia o sal novo em nível visível.
     *
     * <p>Não é depuração. É o único momento em que o sistema cria um segredo por
     * conta própria, e quem subiu precisa saber disso enquanto ainda está
     * olhando o terminal — depois, o arquivo vira uma pasta que ninguém abre até
     * o dia em que a máquina é trocada.</p>
     */
    private static void anunciar(SalResolvido resolvido, ArquivoDeSalLocal.Restricao restricao) {
        LOG.warn("Não havia sal de pseudonimização configurado: um sal novo foi gerado e gravado.");
        LOG.warn("  arquivo ............ {}", resolvido.arquivo().orElseThrow());
        LOG.warn("  impressão digital .. {}", resolvido.impressaoDigital().valor());
        LOG.warn("  permissão .......... {}", restricao.comoFoiFeita());
        LOG.warn("Guarde esse arquivo. Sem ele, os pseudônimos deste acervo não se reproduzem, e "
                + "o mesmo participante passaria a aparecer sob dois pseudônimos.");
    }

    private static SalResolvido semArquivo(String valor, OrigemDoSal origem) {
        return new SalResolvido(
                new SalDeInstalacao(valor), origem, Optional.empty(), Optional.empty());
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }
}
