package br.edu.tcc.auditoria.infraestrutura.xml;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As classes geradas a partir do XSD são detalhe de infraestrutura, e só o
 * pacote {@code infraestrutura.xml} pode enxergá-las.
 *
 * <p>{@code DominioNaoDependeDeFrameworkTest} já barra o domínio. Falta a
 * aplicação e o resto da infraestrutura: nada impede, hoje, que um caso de uso
 * receba um {@code TNFe} por parâmetro porque foi conveniente, e o dia em que
 * isso acontecer o leiaute da NF-e vira parte da assinatura do sistema — trocar
 * a versão do esquema passa a mexer em regra de negócio.</p>
 *
 * <p>A mesma proibição vale para o próprio JAXB: quem quiser ler XML fora deste
 * pacote está reimplementando a fronteira que já existe.</p>
 *
 * <h2>Duas varreduras, porque a dependência tem duas grafias</h2>
 *
 * <ul>
 *   <li>a linha {@code import}, que é como a violação costuma aparecer;</li>
 *   <li>o nome totalmente qualificado escrito no corpo do arquivo, que é como
 *       ela aparece quando alguém quer evitar o import — um retorno declarado
 *       como {@code br.edu.tcc.auditoria.infraestrutura.xml.gerado.TNFe} não
 *       gera linha de import nenhuma e passava pela primeira varredura.</li>
 * </ul>
 *
 * <p>É a mesma correção que o guarda da D001 recebeu na revisão de conformidade
 * da Etapa 1. Este aqui ficou para trás e só foi corrigido depois, quando quatro
 * pacotes novos já haviam sido criados sob a fronteira que ele fiscaliza.</p>
 *
 * <p>As duas varreduras não se excluem: uma linha de {@code import} é acusada
 * pelas duas. A redundância é deliberada — a segunda é um superconjunto da
 * primeira, e continua valendo se alguém mexer na detecção de import.</p>
 */
class ClassesGeradasNaoVazamTest {

    private static final Path RAIZ_DO_CODIGO =
            Path.of("src", "main", "java", "br", "edu", "tcc", "auditoria");

    private static final Path FRONTEIRA_PERMITIDA =
            RAIZ_DO_CODIGO.resolve(Path.of("infraestrutura", "xml"));

    private static final List<String> PACOTES_RESTRITOS = List.of(
            "br.edu.tcc.auditoria.infraestrutura.xml.gerado",
            "jakarta.xml.bind",
            "javax.xml.stream");

    private static final Pattern TEXTO_LITERAL =
            Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    @Test
    void deveEncontrarCodigoForaDaFronteiraParaInspecionar() throws IOException {
        assertThat(arquivosForaDaFronteira()).isNotEmpty();
    }

    @Test
    void somenteInfraestruturaXmlPodeImportarAsClassesGeradasEOJaxb() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String linha = linhas.get(indice).strip();
                if (!linha.startsWith("import ")) {
                    continue;
                }
                int numeroDaLinha = indice + 1;
                PACOTES_RESTRITOS.stream()
                        .filter(linha::contains)
                        .findFirst()
                        .ifPresent(restrito -> violacoes.add(
                                "%s:%d importa %s".formatted(
                                        RAIZ_DO_CODIGO.relativize(arquivo), numeroDaLinha, restrito)));
            }
        }

        assertThat(violacoes)
                .as("Só \"%s\" pode conhecer o leiaute da NF-e. Quem precisar de documento fora dali "
                        + "recebe Documento e ItemDocumento do domínio — ver D005 em "
                        + "docs/DECISOES-ARQUITETURA.md.", FRONTEIRA_PERMITIDA)
                .isEmpty();
    }

    @Test
    void somenteInfraestruturaXmlPodeCitarAsClassesGeradasPeloNomeQualificado() throws IOException {
        List<String> violacoes = new ArrayList<>();

        for (Path arquivo : arquivosForaDaFronteira()) {
            List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            for (int indice = 0; indice < linhas.size(); indice++) {
                String codigo = codigoSemComentarioNemTexto(linhas.get(indice));
                int numeroDaLinha = indice + 1;
                PACOTES_RESTRITOS.stream()
                        .filter(codigo::contains)
                        .findFirst()
                        .ifPresent(restrito -> violacoes.add(
                                "%s:%d cita %s".formatted(
                                        RAIZ_DO_CODIGO.relativize(arquivo), numeroDaLinha, restrito)));
            }
        }

        assertThat(violacoes)
                .as("Escrever o nome por extenso não é uma forma legítima de contornar a proibição de "
                        + "import: um campo declarado como \"%s.TNFe\" leva o leiaute da NF-e para fora "
                        + "de \"%s\" exatamente como o import levaria — ver D005 em "
                        + "docs/DECISOES-ARQUITETURA.md. Comentários e literais de texto são ignorados "
                        + "nesta varredura, de modo que citar o pacote para explicar por que ele não "
                        + "entra continua permitido.",
                        PACOTES_RESTRITOS.get(0), FRONTEIRA_PERMITIDA)
                .isEmpty();
    }

    /**
     * Devolve a linha sem comentário e sem literal de texto, ou vazio se a
     * linha inteira é comentário.
     *
     * <p>Repete o auxiliar de {@code DominioNaoDependeDeFrameworkTest} em vez de
     * compartilhá-lo: extrair para um lugar comum obrigaria a mexer naquele
     * teste, que é da Etapa 1 e fiscaliza outra decisão. Dois guardas
     * independentes valem a duplicação de doze linhas.</p>
     *
     * <p>Apoia-se no estilo em uso no projeto: bloco de comentário sempre com
     * {@code *} no início de cada linha de continuação, e nenhum bloco aberto e
     * fechado no meio de uma linha de código. Se esse estilo mudar, este teste
     * passa a acusar demais, e não de menos — que é o lado seguro de errar.</p>
     */
    private static String codigoSemComentarioNemTexto(String linhaBruta) {
        String linha = linhaBruta.strip();
        if (linha.startsWith("*") || linha.startsWith("/*") || linha.startsWith("//")) {
            return "";
        }
        String semTexto = TEXTO_LITERAL.matcher(linha).replaceAll("\"\"");
        int inicioDoComentario = semTexto.indexOf("//");
        return inicioDoComentario < 0 ? semTexto : semTexto.substring(0, inicioDoComentario);
    }

    private static List<Path> arquivosForaDaFronteira() throws IOException {
        try (Stream<Path> caminhos = Files.walk(RAIZ_DO_CODIGO)) {
            return caminhos.filter(Files::isRegularFile)
                    .filter(caminho -> caminho.getFileName().toString().endsWith(".java"))
                    .filter(caminho -> !caminho.startsWith(FRONTEIRA_PERMITIDA))
                    .toList();
        }
    }
}
