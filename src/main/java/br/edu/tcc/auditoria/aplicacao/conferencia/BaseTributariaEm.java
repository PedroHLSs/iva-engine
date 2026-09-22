package br.edu.tcc.auditoria.aplicacao.conferencia;

import br.edu.tcc.auditoria.aplicacao.catalogo.NaturezaDaCarga;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A base normativa carregada, como ela responde numa data.
 *
 * <h2>A data é do pedido, e vai escrita na resposta</h2>
 *
 * <p>Não existe "a base tributária" sem data: o mesmo catálogo responde coisas
 * diferentes em datas diferentes, que é a razão de ele guardar vigência. Por isso
 * a data volta no corpo, e não fica só na URL — a tela a exibe junto do conteúdo,
 * e uma impressão da tela continua dizendo a que dia ela se refere.</p>
 *
 * <h2>Não perguntado e sem resposta são estados diferentes</h2>
 *
 * <p>Os três campos de consulta pontual são {@link Optional} de uma
 * {@link LeituraDoCatalogo}, e os dois níveis querem dizer coisas distintas:
 * ausente quer dizer que ninguém perguntou por aquele NCM ou por aquele
 * {@code cClassTrib}; presente com motivo quer dizer que perguntaram e a carga
 * nada diz. Colapsar os dois faria a tela parecer ter consultado o que não
 * consultou.</p>
 *
 * <h2>Por que não há despejo das tabelas</h2>
 *
 * <p>Ver {@link BaseNormativa}: os repositórios do domínio expõem busca pontual,
 * e alargá-los sairia da restrição desta etapa. {@code comoConsultar} diz isso na
 * própria resposta, para que quem abre a tela entenda que a ausência de listagem
 * é decisão, e não tela pela metade.</p>
 */
public record BaseTributariaEm(
        LocalDate data,
        String versaoDoCatalogo,
        boolean cargaDisponivel,
        NaturezaDaCarga natureza,
        Optional<CoberturaDoCatalogo> cobertura,
        List<TratamentoDeTributo> aliquotas,
        Optional<LeituraDoCatalogo<DescricaoDeNcm>> ncmConsultado,
        Optional<LeituraDoCatalogo<EnquadramentoDoNcm>> anexosDoNcmConsultado,
        Optional<LeituraDoCatalogo<ClassificacaoDoCatalogo>> classificacaoConsultada) {

    /** O que a tela escreve para explicar o formato de consulta. */
    public static final String COMO_CONSULTAR =
            "Esta tela consulta a base carregada por NCM e por cClassTrib, e mostra as alíquotas "
                    + "vigentes na data escolhida. Ela não lista as tabelas inteiras: a de NCM tem "
                    + "milhares de linhas, e o sistema guarda o catálogo para responder perguntas "
                    + "datadas, não para ser folheado.";

    public BaseTributariaEm {
        if (data == null) {
            throw new ConferenciaInvalida(
                    "A base tributária só existe datada: sem data a resposta não significa nada.");
        }
        if (natureza == null) {
            throw new ConferenciaInvalida(
                    "A resposta precisa da procedência da carga: sem ela a tela exibiria tabela de "
                            + "demonstração sem dizer que é.");
        }
        if (versaoDoCatalogo == null || versaoDoCatalogo.isBlank()) {
            throw new ConferenciaInvalida(
                    "A resposta precisa dizer de qual carga ela veio, ainda que seja para dizer que não "
                            + "há nenhuma.");
        }
        if (cobertura == null
                || aliquotas == null
                || ncmConsultado == null
                || anexosDoNcmConsultado == null
                || classificacaoConsultada == null) {
            throw new ConferenciaInvalida(
                    "Ausência se representa com Optional.empty() e com lista vazia, nunca com nulo.");
        }
        if (cargaDisponivel == cobertura.isEmpty()) {
            throw new ConferenciaInvalida(
                    "Ou a carga está gravada e a cobertura declarada dela vem junto, ou não está e não "
                            + "vem. As duas afirmações precisam concordar.");
        }
        if (ncmConsultado.isPresent() != anexosDoNcmConsultado.isPresent()) {
            throw new ConferenciaInvalida(
                    "Consultar um NCM responde as duas coisas: o que a carga diz dele e a que anexos ela "
                            + "o vincula. Responder uma sem a outra esconderia metade do enquadramento.");
        }
        aliquotas = List.copyOf(aliquotas);
    }

    public String comoConsultar() {
        return COMO_CONSULTAR;
    }
}
